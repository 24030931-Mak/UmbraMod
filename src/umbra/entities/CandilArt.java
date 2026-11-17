package umbra.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

import umbra.math.Mat3;

/**
 * El dibujo de Candil: encapuchado, con capucha de niebla gris azulada y la punta caída, la cara
 * oscura con dos ojos blancos que brillan y una capa que termina en jirones.
 *
 * Solo dibuja: lee el estado de Player con sus getters (pose, mezcla de formas, reloj de la
 * animación, vida...) y no cambia nada de él. La física y la animación siguen en Player.
 */
final class CandilArt {

    private CandilArt() {
    }

    /**
     * Los parámetros con que se dibuja a Candil. Una forma es un punto en este espacio de
     * parámetros, y pasar de una forma a otra es moverse en línea recta entre dos puntos.
     */
    static final class Look {
        double lean;      // inclinación hacia adelante (radianes)
        double curl;      // 0 = de pie, 1 = acurrucado en una bolita
        double fear;      // 0 = tranquilo, 1 = encogido y temblando
        double opacity;   // 1 = capa opaca; menos = más transparente, y los jirones se deshacen
        double sweep;     // jirones que vuelan hacia atrás (al correr)
        double lift;      // jirones que vuelan hacia arriba (al caer); negativo: cuelgan más
        double spread;    // la capa se abre hacia los lados
        double feet;      // 0 = pies escondidos, 1 = se asoman
        double eyes;      // tamaño de los ojos (1 = normal)
        double glow;      // brillo de los ojos (1 = normal)

        Look(double lean, double curl, double fear, double opacity, double sweep, double lift,
             double spread, double feet, double eyes, double glow) {
            this.lean = lean;
            this.curl = curl;
            this.fear = fear;
            this.opacity = opacity;
            this.sweep = sweep;
            this.lift = lift;
            this.spread = spread;
            this.feet = feet;
            this.eyes = eyes;
            this.glow = glow;
        }

        Look(Look o) {
            set(o);
        }

        void set(Look o) {
            lean = o.lean;     curl = o.curl;   fear = o.fear;     opacity = o.opacity;
            sweep = o.sweep;   lift = o.lift;   spread = o.spread; feet = o.feet;
            eyes = o.eyes;     glow = o.glow;
        }

        /**
         * Se acerca a 'goal' una fracción t del camino que falta, en cada parámetro:
         *   p = p + (meta - p) * t
         * Con t = 1 - e^(-k * dt) es el mismo suavizado exponencial de la cámara y de la luz.
         */
        void approach(Look goal, double t) {
            lean    += (goal.lean    - lean)    * t;
            curl    += (goal.curl    - curl)    * t;
            fear    += (goal.fear    - fear)    * t;
            opacity += (goal.opacity - opacity) * t;
            sweep   += (goal.sweep   - sweep)   * t;
            lift    += (goal.lift    - lift)    * t;
            spread  += (goal.spread  - spread)  * t;
            feet    += (goal.feet    - feet)    * t;
            eyes    += (goal.eyes    - eyes)    * t;
            glow    += (goal.glow    - glow)    * t;
        }
    }

    /**
     * Silueta de la capa con capucha, de pie, mirando a la derecha y con los pies en (0, 0)
     * (y crece hacia abajo, como en la pantalla). Los puntos se unen con curvas de Bézier.
     */
    private static final double[][] CLOAK = {
        {   1.0, -31.0 },   //  0 coronilla
        {   8.5, -27.5 },   //  1 frente de la capucha
        {  11.0, -21.0 },   //  2 ala de la capucha, sobre la cara
        {   9.0, -16.5 },   //  3 debajo del ala, donde la capucha se recoge
        {  10.0, -10.0 },   //  4 pecho
        {  12.0,  -4.5 },   //  5 orilla de enfrente   (orilla de abajo: 5, 7, 9, 11, 13)
        {   9.2,   0.5 },   //  6 jirón de enfrente    (puntas de los jirones: 6, 8, 10, 12)
        {   6.2,  -4.8 },   //  7
        {   3.4,   1.8 },   //  8 jirón
        {   0.2,  -4.2 },   //  9
        {  -2.6,   0.8 },   // 10 jirón
        {  -5.8,  -4.8 },   // 11
        {  -9.0,   2.0 },   // 12 jirón de atrás
        { -12.0,  -4.5 },   // 13 orilla de atrás
        { -10.0, -12.0 },   // 14 espalda
        {  -8.5, -19.0 },   // 15 nuca
        { -12.0, -21.2 },   // 16 debajo de la punta
        { -15.3, -19.0 },   // 17 punta de la capucha, caída hacia atrás
        { -13.0, -27.0 },   // 18 la capucha se dobla hacia abajo
        {  -6.5, -31.5 },   // 19 capucha, atrás
    };
    private static final int HOOD_TIP = 17;
    /** Dónde queda cada punto en la bolita: ángulo en grados (0 = adelante, 90 = abajo). */
    private static final double[] BALL_ANGLES = {
        -95, -55, -20, 5, 30, 55, 68, 79, 90, 101, 112, 123, 134, 147, 172, 196, 206, 214, 228, 248 };

