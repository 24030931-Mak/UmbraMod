package umbra.ui.intro;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import umbra.graphics.Background;
import umbra.graphics.CaveShapes;
import umbra.graphics.ThroneHallArt;
import umbra.math.Mat3;
import umbra.math.Vec2;

/**
 * [v0.8.6] El escenario de las tres primeras escenas: la gran caverna cuando el Farol la
 * iluminaba toda. Mide lo mismo que la pantalla (1280 x 720) vista de lejos.
 *
 * Es la misma técnica del fondo del juego (graphics.Background), con la paleta al revés: en vez
 * de azules oscuros, dorados. Las capas de roca se generan con desplazamiento del punto medio y
 * las lejanas son más claras que las cercanas (perspectiva atmosférica).
 *
 * [v0.8.7] Claroscuro y profundidad. La luz sale de la llama y se apaga hacia las orillas: el
 * degradado del aire y la roca van de dorado junto al Farol a casi negro en las esquinas, y las
 * estalactitas y las columnas tienen un hilo de luz solo en la orilla que mira a la llama.
 * Lo que no se mueve se dibuja una sola vez, a más resolución que la pantalla, en tres planos
 * que se pintan cada uno con su propia vista (IntroCamera.getViewMatrix(profundidad)):
 *   - lejano (0.45): la luz, los rayos y la caverna enorme del fondo;
 *   - medio (0.75): las paredes, los túneles, las estalactitas y las ruinas;
 *   - cercano (1): el piso con la terraza del Farol (la misma vista que el Farol).
 * Vista de lejos (zoom 1) los tres coinciden; al acercarse la cámara al Farol, el plano lejano
 * crece y se corre menos que el medio, y el medio menos que el piso: eso da profundidad. Los
 * planos lejano y medio tienen un margen alrededor de la pantalla para que, con el temblor y el
 * paneo, nunca se vea su orilla. Encima va un primer plano de rocas casi negras, más "cercano"
 * todavía (profundidad 1.45): crece y se sale de la pantalla más rápido que todo lo demás.
 *
 * [v0.9.5] La caverna es la sala del trono en su época dorada: el mismo lugar del final, antes de
 * la caída. En el plano lejano, detrás del Farol, va la sala de graphics.ThroneHallArt en su
 * versión dorada (golden = true: rosetón encendido, columnas, estandartes enteros y relieves),
 * con sus orillas fundidas en la roca de la caverna y bañada por la luz del Farol. Las ruinas de
 * columnas del plano medio se quitaron (ahora está la sala) y sus estalactitas se apartan del
 * rosetón. El fuego de los braseros es lo único de la sala que se dibuja en cada cuadro
 * (drawBack, con la vista del plano lejano).
 */
public final class GoldenCavern {

