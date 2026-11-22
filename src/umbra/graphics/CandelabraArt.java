package umbra.graphics;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * [v0.9.5] El Gran Farol: un candelabro de piedra de 6 brazos que cuelga de una corona con tres
 * cadenas gruesas. En el centro tiene el corazón de vidrio, donde vivía la llama; abajo, un cuenco
 * que lo sostiene y una punta. Cada brazo sale de detrás del vidrio, baja y sube hasta una copa con
 * el hueco de un fragmento: 3 por lado, a tres alturas (el de arriba es el más corto y el de abajo
 * el más largo). Misma paleta fría que las losas de LevelArt: bisel claro arriba y borde frío.
 *
 * Se dibuja en dos partes para que algo pueda ir dentro del vidrio (Logi, en la animación final):
 *   drawBack: las cadenas, la corona, los brazos abiertos con sus copas y fragmentos, el cuenco y
 *     el interior del vidrio (oscuro, o tibio según glassWarm);
 *   drawFront: el brillo y el marco del vidrio, las grietas, los brazos que se están cerrando (van
 *     por encima del vidrio, como pétalos) y el halo cálido.
 *
 * Todo escala con la caja ('box'): las medidas de abajo están en unidades donde la caja de la Cúpula
 * (480 x 450) mide 1 unidad por px, con el origen en el centro del corazón. La escala es la menor de
 * ancho / 480 y alto / 450; el candelabro queda centrado en x, con la punta abajo de la caja, y las
 * cadenas crecen hasta arriba de la caja. Las formas fijas se arman una sola vez (son constantes);
 * en cada cuadro solo se cambia la matriz, así que dibujar no arma geometría nueva salvo la de los
 * brazos que se están cerrando. Y lo quieto (la piedra, los fragmentos y sus brillos, el vidrio),
 * que en el juego solo cambia al tomar un fragmento, se guarda dibujado en imágenes mientras los
 * brazos estén abiertos (ver backCache): en cada cuadro solo se dibujan las llamas, la
 * respiración del vidrio y el halo.
 */
public final class CandelabraArt {

    // ---- Paleta (la de las losas de LevelArt) ----
    private static final Color STONE_TOP = new Color(92, 98, 124);
    private static final Color STONE_BOTTOM = new Color(52, 56, 76);
    private static final Color STONE_LIGHT = new Color(150, 158, 190, 200);
    private static final Color STONE_EDGE = new Color(160, 165, 200, 160);
    private static final Color CARVED = new Color(30, 32, 46, 200);
    private static final Color RUNE_LIGHT = new Color(150, 158, 190, 90);
    private static final Color ARM_DARK = new Color(30, 33, 47);
    private static final Color ARM_BODY = new Color(74, 80, 104);
    private static final Color CHAIN = new Color(62, 68, 92);
    private static final Color CHAIN_LIGHT = new Color(140, 148, 180, 170);
    private static final Color HOLLOW = new Color(8, 10, 18);
    private static final Color GLASS_RIM = new Color(130, 142, 178, 190);
    private static final Color GLASS_FRAME = new Color(38, 42, 58);
    private static final Color GLASS_SHINE = new Color(235, 245, 255, 46);
    private static final Color GLASS_SPARK = new Color(240, 248, 255, 90);
    private static final Color CRACK = new Color(232, 240, 255, 215);
    private static final Color WARM = new Color(255, 196, 96);
    private static final Color FLAME_OUT = new Color(255, 170, 70);
    private static final Color FLAME_IN = new Color(255, 238, 192);

    // ---- Medidas (unidades; origen en el centro del corazón) ----
    private static final double REF_W = 480, REF_H = 450;
    private static final double TIP = 152;          // la punta de abajo
    private static final double HEART_R = 44;       // el círculo que cabe dentro del vidrio
    private static final double ROOT_X = 40;
    private static final double[] ROOT_Y = { -40, -4, 32 };
    /** Dónde queda cada copa (el centro del hueco), por altura: abierta y cerrada sobre el vidrio. */
    private static final double[][] OPEN_CUP = { { 128, -84 }, { 168, -48 }, { 204, -10 } };
    private static final double[][] CLOSED_CUP = { { 14, -66 }, { 36, -34 }, { 46, 2 } };
    private static final double STEM_END = 30;      // del centro del hueco a donde llega el brazo
    private static final double LINK = 16;          // largo de un eslabón