    // Colores: niebla gris azulada que se vuelve gris con poca vida; la cara, casi negra
    private static final Color HOOD = new Color(176, 192, 222);
    private static final Color HOOD_DIM = new Color(112, 116, 128);
    private static final Color HEM = new Color(110, 126, 166);
    private static final Color HEM_DIM = new Color(76, 80, 94);
    private static final Color FACE = new Color(16, 14, 30);
    private static final Color FEET = new Color(24, 20, 36);
    private static final Color EYE_DIM = new Color(105, 118, 140);
    private static final Color GLOW = new Color(185, 230, 255);

    /**
     * Dibuja a Candil con una matriz de modelo que lleva su silueta (diseñada mirando a la
     * derecha, con los pies en el origen) a su lugar en el mundo:
     *   M = T(centro + temblor, pies + flote) * R(inclinación) * S(dirección * sx, sy)
     * S estira o aplasta (salto, aterrizaje, miedo, respiración) y con dirección = -1 lo
     * refleja para que mire a la izquierda; R lo inclina hacia donde corre.
     */
    static void draw(Graphics2D g, Player p) {
        if (p.isDead()) return;
        Look look = p.getLook();
        double life = p.getLife();
        double animTime = p.getAnimTime(), stretch = p.getStretch(), seated = p.getSeated();
        double stepPhase = p.getStepPhase();
        int facing = p.getFacing();

        // Flota subiendo y bajando; al correr, en cambio, rebota un poco con cada paso
        double still = (1 - look.feet) * (1 - look.curl * seated);
        double bob = -0.8 * (1 + Math.sin(animTime * 2.4)) * still
                   - 0.9 * Math.abs(Math.sin(stepPhase)) * look.feet;
        double shakeX = look.fear * 0.6 * Math.sin(animTime * 53);
        double shakeY = look.fear * 0.3 * Math.sin(animTime * 41);
        double breath = 1 + 0.025 * Math.sin(animTime * 2.1);   // respira despacio
        double sx = (1 - 0.14 * stretch) * (1 + 0.05 * look.fear);
        double sy = (1 + 0.18 * stretch) * (1 - 0.2 * look.fear) * breath;

        Mat3 m = Mat3.translation(p.getCenterX() + shakeX, p.getBottom() + bob + shakeY)
                .multiply(Mat3.rotation(facing * look.lean))
                .multiply(Mat3.scale(facing * sx, sy));
        AffineTransform old = g.getTransform();
        g.transform(m.toAffineTransform());

        // La bolita del descanso: flotando es redonda; sentado en la repisa, un montoncito
        double ballY = -12 + 3 * seated, ballRx = 10.5 + 2 * seated, ballRy = 10 - 1.5 * seated;

        double op = look.opacity;
        drawAura(g, p, op);
        if (look.feet > 0.02) drawFeet(g, p, op);

        // La capa: degradado de arriba (capucha) a abajo (jirones), con una sombra bajo la
        // capucha (no en la bolita), y encima el brillo interior
        Path2D cloak = cloakShape(p, ballY, ballRx, ballRy);
        Color top = mix(HOOD_DIM, HOOD, life), bottom = mix(HEM_DIM, HEM, life);
        double a = 235 * op;
        g.setPaint(new LinearGradientPaint(0, -31, 0, 2,
            new float[] { 0f, 0.4f, 0.46f, 0.56f, 1f },
            new Color[] { withAlpha(top, a), withAlpha(top, a),
                          withAlpha(mix(top, bottom, 0.85 * (1 - look.curl)), a),
                          withAlpha(mix(top, bottom, 0.45), a),
                          withAlpha(bottom, a * (0.2 + 0.4 * op)) }));
        g.fill(cloak);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(0, -13), 15,
            new float[] { 0f, 1f },
            new Color[] { withAlpha(GLOW, 70 * life * op), withAlpha(GLOW, 0) }));
        g.fill(cloak);

        drawFace(g, p, ballY, ballRx, ballRy);
        g.setTransform(old);
    }

    /**
     * La silueta de la capa: los 20 puntos de CLOAK, movidos según la forma, y unidos con
     * curvas de Bézier.
     *   - Los jirones ondulan con ondas seno. Según la forma, la capa vuela hacia atrás
     *     (sweep), hacia arriba (lift) o se abre (spread); cada punto se mueve según qué tan
     *     abajo está (k = 0 en los hombros, 1 en la orilla): lo de abajo vuela más.
     *   - Para acurrucarse, cada punto se mezcla con su lugar en una bolita (una elipse):
     *     p = capa + (bolita - capa) * curl   (interpolación lineal punto por punto)
     *   - Las curvas son un spline de Catmull-Rom: la tangente en cada punto es la mitad de
     *     (siguiente - anterior), y los puntos de control de Bézier quedan a un tercio de ella.
     *     En las puntas (jirones y capucha) la tangente es 0, y la curva hace una esquina.
     */
    private static Path2D cloakShape(Player candil, double ballY, double ballRx, double ballRy) {
        Look look = candil.getLook();
        double wavePhase = candil.getWavePhase();
        int n = CLOAK.length;
        double[] px = new double[n], py = new double[n];
        double wave = 0.9 * (1 + 1.5 * (1 - look.opacity));   // en la sombra ondulan más
        for (int i = 0; i < n; i++) {
            double cx = CLOAK[i][0], cy = CLOAK[i][1];
            boolean tip = isTatterTip(i);
            double side = cx / 12;                            // -1 atrás ... 1 adelante
            double back = Math.max(0, -side);
            double k = Math.max(0, Math.min(1, (cy + 17) / 18));   // 0 en los hombros ... 1 en la orilla
            if (tip) {   // los jirones ondulan
                int j = (i - 6) / 2;
                cx += wave * Math.sin(wavePhase + 1.9 * j);
                cy += 0.6 * wave * Math.sin(1.3 * wavePhase + 2.3 * j);
            }
            // El viento y la caída mueven la punta de la capucha (y a medias, sus vecinos)
            double flap = i == HOOD_TIP ? 1 : Math.abs(i - HOOD_TIP) == 1 ? 0.5 : 0;
            cx -= flap * 2.5 * look.sweep;
            cy += flap * (-1.5 * look.sweep - 2.5 * look.lift + 0.5 * Math.sin(0.7 * wavePhase));
            // Corriendo, el viento empuja la capa hacia atrás y la levanta: más entre más abajo
            // (k^2) y más por la espalda
            cx -= look.sweep * (4 * k * k + 2 * back * k + (tip ? 1.5 : 0));
            cy -= look.sweep * (2 * k * k + 1.5 * back * k);
            // Cayendo, la orilla y los jirones se levantan y se abren; saltando (lift < 0) cuelgan más
            cx += look.lift * 2 * side * k;
            cy -= look.lift * (tip ? 6.5 : 3.5) * k * k;
            // La capa se abre hacia los lados
            cx += look.spread * 3.5 * side * k;
            cy += look.spread * (tip ? 2 : 0.5) * k;

            // Su lugar en la bolita: la punta de la capucha asoma un poco
            double a = Math.toRadians(BALL_ANGLES[i]);
            double r = i == HOOD_TIP ? 1.3 : tip ? 1.06 : 1;
            double bx = ballRx * r * Math.cos(a), by = ballY + ballRy * r * Math.sin(a);
            px[i] = cx + (bx - cx) * look.curl;
            py[i] = cy + (by - cy) * look.curl;
        }

        double[] mx = new double[n], my = new double[n];   // tangentes de Catmull-Rom
        for (int i = 0; i < n; i++) {
            int prev = (i + n - 1) % n, next = (i + 1) % n;
            double soft = i == HOOD_TIP ? 0 : isTatterTip(i) ? look.curl : 1;   // en la bolita ya no hay puntas
            mx[i] = (px[next] - px[prev]) / 2 * soft;
            my[i] = (py[next] - py[prev]) / 2 * soft;
        }
        Path2D p = new Path2D.Double();
        p.moveTo(px[0], py[0]);
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            p.curveTo(px[i] + mx[i] / 3, py[i] + my[i] / 3,
                      px[next] - mx[next] / 3, py[next] - my[next] / 3,
                      px[next], py[next]);
        }
        p.closePath();
        return p;
    }

    private static boolean isTatterTip(int i) {
        return i >= 6 && i <= 12 && i % 2 == 0;
    }

    /** Brillo alrededor: se apaga mientras pierde vida. */
    private static void drawAura(Graphics2D g, Player p, double op) {
        double life = p.getLife();
        float r = 30f;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(0, -15), r,
            new float[] { 0f, 1f },
            new Color[] { withAlpha(GLOW, (25 + 60 * life) * op), withAlpha(GLOW, 0) }));
        g.fill(new Ellipse2D.Double(-r, -15 - r, r * 2, r * 2));
    }

    /** Dos piecitos oscuros que asoman bajo la capa al caminar, uno adelante y otro atrás. */
    private static void drawFeet(Graphics2D g, Player p, double op) {
        Look look = p.getLook();
        double stepPhase = p.getStepPhase();
        g.setColor(withAlpha(FEET, 255 * look.feet * op));
        double swing = 2 * Math.sin(stepPhase);
        double y0 = lerp(-3.5, -0.9, look.feet);
        double upA = Math.max(0, Math.cos(stepPhase)), upB = Math.max(0, -Math.cos(stepPhase));
        g.fill(new Ellipse2D.Double(3 + swing - 1.4, y0 - upA - 0.9, 2.8, 1.8));
        g.fill(new Ellipse2D.Double(-1 - swing - 1.4, y0 - upB - 0.9, 2.8, 1.8));
    }

    /**
     * La cara oscura dentro de la capucha y los dos ojos blancos. Los ojos brillan con un
     * degradado radial y se apagan con la vida (con raíz cuadrada: siguen claros hasta que
     * queda poca). Al descansar, la cara se mueve al frente de la bolita.
     */
    private static void drawFace(Graphics2D g, Player p, double ballY, double ballRx, double ballRy) {
        Look look = p.getLook();
        double life = p.getLife();
        double blinkLeft = p.getBlinkLeft();
        double c = look.curl;
        double fx = lerp(4.5, 0.43 * ballRx, c), fy = lerp(-21.5, ballY - 0.33 * ballRy, c);
        double fw = lerp(10, 9, c), fh = lerp(9, 6.5, c);
        g.setColor(withAlpha(FACE, 90));   // orilla suave: el hueco de la capucha se hunde
        g.fill(new Ellipse2D.Double(fx - fw / 2 - 1, fy - fh / 2 - 0.8, fw + 2, fh + 1.6));
        g.setColor(FACE);
        g.fill(new Ellipse2D.Double(fx - fw / 2, fy - fh / 2, fw, fh));

        // El brillo de los ojos se queda dentro de la cara: brillar más es más intenso, no más grande
        double light = Math.sqrt(life);
        float haloR = (float) (fw / 2);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(fx, fy), haloR,
            new float[] { 0f, 1f },
            new Color[] { withAlpha(GLOW, 75 * look.glow * light), withAlpha(GLOW, 0) }));
        g.fill(new Ellipse2D.Double(fx - haloR, fy - haloR, haloR * 2, haloR * 2));

        double ew = 2.5 * (0.6 + 0.4 * look.eyes);
        double eh = 3.3 * look.eyes * (blinkLeft > 0 ? 0.25 : 1);
        double gap = 0.23 * fw;   // la mitad de la distancia entre los ojos
        g.setColor(mix(EYE_DIM, Color.WHITE, light));
        g.fill(new Ellipse2D.Double(fx - gap - ew / 2, fy - eh / 2 + 0.3, ew, eh));
        g.fill(new Ellipse2D.Double(fx + gap - ew / 2, fy - eh / 2 + 0.3, ew, eh));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Interpolación lineal entre dos colores: t = 0 regresa 'a', t = 1 regresa 'b'. */
    private static Color mix(Color a, Color b, double t) {
        return new Color(
            (int) Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) Math.round(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }

    private static Color withAlpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.max(0, Math.min(255, Math.round(a))));
    }
}
