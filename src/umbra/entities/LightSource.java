package umbra.entities;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

import umbra.core.Config;
import umbra.math.Mat3;
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

    /** Las formas de Logi: quieta, persecución, ascenso, agitada, chispeando y etérea. */
    public enum Form { CALM, CHASE, RISING, AGITATED, SPARKING, ETHEREAL }

    /** Más rápido que esto (px/s) ya no está calmada: va en persecución (o asciende, si sube). */
    public static final double CHASE_SPEED = 380;
    /** Con Candil más cerca que esto (px), Logi se agita: avisa que él corre peligro. */
    public static final double AGITATED_DISTANCE = 150;

    // ---- Dibujo de la llama (px, segundos y radianes) ----
    /** Radio de la cabeza redonda de la llama. */
    private static final double HEAD = 9;
    /** Largo de la llama calmada (del centro de la cabeza a la punta), en radios de la cabeza. */
    private static final double CALM_LENGTH = 1.7;
    /** Entre estas rapideces (px/s) la gota se vuelve cometa poco a poco. */
    private static final double SLOW = 120, FAST = 640;
    /** Desde esta rapidez (px/s) la cola empieza a enroscarse si sube. */
    private static final double SPIRAL_SPEED = 300;
    /** Qué tan rápido siguen los parámetros de la forma a su meta (mayor = más rápido). */
    private static final double SHAPE_RESPONSE = 10;
    /** Flota: sube y baja BOB px, despacio (BOB_SPEED rad/s). */
    private static final double BOB = 2, BOB_SPEED = 2.4;
    /** Parpadeo de la llama agitada: unas 7 veces por segundo (rad/s). */
    private static final double FLICKER_SPEED = 2 * Math.PI * 7;
    /** Segundos que dura cerrado un parpadeo de los ojos. */
    private static final double BLINK_TIME = 0.12;

    private static final Color OUTER = new Color(255, 135, 40);    // orillas naranjas
    private static final Color MIDDLE = new Color(255, 200, 70);
    private static final Color CORE = new Color(255, 248, 215);    // centro blanco amarillo
    private static final Color EYES = new Color(50, 22, 12);       // ojos oscuros
    private static final Color HALO = new Color(255, 190, 100);    // halo cálido
    private static final Color RING = new Color(255, 240, 200, 200);
    // Con los ojos oscurecidos (solo en la intro): la llama se enfría hacia el rojo
    private static final Color DARK_OUTER = new Color(150, 34, 40);
    private static final Color DARK_MIDDLE = new Color(226, 96, 52);
    private static final Color DARK_CORE = new Color(255, 190, 130);
    // Paleta fría cuando Logi está fija (pinned) y paleta roja de advertencia (agitation)
    private static final Color PIN_OUTER = new Color(60, 165, 255);
    private static final Color PIN_MIDDLE = new Color(135, 215, 255);
    private static final Color PIN_CORE = new Color(235, 250, 255);
    private static final Color PIN_HALO = new Color(110, 195, 255);

    private static final Color DANGER_OUTER = new Color(235, 45, 35);
    private static final Color DANGER_MIDDLE = new Color(255, 110, 60);
    private static final Color DANGER_HALO = new Color(255, 75, 55);

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

    /** Si es false, Logi no se tiñe de azul al fijarse (en la intro sigue cálida; el anillo se dibuja igual). */
    public void setPinTint(boolean on) { pinTint = on; }

    /**
     * [v0.8.6] Oscurece los ojos de Logi, de 0 (como siempre) a 1: se vuelven negros y más
     * grandes, y la llama se enfría hacia el rojo. Cada color es una interpolación lineal entre
     * el normal y el oscuro, color = normal + (oscuro - normal) · d, así que con d = 0 se dibuja
     * exactamente igual que antes. Solo la usa la intro, un instante.
     */
    public void setEyeDarkness(double d) { eyeDarkness = Math.max(0, Math.min(1, d)); }

    public void update(double dt) {
        // Suavizado exponencial: en cada update recorre una fracción 't' del camino restante.
        // Usar exp() hace que la velocidad sea la misma sin importar los FPS.
        double oldX = x, oldY = y;
        double t = 1 - Math.exp(-Config.LIGHT_SMOOTHING * dt);
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
     *   ETHEREAL (menú y final) > AGITATED (Candil a menos de 150 px) > SPARKING (fija)
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

    /** Halo de luz: degradado radial que se desvanece hacia afuera; cambia de color (azul fija, rojo agitada) y late un poco. */
    public void drawGlow(Graphics2D g) {
        float pulse = (float) (1.0 + 0.03 * Math.sin(time * 2.2) + 0.05 * agitation * Math.sin(flickerPhase));
        float r = (float) Config.LIGHT_RADIUS * pulse;

        Color baseGlow = mix(HALO, PIN_HALO, pinMix);
        baseGlow = mix(baseGlow, DANGER_HALO, agitation * 0.85);

        RadialGradientPaint glow = new RadialGradientPaint(
            new Point2D.Double(x, y), r,
            new float[] { 0f, 0.35f, 1f },
            new Color[] {
                withAlpha(baseGlow, 115 + 20 * agitation),
                withAlpha(baseGlow, 42),
                withAlpha(baseGlow, 0)
            });
        g.setPaint(glow);
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }

    /**
     * [v1.0] Logi: la llama con ojos, en el punto de la luz. Todo sale de los parámetros de la forma:
     *   1. un halo cálido pequeño;
     *   2. (etérea) lenguas de fuego difusas;
     *   3. tres capas de llama (orillas naranjas, centro blanco amarillo) con la cola girada
     *      y, entre la de afuera y las de adentro, (subiendo) la cola enroscada en espiral.
     *      Se dibujan en coordenadas locales, con la cola sobre +x, y la matriz
     *      M = T(x, y + flote) · R(ángulo de la cola) las lleva a su lugar. El ángulo es
     *      atan2 del vector de la cola, y su largo dice qué tanto se estira la cola;
     *   4. los ojos, derechos aunque la cola gire;
     *   5. el anillo si está fija.
     * Flota con un vaivén suave, pero solo el dibujo: la luz sigue en (x, y).
     */
    public void drawCore(Graphics2D g) {
        Paint oldPaint = g.getPaint();
        AffineTransform old = g.getTransform();
        double cx = x, cy = y + BOB * Math.sin(time * BOB_SPEED);   // flota
        double e = etherealMix;
        double tail = Math.hypot(tailX, tailY);   // 1 = cola bien definida; 0 = llama redonda
        double flick = 1 - 0.4 * agitation * (0.5 + 0.5 * Math.sin(flickerPhase));   // agitada: parpadea
        double r = HEAD * (1 - 0.15 * agitation);                                    // agitada: más delgada
        double len = Math.max(HEAD, HEAD * CALM_LENGTH * (1 + 2 * motion) * tail)    // rápida: cometa
                * (1 + 0.9 * agitation);                                             // agitada: se estira
        double amp = HEAD * (0.12 + 0.25 * agitation + 0.5 * rise + 0.15 * e);      // cuánto ondula
        double angle = Math.atan2(tailY, tailX)
                + 0.08 * Math.sin(time * 1.9) * (1 - motion)          // vaivén en calma
                + 0.25 * agitation * Math.sin(flickerPhase * 0.55);   // agitada: se sacude

        // Colores de la llama: normal -> azul si está fija -> rojo si Candil está cerca -> más oscuro con los ojos oscuros
        double d = 0.5 * eyeDarkness;   // [v0.8.6] la llama se enfría solo a medias: lo que cambia son los ojos
        Color cOuter = mix(mix(mix(OUTER, PIN_OUTER, pinMix), DANGER_OUTER, agitation * 0.85), DARK_OUTER, d);
        Color cMiddle = mix(mix(mix(MIDDLE, PIN_MIDDLE, pinMix), DANGER_MIDDLE, agitation * 0.85), DARK_MIDDLE, d);
        Color cCore = mix(mix(CORE, PIN_CORE, pinMix), DARK_CORE, d);
        Color cHalo = mix(mix(HALO, PIN_HALO, pinMix), DANGER_HALO, agitation * 0.85);

        // 1. Halo cálido (con el color de la llama: azul fija, rojo agitada)
        float hr = (float) (HEAD * (2.6 + 2 * e));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), hr, new float[] { 0f, 1f },
                new Color[] { withAlpha(cHalo, (80 + 40 * e) * (1 - 0.4 * eyeDarkness)), withAlpha(cHalo, 0) }));
        g.fill(new Ellipse2D.Double(cx - hr, cy - hr, 2 * hr, 2 * hr));

        // 2 y 3. La llama en coordenadas locales: M = T(cx, cy) · R(angle)
        g.transform(Mat3.translation(cx, cy).multiply(Mat3.rotation(angle)).toAffineTransform());
        if (e > 0.01) drawWisps(g, r, len, 255 * e);
        Path2D outer = layer(0, r, len, amp, flamePhase);
        fillLayer(g, outer, cOuter, 225 * flick * (1 - 0.8 * e));
        if (rise > 0.02) drawSpiral(g, outer, r, len, 205 * rise * tail * flick);
        fillLayer(g, layer(-0.1 * HEAD, 0.7 * r, 0.75 * len, 0.8 * amp, flamePhase + 0.4), cMiddle,
                235 * flick * (1 - 0.45 * e));
        fillLayer(g, layer(-0.18 * HEAD, 0.42 * r, 0.5 * len, 0.6 * amp, flamePhase + 0.8), cCore,
                245 * flick * (1 - 0.1 * e));
        g.setTransform(old);   // los ojos y el anillo van en coordenadas del mundo, no en las de la llama

        // 4. Ojos
        drawEyes(g, cx, cy, (235 + 20 * eyeDarkness) * (1 - 0.35 * e));

        // 5. Un anillo indica que está fija
        if (pinned) {
            g.setColor(RING);
            g.draw(new Ellipse2D.Double(x - 19, y - 19, 38, 38));
        }
        g.setPaint(oldPaint);
    }

    /**
     * Contorno de una capa de la llama, en coordenadas locales: la cabeza es un círculo de radio
     * r con centro en (x0, 0) y la cola apunta hacia +x, hasta 'len' del centro. Son cuatro
     * curvas de Bézier cúbicas: dos cuartos de círculo al frente (0.5523·r aproxima un círculo,
     * como en la cabeza de Candil) y dos curvas que se juntan en la punta. Los puntos de control
     * de la cola se mueven de lado con ondas seno desfasadas: es una onda que recorre la cola, y
     * por eso la llama ondula y parpadea.
     */
    private static Path2D layer(double x0, double r, double len, double amp, double phase) {
        double k = 0.5523 * r;
        double w1 = amp * Math.sin(phase * 9);
        double w2 = amp * Math.sin(phase * 9 - 1.9);
        double w3 = amp * 1.3 * Math.sin(phase * 9 - 3.4);                 // la punta se mueve más
        double tip = x0 + len * (1 + 0.08 * Math.sin(phase * 13 + 0.6));   // y crece y se encoge
        double c1 = x0 + 0.35 * (tip - x0), c2 = x0 + 0.7 * (tip - x0);
        Path2D p = new Path2D.Double();
        p.moveTo(x0 - r, 0);
        p.curveTo(x0 - r, -k, x0 - k, -r, x0, -r);                    // frente -> un lado
        p.curveTo(c1, -0.95 * r + w1, c2, -0.4 * r + w2, tip, w3);     // ese lado -> la punta
        p.curveTo(c2, 0.4 * r + w2, c1, 0.95 * r + w1, x0, r);         // la punta -> el otro lado
        p.curveTo(x0 - k, r, x0 - r, k, x0 - r, 0);                    // el otro lado -> frente
        p.closePath();
        return p;
    }

    private static void fillLayer(Graphics2D g, Path2D shape, Color c, double alpha) {
        g.setColor(withAlpha(c, alpha));
        g.fill(shape);
    }

    /**
     * Forma etérea: tres lenguas de fuego, cada una a su ritmo y desplazándose un poco (amorfa).
     * Cada lengua son tres copias de la misma curva, cada vez más chicas, muy transparentes y una
     * sobre otra: la orilla queda tenue y el centro más intenso, así que no tiene bordes fijos.
     */
    private void drawWisps(Graphics2D g, double r, double len, double alpha) {
        for (int i = 0; i < 3; i++) {
            double ph = flamePhase * 0.8 + i * 2.1;
            double ox = HEAD * 0.4 * Math.sin(ph * 1.3), oy = HEAD * 0.45 * Math.sin(ph * 0.9 + i);
            double wl = len * (1.25 + 0.3 * Math.sin(ph * 1.7));
            g.translate(ox, oy);
            for (int s = 0; s < 3; s++) {
                double f = 1 - 0.28 * s;   // 1, 0.72 y 0.44
                Color c = s == 0 ? OUTER : s == 1 ? MIDDLE : CORE;
                fillLayer(g, layer(-0.1 * s * HEAD, r * (1.2 - 0.15 * i) * f, wl * f, HEAD * 0.5, ph), c,
                        alpha * (0.24 + 0.1 * s));
            }
            g.translate(-ox, -oy);
        }
    }

    /**
     * Subiendo: la cola se enrosca en espiral. Una hélice vista de lado son dos ondas seno
     * cruzadas: aquí, dos hebras amarillas (1.75 px, ~80% opacas) a lo largo de la cola, más angostas hacia la punta,
     * que corren hacia atrás (se dibujan bajo las capas de adentro, así que brillan en la orilla,
     * y recortadas con la capa de afuera para que no se salgan de la cola).
     * Cada hebra pasa por 9 puntos unidos con curvas de Bézier cuadráticas que pasan por los
     * puntos medios, para que se vea lisa.
     */
    private void drawSpiral(Graphics2D g, Shape outer, double r, double len, double alpha) {
        Stroke oldStroke = g.getStroke();
        Shape oldClip = g.getClip();
        g.clip(outer);
        g.setStroke(new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(withAlpha(MIDDLE, alpha));
        int n = 8;
        double[] px = new double[n + 1], py = new double[n + 1];
        for (int s = 0; s < 2; s++) {
            for (int i = 0; i <= n; i++) {
                double u = (double) i / n;
                px[i] = len * (0.2 + 0.8 * u);
                py[i] = 0.75 * r * (1 - 0.7 * u) * Math.sin(3 * Math.PI * u - 9 * flamePhase + s * Math.PI);
            }
            Path2D strand = new Path2D.Double();
            strand.moveTo(px[0], py[0]);
            for (int i = 1; i < n; i++) {
                strand.quadTo(px[i], py[i], (px[i] + px[i + 1]) / 2, (py[i] + py[i + 1]) / 2);
            }
            strand.lineTo(px[n], py[n]);
            g.draw(strand);
        }
        g.setClip(oldClip);
        g.setStroke(oldStroke);
    }

    /** Ojos oscuros: se mueven hasta 2 px hacia donde mira, agitada los abre más, y parpadean. */
    private void drawEyes(Graphics2D g, double cx, double cy, double alpha) {
        // [v0.8.6] Oscurecidos: más anchos, más altos y negros (y no parpadean en ese instante)
        double w = 2.4 * (1 + 0.3 * eyeDarkness);
        double h = (blinkLeft > 0 && eyeDarkness < 0.1 ? 0.8 : 3.8 * (1 + 0.35 * agitation)) * (1 + 0.3 * eyeDarkness);
        double ex = cx + 2 * lookX, ey = cy - 0.5 + 1.6 * lookY;
        g.setColor(withAlpha(mix(EYES, Color.BLACK, eyeDarkness), alpha));
        g.fill(new Ellipse2D.Double(ex - 3.4 - w / 2, ey - h / 2, w, h));
        g.fill(new Ellipse2D.Double(ex + 3.4 - w / 2, ey - h / 2, w, h));
    }

    /** Curva suave de 0 (en e0) a 1 (en e1): 3t² - 2t³, arranca y termina despacio. */
    private static double smoothstep(double e0, double e1, double v) {
        double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** Interpolación lineal entre dos colores: t = 0 regresa 'a', t = 1 regresa 'b'. */
    private static Color mix(Color a, Color b, double t) {
        return new Color(
            (int) Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) Math.round(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }

    private static Color withAlpha(Color c, double alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.max(0, Math.min(255, Math.round(alpha))));
    }

    public double getX() { return x; }
    public double getY() { return y; }
    /** [v1.0] Velocidad propia (px/s): para la ceniza que deja atrás. */
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public Vec2 getPosition() { return new Vec2(x, y); }
}