    // ---- Formas fijas ----
    private static final Path2D.Double GLASS = new Path2D.Double();
    private static final Path2D.Double SHINE = new Path2D.Double();
    private static final Path2D.Double CRACKS = new Path2D.Double();
    private static final Path2D.Double CROWN = new Path2D.Double();
    private static final Path2D.Double SPIKES = new Path2D.Double();
    private static final Path2D.Double RUNE = new Path2D.Double();
    private static final Path2D.Double BOWL = new Path2D.Double();
    private static final Path2D.Double FINIAL = new Path2D.Double();
    private static final Path2D.Double CUP = new Path2D.Double();
    private static final Path2D.Double FLAME = new Path2D.Double();
    private static final Path2D.Double[] OPEN_ARMS = new Path2D.Double[CandelabraState.ARMS];
    private static final Path2D.Double MOVING_ARM = new Path2D.Double();   // el que se está cerrando (se rearma)
    private static final Rectangle2D.Double CROWN_PLATE = new Rectangle2D.Double(-62, -124, 124, 9);
    private static final RoundRectangle2D.Double NECK = new RoundRectangle2D.Double(-17, -86, 34, 10, 5, 5);
    private static final RoundRectangle2D.Double BOWL_RING = new RoundRectangle2D.Double(-20, 86, 40, 9, 5, 5);
    private static final Ellipse2D.Double KNOB = new Ellipse2D.Double(-11, 98, 22, 22);
    private static final RoundRectangle2D.Double CEILING = new RoundRectangle2D.Double();
    private static final Ellipse2D.Double LINK_FACE = new Ellipse2D.Double(-5.5, -1, 11, LINK + 4);
    private static final Line2D.Double LINK_EDGE = new Line2D.Double(0, 0, 0, LINK);
    private static final Line2D.Double LINK_SHINE = new Line2D.Double(-3, 4, -3, 9);
    private static final Line2D.Double LINE = new Line2D.Double();
    private static final Ellipse2D.Double SPARK = new Ellipse2D.Double(-24, -60, 6, 11);
    private static final Ellipse2D.Double CUP_RIM = new Ellipse2D.Double(-19, -4.5, 38, 9);
    private static final Ellipse2D.Double CUP_HOLLOW = new Ellipse2D.Double(-12, -3, 24, 6);
    private static final RoundRectangle2D.Double CUP_STEM = new RoundRectangle2D.Double(-4, 10.5, 8, 16, 3, 3);
    private static final Ellipse2D.Double CUP_FOOT = new Ellipse2D.Double(-8, 23.5, 16, 7);
    private static final Rectangle2D.Double GLOW = new Rectangle2D.Double(-36, -46, 72, 72);
    private static final Rectangle2D.Double HALO = new Rectangle2D.Double(-220, -240, 440, 440);   // centro (0, -20), como su degradado

    // ---- Pinturas fijas (en unidades) ----
    private static final Paint CROWN_PAINT = new GradientPaint(0, -148, STONE_TOP, 0, -78, STONE_BOTTOM);
    private static final Paint BOWL_PAINT = new GradientPaint(0, 40, STONE_TOP, 0, 152, STONE_BOTTOM);
    private static final Paint CUP_PAINT = new GradientPaint(0, -5, STONE_TOP, 0, 31, STONE_BOTTOM);
    private static final Paint GLASS_PAINT = new GradientPaint(-40, -60, new Color(36, 46, 70, 232), 40, 50, new Color(10, 13, 22, 240));
    private static final Paint GLASS_WARM = new RadialGradientPaint(0f, 14f, 74f, new float[] { 0f, 0.5f, 1f },
            new Color[] { new Color(255, 240, 196), new Color(255, 178, 82), new Color(176, 84, 30) });
    private static final Paint GLASS_COLD = new RadialGradientPaint(0f, -6f, 58f, new float[] { 0f, 1f },
            new Color[] { new Color(120, 150, 210, 70), new Color(120, 150, 210, 0) });
    private static final Paint CUP_GLOW = new RadialGradientPaint(0f, -12f, 36f, new float[] { 0f, 1f },
            new Color[] { new Color(255, 200, 110, 150), new Color(255, 200, 110, 0) });
    private static final Paint HALO_PAINT = new RadialGradientPaint(0f, -20f, 220f, new float[] { 0f, 0.45f, 1f },
            new Color[] { Background.alpha(WARM, 110), Background.alpha(WARM, 40), Background.alpha(WARM, 0) });

