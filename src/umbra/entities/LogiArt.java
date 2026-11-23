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

/**
 * El dibujo de Logi: halo de luz, llama de tres capas con la cola girada, ojos y anillo de fijada.
 *
 * Solo dibuja: lee el estado de LightSource con sus getters (posición, parámetros de la forma,
 * relojes de la animación...) y no cambia nada de él. La luz y la animación siguen en LightSource.
 */
final class LogiArt {

    private LogiArt() {
    }

    // ---- Dibujo de la llama (px, segundos y radianes) ----
    /** Radio de la cabeza redonda de la llama. */
    private static final double HEAD = 9;
    /** Largo de la llama calmada (del centro de la cabeza a la punta), en radios de la cabeza. */
    private static final double CALM_LENGTH = 1.7;
    /** Flota: sube y baja BOB px, despacio (BOB_SPEED rad/s). */
    private static final double BOB = 2, BOB_SPEED = 2.4;

    // ---- Colores de cada capa, todos aquí ----
    private static final Color OUTER = new Color(255, 135, 40);    // orillas naranjas
    private static final Color MIDDLE = new Color(255, 200, 70);
    private static final Color CORE = new Color(255, 248, 215);    // centro blanco amarillo
    private static final Color EYES = new Color(50, 22, 12);       // ojos oscuros
    private static final Color HALO = new Color(255, 190, 100);    // halo cálido
    private static final Color RING = new Color(255, 240, 200, 200);
    // Con los ojos oscurecidos: la llama se enfría hacia el rojo
    private static final Color DARK_OUTER = new Color(150, 34, 40);
    private static final Color DARK_MIDDLE = new Color(226, 96, 52);
    private static final Color DARK_CORE = new Color(255, 190, 130);
    // Paleta fría cuando Logi está fijo (pinned) y paleta roja de advertencia (agitation)
    private static final Color PIN_OUTER = new Color(60, 165, 255);
    private static final Color PIN_MIDDLE = new Color(135, 215, 255);
    private static final Color PIN_CORE = new Color(235, 250, 255);
    private static final Color PIN_HALO = new Color(110, 195, 255);
    private static final Color DANGER_OUTER = new Color(235, 45, 35);
    private static final Color DANGER_MIDDLE = new Color(255, 110, 60);
    private static final Color DANGER_HALO = new Color(255, 75, 55);

