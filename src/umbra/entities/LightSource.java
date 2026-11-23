package umbra.entities;

import java.awt.Graphics2D;

import umbra.core.Config;
import umbra.math.Vec2;

/**
 * Logi: la llama que el jugador mueve con el ratón. Es la fuente de luz puntual de las sombras.
 * No salta a la posición del ratón: se acerca con interpolación (lerp) para moverse suave.
 *
 * [v1.0] Formas según la situación (hoja "Logi – Creador de sombras sin forma"). Solo cambian
 * cómo se dibuja: la luz sigue en (x, y) y la mecánica no cambia. Las formas no son dibujos
 * separados: salen de parámetros continuos (qué tan rápido va, hacia dónde, qué tan cerca está
 * Candil), suavizados con el mismo suavizado exponencial de la luz, así que pasan de una a otra
 * sin saltos. getForm() dice cuál domina, para las pruebas y para las partículas.
 */
public class LightSource {

    /** Las formas de Logi: en su sitio, persecución, ascenso, agitada, chispeando y etérea. */
    public enum Form { CALM, CHASE, RISING, AGITATED, SPARKING, ETHEREAL }

    /** Más rápido que esto (px/s) ya no está en calma: va en persecución (o asciende, si sube). */
    public static final double CHASE_SPEED = 380;
    /** Con Candil más cerca que esto (px), Logi se agita: avisa que él corre peligro. */
    public static final double AGITATED_DISTANCE = 150;

    // ---- Animación de la llama (px, segundos y radianes); lo que solo dibuja vive en LogiArt ----
    /** Entre estas rapideces (px/s) la gota se vuelve cometa poco a poco. */
    private static final double SLOW = 120, FAST = 640;
    /** Desde esta rapidez (px/s) la cola empieza a enroscarse si sube. */
    private static final double SPIRAL_SPEED = 300;
    /** Qué tan rápido siguen los parámetros de la forma a su meta (mayor = más rápido). */
    private static final double SHAPE_RESPONSE = 10;
    /** Parpadeo de la llama agitada: unas 7 veces por segundo (rad/s). */
    private static final double FLICKER_SPEED = 2 * Math.PI * 7;
    /** Segundos que dura cerrado un parpadeo de los ojos. */
    private static final double BLINK_TIME = 0.12;

    private double x, y;
    private double targetX, targetY;
    private boolean pinned;   // fija: se queda en su lugar del mundo y ya no sigue al ratón

    // ---- Forma [v1.0] ----
    private double vx, vy;                 // velocidad propia (px/s), medida en update
    private boolean knowsCandil;           // ya se sabe dónde está Candil
    private double candilX, candilY;
    private boolean ethereal;              // en el menú y en la pantalla final
    private double time;                   // reloj de la animación (s)
    private double flamePhase;             // fase de las ondas de la llama: corre más rápido si está agitada
    private double flickerPhase;           // fase del parpadeo rápido de la llama agitada
    private boolean pinTint = true;     // ¿se tiñe de azul al fijarse?
    private double motion, rise, agitation, etherealMix, pinMix;   // parámetros de la forma, de 0 a 1 (pinMix: suaviza el cambio de color al fijar)
    private double tailX = 0, tailY = -1;  // hacia dónde apunta la cola (vector de largo <= 1)
    private double lookX, lookY;           // hacia dónde miran los ojos (vector de largo <= 1)
    private double blinkTimer = 2.5;       // segundos para el siguiente parpadeo
    private double blinkLeft;              // segundos que le quedan al parpadeo actual
    private double eyeDarkness;            // [v0.8.6] 0 = como siempre; 1 = ojos negros y llama enrojecida