    // ---- Trazos (en unidades) ----
    private static final Stroke ARM_OUTLINE = new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke ARM_STROKE = new BasicStroke(8.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke ARM_SHINE = new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke CHAIN_STROKE = new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke THIN = new BasicStroke(1.2f);
    private static final Stroke RUNE_STROKE = new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke FRAME_STROKE = new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke RIM_STROKE = new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke CRACK_STROKE = new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    // ---- Lo quieto ya dibujado: una sola versión, la del último tamaño de caja y estado que se
    //      repitió (ver backCache). El halo solo depende del tamaño ----
    private static BufferedImage cache, frontCache, haloCache;
    private static double cacheW = -1, cacheH = -1, cacheWarm = -1, cacheOx, cacheOy, frontOx, frontOy;
    private static final double[] cacheLit = new double[CandelabraState.ARMS];
    private static double haloW = -1, haloH = -1;
    private static double askedW = -1, askedH = -1, askedWarm = -1;   // lo último que se pidió
    private static final double[] askedLit = new double[CandelabraState.ARMS];
    private static final Rectangle2D.Double FRONT_BOUNDS = new Rectangle2D.Double(-64, -88, 128, 162);   // el vidrio y su marco

    /** Transparencias ya hechas (de 0 a 1 en 64 pasos), para no crear una en cada cuadro. */
    private static final AlphaComposite[] ALPHA = new AlphaComposite[65];

    static {
        for (int i = 0; i < ALPHA.length; i++) ALPHA[i] = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, i / 64f);

        // El corazón de vidrio: una gota con la punta arriba, metida en la corona, y abajo redondo
        GLASS.moveTo(0, -80);
        GLASS.curveTo(30, -66, 56, -34, 56, 0);
        GLASS.curveTo(56, 38, 30, 64, 0, 66);
        GLASS.curveTo(-30, 64, -56, 38, -56, 0);
        GLASS.curveTo(-56, -34, -30, -66, 0, -80);
        GLASS.closePath();
        // El brillo: una franja curva arriba a la izquierda
        SHINE.moveTo(-30, -52);
        SHINE.quadTo(-48, -14, -40, 26);
        SHINE.quadTo(-36, -12, -22, -50);
        SHINE.closePath();
        // Las grietas: salen de un golpe arriba a la izquierda y se ramifican
        double[][] cracks = {
            { -12, -20, -34, -8 }, { -34, -8, -46, 8 }, { -34, -8, -40, -26 },
            { -12, -20, -24, -50 }, { -24, -50, -16, -64 },
            { -12, -20, 8, -44 }, { 8, -44, 22, -50 },
            { -12, -20, 12, -2 }, { 12, -2, 34, 6 }, { 12, -2, 18, 26 },
            { -12, -20, -18, 10 }, { -18, 10, -8, 38 }, { -18, 10, -36, 30 },
        };
        for (double[] c : cracks) { CRACKS.moveTo(c[0], c[1]); CRACKS.lineTo(c[2], c[3]); }

        // La corona: un cuerpo que se angosta sobre la punta del vidrio, con almenas arriba
        CROWN.moveTo(-56, -116);
        CROWN.lineTo(56, -116);
        CROWN.lineTo(30, -84);
        CROWN.lineTo(-30, -84);
        CROWN.closePath();
        for (int i = -2; i <= 2; i++) {
            double x = i * 24, top = i == 0 ? -150 : -142 + Math.abs(i) * 2;
            SPIKES.moveTo(x - 8, -123);
            SPIKES.lineTo(x, top);
            SPIKES.lineTo(x + 8, -123);
            SPIKES.closePath();
        }
        RUNE.moveTo(0, -109);
        RUNE.lineTo(6, -100);
        RUNE.lineTo(0, -91);
        RUNE.lineTo(-6, -100);
        RUNE.closePath();

        // El cuenco que sostiene el vidrio, y la punta con su perilla
        BOWL.moveTo(-50, 38);
        BOWL.quadTo(-46, 84, -18, 90);
        BOWL.lineTo(18, 90);
        BOWL.quadTo(46, 84, 50, 38);
        BOWL.quadTo(0, 58, -50, 38);
        BOWL.closePath();
        FINIAL.moveTo(-7, 116);
        FINIAL.lineTo(7, 116);
        FINIAL.lineTo(0, TIP);
        FINIAL.closePath();

        // La copa (sin el borde ni el hueco), con el origen en el centro del hueco
        CUP.moveTo(-19, 0);
        CUP.lineTo(19, 0);
        CUP.quadTo(16, 14.5, 0, 15.5);
        CUP.quadTo(-16, 14.5, -19, 0);
        CUP.closePath();
        // Una llama chica (alto 2.5, con el origen en su centro)
        FLAME.moveTo(0, -1.5);
        FLAME.curveTo(0.9, -0.4, 1, 0.6, 0, 1);
        FLAME.curveTo(-1, 0.6, -0.9, -0.4, 0, -1.5);
        FLAME.closePath();

        for (int i = 0; i < OPEN_ARMS.length; i++) {
            OPEN_ARMS[i] = new Path2D.Double();
            armPath(OPEN_ARMS[i], i, 0);
        }
    }

