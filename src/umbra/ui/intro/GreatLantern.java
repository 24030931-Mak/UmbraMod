package umbra.ui.intro;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * [v0.8.6] El Gran Farol: una linterna antigua y gigante de piedra, hierro y vidrio.
 *
 * Se dibuja en coordenadas propias: el origen (0, 0) es el centro del pie del pedestal y la y
 * crece hacia abajo, así que todo el Farol queda en y negativas (mide unas 475 unidades de
 * alto, más las dos cadenas que suben de la argolla al techo). Quien lo dibuja pone antes la
 * matriz que lo lleva a su lugar: vista · T(pie).
 *
 * [v0.8.7] Silueta gótica: el vidrio es alto y esbelto (en vez de una esfera ancha), el techo
 * sube en aguja, el pedestal es de piedra tallada con chaflanes rectos y dos cadenas colosales
 * lo cuelgan del techo de la caverna.
 *
 * Curvas de Bézier. La panza del vidrio es simétrica; su lado izquierdo son dos curvas cúbicas
 * seguidas (la panza y el cuello). Una cúbica va de P0 a P3 jalada por dos puntos de control:
 *     B(t) = (1-t)³·P0 + 3(1-t)²t·P1 + 3(1-t)t²·P2 + t³·P3,     t de 0 a 1
 * El lado derecho es el mismo con las x cambiadas de signo. Las costillas de hierro son esa
 * misma curva con las x multiplicadas por un factor (1 = la orilla, 0.45 = una costilla de en
 * medio): escalar los puntos de control escala toda la curva, porque B(t) es una combinación
 * lineal de ellos. El techo usa curvas cuadráticas (un solo punto de control).
 *
 * La luz son degradados radiales con centro en la llama: el vidrio va de casi blanco a ámbar, y
 * detrás hay una mancha de brillo aditivo (Glow) que respira. La llama son tres capas (orilla
 * naranja, amarillo, centro casi blanco), cada una un contorno de cuatro cúbicas cuya punta se
 * mece con ondas seno desfasadas.
 *
 * El Farol tiene dos aspectos: entero y encendido (cálido), o roto y apagado (frío, sin la
 * panza de vidrio). Lo que va detrás de la llama (el pedestal y el vidrio con sus degradados,
 * que es lo más caro de pintar) se dibuja una sola vez en una imagen por aspecto. En cada cuadro
 * se dibujan la llama, las grietas que van avanzando y, encima, el hierro y los reflejos: esos
 * sí como curvas, para que sus orillas se vean nítidas con cualquier acercamiento. Las cadenas
 * también van en imágenes aparte (una por aspecto): solo se ven de lejos, arriba del cuadro.
 */
public final class GreatLantern {

    /** Altura del centro de la llama sobre el pie (negativa: hacia arriba). */
    public static final double FLAME_Y = -205;
    /** De dónde salen las grietas: un punto del vidrio, a un lado de la llama. */
    public static final double CRACK_X = -30, CRACK_Y = -250;