    /** [v0.9.5] Dónde cuelga el Gran Farol: el centro del estrado, bajo la punta del candelabro. */
    public static final double LANTERN_X = 640, LANTERN_Y = 574;
    /**
     * [v0.9.5] La sala del trono (en unidades del mundo, en el plano lejano): centrada en el
     * Farol, con su piso detrás del estrado (y = 580). Mide 860 x 560: un poco más baja que la
     * del juego (640 x 470 a la escala del candelabro), para que el rosetón quede debajo del techo
     * de roca. Su esquina cae justo en un píxel de la imagen del plano lejano: (210 + 60) · 1.6 = 432
     * y (20 + 60) · 1.6 = 128.
     */
    private static final Rectangle2D HALL = new Rectangle2D.Double(210, 20, 860, 560);
    /** Cuánto se funden con la roca las orillas de la sala: los lados y la de arriba. */
    private static final double HALL_SIDE_FADE = 70, HALL_TOP_FADE = 60;
    /**
     * Píxeles de imagen por unidad del mundo de cada plano: los de la mayor ampliación con que se
     * ve. La cámara se acerca hasta CLOSE_ZOOM · 1.05 ≈ 2.57 (en la escena 2), y un plano a
     * profundidad d se amplía zoom^d: el lejano 2.57^0.45 ≈ 1.53 y el medio 2.57^0.75 ≈ 2.03. Así
     * se ven igual de nítidos que el piso, con mucha menos memoria y menos tiempo al crearlos.
     */
    private static final double FAR_SCALE = 1.6, MID_SCALE = 2.1, NEAR_SCALE = 2.5;
    private static final int W = 1280, H = 720;
    /** Lo que los planos lejano y medio se extienden más allá de la pantalla, en unidades del mundo. */
    private static final double MARGIN = 60;
    /**
     * [v0.9.5] El piso del plano medio (antes en 566: tapaba los braseros de la sala) y el medio
     * ancho de la franja del centro donde no cuelgan estalactitas del plano medio.
     */
    private static final double MID_FLOOR_Y = 590, CLEAR_X = 340;
    /** Dónde empieza (arriba) el plano cercano: arriba de esto no tiene nada. */
    private static final double NEAR_FROM = 540;
    /** Profundidad de cada plano (ver IntroCamera.getViewMatrix). */
    private static final double FAR_DEPTH = 0.45, MID_DEPTH = 0.75, FRONT_DEPTH = 1.45;
    /** La llama del Farol, de donde sale la luz. */
    private static final Point2D FLAME = new Point2D.Double(LANTERN_X, LANTERN_Y + GreatLantern.FLAME_Y);

    // ---- Paleta dorada: del centro (la luz) hacia las orillas, que quedan casi a oscuras ----
    private static final Color LIGHT = new Color(255, 246, 208);
    private static final Color GOLD = new Color(252, 212, 126);
    private static final Color AMBER = new Color(216, 144, 68);
    private static final Color DEEP = new Color(42, 22, 16);
    private static final Color FAR = new Color(214, 148, 78);
    private static final Color MID = new Color(170, 104, 54);
    private static final Color NEAR_TOP = new Color(128, 74, 40);
    private static final Color NEAR_BOTTOM = new Color(70, 38, 24);
    private static final Color HOLLOW = new Color(74, 38, 24);
    private static final Color RIM = new Color(255, 232, 176, 150);
    private static final Color RIM_LIGHT = new Color(255, 225, 160, 110);
    private static final Color SHADE = new Color(20, 10, 8);
    private static final Color FRONT = new Color(18, 10, 12);
    private static final Color MOTE = new Color(255, 246, 210);

    /**
     * Por dónde se va la luz cuando el Farol se rompe: las bocas de los túneles, los pozos del
     * piso, las chimeneas del techo y, fuera de la pantalla, las galerías de los lados.
     */
    private static final Vec2[] HOLES = {
        new Vec2(104, 452), new Vec2(1178, 446),      // túneles de las paredes
        new Vec2(252, 650), new Vec2(1034, 654),      // pozos del piso
        new Vec2(330, -40), new Vec2(962, -40),       // chimeneas del techo
        new Vec2(-70, 300), new Vec2(1350, 280),      // galerías, a los lados
        new Vec2(460, 780), new Vec2(840, 780),       // grietas del piso, abajo
    };

    private final Plane far, mid, near;
    private final List<Shape> front = new ArrayList<>();
    private final double[] moteX, moteY, moteSpeed, motePhase;

    public GoldenCavern(long seed) {
        Random rnd = new Random(seed);
        far = renderFar(rnd);
        mid = renderMid(rnd);
        near = renderNear(rnd);
        buildFront(rnd);
        int n = 54;
        moteX = new double[n];
        moteY = new double[n];
        moteSpeed = new double[n];
        motePhase = new double[n];
        for (int i = 0; i < n; i++) {
            moteX[i] = rnd.nextDouble() * W;
            moteY[i] = rnd.nextDouble() * H;
            moteSpeed[i] = 5 + rnd.nextDouble() * 11;
            motePhase[i] = rnd.nextDouble() * Math.PI * 2;
        }
    }

