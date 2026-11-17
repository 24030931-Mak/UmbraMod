package umbra.ui.intro;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

import umbra.core.Config;
import umbra.graphics.Background;
import umbra.graphics.CaveShapes;
import umbra.math.Mat3;
import umbra.math.Vec2;

/**
 * [v0.8.7] El fondo del rincón oscuro de las escenas 4, 5 y 6 (antes estaba dentro de
 * DarkStage): capas de roca con desplazamiento del punto medio, como graphics.Background, y las
 * repisas con la paleta de graphics.LevelArt. Encima de todo, unas esporas y una franja de niebla.
 *
 * Se dibuja una sola vez, a 2.5 veces la resolución (la cámara de estas escenas se acerca entre
 * 1.5 y 3.4 veces), en dos planos que se pintan cada uno con su propia vista
 * (IntroCamera.getViewMatrix(profundidad)):
 *   - lejano (0.55, opaco): el cielo de la cueva, el brillo de la chimenea del techo y las
 *     capas de roca lejana y media, con sus ruinas;
 *   - del mundo (1, transparente): las estalactitas cercanas, las dos repisas con su musgo y sus
 *     hongos, y las dos rocas. Es la misma vista de Candil, de Logi y de las sombras.
 * Así, cuando la cámara se mueve o se acerca, la cueva del fondo se mueve menos que las repisas.
 */
public final class DarkCavern {

    private static final double W = DarkStage.W, H = DarkStage.H, GROUND = DarkStage.GROUND;
    private static final double FAR_GLOW_X = DarkStage.FAR_GLOW_X, FAR_GLOW_Y = DarkStage.FAR_GLOW_Y;
    /** Píxeles de la imagen por cada unidad del mundo. */
    private static final double SCALE = 2.5;
    /** Profundidad del plano lejano (ver IntroCamera.getViewMatrix). */
    private static final double FAR_DEPTH = 0.55;
    /** Lo que el plano lejano se extiende más allá del escenario, en unidades del mundo. */
    private static final double MARGIN = 60;

    // ---- Paleta: la del fondo y las repisas del juego ----
    private static final Color SKY_TOP = new Color(5, 8, 16), SKY_BOTTOM = new Color(17, 24, 41);
    private static final Color FAR = new Color(32, 44, 70), MID = new Color(22, 30, 51), NEAR = new Color(12, 17, 29);
    private static final Color FOG = new Color(110, 135, 180);
    private static final Color ROCK_TOP = new Color(46, 54, 76), ROCK_BOTTOM = new Color(22, 26, 40);
    private static final Color RIM = new Color(125, 145, 190, 170);
    private static final Color STRATA = new Color(70, 82, 110, 45);
    private static final Color MOSS = new Color(78, 140, 135);
    private static final Color STONE_TOP = new Color(92, 98, 124), STONE_BOTTOM = new Color(56, 60, 82);
    private static final Color STONE_EDGE = new Color(160, 165, 200, 170);
    private static final Color CAP = new Color(80, 215, 205);
    private static final Color SPORE = new Color(150, 215, 255);

    private final Plane far, world;
    private final BufferedImage fog;
    private final Glow farGlow = new Glow(96, new Color(255, 206, 130), 1.0, 0.5);
    private final double[] sporeX, sporeY, sporeSpeed, sporePhase;
    /** Todo el escenario menos las repisas y las rocas: las sombras solo se pintan ahí. */
    private final Area air = new Area(new Rectangle2D.Double(-100, -100, W + 200, H + 200));

    /** Genera el fondo con los números al azar de 'rnd' (siempre la misma semilla: siempre igual). */
    public DarkCavern(Random rnd) {
        far = renderFar(rnd);
        world = renderWorld(rnd);
        air.subtract(new Area(DarkStage.ROCK_1));
        air.subtract(new Area(DarkStage.ROCK_2));
        fog = renderFog(rnd);
        int n = 70;
        sporeX = new double[n];
        sporeY = new double[n];
        sporeSpeed = new double[n];
        sporePhase = new double[n];
        for (int i = 0; i < n; i++) {
            sporeX[i] = rnd.nextDouble() * W;
            sporeY[i] = rnd.nextDouble() * H;
            sporeSpeed[i] = 4 + rnd.nextDouble() * 9;
            sporePhase[i] = rnd.nextDouble() * Math.PI * 2;
        }
    }

