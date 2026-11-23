package umbra.entities;

import java.util.Random;

import umbra.core.Config;

/**
 * El carácter de Logi: cuantos más fragmentos lleva el jugador, más se resiste Logi.
 * Es lógica pura (sin dibujo ni ventana): cada cuadro recibe lo que pasa y dice a dónde
 * debe ir Logi (el objetivo), con qué suavizado y qué oscuridad aplicar.
 *
 * <p>Máquina de estados, un arrebato completo:
 * <pre>
 *   CALM --(se cumple la espera)--&gt; WARN --(0.8 s)--&gt; FROZEN --(quieto)--&gt; PULL --(tira)--&gt; CALM
 * </pre>
 * <ul>
 * <li>CALM: Logi obedece al ratón; cuenta la espera hasta el siguiente arrebato.</li>
 * <li>WARN: aviso (Logi parpadea); todavía obedece, y se puede fijar a Logi para cancelarlo.</li>
 * <li>FROZEN: el objetivo se queda donde estaba Logi (no responde al ratón).</li>
 * <li>PULL: el objetivo avanza en línea recta hacia Candil a velocidad fija, con suavizado 1.</li>
 * </ul>
 * Las duraciones dependen del tramo de fragmentos (1-2, 3-4, 5-6). Con 0 fragmentos todo
 * queda como antes: siempre CALM, factor 1 y oscuridad 0.
 *
 * <p>Matemáticas: el objetivo avanza con un paso {@code v * dt} sobre el vector unitario
 * hacia su meta, y se recorta para no pasarse. Sin asignaciones por cuadro.
 */
public final class LogiTemper {

    public enum Phase { CALM, WARN, FROZEN, PULL }

    /**
     * Distancia a la que Logi se detiene de Candil, tanto al acercarse solo como al tirar (px).
     * Queda fuera del radio en que Candil se desmorona: Logi quita la sombra y la vida, no desmorona.
     */
    public static final double SAFE_DISTANCE = Config.CRUMBLE_RADIUS + 35;

    public static final double WARN_TIME = 0.8, UNPIN_GRACE = 1.5, IDLE_DELAY = 2.0, IDLE_STOP = SAFE_DISTANCE;

    private static final int MAX_FRAGMENTS = 6;

    // Por tramo (1-2, 3-4, 5-6): espera mínima y máxima, tiempo quieto y tiempo que tira.
    private static final double[] WAIT_MIN = { 25, 18, 12 };
    private static final double[] WAIT_MAX = { 35, 25, 18 };
    private static final double[] FROZEN_TIME = { 0.6, 0.8, 1.0 };
    private static final double[] PULL_TIME = { 1.0, 1.3, 1.6 };

    private final Random rnd;
    /** Velocidad del tirón, fija para toda la partida (250 a 350 px/s). */
    private final double pullSpeed;

    private Phase phase = Phase.CALM;
    private double phaseTimer;       // tiempo dentro de WARN / FROZEN / PULL
    private double waitTimer;        // tiempo transcurrido en CALM desde el último arrebato
    private double waitTarget;       // espera elegida al azar para este arrebato
    private int tier = -1;           // tramo con el que se eligió waitTarget
    private double frozenTime, pullTime; // duraciones del arrebato en curso
    private boolean deferred;        // la espera se cumplió con Logi fijo: esperando soltarlo
    private double graceLeft;
    private double sinceUnpin;       // tiempo desde que se soltó a Logi (0 mientras está fijo)

    private double targetX, targetY;
    private double frozenX, frozenY;
    private double idleTime;         // tiempo con el ratón sin moverse
    private boolean drifting;        // el objetivo se acerca solo a Candil

    private double smoothing = 1, darkness, warning;

    public LogiTemper(long seed) {
        rnd = new Random(seed);
        pullSpeed = 250 + rnd.nextDouble() * 100;
        reset();
    }

    /** Oscuridad máxima jugando (6 fragmentos): rojo muy oscuro con los ojos ya claros. El negro total (1) queda para el final. */
    public static final double PLAY_DARKNESS_MAX = 5.0 / 6.0;