    private CandelabraArt() { }

    // =========================================================
    //  Medidas públicas
    // =========================================================

    /** Unidades a px para esta caja. */
    private static double scale(Rectangle2D box) {
        return Math.min(box.getWidth() / REF_W, box.getHeight() / REF_H);
    }

    /** El centro del corazón de vidrio (sin la sacudida). */
    public static Point2D heartCenter(Rectangle2D box) {
        return new Point2D.Double(box.getCenterX(), heartY(box));
    }

    private static double heartY(Rectangle2D box) {
        return box.getMaxY() - (TIP + 2) * scale(box);
    }

    /** El radio del círculo que cabe dentro del corazón de vidrio (lo que va dentro, como Logi). */
    public static double heartRadius(Rectangle2D box) {
        return HEART_R * scale(box);
    }

    /**
     * El centro del hueco de la copa del brazo 'arm' (0 a 5), donde va su fragmento, con lo
     * cerrado que esté según 's' y con su sacudida.
     */
    public static Point2D cupCenter(Rectangle2D box, CandelabraState s, int arm) {
        double k = scale(box), t = clamp(s.armClosed[arm]);
        return new Point2D.Double(box.getCenterX() + s.shake + cupX(arm, t) * k, heartY(box) + cupY(arm, t) * k);
    }

    // =========================================================
    //  Dibujo
    // =========================================================

    /**
     * Las cadenas, la corona, los brazos abiertos con sus copas (y sus fragmentos y llamas), el
     * cuenco y el interior del vidrio. Mientras los 6 brazos estén abiertos, todo lo quieto sale
     * de una imagen ya dibujada (ver backCache) y en cada cuadro solo se dibujan las llamas de las
     * copas y la respiración fría del vidrio.
     */
    public static void drawBack(Graphics2D g, Rectangle2D box, CandelabraState s, double time) {
        AffineTransform saved = g.getTransform();
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();
        Composite oldComposite = g.getComposite();
        double k = scale(box), tx = box.getCenterX() + s.shake, ty = heartY(box);
        boolean cached = allOpen(s) && backCache(box, s);
        if (cached) g.drawImage(cache, (int) Math.round(tx - cacheOx), (int) Math.round(ty - cacheOy), null);
        g.translate(tx, ty);
        g.scale(k, k);
        if (!cached) {
            stoneLayer(g, (box.getMinY() - ty) / k, s);
            litLayer(g, s);
        }
        // Lo que se mueve: las llamas de las copas y el vidrio frío, que respira
        for (int arm = 0; arm < CandelabraState.ARMS; arm++) {
            if (s.armClosed[arm] <= 0.001) cupFlame(g, arm, 0, s.armLit[arm], time);
        }
        if (s.glassWarm < 1) {
            double breath = 0.5 + 0.5 * Math.sin(time * 1.3);
            g.setComposite(alpha((1 - s.glassWarm) * (0.45 + 0.55 * breath)));
            g.setPaint(GLASS_COLD);
            g.fill(GLASS);
        }

        g.setComposite(oldComposite);
        g.setStroke(oldStroke);
        g.setPaint(oldPaint);
        g.setTransform(saved);
    }

    private static boolean allOpen(CandelabraState s) {
        for (double c : s.armClosed) if (c > 0.001) return false;
        return true;
    }

