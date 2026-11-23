package umbra.ui.intro;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import umbra.graphics.CandelabraArt;
import umbra.graphics.CandelabraState;
import umbra.math.Mat3;

/**
 * [v0.8.6] El Gran Farol de la intro.
 *
 * [v0.9.5] Es el mismo candelabro de 6 brazos de la sala del trono: lo dibuja
 * graphics.CandelabraArt, con el mismo código que en el juego, a SIZE veces su tamaño de la
 * Cúpula. Encendido, con los 6 brazos ardiendo y el vidrio tibio (CandelabraState.setFragments(6));
 * roto, con las copas vacías y el vidrio frío (setFragments(0)). El estado es una instancia propia
 * de la intro, no la del nivel.
 *
 * Lo que es solo de la intro es lo que vive dentro del corazón de vidrio y lo que pasa al romperse:
 *   - la llama viva (Logi, sin ojos desde la v0.8.7) con sus dos anillos de astrolabio, que se
 *     inquieta (agitation) y laten (ringPulse) con el presagio;
 *   - el halo que respira detrás y se encoge con la implosión (haloScale);
 *   - las grietas que avanzan sobre el vidrio (Cracks), recortadas al contorno del corazón;
 *   - la brasa que queda dentro al romperse (ember);
 *   - la silueta del candelabro roto, toda de un color casi negro, para el cuadro de impacto.
 *
 * Coordenadas propias: el origen es el centro del estrado, bajo la punta del candelabro (el punto
 * GoldenCavern.LANTERN_X, LANTERN_Y), y la y crece hacia abajo: todo el candelabro queda en y
 * negativas, colgado de sus cadenas, que suben hasta arriba de la pantalla. Quien lo dibuja pone
 * antes la matriz vista · T(estrado).
 *
 * Lo quieto se dibuja una sola vez, como los planos de la caverna. CandelabraArt mide su caja en
 * píxeles; aquí la caja (BOX) está en píxeles de una imagen de RES píxeles por unidad propia
 * (con RES ≈ 2.37, de cerca, con la cámara a 2.57, cada píxel ocupa apenas 1.1 de la pantalla).
 * Al crearse, el Farol dibuja con CandelabraArt.drawBackStill y drawFrontStill sus dos aspectos,
 * encendido y roto, cada uno en dos imágenes (lo de atrás, y el vidrio por delante), y las guarda
 * como planos recortados (Plane.cropped): entre los brazos hay mucho vacío, y así no se pinta. En
 * cada cuadro solo se copian, con la matriz
 *     vista · T(estrado) · S(1 / RES)
 * y se dibuja lo que se mueve: las llamas de las copas (CandelabraArt.drawBackMoving, con el
 * candelabro encendido), la llama de adentro con sus anillos y las grietas. No usa lo que
 * CandelabraArt guarda dibujado para el juego (ni lo toca), ni su halo grande: aquí va el suyo.
 */
public final class GreatLantern {

    /** El candelabro mide SIZE veces lo que mide en la Cúpula. */
    public static final double SIZE = 1.35;
    /** Unidades del candelabro (las de CandelabraArt) por píxel de BOX: SIZE · RES. */
    private static final double BOX_SCALE = 3.2;
    /** Píxeles de BOX por unidad propia (del mundo). */
    private static final double RES = BOX_SCALE / SIZE;
    /**
     * La caja del candelabro, en píxeles de BOX: 480 x 450 unidades de CandelabraArt a BOX_SCALE.
     * Su borde de arriba (donde se cuelgan las cadenas) queda fuera de la pantalla; la punta, a
     * unas 38 unidades sobre el estrado.
     */
    private static final Rectangle2D BOX = new Rectangle2D.Double(-768, -1526, 480 * BOX_SCALE, 450 * BOX_SCALE);
    /** Lo que las imágenes de lo quieto se extienden más allá de BOX (las orillas suavizadas), en píxeles. */
    private static final int EDGE = 4;
    /** La matriz que no cambia nada: los planos del Farol se pintan con la del Graphics (vista · T(estrado)). */
    private static final Mat3 SAME = Mat3.identity();