    /** El aire del escenario (sin las repisas ni las rocas), en coordenadas del mundo. */
    public Area getAir() {
        return air;
    }

    // =========================================================
    //  DIBUJO
    // =========================================================

    /**
     * El fondo: el plano lejano con el brillo de la chimenea (que respira con 'clock') y,
     * delante, el plano del mundo con las repisas y las rocas.
     */
    public void drawBack(Graphics2D g, IntroCamera camera, double clock) {
        Mat3 farView = camera.getViewMatrix(FAR_DEPTH);
        far.draw(g, farView);
        AffineTransform onScreen = g.getTransform();
        g.transform(farView.toAffineTransform());
        double pulse = 1 + 0.08 * Math.sin(clock * 1.3) + 0.04 * Math.sin(clock * 3.1);
        farGlow.draw(g, FAR_GLOW_X, FAR_GLOW_Y, 46 * pulse);
        g.setTransform(onScreen);
        world.draw(g, camera.getViewMatrix());
    }

    /** Lo que va delante de todo: las esporas y la niebla. */
    public void drawFront(Graphics2D g, IntroCamera camera, double clock) {
        drawSpores(g, camera.getViewMatrix(), clock);
        drawFog(g, camera, clock);
    }

    /** Esporas: puntitos de luz que suben despacio, como las del fondo del juego. En píxeles de pantalla. */
    private void drawSpores(Graphics2D g, Mat3 view, double clock) {
        for (int i = 0; i < sporeX.length; i++) {
            double y = H - ((H - sporeY[i] + clock * sporeSpeed[i]) % H);
            double x = sporeX[i] + 6 * Math.sin(clock * 0.8 + sporePhase[i]);
            Vec2 p = view.apply(new Vec2(x, y));
            if (p.x < -4 || p.x > Config.WIDTH + 4 || p.y < -4 || p.y > Config.HEIGHT + 4) continue;
            double glow = 0.5 + 0.5 * Math.sin(clock * 1.7 + sporePhase[i] * 2);
            double r = 1.2 + 0.9 * (i % 3);
            g.setColor(Ease.fade(SPORE, 0.12 + 0.3 * glow));
            g.fill(new Ellipse2D.Double(p.x - r * 2, p.y - r * 2, r * 4, r * 4));
            g.setColor(Ease.fade(SPORE, 0.3 + 0.6 * glow));
            g.fill(new Ellipse2D.Double(p.x - r * 0.6, p.y - r * 0.6, r * 1.2, r * 1.2));
        }
    }