    /**
     * La piedra de atrás, sin nada encendido: cadenas, brazos abiertos con sus copas vacías,
     * corona, cuenco y el vidrio oscuro. Si 's' no es null, se salta los brazos que se cierran
     * (van en drawFront). 'top' es el borde de arriba de la caja, en unidades.
     */
    private static void stoneLayer(Graphics2D g, double top, CandelabraState s) {
        chains(g, top);
        // Los brazos de abajo primero: los de arriba quedan encima donde se acercan
        for (int h = 2; h >= 0; h--) {
            for (int side = 0; side < 2; side++) {
                int arm = side * 3 + h;
                if (s != null && s.armClosed[arm] > 0.001) continue;
                arm(g, OPEN_ARMS[arm]);
                cupStone(g, arm, 0);
            }
        }
        // La corona
        stone(g, SPIKES, CROWN_PAINT);
        stone(g, CROWN_PLATE, CROWN_PAINT);
        stone(g, CROWN, CROWN_PAINT);
        g.setColor(STONE_LIGHT);
        g.setStroke(THIN);
        line(g, -58, -122.5, 58, -122.5);
        line(g, -53, -114.5, 53, -114.5);
        g.setStroke(RUNE_STROKE);   // el rombo grabado: una línea clara debajo de la oscura
        g.setColor(RUNE_LIGHT);
        g.translate(0, 1);
        g.draw(RUNE);
        g.translate(0, -1);
        g.setColor(CARVED);
        g.draw(RUNE);
        stone(g, NECK, CROWN_PAINT);
        // El cuenco, la perilla y la punta
        stone(g, FINIAL, BOWL_PAINT);
        stone(g, KNOB, BOWL_PAINT);
        stone(g, BOWL_RING, BOWL_PAINT);
        stone(g, BOWL, BOWL_PAINT);
        // El vidrio, oscuro
        g.setPaint(GLASS_PAINT);
        g.fill(GLASS);
    }

    /**
     * Lo que arde y no se mueve, encima de la piedra: el brillo y el fragmento de cada copa
     * abierta con fragmento, el rombo de la corona y el vidrio tibio (según glassWarm).
     */
    private static void litLayer(Graphics2D g, CandelabraState s) {
        Composite old = g.getComposite();
        for (int arm = 0; arm < CandelabraState.ARMS; arm++) {
            if (s.armClosed[arm] <= 0.001) cupLit(g, arm, 0, s.armLit[arm]);
        }
        if (s.glassWarm > 0) {
            g.setComposite(alpha(s.glassWarm));
            g.setStroke(RUNE_STROKE);
            g.setColor(WARM);
            g.draw(RUNE);
            g.setComposite(alpha(s.glassWarm * 0.9));
            g.setPaint(GLASS_WARM);
            g.fill(GLASS);
            g.setComposite(old);
        }
    }

    /** El vidrio por delante: un brillo frío arriba a la izquierda y el marco (tibio con glassWarm). */
    private static void glassFront(Graphics2D g, double warm) {
        g.setColor(GLASS_SHINE);
        g.fill(SHINE);
        g.setColor(GLASS_SPARK);
        g.fill(SPARK);
        g.setStroke(FRAME_STROKE);
        g.setColor(GLASS_FRAME);
        g.draw(GLASS);
        g.setStroke(RIM_STROKE);
        g.setColor(GLASS_RIM);
        g.draw(GLASS);
        if (warm > 0) {
            Composite old = g.getComposite();
            g.setComposite(alpha(warm));
            g.setColor(WARM);
            g.draw(GLASS);
            g.setComposite(old);
        }
    }

    /** ¿Lo guardado es de este tamaño de caja y de estos brazos y vidrio? */
    private static boolean cacheMatches(Rectangle2D box, CandelabraState s) {
        if (cache == null || box.getWidth() != cacheW || box.getHeight() != cacheH || s.glassWarm != cacheWarm) return false;
        for (int i = 0; i < CandelabraState.ARMS; i++) if (s.armLit[i] != cacheLit[i]) return false;
        return true;
    }