    private static final double GLASS_BOTTOM = -98, GLASS_TOP = -365;
    private static final double HEAD_Y = -190;   // centro de la cabeza redonda de la llama
    /**
     * El lado izquierdo del vidrio, de abajo hacia arriba: la panza (P0, P1, P2, P3) y el cuello
     * (sus dos puntos de control y su final), como pares x, y. El primer punto de control del
     * cuello sigue la dirección P3 - P2 con que termina la panza, para que no se note la unión.
     */
    private static final double[] BELLY = { -56, GLASS_BOTTOM, -78, -135, -84, -245, -62, -295 };
    private static final double[] NECK = { -53, -315, -45, -342, -44, GLASS_TOP };
    /** Dónde está la argolla del remate (su orilla de arriba): de ahí salen las cadenas. */
    private static final double RING_TOP = -475;
    /** La punta del techo en aguja, sobre el collar. */
    private static final double ROOF_PEAK = GLASS_TOP - 80;
    /** Dónde termina arriba el mechero (la copa en que se apoya la cabeza de la llama). */
    private static final double BURNER_TOP = -160;
    /**
     * Hasta dónde suben las cadenas (el techo, fuera del cuadro) y cuánto se abren a cada lado.
     * El borde de la roca del techo queda en y ≈ -560…-500 (el mundo y ≈ 16…76) y el borde de
     * arriba de la pantalla, en la toma de lejos, en y ≈ -582: las cadenas siguen de largo.
     */
    private static final double CHAIN_END = -700, CHAIN_SPREAD = 120;
    /** El rectángulo que ocupa el Farol (unidades propias) y los píxeles de imagen por unidad. */
    private static final int LEFT = -140, TOP = -490, WIDTH = 280, HEIGHT = 500;
    /** El rectángulo de las cadenas (de la argolla al techo). */
    private static final int CHAIN_LEFT = -140, CHAIN_TOP = (int) CHAIN_END - 20, CHAIN_WIDTH = 280,
            CHAIN_HEIGHT = (int) (RING_TOP - CHAIN_END) + 32;
    private static final double RES = 2.5;

    // ---- Colores: encendido (cálidos) y apagado (fríos) ----
    private static final Color STONE_LIT = new Color(244, 202, 140), STONE_COLD = new Color(74, 78, 100);
    private static final Color STONE_SIDE = new Color(168, 110, 66), STONE_SIDE_COLD = new Color(40, 44, 62);
    private static final Color IRON_LIT = new Color(86, 54, 40), IRON_COLD = new Color(22, 24, 36);
    private static final Color IRON_SHINE = new Color(255, 200, 124), IRON_SHINE_COLD = new Color(96, 108, 140);
    private static final Color GLASS_COLD = new Color(120, 140, 185, 46);
    private static final Color GLASS_EDGE = new Color(255, 248, 220), GLASS_EDGE_COLD = new Color(150, 168, 205);
    private static final Color OUTER = new Color(255, 135, 40);
    private static final Color MIDDLE = new Color(255, 200, 70);
    private static final Color CORE = new Color(255, 248, 215);

    private static final Stroke RIB = new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke INNER_RIB = new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke THIN = new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private final Path2D glass = glassShape();
    private final Area brokenGlass;
    private final Path2D[] glassShards;
    private final Cracks cracks;
    private final BufferedImage litBack, brokenBack, litChains, coldChains;
    private final Glow halo = new Glow(192, new Color(255, 206, 120), 0.5, 0.62);
    private final Glow ember = new Glow(48, new Color(255, 140, 60), 1.1, 0.5);

    private double time, flamePhase;
    private double agitation;       // 0 = tranquila, 1 = se sacude
    private double crack;           // cuánto han avanzado las grietas (0 a 1)
    private boolean broken;         // roto y apagado
    private double emberGlow;       // brasa que queda en el mechero al romperse (0 a 1)