    /** El centro del corazón de vidrio sobre el estrado (negativo: hacia arriba). */
    public static final double HEART_Y;
    /** Altura del centro de la llama sobre el estrado: un poco abajo del centro del vidrio. */
    public static final double FLAME_Y;
    private static final double HEAD_Y;   // centro de la cabeza redonda de la llama
    /** Dónde queda el hueco de cada copa (con los brazos abiertos), en unidades propias. */
    private static final double[] CUP_X = new double[CandelabraState.ARMS], CUP_Y = new double[CandelabraState.ARMS];

    static {
        HEART_Y = CandelabraArt.heartCenter(BOX).getY() / RES;
        FLAME_Y = HEART_Y + 20;
        HEAD_Y = FLAME_Y + 15;
        CandelabraState open = new CandelabraState();
        for (int i = 0; i < CandelabraState.ARMS; i++) {
            Point2D p = CandelabraArt.cupCenter(BOX, open, i);
            CUP_X[i] = p.getX() / RES;
            CUP_Y[i] = p.getY() / RES;
        }
    }

    /**
     * La silueta del cuadro de impacto se guarda a INK_SHARE de la resolución de BOX (solo dura 4
     * cuadros, sobre el blanco): su caja es BOX · INK_SHARE (480 · 2.4 = 1152 y 450 · 2.4 = 1080).
     */
    private static final double INK_SHARE = 0.75;
    private static final double INK_RES = RES * INK_SHARE;
    /** El color de la silueta del cuadro de impacto (#05060E) y cuánto se refuerza su opacidad. */
    private static final int INK = 0x05060E;
    private static final int INK_BOOST = 6;

    /**
     * Los anillos de astrolabio: radio, inclinación (radianes; uno hacia cada lado, para que se
     * crucen como los aros de una esfera armilar) y velocidad de giro (radianes por segundo; el
     * de adentro, al revés). Caben dentro del vidrio: a la altura de la llama la orilla del
     * corazón queda a unas 75 unidades del centro, y la cabeza de la llama mide 30 de radio.
     */
    private static final double[] RING_RADIUS = { 64, 47 };
    private static final double[] RING_TILT = { -0.16, 0.25 };
    private static final double[] RING_SPEED = { 0.4, -0.65 };
    /** Cuánto se aplasta el círculo al verlo casi de canto (perspectiva 2.5D) y el alto de la banda. */
    private static final double RING_SQUASH = 0.28, RING_BAND = 3.4;
    /** Rayas de graduación de cada anillo; una de cada RING_MAJOR es larga. */
    private static final int[] RING_TICKS = { 36, 24 };
    private static final int RING_MAJOR = 6;
    /** Cuántas veces más rápido giran los anillos con la llama agitada al máximo (además del 1). */
    private static final double RING_RUSH = 2.5;
    private static final Stroke RING_EDGE = new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke RING_TICK = new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    /**
     * El latido de los anillos (presagio): cada RING_BEAT segundos crecen un poco y sus orillas
     * se aclaran hacia el blanco de la llama. Con pulso p (0 a 1) y t el reloj del Farol,
     *     latido = p · (1 - cos(2π·t / RING_BEAT)) / 2      (de 0 a p, sin brincos)
     *     radio  = R · (1 + RING_SWELL · latido)
     */
    private static final double RING_BEAT = 1.8, RING_SWELL = 0.045;

    // ---- Colores de la llama y de los anillos ----
    private static final Color IRON_LIT = new Color(86, 54, 40);
    private static final Color IRON_SHINE = new Color(255, 200, 124);
    private static final Color OUTER = new Color(255, 135, 40);
    private static final Color MIDDLE = new Color(255, 200, 70);
    private static final Color CORE = new Color(255, 248, 215);

    private final CandelabraState state = new CandelabraState();   // la de la intro, no la del nivel
    private final Shape glass;                                     // el corazón de vidrio, en unidades propias
    private final Cracks cracks;
    /** Lo quieto ya dibujado: lo de atrás y el vidrio por delante, encendido y roto (con sus grietas). */
    private final Plane litBack, litFront, deadBack, deadFront;
    private final BufferedImage ink;                               // la silueta del candelabro roto
    private final int inkX, inkY;                                  // su esquina, en píxeles de INK_RES
    private final Glow halo = new Glow(192, new Color(255, 206, 120), 0.5, 0.62);
    private final Glow ember = new Glow(48, new Color(255, 140, 60), 1.1, 0.5);