    /**
     * Lo quieto de atrás (piedra y lo que arde sin moverse) y de adelante (el vidrio) ya
     * dibujado, para una caja de este tamaño (no importa dónde esté) y estos brazos y vidrio. En
     * el juego solo cambia al tomar un fragmento: se dibuja una vez y se copia. Si el tamaño o el
     * estado son nuevos, la primera vez no se arma (la animación podría estar cambiándolos en
     * cada cuadro): solo si se repiten. Regresa si se puede usar.
     */
    private static boolean backCache(Rectangle2D box, CandelabraState s) {
        if (cacheMatches(box, s)) return true;
        double w = box.getWidth(), h = box.getHeight();
        boolean repeated = w == askedW && h == askedH && s.glassWarm == askedWarm;
        for (int i = 0; i < CandelabraState.ARMS; i++) repeated &= s.armLit[i] == askedLit[i];
        if (!repeated) {
            askedW = w;
            askedH = h;
            askedWarm = s.glassWarm;
            System.arraycopy(s.armLit, 0, askedLit, 0, CandelabraState.ARMS);
            return false;
        }
        double k = scale(box);
        int iw = 2 * (int) Math.ceil(REF_W / 2 * k + 3), ih = (int) Math.ceil(h + 4);
        BufferedImage img = Background.newImage(iw, ih);
        double ox = iw / 2.0, oy = 2 + heartY(box) - box.getMinY();
        Graphics2D cg = Background.smooth(img);
        cg.translate(ox, oy);
        cg.scale(k, k);
        stoneLayer(cg, (box.getMinY() - heartY(box)) / k, null);
        litLayer(cg, s);
        cg.dispose();
        int fw = 2 * (int) Math.ceil(FRONT_BOUNDS.getMaxX() * k + 2), fh = (int) Math.ceil(FRONT_BOUNDS.getHeight() * k + 4);
        BufferedImage front = Background.newImage(fw, fh);
        double fx = fw / 2.0, fy = 2 - FRONT_BOUNDS.getMinY() * k;
        cg = Background.smooth(front);
        cg.translate(fx, fy);
        cg.scale(k, k);
        glassFront(cg, s.glassWarm);
        cg.dispose();
        cache = img;
        frontCache = front;
        cacheOx = ox;
        cacheOy = oy;
        frontOx = fx;
        frontOy = fy;
        cacheW = w;
        cacheH = h;
        cacheWarm = s.glassWarm;
        System.arraycopy(s.armLit, 0, cacheLit, 0, CandelabraState.ARMS);
        return true;
    }

    /** El halo ya dibujado para este tamaño de caja (no depende del estado: su fuerza va en la transparencia). */
    private static BufferedImage halo(Rectangle2D box) {
        if (haloCache != null && box.getWidth() == haloW && box.getHeight() == haloH) return haloCache;
        if (cache == null || box.getWidth() != cacheW || box.getHeight() != cacheH) return null;   // tamaño que no se repite
        double k = scale(box);
        int hs = 2 * (int) Math.ceil(HALO.getWidth() / 2 * k);
        BufferedImage img = Background.newImage(hs, hs);
        Graphics2D cg = Background.smooth(img);
        cg.translate(hs / 2.0, hs / 2.0);
        cg.scale(k, k);
        cg.translate(-HALO.getCenterX(), -HALO.getCenterY());
        cg.setPaint(HALO_PAINT);
        cg.fill(HALO);
        cg.dispose();
        haloCache = img;
        haloW = box.getWidth();
        haloH = box.getHeight();
        return img;
    }