    public GreatLantern(long seed) {
        Random rnd = new Random(seed);
        cracks = new Cracks(seed, CRACK_X, CRACK_Y, new double[][] {
            { -88, -190 }, { -66, -312 }, { 14, -356 }, { 80, -282 }, { 86, -168 }, { 4, -104 }, { -66, -118 } });

        // El vidrio roto: a la panza se le resta toda la franja de en medio, con las dos orillas
        // en picos al azar. Quedan los colmillos de abajo (sobre el plato) y los de arriba (bajo
        // el collar).
        Path2D hole = new Path2D.Double();
        int teeth = 11;
        hole.moveTo(-110, -310);
        for (int i = 0; i <= teeth; i++) {   // orilla de arriba, de izquierda a derecha
            double x = -94 + 188.0 * i / teeth;
            hole.lineTo(x, i % 2 == 0 ? -302 - rnd.nextDouble() * 12 : -334 - rnd.nextDouble() * 14);
        }
        hole.lineTo(110, -310);
        hole.lineTo(110, -128);
        for (int i = teeth; i >= 0; i--) {   // orilla de abajo, de regreso
            double x = -94 + 188.0 * i / teeth;
            hole.lineTo(x, i % 2 == 0 ? -144 + rnd.nextDouble() * 14 : -112 + rnd.nextDouble() * 10);
        }
        hole.lineTo(-110, -128);
        hole.closePath();
        brokenGlass = new Area(glass);
        brokenGlass.subtract(new Area(hole));

        // Pedazos de vidrio caídos sobre el pedestal: triángulos chicos
        glassShards = new Path2D[11];
        for (int i = 0; i < glassShards.length; i++) {
            double x = -118 + rnd.nextDouble() * 236, s = 4 + rnd.nextDouble() * 7;
            double y = Math.abs(x) > 104 ? -21 : Math.abs(x) > 80 ? -41 : -67;   // sobre el escalón que le toca
            Path2D p = new Path2D.Double();
            p.moveTo(x - s, y);
            p.lineTo(x + s * (0.2 + rnd.nextDouble() * 0.8), y);
            p.lineTo(x - s * 0.3 + rnd.nextDouble() * s, y - s * (0.5 + rnd.nextDouble() * 0.7));
            p.closePath();
            glassShards[i] = p;
        }

        litBack = render(false);
        brokenBack = render(true);
        litChains = renderChains(true);
        coldChains = renderChains(false);
    }

    // =========================================================
    //  ESTADO (lo fijan las escenas)
    // =========================================================

    /** Qué tan agitada está la llama: 0 = tranquila, 1 = se sacude y parpadea. */
    public void setAgitation(double a) { this.agitation = Ease.clamp01(a); }

    /** Cuánto han avanzado las grietas del vidrio, de 0 a 1. */
    public void setCrack(double progress) { this.crack = Ease.clamp01(progress); }

    /** Roto: apagado, sin la panza de vidrio (queda el borde quebrado) y con pedazos sobre el pedestal. */
    public void setBroken(boolean broken) { this.broken = broken; }

    /** La brasa que queda en el mechero después de romperse (0 a 1). */
    public void setEmber(double e) { this.emberGlow = Ease.clamp01(e); }

    public void update(double dt) {
        time += dt;
        flamePhase += dt * (1 + 2.2 * agitation);   // agitada, las ondas corren más rápido
    }

    // =========================================================
    //  DIBUJO DE CADA CUADRO
    // =========================================================

    /** El resplandor alrededor del Farol encendido. Va antes que el Farol, para quedar detrás. */
    public void drawHalo(Graphics2D g) {
        if (broken) return;
        double breath = 1 + 0.035 * Math.sin(time * 1.7) + 0.02 * Math.sin(time * 4.3);
        double leak = 1 + 0.45 * crack;   // por las grietas se escapa más luz
        halo.draw(g, 0, FLAME_Y, 128 * breath * leak);
    }

    public void draw(Graphics2D g) {
        drawLayer(g, broken ? coldChains : litChains, CHAIN_LEFT, CHAIN_TOP, CHAIN_WIDTH, CHAIN_HEIGHT);
        drawLayer(g, broken ? brokenBack : litBack, LEFT, TOP, WIDTH, HEIGHT);
        if (broken) {
            if (emberGlow > 0.02) ember.draw(g, 0, BURNER_TOP, 26 * emberGlow * (0.75 + 0.25 * Math.sin(time * 5.1)));
        } else {
            drawFlame(g);
            if (crack > 0) {   // las grietas, recortadas al vidrio
                Shape oldClip = g.getClip();
                g.clip(glass);
                cracks.draw(g, crack, 1);
                g.setClip(oldClip);
            }
        }
        Stroke oldStroke = g.getStroke();
        drawReflections(g, !broken, broken);
        drawIron(g, !broken);
        g.setStroke(oldStroke);
    }