    private double time, flamePhase;
    private double agitation;       // 0 = tranquila, 1 = se sacude
    private double crack;           // cuánto han avanzado las grietas (0 a 1)
    private boolean broken;         // roto y apagado
    private double emberGlow;       // brasa que queda en el corazón al romperse (0 a 1)
    private double ringPulse;       // qué tan fuerte laten los anillos (0 = nada, 1 = presagio)
    private double haloScale = 1;   // el halo encogido (1 = normal; la implosión)
    private final double[] ringTurn = new double[RING_RADIUS.length];   // cuánto ha girado cada anillo

    public GreatLantern(long seed) {
        glass = AffineTransform.getScaleInstance(1 / RES, 1 / RES).createTransformedShape(CandelabraArt.heartGlass(BOX));
        // Las grietas salen de un golpe arriba a la izquierda del centro del vidrio y llegan a
        // su orilla (lo que se pasa de ella lo recorta el contorno del corazón)
        double h = HEART_Y;
        cracks = new Cracks(seed, -16, h - 27, new double[][] {
            { -84, h + 8 }, { -62, h - 76 }, { 4, h - 112 }, { 66, h - 74 }, { 84, h + 22 }, { 28, h + 92 }, { -52, h + 76 } });

        // Lo quieto de los dos aspectos. El roto lleva además, de una vez, la respiración de su
        // vidrio frío (a medias: no se mueve) y, sobre el vidrio, las grietas ya abiertas y oscuras
        CandelabraState lit = new CandelabraState(), dead = new CandelabraState();
        lit.setFragments(CandelabraState.ARMS);
        dead.setFragments(0);
        Rectangle2D glassBox = glass.getBounds2D();
        litBack = still(BOX.getX(), BOX.getY(), BOX.getWidth(), BOX.getHeight(), lit, true, false);
        deadBack = still(BOX.getX(), BOX.getY(), BOX.getWidth(), BOX.getHeight(), dead, true, false);
        double gx = glassBox.getX() * RES - 6 * BOX_SCALE, gy = glassBox.getY() * RES - 6 * BOX_SCALE;
        double gw = glassBox.getWidth() * RES + 12 * BOX_SCALE, gh = glassBox.getHeight() * RES + 12 * BOX_SCALE;
        litFront = still(gx, gy, gw, gh, lit, false, false);
        deadFront = still(gx, gy, gw, gh, dead, false, true);

        // La silueta del cuadro de impacto: el candelabro roto (sin nada encendido), dibujado una
        // vez con CandelabraArt y vuelto de un solo color (ver inked)
        Rectangle2D inkBox = new Rectangle2D.Double(BOX.getX() * INK_SHARE, BOX.getY() * INK_SHARE,
                BOX.getWidth() * INK_SHARE, BOX.getHeight() * INK_SHARE);
        inkX = (int) Math.floor(inkBox.getX()) - EDGE;
        inkY = (int) Math.floor(inkBox.getY()) - EDGE;
        BufferedImage img = new BufferedImage((int) Math.ceil(inkBox.getWidth()) + 2 * EDGE, (int) Math.ceil(inkBox.getHeight()) + 2 * EDGE,
                BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(-inkX, -inkY);
        CandelabraArt.drawBackStill(g, inkBox, dead);
        CandelabraArt.drawFrontStill(g, inkBox, dead);
        g.dispose();
        ink = inked(img);

        state.setFragments(CandelabraState.ARMS);
    }

    /**
     * Un plano con lo quieto del candelabro en el estado 's': lo de atrás ('back': con
     * drawBackStill; si el vidrio está frío, también su respiración, fija) o el vidrio por delante
     * (drawFrontStill; con 'withCracks', sobre las grietas ya abiertas y oscuras, recortadas al
     * vidrio). La imagen cubre el rectángulo (x, y, w, h) en píxeles de BOX, más EDGE de orilla;
     * el plano queda en unidades propias, con RES píxeles por unidad.
     */
    private Plane still(double x, double y, double w, double h, CandelabraState s, boolean back, boolean withCracks) {
        int x0 = (int) Math.floor(x) - EDGE, y0 = (int) Math.floor(y) - EDGE;
        BufferedImage img = new BufferedImage((int) Math.ceil(x + w) + EDGE - x0, (int) Math.ceil(y + h) + EDGE - y0,
                BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(-x0, -y0);
        if (back) {
            CandelabraArt.drawBackStill(g, BOX, s);
            if (s.glassWarm < 1) CandelabraArt.drawBackMoving(g, BOX, s, 0);   // sin llamas: solo el vidrio frío
        } else {
            if (withCracks) {
                Graphics2D cg = (Graphics2D) g.create();
                cg.scale(RES, RES);
                cg.clip(glass);
                cracks.draw(cg, 1, 0);
                cg.dispose();
            }
            CandelabraArt.drawFrontStill(g, BOX, s);
        }
        g.dispose();
        return Plane.cropped(img, x0 / RES, y0 / RES, RES);
    }

    /** Dónde queda el hueco de la copa del brazo 'arm' (0 a 5), en unidades propias: de ahí sale su pedazo. */
    public static double cupX(int arm) { return CUP_X[arm]; }

    public static double cupY(int arm) { return CUP_Y[arm]; }

    // =========================================================
    //  ESTADO (lo fijan las escenas)
    // =========================================================

    /** Qué tan agitada está la llama: 0 = tranquila, 1 = se sacude y parpadea. */
    public void setAgitation(double a) { this.agitation = Ease.clamp01(a); }

    /** Cuánto han avanzado las grietas del vidrio, de 0 a 1 (rotas, ya están todas abiertas). */
    public void setCrack(double progress) { this.crack = Ease.clamp01(progress); }

    /** Roto: apagado, con las copas vacías y el vidrio frío y agrietado. */
    public void setBroken(boolean broken) {
        this.broken = broken;
        state.setFragments(broken ? 0 : CandelabraState.ARMS);
    }

    /** La brasa que queda en el corazón después de romperse (0 a 1). */
    public void setEmber(double e) { this.emberGlow = Ease.clamp01(e); }

    /** Qué tan fuerte laten los anillos, de 0 (nada) a 1: el presagio de que se va a romper. */
    public void setRingPulse(double p) { this.ringPulse = Ease.clamp01(p); }

    /** Escala del halo: 1 = normal, menos = la luz se encoge. */
    public void setHaloScale(double s) { this.haloScale = Math.max(0, s); }

    public void update(double dt) {
        time += dt;
        flamePhase += dt * (1 + 2.2 * agitation);   // agitada, las ondas corren más rápido
        // El giro se acumula (como la fase de la llama): si cambia la velocidad, el anillo
        // acelera desde donde iba en vez de brincar
        double rush = 1 + RING_RUSH * agitation;
        for (int i = 0; i < ringTurn.length; i++) ringTurn[i] += dt * RING_SPEED[i] * rush;
    }

    // =========================================================
    //  DIBUJO DE CADA CUADRO
    // =========================================================

    /**
     * El resplandor alrededor del corazón encendido. Va antes que el candelabro, para quedar
     * detrás (también detrás de las cadenas y los brazos). Roto, ya no da luz: no hay.
     */
    public void drawHalo(Graphics2D g) {
        if (broken) return;
        double breath = 1 + 0.035 * Math.sin(time * 1.7) + 0.02 * Math.sin(time * 4.3);
        double leak = 1 + 0.45 * crack;   // por las grietas se escapa más luz
        halo.draw(g, 0, FLAME_Y, 128 * breath * leak * haloScale);
    }

    /**
     * La silueta del candelabro roto (cadenas, corona, brazos, corazón y cuenco), toda de
     * #05060E: el cuadro de impacto la pone sobre la pantalla en blanco. Es una imagen ya hecha.
     */
    public void drawSilhouette(Graphics2D g) {
        AffineTransform old = g.getTransform();
        Object sampling = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, Scene.smoothScaling(g));
        g.scale(1 / INK_RES, 1 / INK_RES);
        g.drawImage(ink, inkX, inkY, null);
        g.setTransform(old);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, sampling != null ? sampling
                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);   // sin indicación, Java2D usa el vecino
    }