    /** El brillo y el marco del vidrio, las grietas, los brazos que se cierran y el halo. */
    public static void drawFront(Graphics2D g, Rectangle2D box, CandelabraState s, double time) {
        AffineTransform saved = g.getTransform();
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();
        Composite oldComposite = g.getComposite();
        double k = scale(box), tx = box.getCenterX() + s.shake, ty = heartY(box);
        if (allOpen(s) && cacheMatches(box, s)) {   // drawBack ya lo dejó dibujado
            g.drawImage(frontCache, (int) Math.round(tx - frontOx), (int) Math.round(ty - frontOy), null);
        } else {
            g.translate(tx, ty);
            g.scale(k, k);
            glassFront(g, s.glassWarm);
            g.setTransform(saved);
        }
        g.translate(tx, ty);
        g.scale(k, k);

        if (s.cracks > 0) {
            g.setComposite(alpha(s.cracks));
            g.setStroke(CRACK_STROKE);
            g.setColor(CRACK);
            g.draw(CRACKS);
            g.setComposite(oldComposite);
        }

        // Los brazos que se están cerrando pasan por delante del vidrio, como pétalos
        for (int h = 2; h >= 0; h--) {
            for (int side = 0; side < 2; side++) {
                int arm = side * 3 + h;
                double t = clamp(s.armClosed[arm]);
                if (t <= 0.001) continue;
                armPath(MOVING_ARM, arm, t);
                arm(g, MOVING_ARM);
                cupStone(g, arm, t);
                cupLit(g, arm, t, s.armLit[arm]);
                cupFlame(g, arm, t, s.armLit[arm], time);
            }
        }

        // El halo cálido: crece con los brazos encendidos y con el vidrio. Si ya está dibujado para
        // este tamaño de caja, se copia (un degradado tan grande es caro de calcular en cada cuadro)
        double lit = 0;
        for (double a : s.armLit) lit += clamp(a);
        double glow = 0.6 * lit / CandelabraState.ARMS + 0.4 * clamp(s.glassWarm);
        if (glow > 0.001) {
            double breath = 0.5 + 0.5 * Math.sin(time * 1.3);
            g.setComposite(alpha(glow * glow * (0.88 + 0.12 * breath)));
            BufferedImage halo = halo(box);
            if (halo != null) {
                g.setTransform(saved);
                double hx = tx + HALO.getCenterX() * k, hy = ty + HALO.getCenterY() * k;
                g.drawImage(halo, (int) Math.round(hx - halo.getWidth() / 2.0), (int) Math.round(hy - halo.getHeight() / 2.0), null);
            } else {
                g.setPaint(HALO_PAINT);
                g.fill(HALO);
            }
        }

        g.setComposite(oldComposite);
        g.setStroke(oldStroke);
        g.setPaint(oldPaint);
        g.setTransform(saved);
    }

    // =========================================================
    //  Partes
    // =========================================================

    /**
     * Las tres cadenas: de la placa del techo (arriba de la caja) a la corona. Las de los lados se
     * abren hacia abajo; la del centro baja a la almena de en medio. Eslabones de frente y de canto.
     */
    private static void chains(Graphics2D g, double top) {
        CEILING.setRoundRect(-44, top, 88, 12, 6, 6);
        g.setStroke(CHAIN_STROKE);
        chain(g, -26, top + 8, -50, -120);
        chain(g, 26, top + 8, 50, -120);
        chain(g, 0, top + 8, 0, -150);
        stone(g, CEILING, CROWN_PAINT);
    }