    /** (n / 6) · (5 / 6): entre 0 y 5/6. La animación final lleva a Logi de ahí hasta 1. */
    public static double darknessFor(int fragments) {
        return clampFragments(fragments) / (double) MAX_FRAGMENTS * PLAY_DARKNESS_MAX;
    }

    /** 1 con 0 fragmentos; baja hasta 0.5 con 6 (Logi tarda más en seguir al ratón). */
    public static double smoothingFactorFor(int fragments) {
        return 1 - 0.5 * clampFragments(fragments) / MAX_FRAGMENTS;
    }

    private static int clampFragments(int n) {
        return n < 0 ? 0 : (n > MAX_FRAGMENTS ? MAX_FRAGMENTS : n);
    }

    private static int tierFor(int n) {
        return n <= 2 ? 0 : (n <= 4 ? 1 : 2);
    }

    /** Vuelve a CALM con una espera nueva al azar. */
    public void reset() {
        phase = Phase.CALM;
        phaseTimer = 0;
        waitTimer = 0;
        tier = -1; // la espera se elige en el primer update con fragmentos
        deferred = false;
        graceLeft = 0;
        sinceUnpin = UNPIN_GRACE;
        idleTime = 0;
        drifting = false;
        smoothing = 1;
        darkness = 0;
        warning = 0;
    }

    private void newWait(int fragments) {
        tier = tierFor(fragments);
        waitTarget = WAIT_MIN[tier] + rnd.nextDouble() * (WAIT_MAX[tier] - WAIT_MIN[tier]);
    }

    public void update(double dt, int fragments, boolean pinned,
                       double logiX, double logiY, double candilX, double candilY,
                       double mouseX, double mouseY, boolean mouseMoved) {
        int n = clampFragments(fragments);
        darkness = darknessFor(n);
        warning = 0;

        // Sin fragmentos el juego queda como antes: nada de arrebatos ni de acercarse solo.
        if (n == 0) {
            phase = Phase.CALM;
            waitTimer = 0;
            tier = -1;
            deferred = false;
            drifting = false;
            idleTime = 0;
            smoothing = 1;
            targetX = mouseX;
            targetY = mouseY;
            return;
        }

        // Si cambia el tramo de fragmentos en CALM, la espera se vuelve a elegir con la tabla nueva
        // (se conserva el tiempo ya esperado).
        // Si ya esperó más de la cuenta, quedan al menos 3 s: el aviso no sale justo con la recompensa.
        if (tier < 0 || (phase == Phase.CALM && tierFor(n) != tier)) {
            newWait(n);
            waitTimer = Math.min(waitTimer, waitTarget - 3);
        }

        if (pinned) sinceUnpin = 0; else sinceUnpin += dt;

        switch (phase) {
            case CALM:
                waitTimer += dt;
                if (waitTimer >= waitTarget) {
                    if (pinned) {
                        // Con Logi fijo no hay arrebato: se aplaza hasta UNPIN_GRACE s después de soltarlo.
                        deferred = true;
                        graceLeft = UNPIN_GRACE;
                    } else if (!deferred) {
                        if (sinceUnpin < UNPIN_GRACE) { // soltado hace poco: se respeta lo que falta de la gracia
                            deferred = true;
                            graceLeft = UNPIN_GRACE - sinceUnpin;
                        } else {
                            startWarn(n);
                        }
                    }
                }
                if (deferred && !pinned) {
                    graceLeft -= dt;
                    if (graceLeft <= 0) startWarn(n);
                }
                break;
            case WARN:
                if (pinned) { // fijar durante el aviso lo cancela: CALM y espera nueva
                    phase = Phase.CALM;
                    waitTimer = 0;
                    newWait(n);
                    break;
                }
                phaseTimer += dt;
                if (phaseTimer >= WARN_TIME) {
                    phase = Phase.FROZEN;
                    phaseTimer = 0;
                    frozenX = logiX;
                    frozenY = logiY;
                    drifting = false;
                }
                break;
            case FROZEN:
                phaseTimer += dt;
                if (phaseTimer >= frozenTime) {
                    phase = Phase.PULL;
                    phaseTimer = 0;
                    targetX = logiX; // el tirón arranca desde donde esté Logi
                    targetY = logiY;
                }
                break;
            default: // PULL
                phaseTimer += dt;
                if (phaseTimer >= pullTime) {
                    phase = Phase.CALM;
                    waitTimer = 0;
                    newWait(n);
                    idleTime = 0; // el ratón vuelve a mandar y se espera de nuevo antes de acercarse solo
                    drifting = false;
                }
                break;
        }

        // El aviso solo vale algo si seguimos en WARN tras las transiciones (en FROZEN ya es 0).
        if (phase == Phase.WARN) warning = Math.min(1, phaseTimer / WARN_TIME);

        // Objetivo y suavizado según la fase (la de este cuadro, tras las transiciones).
        switch (phase) {
            case FROZEN:
                targetX = frozenX;
                targetY = frozenY;
                smoothing = smoothingFactorFor(n);
                break;
            case PULL:
                // Avanza hacia Candil pero se detiene a SAFE_DISTANCE, en la recta que lo une con Logi
                // (si Logi está justo encima de Candil, por arriba).
                double ux = targetX - candilX, uy = targetY - candilY;
                double ul = Math.sqrt(ux * ux + uy * uy);
                if (ul < 1e-6) { ux = 0; uy = -1; ul = 1; }
                moveToward(candilX + ux / ul * SAFE_DISTANCE, candilY + uy / ul * SAFE_DISTANCE, pullSpeed * dt);
                smoothing = 1;
                break;
            default:
                followMouse(dt, n, pinned, logiX, logiY, candilX, candilY, mouseX, mouseY, mouseMoved);
                smoothing = smoothingFactorFor(n);
        }
    }