    /**
     * Una franja de niebla delante de todo, en píxeles de pantalla. Se mueve sola y además
     * con la cámara, a la mitad de su paso (parallax); el módulo la repite sin cortes.
     */
    private void drawFog(Graphics2D g, IntroCamera camera, double clock) {
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int w = fog.getWidth();
        double[] ys = { 470 }, speeds = { 14 };
        for (int k = 0; k < ys.length; k++) {
            double offset = (clock * speeds[k] - camera.getX() * camera.getZoom() * 0.5) % w;
            if (offset > 0) offset -= w;
            int y = (int) Math.round(ys[k] - (camera.getY() - 380) * camera.getZoom() * 0.5);
            for (double x = offset; x < Config.WIDTH; x += w) g.drawImage(fog, (int) Math.round(x), y, null);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    // =========================================================
    //  GENERACIÓN (una sola vez)
    // =========================================================

    /** Una imagen para un plano que cubre del mundo el rectángulo (x, y, w, h), y su Graphics en unidades del mundo. */
    private static BufferedImage newPlane(double w, double h, int type) {
        return new BufferedImage((int) Math.ceil(w * SCALE), (int) Math.ceil(h * SCALE), type);
    }

    /** Graphics de una imagen de plano: suavizado y en unidades del mundo, M = S(SCALE) · T(-x, -y). */
    private static Graphics2D worldGraphics(BufferedImage img, double x, double y) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(SCALE, SCALE);
        g.translate(-x, -y);
        return g;
    }

    /**
     * El plano lejano (opaco): el cielo, el resplandor de la chimenea y las capas de roca lejana
     * (más clara) y media (con ruinas), separadas por un banco de niebla quieto. Los techos
     * tienen una muesca donde se abre la chimenea.
     */
    private static Plane renderFar(Random rnd) {
        double x0 = -MARGIN, y0 = -MARGIN, w = W + 2 * MARGIN, h = H + 2 * MARGIN;
        double left = x0, right = x0 + w, top = y0, bottom = y0 + h;
        BufferedImage img = newPlane(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = worldGraphics(img, x0, y0);

        g.setPaint(new GradientPaint(0, 0, SKY_TOP, 0, (float) H, SKY_BOTTOM));
        g.fill(new Rectangle2D.Double(x0, y0, w, h));
        // El resplandor lejano, por una chimenea del techo: lo que quedó encendido allá arriba
        g.setPaint(new RadialGradientPaint(new Point2D.Double(FAR_GLOW_X, FAR_GLOW_Y - 20), 240, new float[] { 0f, 0.4f, 1f },
                new Color[] { new Color(255, 200, 120, 105), new Color(190, 130, 100, 40), new Color(120, 110, 140, 0) }));
        g.fill(new Rectangle2D.Double(x0, y0, w, 380 - y0));

        // Capas de roca: lejos (más clara) y en medio (con ruinas)
        g.setColor(FAR);
        double[] ceiling = Background.midpointDisplacement(rnd, 64, 30, 0.55);
        double[] floor = Background.midpointDisplacement(rnd, 64, 34, 0.55);
        g.fill(notchedRidge(ceiling, left, right, 118, top, 230, 84));
        g.fill(notchedRidge(floor, left, right, 492, bottom, 0, 1));
        for (int i = 0; i < 26; i++) {
            double x = rnd.nextDouble() * W;
            if (Math.abs(x - FAR_GLOW_X) < 100) continue;
            g.fill(CaveShapes.spike(x, 100, 7 + rnd.nextDouble() * 14, 30 + rnd.nextDouble() * 100, true));
        }
        g.setColor(MID);
        ceiling = Background.midpointDisplacement(rnd, 64, 24, 0.6);
        floor = Background.midpointDisplacement(rnd, 64, 26, 0.6);
        g.fill(notchedRidge(ceiling, left, right, 74, top, 190, 62));
        g.fill(notchedRidge(floor, left, right, 536, bottom, 0, 1));
        g.setStroke(new BasicStroke(9, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        double[][] ruins = { { 62, 86, 150, 1 }, { 500, 70, 118, 0 }, { 1004, 92, 168, 1 } };   // x, claro, alto, ¿con arco?
        for (double[] r : ruins) {
            for (int k = 0; k < 2; k++) {
                double tall = r[3] == 1 ? r[2] : r[2] * (0.5 + 0.5 * k);
                g.fill(new Rectangle2D.Double(r[0] + k * r[1] - 8, 540 - tall, 16, tall + 20));
                g.fill(new Rectangle2D.Double(r[0] + k * r[1] - 12, 540 - tall, 24, 7));
            }
            if (r[3] == 1) g.draw(new Arc2D.Double(r[0], 540 - r[2] - r[1] / 2, r[1], r[1], 0, 180, Arc2D.OPEN));
        }
        for (int i = 0; i < 18; i++) {
            double x = rnd.nextDouble() * W;
            if (Math.abs(x - FAR_GLOW_X) < 80) continue;
            g.fill(CaveShapes.spike(x, 58, 10 + rnd.nextDouble() * 20, 40 + rnd.nextDouble() * 110, true));
        }
        // Un banco de niebla quieto entre las capas, para separarlas
        for (int i = 0; i < 26; i++) {
            double x = rnd.nextDouble() * W, y = 330 + rnd.nextDouble() * 130;
            float r = (float) (50 + rnd.nextDouble() * 70);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), r, new float[] { 0f, 1f },
                    new Color[] { alpha(FOG, 15), alpha(FOG, 0) }));
            g.fill(new Ellipse2D.Double(x - r * 1.8, y - r * 0.6, r * 3.6, r * 1.2));
        }
        g.dispose();
        return Plane.whole(img, x0, y0, SCALE);
    }

    /**
     * El plano del mundo (transparente): estalactitas cercanas, las dos repisas, las dos rocas y,
     * sobre el hueco, una sombra que lo hace verse hondo.
     */
    private Plane renderWorld(Random rnd) {
        double x0 = -MARGIN, y0 = -MARGIN, w = W + 2 * MARGIN, h = H + 2 * MARGIN;
        BufferedImage img = newPlane(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = worldGraphics(img, x0, y0);

        g.setColor(NEAR);
        for (double x : new double[] { 30, 130, 472, 640, 772, 1010, 1100 }) {
            g.fill(CaveShapes.spike(x, -6, 26 + rnd.nextDouble() * 40, 60 + rnd.nextDouble() * 120, true));
        }
        drawLedge(g, rnd, -30, DarkStage.GAP_LEFT, true);
        drawLedge(g, rnd, DarkStage.GAP_RIGHT, W + 30, false);
        drawRock(g, rnd, DarkStage.ROCK_1);
        drawRock(g, rnd, DarkStage.ROCK_2);

        // El hueco se oscurece hacia abajo, para que se vea hondo
        g.setPaint(new GradientPaint(0, 500, new Color(2, 3, 8, 0), 0, (float) H, new Color(2, 3, 8, 215)));
        g.fill(new Rectangle2D.Double(x0, 500, w, y0 + h - 500));   // hasta el borde del plano (con margen), no solo hasta H
        g.dispose();
        return Plane.cropped(img, x0, y0, SCALE);
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /**
     * Una repisa de roca como las del juego: cara de arriba plana (es donde pisa Candil) con una
     * orilla clara, y la pared que da al hueco irregular (desplazamiento del punto medio), cada
     * vez más metida conforme baja, como un risco. Encima, vetas, musgo y unos hongos que brillan.
     */
    private void drawLedge(Graphics2D g, Random rnd, double x0, double x1, boolean cliffOnRight) {
        double[] inset = Background.midpointDisplacement(rnd, 16, 9, 0.6);
        Path2D body = new Path2D.Double();
        double flat = cliffOnRight ? x0 : x1, cliff = cliffOnRight ? x1 : x0, dir = cliffOnRight ? -1 : 1;
        body.moveTo(flat, GROUND);
        body.lineTo(cliff, GROUND);
        for (int i = 1; i <= 16; i++) {
            double y = GROUND + (H - GROUND + 10) * i / 16.0;
            double in = 4 + 1.1 * (y - GROUND) * 0.32 + Math.abs(inset[i]) + (i == 1 ? 3 : 0);
            body.lineTo(cliff + dir * in, y);
        }
        body.lineTo(flat, H + 10);
        body.closePath();
        g.setPaint(new GradientPaint(0, (float) GROUND, ROCK_TOP, 0, (float) GROUND + 130, ROCK_BOTTOM));
        g.fill(body);
        air.subtract(new Area(body));   // las sombras no se pintan sobre la roca (ver DarkStage.drawShadows)

        // Vetas: rayas casi horizontales
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(STRATA);
        double left = Math.min(x0, x1), right = Math.max(x0, x1);
        for (int i = 0; i < 9; i++) {
            double y = GROUND + 12 + rnd.nextDouble() * 110;
            double a = left + rnd.nextDouble() * (right - left - 120), len = 40 + rnd.nextDouble() * 90;
            double shrink = cliffOnRight ? Math.min(a + len, x1 - 10 - (y - GROUND) * 0.4) - a : len;
            if (!cliffOnRight) a = Math.max(a, x0 + 10 + (y - GROUND) * 0.4);
            if (shrink > 8) g.draw(new Line2D.Double(a, y, a + shrink, y + rnd.nextDouble() * 4 - 2));
        }
        // Orilla clara: lo que tiene borde claro arriba se puede pisar (la regla visual del juego)
        g.setColor(RIM);
        g.fill(new Rectangle2D.Double(left, GROUND, right - left, 1.4));
        // Musgo: matas de briznas cortas sobre la orilla
        g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 16; i++) {
            double x = left + 34 + rnd.nextDouble() * (right - left - 68);
            if (DarkStage.ROCK_1.intersects(x - 8, GROUND - 4, 16, 4) || DarkStage.ROCK_2.intersects(x - 8, GROUND - 4, 16, 4)) continue;
            for (int b = 0; b < 4; b++) {
                double bx = x + (b - 1.5) * 1.8, h = 2.5 + rnd.nextDouble() * 4;
                g.setColor(Ease.fade(MOSS, 0.5 + rnd.nextDouble() * 0.4));
                g.draw(new Line2D.Double(bx, GROUND, bx + (rnd.nextDouble() - 0.5) * 3, GROUND - h));
            }
        }
        // Hongos chicos que brillan, cerca del risco
        for (int i = 0; i < 2; i++) {
            double x = cliff + dir * (16 + i * 13 + rnd.nextDouble() * 5), h = 5 + rnd.nextDouble() * 3;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x, GROUND - h), 11, new float[] { 0f, 1f },
                    new Color[] { alpha(CAP, 70), alpha(CAP, 0) }));
            g.fill(new Ellipse2D.Double(x - 11, GROUND - h - 11, 22, 22));
            g.setColor(new Color(150, 170, 190));
            g.draw(new Line2D.Double(x, GROUND, x, GROUND - h));
            g.setColor(CAP);
            g.fill(new Arc2D.Double(x - 3.6, GROUND - h - 2.6, 7.2, 5.2, 0, 180, Arc2D.CHORD));
        }
    }

    /** Una roca: su rectángulo de colisión con las esquinas cortadas al azar, de piedra más clara. */
    private static void drawRock(Graphics2D g, Random rnd, Rectangle2D r) {
        double x0 = r.getMinX(), x1 = r.getMaxX(), y0 = r.getMinY(), y1 = r.getMaxY();
        double c1 = 5 + rnd.nextDouble() * 6, c2 = 5 + rnd.nextDouble() * 6;
        Path2D p = new Path2D.Double();
        p.moveTo(x0, y1);
        p.lineTo(x0 + 0.5, y0 + c1 + 4);
        p.lineTo(x0 + c1, y0 + 0.5);
        p.lineTo(x1 - c2, y0);
        p.lineTo(x1 - 0.5, y0 + c2);
        p.lineTo(x1, y1);
        p.closePath();
        g.setPaint(new GradientPaint(0, (float) y0, STONE_TOP, 0, (float) y1, STONE_BOTTOM));
        g.fill(p);
        g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(STONE_EDGE);
        Path2D top = new Path2D.Double();
        top.moveTo(x0 + 0.5, y0 + c1 + 4);
        top.lineTo(x0 + c1, y0 + 0.5);
        top.lineTo(x1 - c2, y0);
        top.lineTo(x1 - 0.5, y0 + c2);
        g.draw(top);
        g.setColor(new Color(22, 24, 38, 190));   // dos grietas
        g.draw(new Line2D.Double(x0 + r.getWidth() * 0.35, y0 + 3, x0 + r.getWidth() * 0.45, y0 + r.getHeight() * 0.55));
        g.draw(new Line2D.Double(x0 + r.getWidth() * 0.45, y0 + r.getHeight() * 0.55, x0 + r.getWidth() * 0.3, y1 - 6));
        g.draw(new Line2D.Double(x1 - 12, y0 + r.getHeight() * 0.4, x1 - 5, y0 + r.getHeight() * 0.7));
    }

    /** Textura de niebla repetible de lado a lado: manchas alargadas, con copia a un ancho de distancia. */
    private static BufferedImage renderFog(Random rnd) {
        int w = 1024, h = 200;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < 22; i++) {
            double cx = rnd.nextDouble() * w, cy = 70 + rnd.nextDouble() * (h - 140);
            float r = (float) (30 + rnd.nextDouble() * 38);
            for (int k = -1; k <= 1; k++) {
                AffineTransform old = g.getTransform();
                g.translate(cx + k * w, cy);
                g.scale(2.6, 1);
                g.setPaint(new RadialGradientPaint(new Point2D.Double(0, 0), r, new float[] { 0f, 1f },
                        new Color[] { alpha(FOG, 17), alpha(FOG, 0) }));
                g.fill(new Ellipse2D.Double(-r, -r, 2 * r, 2 * r));
                g.setTransform(old);
            }
        }
        g.dispose();
        return img;
    }

    /**
     * Perfil de techo o de piso entre 'left' y 'right' (CaveShapes.ridge). A los techos se les
     * resta una campana de Gauss centrada en la chimenea, notch · e^(-((x - centro) / ancho)²),
     * para abrir la muesca por donde entra el brillo lejano.
     */
    private static Path2D notchedRidge(double[] h, double left, double right, double base, double edgeY,
                                       double notch, double notchWidth) {
        double[] cut = new double[h.length];
        for (int i = 0; i < h.length; i++) {
            double x = left + (right - left) * i / (h.length - 1);
            double d = (x - FAR_GLOW_X) / notchWidth;
            cut[i] = h[i] - notch * Math.exp(-d * d);
        }
        return CaveShapes.ridge(cut, left, right, base, edgeY);
    }
}