    /**
     * Halo de luz: degradado radial que se desvanece hacia afuera; cambia de color (azul si está fijo,
     * rojo agitada) y late un poco: su radio sigue un seno lento, y agitada tiembla con el parpadeo.
     */
    static void drawGlow(Graphics2D g, LightSource l) {
        double x = l.getX(), y = l.getY();
        double agitation = l.getAgitation();
        float pulse = (float) (1.0 + 0.03 * Math.sin(l.getTime() * 2.2) + 0.05 * agitation * Math.sin(l.getFlickerPhase()));
        float r = (float) Config.LIGHT_RADIUS * pulse;

        Color baseGlow = mix(HALO, PIN_HALO, l.getPinMix());
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
     *   5. el anillo si está fijo.
     * Flota con un vaivén suave, pero solo el dibujo: la luz sigue en (x, y).
     */
    static void drawCore(Graphics2D g, LightSource l) {
        Paint oldPaint = g.getPaint();
        AffineTransform old = g.getTransform();

        double x = l.getX(), y = l.getY();
        double motion = l.getMotion(), rise = l.getRise(), agitation = l.getAgitation();
        double eyeDarkness = l.getEyeDarkness(), time = l.getTime(), flamePhase = l.getFlamePhase();
        double flickerPhase = l.getFlickerPhase();
        double tailX = l.getTailX(), tailY = l.getTailY();

        double cx = x, cy = y + BOB * Math.sin(time * BOB_SPEED);   // flota
        double e = l.getEtherealMix();
        double tail = Math.hypot(tailX, tailY);   // 1 = cola bien definida; 0 = llama redonda
        double flick = 1 - 0.4 * agitation * (0.5 + 0.5 * Math.sin(flickerPhase));   // agitada: parpadea
        double r = HEAD * (1 - 0.15 * agitation);                                    // agitada: más delgada
        double len = Math.max(HEAD, HEAD * CALM_LENGTH * (1 + 2 * motion) * tail)    // rápida: cometa
                * (1 + 0.9 * agitation);                                             // agitada: se estira
        double amp = HEAD * (0.12 + 0.25 * agitation + 0.5 * rise + 0.15 * e);      // cuánto ondula
        double angle = Math.atan2(tailY, tailX)
                + 0.08 * Math.sin(time * 1.9) * (1 - motion)          // vaivén en calma
                + 0.25 * agitation * Math.sin(flickerPhase * 0.55);   // agitada: se sacude

        // Colores de la llama: normal -> azul si está fijo -> rojo si Candil está cerca -> más oscuro con los ojos oscuros
        double pinMix = l.getPinMix();
        double d = 0.5 * eyeDarkness;   // [v0.8.6] la llama se enfría solo a medias: lo que cambia son los ojos
        Color cOuter = mix(mix(mix(OUTER, PIN_OUTER, pinMix), DANGER_OUTER, agitation * 0.85), DARK_OUTER, d);
        Color cMiddle = mix(mix(mix(MIDDLE, PIN_MIDDLE, pinMix), DANGER_MIDDLE, agitation * 0.85), DARK_MIDDLE, d);
        Color cCore = mix(mix(CORE, PIN_CORE, pinMix), DARK_CORE, d);
        Color cHalo = mix(mix(HALO, PIN_HALO, pinMix), DANGER_HALO, agitation * 0.85);

        // 1. Halo cálido (con el color de la llama: azul si está fijo, rojo agitada)
        float hr = (float) (HEAD * (2.6 + 2 * e));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), hr, new float[] { 0f, 1f },
                new Color[] { withAlpha(cHalo, (80 + 40 * e) * (1 - 0.4 * eyeDarkness)), withAlpha(cHalo, 0) }));
        g.fill(new Ellipse2D.Double(cx - hr, cy - hr, 2 * hr, 2 * hr));

        // 2 y 3. La llama en coordenadas locales: M = T(cx, cy) · R(angle)
        g.transform(Mat3.translation(cx, cy).multiply(Mat3.rotation(angle)).toAffineTransform());
        if (e > 0.01) drawWisps(g, flamePhase, r, len, 255 * e);
        // Las proporciones de las tres capas (0.7, 0.75, 0.8; 0.42, 0.5, 0.6) y sus desfases (0.4 y 0.8)
        // son las mismas en drawDark: si se cambian aquí, hay que cambiarlas allá también
        // (PruebaLlamaNegra.ceroIgual lo vigila: con darkness = 0 ambas deben salir iguales).
        Path2D outer = layer(0, r, len, amp, flamePhase);
        fillLayer(g, outer, cOuter, 225 * flick * (1 - 0.8 * e));
        if (rise > 0.02) drawSpiral(g, flamePhase, outer, r, len, 205 * rise * tail * flick);
        fillLayer(g, layer(-0.1 * HEAD, 0.7 * r, 0.75 * len, 0.8 * amp, flamePhase + 0.4), cMiddle,
                235 * flick * (1 - 0.45 * e));
        fillLayer(g, layer(-0.18 * HEAD, 0.42 * r, 0.5 * len, 0.6 * amp, flamePhase + 0.8), cCore,
                245 * flick * (1 - 0.1 * e));
        g.setTransform(old);   // los ojos y el anillo van en coordenadas del mundo, no en las de la llama

        // 4. Ojos
        drawEyes(g, l, cx, cy, (235 + 20 * eyeDarkness) * (1 - 0.35 * e));

        // 5. Un anillo indica que está fijo
        if (l.isPinned()) {
            g.setColor(RING);
            g.draw(new Ellipse2D.Double(x - 19, y - 19, 38, 38));
        }
        g.setPaint(oldPaint);
    }

    // ---- La llama negra: los colores por donde pasa de naranja a rojo y a negro ----
    private static final Color RED_OUTER = new Color(190, 40, 40);     // a la mitad (boceto: 190, 40, 40)
    private static final Color RED_MIDDLE = new Color(215, 70, 45);
    private static final Color RED_CORE = new Color(240, 150, 100);
    private static final Color BLACK_OUTER = new Color(8, 8, 12);      // la llama negra (boceto: 8, 8, 12)
    private static final Color BLACK_MIDDLE = new Color(18, 12, 18);
    private static final Color BLACK_CORE = new Color(40, 24, 30);
    private static final Color RED_HALO = new Color(255, 122, 61);
    private static final Color BLACK_HALO = new Color(120, 20, 30);
    private static final Color WHITE_EYES = new Color(244, 246, 255);

    /**
     * La llama negra: Logi en calma, como lo dibuja drawCore,, pero sin LightSource (en (x, y), con 'time'), que se va
     * oscureciendo. La forma es la misma y el reloj de la llama es 'time' (en calma, la fase de LightSource
     * avanza igual que su reloj: con darkness = 0 y scale = 1 sale pixel a pixel lo mismo que drawCore).
     *   0 a 0,5     el color pasa de naranja a rojo (y el centro blanco amarillo se entibia);
     *   0,5 a 1     de rojo a negro (el centro se apaga) y la llama engorda y se estira un poco;
     *   desde 0,7   salen las lenguas de los lados y los ojos se vuelven blancos, alargados y torcidos.
     * No dibuja el halo grande de luz (drawGlow): solo el halo cercano, que pasa de cálido a rojo oscuro.
     */
    static void drawDark(Graphics2D g, double x, double y, double darkness, double scale, double time) {
        Paint oldPaint = g.getPaint();
        AffineTransform old = g.getTransform();
        double dk = Math.max(0, Math.min(1, darkness));
        double toRed = Math.min(1, 2 * dk);                     // 0 a 0,5 -> 0 a 1
        double toBlack = Math.max(0, 2 * dk - 1);               // 0,5 a 1 -> 0 a 1
        double grow = smooth(0.5, 1, dk);                       // engorda en la segunda mitad
        double tongues = smooth(0.7, 0.85, dk);                 // lenguas de los lados
        double white = smooth(0.6, 0.8, dk);                    // ojos blancos

        double cx = x, cy = y + BOB * scale * Math.sin(time * BOB_SPEED);   // flota
        double r = HEAD * (1 + 0.3 * grow);
        double len = Math.max(HEAD, HEAD * CALM_LENGTH) * (1 + 0.4 * grow);
        double amp = HEAD * (0.12 + 0.2 * grow);                // y ondula más
        double angle = Math.atan2(-1, 0) + 0.08 * Math.sin(time * 1.9);

        Color cOuter = mix(mix(OUTER, RED_OUTER, toRed), BLACK_OUTER, toBlack);
        Color cMiddle = mix(mix(MIDDLE, RED_MIDDLE, toRed), BLACK_MIDDLE, toBlack);
        Color cCore = mix(mix(CORE, RED_CORE, toRed), BLACK_CORE, toBlack);
        Color cHalo = mix(mix(HALO, RED_HALO, toRed), BLACK_HALO, toBlack);

        // 1. Halo cercano
        float hr = (float) (HEAD * 2.6 * scale * (1 + 0.6 * grow));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), hr, new float[] { 0f, 1f },
                new Color[] { withAlpha(cHalo, 80 * (1 - 0.1 * dk)), withAlpha(cHalo, 0) }));
        g.fill(new Ellipse2D.Double(cx - hr, cy - hr, 2 * hr, 2 * hr));

        // 2. La llama en coordenadas locales (cola sobre +x), con su tamaño
        g.transform(Mat3.translation(cx, cy).multiply(Mat3.rotation(angle)).toAffineTransform());
        g.scale(scale, scale);
        if (tongues > 0.01) {
            // Dos lenguas a los lados de la cola: la misma curva, girada casi a lo ancho y con su propio ritmo
            for (int s = -1; s <= 1; s += 2) {
                AffineTransform at = g.getTransform();
                g.rotate(s * 1.15);
                fillLayer(g, layer(0, 0.62 * r, tongues * 0.95 * len, 0.9 * amp, time + s * 1.7), cOuter, 225 * tongues);
                g.setTransform(at);
            }
        }
        // Las proporciones de las tres capas (0.7, 0.75, 0.8; 0.42, 0.5, 0.6) y sus desfases (0.4 y 0.8)
        // son las mismas de drawCore: si se cambian aquí, hay que cambiarlas allá también
        // (PruebaLlamaNegra.ceroIgual lo vigila: con darkness = 0 ambas deben salir iguales).
        fillLayer(g, layer(0, r, len, amp, time), cOuter, 225);
        fillLayer(g, layer(-0.1 * HEAD, 0.7 * r, 0.75 * len, 0.8 * amp, time + 0.4), cMiddle, 235);
        fillLayer(g, layer(-0.18 * HEAD, 0.42 * r, 0.5 * len, 0.6 * amp, time + 0.8), cCore,
                245 * (1 - smooth(0.4, 0.9, dk)));   // el centro se apaga
        g.setTransform(old);

        // 3. Ojos: derechos, oscuros -> negros -> blancos, alargados y torcidos
        double w = 2.4 * (1 + 0.3 * white) * scale;
        double h = 3.8 * (1 + 0.8 * white) * scale;
        double ex = cx, ey = cy - 0.5 * scale, sep = 3.4 * scale, tilt = 0.35 * white;
        g.setColor(withAlpha(mix(mix(EYES, Color.BLACK, Math.min(1, dk / 0.6)), WHITE_EYES, white), 235 + 20 * white));
        for (int s = -1; s <= 1; s += 2) {
            if (tilt > 0) g.rotate(-s * tilt, ex + s * sep, ey);   // los de arriba hacia adentro, como el boceto
            g.fill(new Ellipse2D.Double(ex + s * sep - w / 2, ey - h / 2, w, h));
            if (tilt > 0) g.rotate(s * tilt, ex + s * sep, ey);
        }
        g.setTransform(old);
        g.setPaint(oldPaint);
    }

    /** Curva suave de 0 (en e0) a 1 (en e1): 3t² - 2t³. */
    private static double smooth(double e0, double e1, double v) {
        double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
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
    private static void drawWisps(Graphics2D g, double flamePhase, double r, double len, double alpha) {
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
    private static void drawSpiral(Graphics2D g, double flamePhase, Shape outer, double r, double len, double alpha) {
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
    private static void drawEyes(Graphics2D g, LightSource l, double cx, double cy, double alpha) {
        double eyeDarkness = l.getEyeDarkness(), agitation = l.getAgitation(), blinkLeft = l.getBlinkLeft();
        double lookX = l.getLookX(), lookY = l.getLookY();
        // [v0.8.6] Oscurecidos: más anchos, más altos y negros (y no parpadean en ese instante)
        double w = 2.4 * (1 + 0.3 * eyeDarkness);
        double h = (blinkLeft > 0 && eyeDarkness < 0.1 ? 0.8 : 3.8 * (1 + 0.35 * agitation)) * (1 + 0.3 * eyeDarkness);
        double ex = cx + 2 * lookX, ey = cy - 0.5 + 1.6 * lookY;
        g.setColor(withAlpha(mix(EYES, Color.BLACK, eyeDarkness), alpha));
        g.fill(new Ellipse2D.Double(ex - 3.4 - w / 2, ey - h / 2, w, h));
        g.fill(new Ellipse2D.Double(ex + 3.4 - w / 2, ey - h / 2, w, h));
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
}