    private static void chain(Graphics2D g, double x0, double y0, double x1, double y1) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        if (len < 1) return;
        double angle = Math.atan2(y1 - y0, x1 - x0) - Math.PI / 2;   // el eje y local va a lo largo de la cadena
        g.translate(x0, y0);
        g.rotate(angle);
        int n = (int) Math.ceil(len / LINK);
        for (int j = 0; j < n; j++) {
            g.setColor(CHAIN);
            if (j % 2 == 0) g.draw(LINK_FACE); else g.draw(LINK_EDGE);
            g.setColor(CHAIN_LIGHT);
            g.draw(LINK_SHINE);
            g.translate(0, LINK);
        }
        g.translate(0, -LINK * n);
        g.rotate(-angle);
        g.translate(-x0, -y0);
    }

    /** Un brazo: contorno oscuro, cuerpo de piedra y una línea clara arriba (el bisel). */
    private static void arm(Graphics2D g, Shape path) {
        g.setStroke(ARM_OUTLINE);
        g.setColor(ARM_DARK);
        g.draw(path);
        g.setStroke(ARM_STROKE);
        g.setColor(ARM_BODY);
        g.draw(path);
        g.setStroke(ARM_SHINE);
        g.setColor(STONE_LIGHT);
        g.translate(0, -2.6);
        g.draw(path);
        g.translate(0, 2.6);
    }

    /** La piedra de la copa del brazo 'arm' cerrado 't': tallo, pie, cuenco y borde con su hueco vacío. */
    private static void cupStone(Graphics2D g, int arm, double t) {
        double x = cupX(arm, t), y = cupY(arm, t);
        g.translate(x, y);
        stone(g, CUP_STEM, CUP_PAINT);
        stone(g, CUP_FOOT, CUP_PAINT);
        stone(g, CUP, CUP_PAINT);
        g.setPaint(CUP_PAINT);
        g.fill(CUP_RIM);
        g.setStroke(THIN);
        g.setColor(STONE_LIGHT);
        g.draw(CUP_RIM);
        g.setColor(HOLLOW);
        g.fill(CUP_HOLLOW);
        g.translate(-x, -y);
    }

    /**
     * Lo que arde quieto en la copa del brazo 'arm' cerrado 't', encima de su piedra ('lit' de
     * 0 a 1): un brillo cálido y el fragmento dorado en el hueco.
     */
    private static void cupLit(Graphics2D g, int arm, double t, double lit) {
        lit = clamp(lit);
        if (lit <= 0) return;
        double x = cupX(arm, t), y = cupY(arm, t);
        Composite old = g.getComposite();
        g.translate(x, y);
        g.setComposite(alpha(lit * 0.9));
        g.setPaint(CUP_GLOW);
        g.fill(GLOW);
        g.setComposite(old);
        if (lit > 0.02) FragmentArt.drawIcon(g, 0, -2.5, 0.72);
        g.translate(-x, -y);
    }

    /** La llama chica que sube del hueco de la copa encendida y tiembla (se dibuja en cada cuadro). */
    private static void cupFlame(Graphics2D g, int arm, double t, double lit, double time) {
        lit = clamp(lit);
        if (lit <= 0.02) return;
        double x = cupX(arm, t), y = cupY(arm, t);
        double flick = Math.sin(time * 9.1 + arm * 2.3);
        double sx = 7.5 * lit * (1 + 0.06 * flick), sy = 7.5 * lit * (1 + 0.14 * flick), fy = y - 16 - 4 * lit;
        g.translate(x, 0);
        flame(g, fy, sx, sy, FLAME_OUT);
        flame(g, fy + 0.2 * sy, sx * 0.55, sy * 0.55, FLAME_IN);
        g.translate(-x, 0);
    }

    /** La llama chica centrada en (0, y), estirada sx a lo ancho y sy a lo alto. */
    private static void flame(Graphics2D g, double y, double sx, double sy, Color c) {
        g.translate(0, y);
        g.scale(sx, sy);
        g.setColor(c);
        g.fill(FLAME);
        g.scale(1 / sx, 1 / sy);
        g.translate(0, -y);
    }

    /** Rellena 'shape' como piedra tallada: degradado de arriba abajo y borde claro. */
    private static void stone(Graphics2D g, Shape shape, Paint paint) {
        g.setPaint(paint);
        g.fill(shape);
        g.setStroke(THIN);
        g.setColor(STONE_EDGE);
        g.draw(shape);
    }

    private static void line(Graphics2D g, double x0, double y0, double x1, double y1) {
        LINE.setLine(x0, y0, x1, y1);
        g.draw(LINE);
    }

    // =========================================================
    //  Geometría de los brazos
    // =========================================================

    private static double side(int arm) { return arm < 3 ? -1 : 1; }

    private static double cupX(int arm, double t) {
        int h = arm % 3;
        return side(arm) * lerp(OPEN_CUP[h][0], CLOSED_CUP[h][0], t);
    }

    private static double cupY(int arm, double t) {
        int h = arm % 3;
        return lerp(OPEN_CUP[h][1], CLOSED_CUP[h][1], t);
    }

    /**
     * El brazo 'arm' cerrado 't' (0 abierto, 1 cerrado): sale de detrás del vidrio, baja hacia
     * afuera y sube derecho hasta el tallo de su copa. Al cerrarse, la copa se acerca al vidrio y
     * la curva lo rodea por el lado.
     */
    private static void armPath(Path2D.Double p, int arm, double t) {
        int h = arm % 3;
        double sd = side(arm);
        double rx = sd * ROOT_X, ry = ROOT_Y[h];
        double cx = cupX(arm, t), cy = cupY(arm, t);
        double c1x = rx + sd * lerp(58, 50, t), c1y = ry + lerp(50, 34, t);
        double c2x = sd * lerp(OPEN_CUP[h][0], CLOSED_CUP[h][0] + 58, t), c2y = cy + lerp(78, 46, t);
        p.reset();
        p.moveTo(rx, ry);
        p.curveTo(c1x, c1y, c2x, c2y, cx, cy + STEM_END);
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static double clamp(double v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    private static AlphaComposite alpha(double a) {
        return ALPHA[(int) Math.round(clamp(a) * (ALPHA.length - 1))];
    }
}