    /** CALM y WARN: el objetivo es el ratón, salvo que lleve quieto IDLE_DELAY s y Logi no esté fijo. */
    private void followMouse(double dt, int n, boolean pinned, double logiX, double logiY,
                             double candilX, double candilY, double mouseX, double mouseY, boolean mouseMoved) {
        if (mouseMoved) {
            idleTime = 0;
            drifting = false;
        } else {
            idleTime += dt;
        }
        if (pinned) drifting = false;

        if (!drifting && !pinned && idleTime >= IDLE_DELAY) {
            drifting = true;       // empieza a acercarse desde donde está Logi
            targetX = logiX;
            targetY = logiY;
        }
        if (drifting) {
            // Lento, y más rápido cuantos más fragmentos (42 a 102 px/s): siempre por debajo del tirón.
            double speed = 30 + 12 * n;
            double dx = candilX - targetX, dy = candilY - targetY;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > IDLE_STOP) {
                double step = Math.min(speed * dt, dist - IDLE_STOP); // se detiene justo a IDLE_STOP
                targetX += dx / dist * step;
                targetY += dy / dist * step;
            }
        } else {
            targetX = mouseX;
            targetY = mouseY;
        }
    }

    /** Mueve el objetivo hacia (px, py) como mucho {@code step} px, sin pasarse. */
    private void moveToward(double px, double py, double step) {
        double dx = px - targetX, dy = py - targetY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist <= step || dist == 0) {
            targetX = px;
            targetY = py;
        } else {
            targetX += dx / dist * step;
            targetY += dy / dist * step;
        }
    }

    private void startWarn(int n) {
        phase = Phase.WARN;
        phaseTimer = 0;
        deferred = false;
        frozenTime = FROZEN_TIME[tierFor(n)];
        pullTime = PULL_TIME[tierFor(n)];
    }

    public Phase getPhase() { return phase; }
    public double getTargetX() { return targetX; }
    public double getTargetY() { return targetY; }
    public double getSmoothingFactor() { return smoothing; }
    public double getDarkness() { return darkness; }
    /** 0..1: fuerza del aviso (solo en WARN). */
    public double getWarning() { return warning; }
    /** Durante FROZEN y PULL no se puede fijar a Logi. */
    public boolean acceptsClick() { return phase == Phase.CALM || phase == Phase.WARN; }
}