    /** Una de las imágenes ya hechas, en su rectángulo (unidades propias del Farol). */
    private static void drawLayer(Graphics2D g, BufferedImage layer, int x, int y, int w, int h) {
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object quality = g.getRenderingHint(RenderingHints.KEY_RENDERING);
        Object sampling = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, Scene.smoothScaling(g));   // bilineal con la tarjeta de video
        g.drawImage(layer, x, y, w, h, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, quality);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, sampling != null ? sampling
                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);   // sin indicación, Java2D usa el vecino
    }

    /**
     * La llama: tres capas, de la orilla naranja al centro casi blanco.
     * Agitada se estira, ondula más y parpadea (su transparencia sube y baja 7 veces por segundo).
     */
    private void drawFlame(Graphics2D g) {
        double flick = 1 - 0.3 * agitation * (0.5 + 0.5 * Math.sin(time * 44));
        double r = 30 * (1 - 0.12 * agitation);
        double len = 104 * (1 + 0.28 * agitation);
        double amp = r * (0.1 + 0.3 * agitation);
        double lean = 3 * Math.sin(time * 1.3) + 12 * agitation * Math.sin(time * 9.5);

        g.translate(0, HEAD_Y);
        fillLayer(g, flameLayer(r, len, amp, flamePhase, lean), OUTER, 232 * flick);
        g.translate(0, 0.12 * r);
        fillLayer(g, flameLayer(0.7 * r, 0.74 * len, 0.8 * amp, flamePhase + 0.4, 0.7 * lean), MIDDLE, 240 * flick);
        g.translate(0, 0.1 * r);
        fillLayer(g, flameLayer(0.42 * r, 0.5 * len, 0.6 * amp, flamePhase + 0.8, 0.4 * lean), CORE, 248 * flick);
        g.translate(0, -0.22 * r);

        g.translate(0, -HEAD_Y);
    }

    /**
     * Contorno de una capa de la llama, con la cabeza redonda de radio r centrada en el origen y
     * la punta hacia arriba, a 'len' del centro. Cuatro cúbicas: dos cuartos de círculo abajo
     * (con puntos de control a 0.5523·r, la constante que mejor aproxima un círculo) y dos
     * curvas que suben a la punta. Los puntos de control de la punta se mueven de lado con ondas
     * seno desfasadas: una onda que sube por la llama y la hace ondular.
     */
    private static Path2D flameLayer(double r, double len, double amp, double phase, double lean) {
        double k = 0.5523 * r;
        double w1 = amp * Math.sin(phase * 9) + 0.2 * lean;
        double w2 = amp * Math.sin(phase * 9 - 1.9) + 0.6 * lean;
        double w3 = amp * 1.3 * Math.sin(phase * 9 - 3.4) + lean;
        double tip = len * (1 + 0.07 * Math.sin(phase * 13 + 0.6));
        double c1 = 0.35 * tip, c2 = 0.7 * tip;
        Path2D p = new Path2D.Double();
        p.moveTo(0, r);
        p.curveTo(k, r, r, k, r, 0);                                  // abajo -> derecha
        p.curveTo(0.95 * r + w1, -c1, 0.4 * r + w2, -c2, w3, -tip);   // derecha -> punta
        p.curveTo(-0.4 * r + w2, -c2, -0.95 * r + w1, -c1, -r, 0);    // punta -> izquierda
        p.curveTo(-r, k, -k, r, 0, r);                                // izquierda -> abajo
        p.closePath();
        return p;
    }

    private static void fillLayer(Graphics2D g, Shape shape, Color c, double alpha) {
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.max(0, Math.min(255, Math.round(alpha)))));
        g.fill(shape);
    }

    // =========================================================
    //  LAS IMÁGENES (una sola vez)
    // =========================================================

    /** Graphics de una imagen para el rectángulo que empieza en (x, y): matriz S(RES) · T(-x, -y). */
    private static Graphics2D layerGraphics(BufferedImage img, int x, int y) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(RES, RES);
        g.translate(-x, -y);
        return g;
    }

    /**
     * Dibuja una de las dos imágenes: lo que va detrás de la llama (pedestal, vidrio y mechero)
     * del Farol entero y encendido, o del roto y apagado. La imagen usa la matriz
     * S(RES) · T(-LEFT, -TOP): lleva la esquina del rectángulo del Farol al (0, 0) de la imagen.
     */
    private BufferedImage render(boolean isBroken) {
        BufferedImage img = new BufferedImage((int) (WIDTH * RES), (int) (HEIGHT * RES), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = layerGraphics(img, LEFT, TOP);
        drawPedestal(g, !isBroken, isBroken);
        drawGlass(g, !isBroken, isBroken);
        g.dispose();
        return img;
    }

    /** Las dos cadenas, de hierro cálido (encendido) o frío (apagado). */
    private static BufferedImage renderChains(boolean lit) {
        BufferedImage img = new BufferedImage((int) (CHAIN_WIDTH * RES), (int) (CHAIN_HEIGHT * RES), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = layerGraphics(img, CHAIN_LEFT, CHAIN_TOP);
        drawChains(g, lit);
        g.dispose();
        return img;
    }

    /**
     * Pedestal de piedra tallada: tres escalones (el de arriba, un tambor con runas) y el plato
     * de hierro. Cada escalón es un trapecio (los lados se cierran un poco hacia arriba) con las
     * dos esquinas de arriba cortadas por un chaflán recto a 45°: el corte avanza lo mismo en x
     * que en y. La cara del chaflán mira arriba y afuera, así que le da la luz: va más clara.
     */
    private void drawPedestal(Graphics2D g, boolean lit, boolean isBroken) {
        Color top = lit ? STONE_LIT : STONE_COLD, side = lit ? STONE_SIDE : STONE_SIDE_COLD;
        Color iron = lit ? IRON_LIT : IRON_COLD, shine = lit ? IRON_SHINE : IRON_SHINE_COLD;
        Color edge = Ease.fade(lit ? new Color(255, 240, 200) : shine, 0.75);
        double[][] steps = { { 130, 0, 22 }, { 104, -22, 20 }, { 80, -42, 26 } };   // medio ancho, pie, alto
        double taper = 3, cut = 5;   // cuánto se cierra cada lado hacia arriba y el tamaño del chaflán
        for (double[] s : steps) {
            double hw = s[0], foot = s[1], head = s[1] - s[2], hwTop = hw - taper;
            Path2D block = new Path2D.Double();
            block.moveTo(-hw, foot);
            block.lineTo(-hwTop, head + cut);
            block.lineTo(-hwTop + cut, head);
            block.lineTo(hwTop - cut, head);
            block.lineTo(hwTop, head + cut);
            block.lineTo(hw, foot);
            block.closePath();
            // Degradado de lado a lado, espejeado: más claro al centro, donde le da la luz
            g.setPaint(new GradientPaint((float) -hw, 0, side, 0, 0, top, true));
            g.fill(block);
            // Las caras de los dos chaflanes (una franja clara a 45°) y el filo de arriba
            g.setColor(Ease.fade(edge, 0.55));
            for (int k = -1; k <= 1; k += 2) {
                Path2D bevel = new Path2D.Double();
                bevel.moveTo(k * hwTop, head + cut);
                bevel.lineTo(k * (hwTop - cut), head);
                bevel.lineTo(k * (hwTop - cut), head + 2);
                bevel.lineTo(k * (hwTop - 1.2), head + cut + 0.8);
                bevel.closePath();
                g.fill(bevel);
            }
            g.setColor(edge);
            g.fill(new Rectangle2D.Double(-hwTop + cut, head, 2 * (hwTop - cut), 1.6));
            // Una junta tallada a lo largo del escalón y la sombra del pie
            g.setColor(new Color(20, 10, 10, 46));
            g.fill(new Rectangle2D.Double(-hw + 4, head + s[2] * 0.55, 2 * hw - 8, 1.2));
            g.setColor(new Color(20, 10, 10, 70));
            g.fill(new Rectangle2D.Double(-hw + 2, foot - 2.5, 2 * hw - 4, 2.5));
        }
        // Runas talladas en el tambor: rombos, uno sí y uno no con una raya
        g.setStroke(THIN);
        g.setColor(Ease.fade(lit ? new Color(112, 64, 38) : new Color(16, 18, 30), 0.85));
        for (int i = -2; i <= 2; i++) {
            double x = i * 27, y = -55;
            Path2D rune = new Path2D.Double();
            rune.moveTo(x, y - 7);
            rune.lineTo(x + 5, y);
            rune.lineTo(x, y + 7);
            rune.lineTo(x - 5, y);
            rune.closePath();
            if (i % 2 == 0) {
                rune.moveTo(x, y - 7);
                rune.lineTo(x, y + 7);
            }
            g.draw(rune);
        }
        // El plato de hierro que sostiene el vidrio
        Path2D bowl = new Path2D.Double();
        bowl.moveTo(-56, -68);
        bowl.lineTo(56, -68);
        bowl.quadTo(76, -82, 64, GLASS_BOTTOM - 2);
        bowl.lineTo(-64, GLASS_BOTTOM - 2);
        bowl.quadTo(-76, -82, -56, -68);
        bowl.closePath();
        g.setPaint(new GradientPaint(-70, 0, iron, 0, 0, Ease.mix(iron, shine, 0.45), true));
        g.fill(bowl);

        if (isBroken) {   // vidrios caídos
            g.setColor(Ease.fade(GLASS_EDGE_COLD, 0.6));
            for (Path2D shard : glassShards) g.fill(shard);
        }
    }

    /** El vidrio (iluminado desde la llama con un degradado radial, o frío) y el mechero. */
    private void drawGlass(Graphics2D g, boolean lit, boolean isBroken) {
        Shape body = isBroken ? brokenGlass : glass;
        if (lit) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(0, FLAME_Y), 175, new float[] { 0f, 0.42f, 1f },
                    new Color[] { new Color(255, 250, 218, 232), new Color(255, 224, 142, 196), new Color(238, 162, 72, 150) }));
            g.fill(body);
        } else {
            g.setColor(GLASS_COLD);
            g.fill(body);
            Shape oldClip = g.getClip();   // las grietas quedan como rayas oscuras en lo que sobrevivió
            g.clip(body);
            cracks.draw(g, 1, 0);
            g.setClip(oldClip);
        }
        // El mechero: la base, un vástago y la copa donde se apoya la cabeza de la llama
        g.setColor(lit ? IRON_LIT : IRON_COLD);
        g.fill(new Rectangle2D.Double(-20, -104, 40, 8));
        g.fill(new Rectangle2D.Double(-6, BURNER_TOP + 20, 12, -BURNER_TOP - 118));
        g.fill(new RoundRectangle2D.Double(-14, BURNER_TOP, 28, 24, 6, 6));
    }

    /** Los reflejos del vidrio (dos curvas claras) y su contorno. Van delante de la llama. */
    private void drawReflections(Graphics2D g, boolean lit, boolean isBroken) {
        if (!isBroken) {
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 255, 255, 84));
            g.draw(new QuadCurve2D.Double(-58, -146, -73, -212, -56, -282));
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 255, 255, 50));
            g.draw(new QuadCurve2D.Double(52, -160, 64, -205, 55, -250));
        }
        g.setStroke(THIN);
        g.setColor(Ease.fade(lit ? GLASS_EDGE : GLASS_EDGE_COLD, isBroken ? 0.8 : 0.6));
        g.draw(isBroken ? brokenGlass : glass);
    }

    /**
     * La jaula de hierro (costillas y aros), el collar, el techo en aguja (gótico) y el remate
     * con su argolla.
     */
    private static void drawIron(Graphics2D g, boolean lit) {
        Color iron = lit ? IRON_LIT : IRON_COLD;
        Color shineColor = lit ? IRON_SHINE : IRON_SHINE_COLD;
        Color shine = Ease.fade(shineColor, 0.7);

        // Aros: se ven como arcos porque rodean la panza (una cuadrática que cuelga un poco)
        g.setStroke(INNER_RIB);
        g.setColor(iron);
        for (double y : new double[] { -140, -282 }) {
            double hw = halfWidthAt(y);
            g.draw(new QuadCurve2D.Double(-hw, y, 0, y + 9, hw, y));
        }
        // Costillas: la curva del vidrio con las x multiplicadas por un factor
        for (double f : new double[] { -0.45, 0.45 }) g.draw(rib(f));
        g.setStroke(RIB);
        for (double f : new double[] { -1, 1 }) g.draw(rib(f));
        g.setStroke(THIN);
        g.setColor(shine);
        for (double f : new double[] { -0.985, 0.985, -0.44, 0.46 }) g.draw(rib(f));

        // Collar y techo: el alero apenas sale del collar y sube en curva hasta una aguja
        double eave = GLASS_TOP - 10, peak = ROOF_PEAK;
        g.setColor(iron);
        g.fill(new RoundRectangle2D.Double(-52, GLASS_TOP - 14, 104, 16, 5, 5));
        Path2D roof = new Path2D.Double();
        roof.moveTo(-84, eave);
        roof.quadTo(-30, eave - 16, -10, peak);   // los lados se curvan hacia dentro: una aguja gótica
        roof.lineTo(10, peak);
        roof.quadTo(30, eave - 16, 84, eave);
        roof.quadTo(0, eave - 12, -84, eave);
        roof.closePath();
        g.setPaint(new GradientPaint(-84, 0, iron, 0, 0, Ease.mix(iron, shineColor, 0.3), true));
        g.fill(roof);
        g.setStroke(THIN);
        g.setColor(shine);
        Path2D eaveLine = new Path2D.Double();
        eaveLine.moveTo(-84, eave);
        eaveLine.quadTo(0, eave - 12, 84, eave);
        g.draw(eaveLine);
        g.draw(new QuadCurve2D.Double(-80, eave - 1, -30, eave - 16, -11, peak + 3));

        // Remate: una bola sobre la aguja y, encima, la argolla de donde salen las cadenas
        double ball = peak - 8;   // centro de la bola
        g.setColor(iron);
        g.fill(new Ellipse2D.Double(-8, ball - 8, 16, 16));
        g.setStroke(new BasicStroke(3.4f));
        g.draw(new Ellipse2D.Double(-10, RING_TOP, 20, ball - 6 - RING_TOP));
        g.setStroke(THIN);
        g.setColor(shine);
        g.draw(new QuadCurve2D.Double(-6, ball + 4, 0, ball - 2, 6, ball + 4));
    }

    /**
     * Las dos cadenas colosales: salen juntas de la argolla y suben abiertas en V hasta el techo
     * (CHAIN_END, más arriba que el borde de cualquier toma: nunca se ve dónde terminan). Cada
     * eslabón es un óvalo; uno sí y uno no se ve de frente (el aro abierto) o de canto (una
     * barra), como en una cadena de verdad, donde cada eslabón gira 90° respecto al anterior.
     * Para ponerlos a lo largo de la cadena se rota cada uno al ángulo de la recta:
     *     M = T(punto) · R(ángulo)
     */
    private static void drawChains(Graphics2D g, boolean lit) {
        Color iron = lit ? IRON_LIT : IRON_COLD;
        Color shine = Ease.fade(lit ? IRON_SHINE : IRON_SHINE_COLD, 0.6);
        Stroke ring = new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        double linkLength = 30, pitch = 23;   // largo de un eslabón y distancia entre sus centros
        for (int side = -1; side <= 1; side += 2) {
            double x0 = side * 3, y0 = RING_TOP + 4, x1 = side * CHAIN_SPREAD, y1 = CHAIN_END;
            double len = Math.hypot(x1 - x0, y1 - y0), angle = Math.atan2(y1 - y0, x1 - x0);
            int links = (int) Math.ceil(len / pitch) + 1;
            for (int i = 0; i < links; i++) {
                double d = Math.min(len, linkLength / 2 + i * pitch);
                AffineTransform old = g.getTransform();
                g.translate(x0 + (x1 - x0) * d / len, y0 + (y1 - y0) * d / len);
                g.rotate(angle);
                if (i % 2 == 0) {   // de frente: el aro abierto
                    g.setStroke(ring);
                    g.setColor(iron);
                    g.draw(new RoundRectangle2D.Double(-linkLength / 2, -8.5, linkLength, 17, 16, 16));
                    g.setStroke(THIN);
                    g.setColor(shine);
                    g.draw(new Line2D.Double(-linkLength / 2 + 7, -6.8, linkLength / 2 - 7, -6.8));
                } else {            // de canto: una barra
                    g.setColor(iron);
                    g.fill(new RoundRectangle2D.Double(-linkLength / 2, -3.4, linkLength, 6.8, 6, 6));
                    g.setStroke(THIN);
                    g.setColor(shine);
                    g.draw(new Line2D.Double(-linkLength / 2 + 4, -2, linkLength / 2 - 4, -2));
                }
                g.setTransform(old);
            }
        }
    }

    // =========================================================
    //  FORMAS
    // =========================================================

    /** La panza de vidrio completa: el lado izquierdo de abajo hacia arriba, y el derecho de regreso. */
    private static Path2D glassShape() {
        Path2D p = rib(1);
        p.lineTo(-NECK[4], NECK[5]);
        p.curveTo(-NECK[2], NECK[3], -NECK[0], NECK[1], -BELLY[6], BELLY[7]);
        p.curveTo(-BELLY[4], BELLY[5], -BELLY[2], BELLY[3], -BELLY[0], BELLY[1]);
        p.closePath();
        return p;
    }

    /** El lado izquierdo del vidrio con las x multiplicadas por f (f negativo: del lado derecho). */
    private static Path2D rib(double f) {
        Path2D p = new Path2D.Double();
        p.moveTo(BELLY[0] * f, BELLY[1]);
        p.curveTo(BELLY[2] * f, BELLY[3], BELLY[4] * f, BELLY[5], BELLY[6] * f, BELLY[7]);   // la panza
        p.curveTo(NECK[0] * f, NECK[1], NECK[2] * f, NECK[3], NECK[4] * f, NECK[5]);         // el cuello
        return p;
    }

    /**
     * Medio ancho de la panza a la altura y. Se evalúa la cúbica de la panza,
     *     B(t) = (1-t)³·P0 + 3(1-t)²t·P1 + 3(1-t)t²·P2 + t³·P3,
     * en 40 valores de t y se toma el punto cuya y queda más cerca de la pedida.
     */
    private static double halfWidthAt(double y) {
        double best = -BELLY[0], bestGap = Double.MAX_VALUE;
        for (int i = 0; i <= 40; i++) {
            double t = i / 40.0, s = 1 - t;
            double a = s * s * s, b = 3 * s * s * t, c = 3 * s * t * t, d = t * t * t;
            double by = a * BELLY[1] + b * BELLY[3] + c * BELLY[5] + d * BELLY[7];
            double bx = -(a * BELLY[0] + b * BELLY[2] + c * BELLY[4] + d * BELLY[6]);
            if (Math.abs(by - y) < bestGap) {
                bestGap = Math.abs(by - y);
                best = bx;
            }
        }
        return best;
    }
}