    // ---- [B5] Logi se resiste: lo manda LogiTemper desde GamePanel ----
    private double smoothingFactor = 1;    // multiplica a LIGHT_SMOOTHING: menor = sigue al ratón más lento
    private double darkness;               // 0 = el Logi de siempre; 0,5 = rojo; 1 = llama negra con ojos blancos
    private double warning;                // 0 a 1: el aviso rojo antes de un arrebato
    private final LogiArt.Palette palette = new LogiArt.Palette();   // colores de la llama para 'darkness'

    public LightSource(double x, double y) {
        this.x = x;
        this.y = y;
        this.targetX = x;
        this.targetY = y;
    }

    /**
     * Coloca la luz de inmediato: posición y destino en el mismo punto (sin suavizado).
     * También reinicia su velocidad y su forma: el salto no es movimiento. Si no, al cambiar de
     * nivel Logi llegaría como cometa, con la velocidad que traía en el otro nivel.
     */
    public void snapTo(double x, double y) {
        this.x = targetX = x;
        this.y = targetY = y;
        vx = vy = 0;
        motion = rise = 0;
        tailX = 0;
        tailY = -1;   // la cola hacia arriba: calmada
    }

    /** Nuevo destino de la luz. Si está fija se ignora: se queda donde se le dejó. */
    public void setTarget(double tx, double ty) {
        if (pinned) return;
        targetX = tx;
        targetY = ty;
    }