    /**
     * El candelabro con lo que vive dentro: lo quieto de atrás (y, encendido, las llamas de las
     * copas), luego la llama y sus anillos (o la brasa, si está roto) y las grietas que avanzan, y
     * encima el vidrio por delante: lo de adentro queda dentro del vidrio.
     */
    public void draw(Graphics2D g) {
        (broken ? deadBack : litBack).draw(g, SAME);
        Stroke oldStroke = g.getStroke();
        if (broken) {
            if (emberGlow > 0.02) ember.draw(g, 0, HEAD_Y, 26 * emberGlow * (0.75 + 0.25 * Math.sin(time * 5.1)));
        } else {
            AffineTransform old = g.getTransform();
            g.scale(1 / RES, 1 / RES);
            CandelabraArt.drawBackMoving(g, BOX, state, time);   // las llamas de las copas
            g.setTransform(old);
            drawArcaneRings(g, false);   // la mitad de atrás de los anillos, detrás de la llama
            drawFlame(g);
            drawArcaneRings(g, true);    // la mitad de adelante, cruzando frente a la llama
            if (crack > 0) {   // las grietas, recortadas al vidrio; por ellas sale luz
                Shape oldClip = g.getClip();
                g.clip(glass);
                cracks.draw(g, crack, 1);
                g.setClip(oldClip);
            }
        }
        g.setStroke(oldStroke);
        (broken ? deadFront : litFront).draw(g, SAME);   // el roto, con sus grietas ya abiertas
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

    /**
     * Los anillos de astrolabio: dos bandas de metal alrededor de la llama, cada una con su
     * inclinación, que giran sobre su propio eje.
     *
     * Cada anillo es un círculo de radio R en su propio plano, que se lleva al Farol con
     *     M = T(0, FLAME_Y) · R(inclinación) · S(1, 0.28)
     * (primero se aplasta: un círculo visto casi de canto se ve como elipse; luego se inclina y
     * se pone en la llama). El punto del anillo a un ángulo θ es M · (R·cos θ, R·sin θ). La banda
     * tiene alto: su orilla de arriba es la misma elipse corrida RING_BAND hacia "arriba" del
     * anillo, M' = T(0, FLAME_Y) · R(inclinación) · T(0, -RING_BAND) · S(1, 0.28); el corrimiento
     * va después de inclinar, para que la banda se incline junto con el anillo.
     *
     * Como el anillo se ve un poco desde arriba, la mitad con sin θ > 0 (abajo en pantalla) es
     * la que queda cerca: va delante de la llama. La de sin θ < 0 queda lejos y va detrás, más
     * tenue (se ve a través de la luz de la llama y del vidrio). Un círculo girando sobre su eje
     * se ve igual, así que lo que deja ver el giro son las rayas de graduación: la raya k está en
     * el ángulo giro + k·2π/n.
     *
     * Todo son trazos y una banda delgada: cada cuadro se arman unos pocos caminos de 25 puntos.
     */
    private void drawArcaneRings(Graphics2D g, boolean front) {
        double fade = front ? 1 : 0.42;
        double wobble = 0.05 * agitation * Math.sin(time * 6.3);   // agitada, la inclinación tiembla
        double beat = ringPulse * 0.5 * (1 - Math.cos(2 * Math.PI * time / RING_BEAT));
        Color shineColor = beat > 0.004 ? Ease.mix(IRON_SHINE, CORE, 0.55 * beat) : IRON_SHINE;
        int arc = 24;                                            // tramos de cada media elipse
        double[] local = new double[2 * (arc + 1)], pts = new double[4 * (arc + 1)];
        double[] tick = new double[4];
        double from = front ? 0 : Math.PI;   // adelante: de 0 a π; atrás: de π a 2π
        for (int i = 0; i <= arc; i++) {
            double a = from + Math.PI * i / arc;
            local[2 * i] = Math.cos(a);
            local[2 * i + 1] = Math.sin(a);
        }
        for (int r = 0; r < RING_RADIUS.length; r++) {
            double radius = RING_RADIUS[r] * (1 + RING_SWELL * beat);
            Mat3 place = Mat3.translation(0, FLAME_Y)
                    .multiply(Mat3.rotation(RING_TILT[r] + (r == 0 ? wobble : -wobble)));
            Mat3 shape = Mat3.scale(radius, radius * RING_SQUASH);   // S(1, 0.28) · S(R): el círculo unitario
            AffineTransform low = place.multiply(shape).toAffineTransform();
            AffineTransform high = place.multiply(Mat3.translation(0, -RING_BAND)).multiply(shape).toAffineTransform();

            // La banda: la orilla de abajo de ida y la de arriba de regreso
            low.transform(local, 0, pts, 0, arc + 1);
            high.transform(local, 0, pts, 2 * (arc + 1), arc + 1);
            Path2D band = new Path2D.Double();
            Path2D lowEdge = new Path2D.Double(), highEdge = new Path2D.Double();
            band.moveTo(pts[0], pts[1]);
            lowEdge.moveTo(pts[0], pts[1]);
            for (int i = 1; i <= arc; i++) {
                band.lineTo(pts[2 * i], pts[2 * i + 1]);
                lowEdge.lineTo(pts[2 * i], pts[2 * i + 1]);
            }
            int top = 2 * (arc + 1);
            highEdge.moveTo(pts[top + 2 * arc], pts[top + 2 * arc + 1]);
            for (int i = arc; i >= 0; i--) {
                band.lineTo(pts[top + 2 * i], pts[top + 2 * i + 1]);
                if (i < arc) highEdge.lineTo(pts[top + 2 * i], pts[top + 2 * i + 1]);
            }
            band.closePath();
            fillLayer(g, band, IRON_LIT, 210 * fade);

            // La mitad de atrás, tenue y tapada en parte por la llama, se queda sin rayas y con
            // las dos orillas de un solo trazo: cada trazo suavizado cuesta, y ahí no se notan
            if (!front) {
                lowEdge.append(highEdge, false);
                g.setStroke(RING_EDGE);
                g.setColor(Ease.fade(shineColor, 0.8 * fade));
                g.draw(lowEdge);
                continue;
            }

            // Las rayas de graduación, de una orilla a la otra; las largas sobresalen un poco.
            // Van todas en un solo camino: se trazan de una vez
            Path2D marks = new Path2D.Double();
            int ticks = RING_TICKS[r];
            for (int k = 0; k < ticks; k++) {
                double a = ringTurn[r] + 2 * Math.PI * k / ticks;
                double s = Math.sin(a);
                if (s < 0) continue;   // esta raya está en la mitad de atrás
                tick[0] = Math.cos(a);
                tick[1] = s;
                tick[2] = tick[0];
                tick[3] = s;
                low.transform(tick, 0, tick, 0, 1);
                high.transform(tick, 2, tick, 2, 1);
                double over = k % RING_MAJOR == 0 ? 0.6 : 0;
                double dx = tick[2] - tick[0], dy = tick[3] - tick[1];
                marks.moveTo(tick[0] - over * dx, tick[1] - over * dy);
                marks.lineTo(tick[2] + over * dx, tick[3] + over * dy);
            }
            g.setStroke(RING_TICK);
            g.setColor(Ease.fade(IRON_SHINE, 0.75));
            g.draw(marks);

            // Las orillas: hilos dorados (la de abajo, que da a la llama, más brillante)
            g.setStroke(RING_EDGE);
            g.setColor(Ease.fade(shineColor, 0.95));
            g.draw(lowEdge);
            g.setColor(Ease.fade(shineColor, 0.6));
            g.draw(highEdge);
        }
    }

    private static void fillLayer(Graphics2D g, Shape shape, Color c, double alpha) {
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.max(0, Math.min(255, Math.round(alpha)))));
        g.fill(shape);
    }

    /**
     * Una copia de la imagen toda de color INK, conservando su forma. Cada píxel se queda con
     * su opacidad multiplicada por INK_BOOST (sin pasar de 255), para que lo casi transparente
     * salga sólido; las orillas suavizadas siguen suaves. Como la imagen es premultiplicada, cada
     * canal se guarda ya multiplicado: c · a / 255.
     */
    private static BufferedImage inked(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = (int[]) src.getRaster().getDataElements(0, 0, w, h, null);
        int ir = (INK >> 16) & 255, ig = (INK >> 8) & 255, ib = INK & 255;
        for (int i = 0; i < px.length; i++) {
            int a = Math.min(255, (px[i] >>> 24) * INK_BOOST);
            px[i] = (a << 24) | (ir * a / 255 << 16) | (ig * a / 255 << 8) | (ib * a / 255);
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        out.getRaster().setDataElements(0, 0, w, h, px);
        return out;
    }
}