    /** A dónde vuelan los pedazos de luz (coordenadas del mundo). */
    public Vec2[] getHoles() {
        return HOLES.clone();
    }

    // =========================================================
    //  DIBUJAR
    // =========================================================

    /**
     * Los planos lejano y medio: lo que queda detrás del Farol. [v0.9.5] Entre los dos, el fuego
     * de los braseros de la sala ('braziers' de 0, apagados, a 1, ardiendo), con la vista del
     * plano lejano, que es donde está la sala. Su resplandor es una imagen ya hecha que aquí se
     * estira: se muestrea como los planos (Scene.smoothScaling), no con el muestreo bilineal que
     * Java2D usaría por su cuenta con la calidad alta, que sin tarjeta de video es muy caro.
     */
    public void drawBack(Graphics2D g, IntroCamera camera, double braziers, double time) {
        Mat3 farView = camera.getViewMatrix(FAR_DEPTH);
        far.draw(g, farView);
        if (braziers > 0.001) {
            AffineTransform old = g.getTransform();
            Object sampling = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, Scene.smoothScaling(g));
            g.transform(farView.toAffineTransform());
            ThroneHallArt.drawLights(g, HALL, 0, braziers, time);
            g.setTransform(old);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, sampling != null ? sampling
                    : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);   // sin indicación, Java2D usa el vecino
        }
        mid.draw(g, camera.getViewMatrix(MID_DEPTH));
    }

    /** El plano cercano: el piso donde se para el Farol, con la misma vista que el Farol. */
    public void drawGround(Graphics2D g, IntroCamera camera) {
        near.draw(g, camera.getViewMatrix());
    }

    /**
     * Polvo dorado flotando en la luz. Cada mota sube despacio y se mece con una onda seno; el
     * operador módulo la regresa abajo al salir por arriba. Su posición es función directa del
     * tiempo. Se dibujan en píxeles de pantalla (se transforma solo su centro) para que no
     * crezcan con el acercamiento. 'alpha' las apaga cuando la caverna se oscurece.
     */
    public void drawMotes(Graphics2D g, IntroCamera camera, double time, double alpha) {
        if (alpha <= 0.01) return;
        Mat3 view = camera.getViewMatrix();
        for (int i = 0; i < moteX.length; i++) {
            double y = H - ((H - moteY[i] + time * moteSpeed[i]) % H);
            double x = moteX[i] + 9 * Math.sin(time * 0.7 + motePhase[i]);
            Vec2 p = view.apply(new Vec2(x, y));
            if (p.x < -4 || p.x > W + 4 || p.y < -4 || p.y > H + 4) continue;
            double twinkle = 0.45 + 0.55 * Math.sin(time * 1.6 + motePhase[i] * 3);
            double r = 1.1 + 1.1 * ((i * 7) % 5) / 4.0;
            g.setColor(Ease.fade(MOTE, alpha * (0.25 + 0.6 * twinkle)));
            g.fill(new Ellipse2D.Double(p.x - r, p.y - r, 2 * r, 2 * r));
        }
    }

    /** El primer plano: rocas oscuras en las orillas, con su propia vista (más cercana). */
    public void drawFront(Graphics2D g, IntroCamera camera) {
        AffineTransform old = g.getTransform();
        g.transform(camera.getViewMatrix(FRONT_DEPTH).toAffineTransform());
        g.setColor(FRONT);
        for (Shape s : front) g.fill(s);
        g.setTransform(old);
    }

    // =========================================================
    //  GENERACIÓN (una sola vez)
    // =========================================================

    /** Una imagen para un plano que cubre un rectángulo del mundo de w x h, con 'scale' píxeles por unidad. */
    private static BufferedImage newPlane(double w, double h, double scale, int type) {
        return new BufferedImage((int) Math.ceil(w * scale), (int) Math.ceil(h * scale), type);
    }

    /** Graphics de una imagen de plano: suavizado y en unidades del mundo, M = S(scale) · T(-x, -y). */
    private static Graphics2D worldGraphics(BufferedImage img, double x, double y, double scale) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(scale, scale);
        g.translate(-x, -y);
        return g;
    }

    /**
     * La roca "con luz": su color cerca de la llama y cada vez más oscura hacia las orillas, con
     * un degradado radial centrado en la llama. Así la roca también se ve iluminada por el Farol.
     */
    private static RadialGradientPaint lit(Color c) {
        Color dark = new Color(c.getRed() * 22 / 100, c.getGreen() * 18 / 100, c.getBlue() * 18 / 100);
        return new RadialGradientPaint(FLAME, 760, new float[] { 0f, 0.25f, 1f }, new Color[] { c, c, dark });
    }

    /**
     * El plano lejano (opaco): la luz que llena la caverna, los rayos y el techo y el piso de una
     * caverna enorme, con estalactitas finas.
     */
    private static Plane renderFar(Random rnd) {
        double x0 = -MARGIN, y0 = -MARGIN, w = W + 2 * MARGIN, h = H + 2 * MARGIN;
        BufferedImage img = newPlane(w, h, FAR_SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = worldGraphics(img, x0, y0, FAR_SCALE);

        // 1. La luz: un degradado radial con centro en la llama. Llena toda la caverna.
        g.setPaint(new RadialGradientPaint(FLAME, 800, new float[] { 0f, 0.18f, 0.48f, 0.9f },
                new Color[] { LIGHT, GOLD, AMBER, DEEP }));
        g.fill(new Rectangle2D.Double(x0, y0, w, h));

        // 2. Rayos: cuñas muy tenues que salen de la llama (se ven en el polvo del aire)
        Paint rayPaint = new RadialGradientPaint(FLAME, 900, new float[] { 0f, 1f },
                new Color[] { new Color(255, 248, 214, 44), new Color(255, 248, 214, 0) });
        g.setPaint(rayPaint);
        Path2D rays = new Path2D.Double();
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI * 2 / 18 + (rnd.nextDouble() - 0.5) * 0.25;
            double half = 0.025 + rnd.nextDouble() * 0.06;
            Path2D ray = new Path2D.Double();
            ray.moveTo(FLAME.getX(), FLAME.getY());
            ray.lineTo(FLAME.getX() + 1100 * Math.cos(a - half), FLAME.getY() + 1100 * Math.sin(a - half));
            ray.lineTo(FLAME.getX() + 1100 * Math.cos(a + half), FLAME.getY() + 1100 * Math.sin(a + half));
            ray.closePath();
            g.fill(ray);
            rays.append(ray, false);
        }

        // 3. El techo y el piso lejanos, con estalactitas finas
        g.setPaint(lit(FAR));
        double left = x0, right = x0 + w;
        double[] ceiling = Background.midpointDisplacement(rnd, 64, 38, 0.55);
        double[] floor = Background.midpointDisplacement(rnd, 64, 34, 0.55);
        g.fill(CaveShapes.ridge(ceiling, left, right, 92, y0));
        g.fill(CaveShapes.ridge(floor, left, right, 512, y0 + h));
        for (int i = 0; i < 30; i++) {
            double x = left + rnd.nextDouble() * w;
            g.fill(CaveShapes.spike(x, CaveShapes.heightAt(ceiling, left, right, 92, x) - 4,
                    8 + rnd.nextDouble() * 16, 30 + rnd.nextDouble() * 110, true));
        }
        for (int i = 0; i < 9; i++) {
            double x = left + rnd.nextDouble() * w;
            if (Math.abs(x - LANTERN_X) < 150) continue;   // nada detrás del Farol
            g.fill(CaveShapes.spike(x, CaveShapes.heightAt(floor, left, right, 512, x) + 4,
                    22 + rnd.nextDouble() * 30, 50 + rnd.nextDouble() * 100, false));
        }

        // 4. [v0.9.5] La sala del trono, ya fundida en la roca (ver renderHall). Su esquina cae
        //    justo en un píxel de esta imagen: se copia sin la matriz, píxel por píxel
        AffineTransform world = g.getTransform();
        g.setTransform(new AffineTransform());
        g.drawImage(renderHall(), (int) Math.round((HALL.getX() - x0) * FAR_SCALE), (int) Math.round((HALL.getY() - y0) * FAR_SCALE), null);
        g.setTransform(world);

        // 5. La luz del Farol sobre la sala: un baño dorado que sale de la llama y se apaga hacia
        //    las orillas, y otra vez los mismos rayos, para que la sala también los tenga
        g.setPaint(new RadialGradientPaint(FLAME, 760, new float[] { 0f, 0.22f, 0.55f, 1f },
                new Color[] { Ease.fade(LIGHT, 0.62), Ease.fade(GOLD, 0.34), Ease.fade(AMBER, 0.12), Ease.fade(AMBER, 0) }));
        g.fill(HALL);
        g.setPaint(rayPaint);
        g.fill(rays);
        g.dispose();
        return Plane.whole(img, x0, y0, FAR_SCALE);
    }

    /**
     * [v0.9.5] La sala del trono dorada (ThroneHallArt.drawStatic con golden = true: ya trae el
     * rosetón encendido y las brasas de los braseros), en una imagen transparente a la resolución
     * del plano lejano. Sus orillas se funden en
     * la roca: la imagen se multiplica por degradados de opacidad (AlphaComposite.DstIn, que deja
     * lo ya dibujado con su opacidad por la del degradado) que van de 0 en la orilla a 1 adentro,
     * HALL_SIDE_FADE en los lados y HALL_TOP_FADE arriba. Abajo no hace falta: ahí está el estrado.
     */
    private static BufferedImage renderHall() {
        BufferedImage img = newPlane(HALL.getWidth(), HALL.getHeight(), FAR_SCALE, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = worldGraphics(img, HALL.getX(), HALL.getY(), FAR_SCALE);
        ThroneHallArt.drawStatic(g, HALL, true);
        g.setComposite(AlphaComposite.DstIn);
        double x0 = HALL.getX(), y0 = HALL.getY(), x1 = HALL.getMaxX(), h = HALL.getHeight();
        Color clear = new Color(0, 0, 0, 0), solid = Color.BLACK;
        g.setPaint(new GradientPaint((float) x0, 0, clear, (float) (x0 + HALL_SIDE_FADE), 0, solid));
        g.fill(new Rectangle2D.Double(x0, y0, HALL_SIDE_FADE, h));
        g.setPaint(new GradientPaint((float) x1, 0, clear, (float) (x1 - HALL_SIDE_FADE), 0, solid));
        g.fill(new Rectangle2D.Double(x1 - HALL_SIDE_FADE, y0, HALL_SIDE_FADE, h));
        g.setPaint(new GradientPaint(0, (float) y0, clear, 0, (float) (y0 + HALL_TOP_FADE), solid));
        g.fill(new Rectangle2D.Double(x0, y0, HALL.getWidth(), HALL_TOP_FADE));
        g.dispose();
        return img;
    }

    /**
     * El plano medio (transparente): paredes con las bocas de dos túneles y estalactitas. Cada
     * estalactita lleva un hilo de luz en la orilla que mira a la llama (rim light), y nada más en
     * esa: la otra queda en sombra y así se ve el volumen.
     *
     * [v0.9.5] Las ruinas de columnas y arcos que había aquí se quitaron: ahora está la sala del
     * trono (plano lejano). Y las estalactitas no cuelgan delante del rosetón ni de las cadenas
     * del Farol (a menos de CLEAR_X del centro), y el piso se bajó hasta MID_FLOOR_Y para que se
     * vean los braseros y el piso de la sala.
     */
    private static Plane renderMid(Random rnd) {
        double x0 = -MARGIN, y0 = -MARGIN, w = W + 2 * MARGIN, h = H + 2 * MARGIN;
        BufferedImage img = newPlane(w, h, MID_SCALE, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = worldGraphics(img, x0, y0, MID_SCALE);
        Stroke rim = new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        Paint rock = lit(MID);

        double left = x0, right = x0 + w;
        double[] ceiling = Background.midpointDisplacement(rnd, 64, 30, 0.6);
        double[] floor = Background.midpointDisplacement(rnd, 64, 22, 0.6);
        g.setPaint(rock);
        g.fill(CaveShapes.ridge(ceiling, left, right, 46, y0));
        g.fill(CaveShapes.ridge(floor, left, right, MID_FLOOR_Y, y0 + h));
        g.fill(wall(rnd, true));
        g.fill(wall(rnd, false));
        for (int i = 0; i < 22; i++) {
            double x = left + rnd.nextDouble() * w;
            double base = CaveShapes.heightAt(ceiling, left, right, 46, x) - 4;
            double sw = 12 + rnd.nextDouble() * 24, len = 40 + rnd.nextDouble() * 130;
            if (Math.abs(x - LANTERN_X) < CLEAR_X) continue;   // ni delante del rosetón ni de las cadenas
            g.setPaint(rock);
            g.fill(CaveShapes.spike(x, base, sw, len, true));
            g.setStroke(rim);
            g.setColor(rimColor(x, base + len * 0.6));
            g.draw(CaveShapes.spikeSide(x, base, sw, len, true, x < FLAME.getX()));
        }
        // Las bocas de los túneles y las chimeneas del techo: huecos, pero no negros (hay luz)
        g.setColor(HOLLOW);
        g.fill(tunnelMouth(HOLES[0].x, HOLES[0].y, 46, 62));
        g.fill(tunnelMouth(HOLES[1].x, HOLES[1].y, 50, 66));
        g.fill(new Ellipse2D.Double(HOLES[4].x - 34, -30, 68, 74));
        g.fill(new Ellipse2D.Double(HOLES[5].x - 38, -34, 76, 82));
        g.dispose();
        return Plane.cropped(img, x0, y0, MID_SCALE);
    }

    /**
     * El hilo de luz se apaga con la distancia d a la llama:
     *     alfa = 110 · (0.35 + 0.65 · (1 - d / 800))
     * sin bajar de 0.35 · 110 (a todas partes llega algo de luz).
     */
    private static Color rimColor(double x, double y) {
        double d = FLAME.distance(x, y);
        return Ease.fade(RIM_LIGHT, 0.35 + 0.65 * Ease.clamp01(1 - d / 800));
    }

    /**
     * El plano cercano (transparente, solo la franja de abajo): el piso, con la terraza
     * escalonada donde se para el Farol y dos pozos. Al final se oscurece hacia las orillas con
     * un degradado radial pintado con AlphaComposite.SRC_ATOP, que solo toca lo ya dibujado:
     *     piso = piso · (1 - a) + sombra · a      (a crece con la distancia a la llama)
     */
    private static Plane renderNear(Random rnd) {
        double x0 = -MARGIN, y0 = NEAR_FROM, w = W + 2 * MARGIN, h = H + MARGIN - NEAR_FROM;
        BufferedImage img = newPlane(w, h, NEAR_SCALE, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = worldGraphics(img, x0, y0, NEAR_SCALE);
        double left = x0, right = x0 + w;

        double[] floor = Background.midpointDisplacement(rnd, 128, 12, 0.6);
        g.setPaint(new GradientPaint(0, 600, NEAR_TOP, 0, H, NEAR_BOTTOM));
        g.fill(CaveShapes.ridge(floor, left, right, 624, y0 + h));
        double[][] steps = { { 250, 624 }, { 205, 607 }, { 164, 590 } };   // medio ancho y cara de arriba
        for (double[] s : steps) {
            g.setPaint(new GradientPaint((float) (LANTERN_X - s[0]), 0, NEAR_TOP, (float) LANTERN_X, 0, new Color(196, 128, 72), true));
            g.fill(new Rectangle2D.Double(LANTERN_X - s[0], s[1] - 17, 2 * s[0], 60));
            g.setColor(RIM);
            g.fill(new Rectangle2D.Double(LANTERN_X - s[0], s[1] - 17, 2 * s[0], 1.5));
        }
        g.setColor(RIM);
        g.setStroke(new BasicStroke(1.5f));
        Path2D edge = new Path2D.Double();
        for (int i = 0; i < floor.length; i++) {
            double x = left + w * i / (floor.length - 1), y = 624 + floor[i];
            if (i == 0) edge.moveTo(x, y); else edge.lineTo(x, y);
        }
        g.draw(edge);
        g.setColor(HOLLOW);
        g.fill(new Ellipse2D.Double(HOLES[2].x - 62, HOLES[2].y - 17, 124, 34));
        g.fill(new Ellipse2D.Double(HOLES[3].x - 70, HOLES[3].y - 18, 140, 36));
        // La sombra de las orillas (solo sobre el piso ya pintado)
        g.setComposite(AlphaComposite.SrcAtop);
        g.setPaint(new RadialGradientPaint(FLAME, 820, new float[] { 0f, 0.35f, 1f },
                new Color[] { Ease.fade(SHADE, 0), Ease.fade(SHADE, 0), Ease.fade(SHADE, 0.72) }));
        g.fill(new Rectangle2D.Double(x0, y0, w, h));
        g.dispose();
        return Plane.cropped(img, x0, y0, NEAR_SCALE);
    }

    /** Rocas del primer plano: estalactitas grandes en las esquinas de arriba y peñascos abajo. */
    private void buildFront(Random rnd) {
        double[] xs = { -10, 70, 150, 1130, 1215, 1290 };
        for (double x : xs) {
            front.add(CaveShapes.spike(x, -20, 90 + rnd.nextDouble() * 70, 150 + rnd.nextDouble() * 130, true));
        }
        front.add(new Ellipse2D.Double(-150, 640, 330, 190));
        front.add(new Ellipse2D.Double(1085, 650, 340, 180));
        front.add(CaveShapes.spike(60, 760, 130, 170, false));
        front.add(CaveShapes.spike(1225, 760, 120, 150, false));
    }

    /** Pared de un lado de la caverna: una franja de roca con la orilla irregular (llega hasta el margen). */
    private static Path2D wall(Random rnd, boolean left) {
        double[] h = Background.midpointDisplacement(rnd, 32, 26, 0.6);
        Path2D p = new Path2D.Double();
        double edge = left ? -MARGIN : W + MARGIN, top = -MARGIN, bottom = H + MARGIN;
        p.moveTo(edge, top);
        for (int i = 0; i < h.length; i++) {
            double y = top + (bottom - top) * i / (h.length - 1);
            double depth = 150 + h[i] + 40 * Math.cos(y / H * Math.PI * 2);   // más ancha arriba y abajo
            p.lineTo(left ? depth : W - depth, y);
        }
        p.lineTo(edge, bottom);
        p.closePath();
        return p;
    }

    /** Boca de túnel: un arco (medio círculo arriba, lados rectos). */
    private static Shape tunnelMouth(double cx, double cy, double halfWidth, double height) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx - halfWidth, cy + height / 2);
        p.lineTo(cx - halfWidth, cy - height / 2 + halfWidth);
        p.curveTo(cx - halfWidth, cy - height / 2 - halfWidth * 0.33, cx + halfWidth, cy - height / 2 - halfWidth * 0.33,
                cx + halfWidth, cy - height / 2 + halfWidth);
        p.lineTo(cx + halfWidth, cy + height / 2);
        p.closePath();
        return p;
    }
}