    /**
     * [AVANCE 70%] Fija o suelta la luz. Fija, se queda en su punto del mundo aunque
     * la pantalla avance; así las sombras no cambian mientras el jugador camina sobre ellas.
     */
    public void togglePinned() { pinned = !pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public boolean isPinned() { return pinned; }

    /** [v1.0] Dónde está Candil (su centro): Logi lo mira y se agita si está cerca. */
    public void setCandil(double x, double y) {
        knowsCandil = true;
        candilX = x;
        candilY = y;
    }

    /** [v1.0] Etérea: fuego sin bordes fijos (Logi del menú y de la pantalla final). */
    public void setEthereal(boolean e) { ethereal = e; }

    /** Si es false, Logi no se tiñe de azul al fijarse (en la intro sigue cálido; el anillo se dibuja igual). */
    public void setPinTint(boolean on) { pinTint = on; }

    /**
     * [v0.8.6] Oscurece los ojos de Logi, de 0 (como siempre) a 1: se vuelven negros y más
     * grandes, y la llama se enfría hacia el rojo. Cada color es una interpolación lineal entre
     * el normal y el oscuro, color = normal + (oscuro - normal) · d, así que con d = 0 se dibuja
     * exactamente igual que antes. Solo la usa la intro, un instante.
     */
    public void setEyeDarkness(double d) { eyeDarkness = Math.max(0, Math.min(1, d)); }

    /**
     * [B5] Qué tan rápido sigue al ratón: multiplica a Config.LIGHT_SMOOTHING en update.
     * 1 (por omisión) es como siempre; con 0,5 tarda el doble en recorrer lo mismo.
     */
    public void setSmoothingFactor(double f) { smoothingFactor = f; }

    /**
     * [B5] Oscuridad de Logi, de 0 a 1 (con cada fragmento sube 1/6): 0 es el Logi de siempre,
     * 0,5 rojo y 1 la llama negra con ojos blancos del final. Solo cambia los colores de drawCore;
     * la luz (drawGlow, el radio y las sombras) y las formas siguen iguales. Es distinta de
     * setEyeDarkness, la de la intro. La paleta se recalcula aquí, una vez, y no en cada cuadro.
     */
    public void setDarkness(double d) {
        darkness = Math.max(0, Math.min(1, d));
        palette.setDarkness(darkness);
    }

    public double getDarkness() { return darkness; }

    /** [B5] Aviso de un arrebato, de 0 a 1: la llama parpadea en rojo. 0 (por omisión) = sin aviso. */
    public void setWarning(double w) { warning = Math.max(0, Math.min(1, w)); }

    public void update(double dt) {
        // Suavizado exponencial: en cada update recorre una fracción 't' del camino restante.
        // Usar exp() hace que la velocidad sea la misma sin importar los FPS.
        double oldX = x, oldY = y;
        // [B5] smoothingFactor < 1 la hace más lenta (Logi obedece de mala gana); con 1 es como siempre.
        double t = 1 - Math.exp(-Config.LIGHT_SMOOTHING * smoothingFactor * dt);
        x += (targetX - x) * t;
        y += (targetY - y) * t;

        // Velocidad propia: lo que se movió en este cuadro entre el tiempo que pasó (px/s)
        if (dt > 0) {
            vx = (x - oldX) / dt;
            vy = (y - oldY) / dt;
        }
        animate(dt);
    }

    /**
     * [v1.0] La forma que domina ahora, de mayor a menor prioridad:
     *   ETHEREAL (menú y final) > AGITATED (Candil a menos de 150 px) > SPARKING (Logi fijo)
     *   > RISING (rápida y subiendo) > CHASE (rápida) > CALM.
     * Rápida es más de 380 px/s. Subiendo es vy < -0.7 · rapidez: el eje y crece hacia abajo,
     * así que subir es vy negativa, y -vy / rapidez es el seno del ángulo sobre la horizontal:
     * más de 0.7 quiere decir a menos de ~45° de la vertical.
     */
    public Form getForm() {
        if (ethereal) return Form.ETHEREAL;
        if (candilDistance() < AGITATED_DISTANCE) return Form.AGITATED;
        if (pinned) return Form.SPARKING;
        double speed = Math.hypot(vx, vy);
        if (speed > CHASE_SPEED) return vy < -0.7 * speed ? Form.RISING : Form.CHASE;
        return Form.CALM;
    }

    /** Distancia a Candil (infinita mientras no se sepa dónde está). */
    private double candilDistance() {
        return knowsCandil ? Math.hypot(candilX - x, candilY - y) : Double.POSITIVE_INFINITY;
    }

    /**
     * Parámetros continuos de la forma, de 0 a 1. Cada uno tiene una meta y se acerca a ella
     * con el suavizado exponencial 1 - exp(-k·dt), así que la llama nunca cambia de golpe:
     *   motion     qué tan cometa es: curva suave de la rapidez entre 120 y 640 px/s
     *   rise       qué tan enroscada va la cola: rapidez (desde 300 px/s) por qué tan vertical sube
     *   agitation  qué tan cerca está Candil: 1 a 110 px o menos y 0 a 190 px o más
     * También gira la cola, mueve la mirada y lleva los relojes del parpadeo.
     */
    private void animate(double dt) {
        time += dt;
        double k = 1 - Math.exp(-SHAPE_RESPONSE * dt);
        double speed = Math.hypot(vx, vy);
        double fast = smoothstep(SLOW, FAST, speed);   // la meta de motion
        double up = speed > 0 ? -vy / speed : 0;       // 1 = sube derecho, -1 = baja derecho

        motion += (fast - motion) * k;
        rise += (smoothstep(SPIRAL_SPEED, FAST, speed) * smoothstep(0.55, 0.85, up) - rise) * k;
        double near = 1 - smoothstep(AGITATED_DISTANCE - 40, AGITATED_DISTANCE + 40, candilDistance());
        agitation += (near - agitation) * k;
        etherealMix += ((ethereal ? 1 : 0) - etherealMix) * k;
        pinMix += ((pinned && pinTint && !ethereal ? 1 : 0) - pinMix) * k;

        // Las ondas de la llama corren más rápido si está agitada y más lento si es etérea.
        // Se acumula la fase (en vez de usar sin(frecuencia · tiempo)) para que al cambiar la
        // frecuencia la onda no brinque.
        flamePhase += dt * (1 + 1.6 * agitation - 0.3 * etherealMix);
        flickerPhase += dt * FLICKER_SPEED;

        // La cola apunta hacia la suma de dos vectores: el empuje hacia arriba (0, -1), como una
        // vela, que se apaga al ir rápido, y el arrastre hacia atrás de la velocidad, -v / |v|:
        //     meta = (0, -1)·(1 - f)² + (-v / |v|)·f,   con f = qué tan rápido va (de 0 a 1).
        // El empuje se apaga al cuadrado para que, subiendo, la cola quede abajo antes de los
        // 380 px/s. Se suaviza el vector y el ángulo sale después con atan2, al dibujar. Si al
        // subir los dos vectores se cancelan, la meta pasa por cero: en ese momento la llama
        // queda redonda, así que el giro de la cola de arriba hacia abajo no se nota.
        double push = (1 - fast) * (1 - fast);
        double goalX = 0, goalY = -push;
        if (speed > 0) {
            goalX -= vx / speed * fast;
            goalY -= vy / speed * fast;
        }
        tailX += (goalX - tailX) * k;
        tailY += (goalY - tailY) * k;

        // Mirada: hacia Candil; cuando va rápido, hacia donde va
        double gx = 0, gy = 0;
        double d = candilDistance();
        if (knowsCandil && d > 0) {
            gx = (candilX - x) / d;
            gy = (candilY - y) / d;
        }
        if (speed > 0) {
            gx += (vx / speed - gx) * motion;
            gy += (vy / speed - gy) * motion;
        }
        lookX += (gx - lookX) * k;
        lookY += (gy - lookY) * k;

        // Parpadea cada 3 s; agitada, cada medio segundo (parpadea rápido)
        double interval = 3.0 - 2.5 * agitation;
        blinkTimer = Math.min(blinkTimer - dt, interval);
        if (blinkTimer <= 0) {
            blinkLeft = BLINK_TIME;
            blinkTimer = interval;
        }
        blinkLeft = Math.max(0, blinkLeft - dt);
    }

    /** Halo de luz. Lo dibuja LogiArt. */
    public void drawGlow(Graphics2D g) {
        LogiArt.drawGlow(g, this);
    }

    /** [v1.0] Logi: la llama con ojos, en el punto de la luz. Lo dibuja LogiArt. */
    public void drawCore(Graphics2D g) {
        LogiArt.drawCore(g, this);
    }

    /**
     * La llama negra del final: Logi sin LightSource, en (x, y). Es el mismo camino que drawCore
     * (LightSource es la puerta pública de LogiArt, que es de paquete). Lo dibuja LogiArt.
     * @param darkness 0 = Logi en calma; 0,5 = roja; 1 = la llama negra
     * @param scale    tamaño (1 = el normal)
     * @param time     reloj de la animación, en segundos
     */
    public static void drawDark(Graphics2D g, double x, double y, double darkness, double scale, double time) {
        LogiArt.drawDark(g, x, y, darkness, scale, time);
    }

    /** Curva suave de 0 (en e0) a 1 (en e1): 3t² - 2t³, arranca y termina despacio. */
    private static double smoothstep(double e0, double e1, double v) {
        double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    /** [v1.0] Velocidad propia (px/s): para la ceniza que deja atrás. */
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public Vec2 getPosition() { return new Vec2(x, y); }

    // Solo lectura, para LogiArt (el dibujo de Logi): el estado de la animación
    double getTime() { return time; }
    double getFlamePhase() { return flamePhase; }
    double getFlickerPhase() { return flickerPhase; }
    double getMotion() { return motion; }
    double getRise() { return rise; }
    double getAgitation() { return agitation; }
    double getEtherealMix() { return etherealMix; }
    double getPinMix() { return pinMix; }
    double getTailX() { return tailX; }
    double getTailY() { return tailY; }
    double getLookX() { return lookX; }
    double getLookY() { return lookY; }
    double getBlinkLeft() { return blinkLeft; }
    double getEyeDarkness() { return eyeDarkness; }
    double getWarning() { return warning; }
    LogiArt.Palette getPalette() { return palette; }
}
