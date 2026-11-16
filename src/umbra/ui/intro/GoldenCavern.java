package umbra.ui.intro;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import umbra.graphics.Background;
import umbra.math.Mat3;
import umbra.math.Vec2;

/**
 * [v0.8.6] El escenario de las tres primeras escenas: la gran caverna cuando el Farol la
 * iluminaba toda. Mide lo mismo que la pantalla (1280 x 720) vista de lejos.
 *
 * Es la misma técnica del fondo del juego (graphics.Background), con la paleta al revés: en vez
 * de azules oscuros, dorados. Las capas de roca se generan con desplazamiento del punto medio y
 * las lejanas son más claras que las cercanas (perspectiva atmosférica), pero aquí "más claro"
 * es más parecido a la luz: no hay una sola sombra.
 *
 * Todo lo que no se mueve se dibuja una sola vez en una imagen al doble de resolución
 * (2560 x 1440). Cuando la cámara se acerca hasta 2x la imagen se sigue viendo nítida, y de
 * lejos se reduce. Encima va un primer plano de rocas oscuras, dibujado con una vista más
 * "cercana" (IntroCamera.getViewMatrix con profundidad mayor a 1): al acercarse la cámara, esas
 * rocas crecen y se salen de la pantalla más rápido que el fondo, y eso da profundidad.
 */
public final class GoldenCavern {

    /** Dónde se para el Gran Farol: el centro del pie de su pedestal. */
    public static final double LANTERN_X = 640, LANTERN_Y = 574;
    /** Píxeles de la imagen por cada unidad del mundo. */
    private static final double SCALE = 2;
    private static final int W = 1280, H = 720;
    /** Qué tanto más "cerca" está el primer plano (ver IntroCamera.getViewMatrix). */
    private static final double FRONT_DEPTH = 1.45;

    // ---- Paleta dorada: del centro (la luz) hacia las orillas ----
    private static final Color LIGHT = new Color(255, 246, 208);
    private static final Color GOLD = new Color(252, 212, 126);
    private static final Color AMBER = new Color(216, 144, 68);
    private static final Color DEEP = new Color(116, 64, 38);
    private static final Color FAR = new Color(214, 148, 78);
    private static final Color MID = new Color(170, 104, 54);
    private static final Color NEAR_TOP = new Color(128, 74, 40);
    private static final Color NEAR_BOTTOM = new Color(70, 38, 24);
    private static final Color HOLLOW = new Color(74, 38, 24);
    private static final Color RIM = new Color(255, 232, 176, 150);
    private static final Color FRONT = new Color(58, 30, 20);
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

    private final BufferedImage back;
    private final List<Shape> front = new ArrayList<>();
    private final double[] moteX, moteY, moteSpeed, motePhase;

    public GoldenCavern(long seed) {
        Random rnd = new Random(seed);
        back = renderBack(rnd);
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

    /** El fondo ya dibujado, con la vista de la cámara. */
    public void drawBack(Graphics2D g, IntroCamera camera) {
        Scene.drawImage(g, back, camera.getViewMatrix(), 0, 0, SCALE);
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

    private static BufferedImage renderBack(Random rnd) {
        BufferedImage img = new BufferedImage((int) (W * SCALE), (int) (H * SCALE), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(SCALE, SCALE);
        Point2D flame = new Point2D.Double(LANTERN_X, LANTERN_Y + GreatLantern.FLAME_Y);

        // 1. La luz: un degradado radial con centro en la llama. Llena toda la caverna.
        g.setPaint(new RadialGradientPaint(flame, 800, new float[] { 0f, 0.2f, 0.55f, 1f },
                new Color[] { LIGHT, GOLD, AMBER, DEEP }));
        g.fillRect(0, 0, W, H);

        // 2. Rayos: cuñas muy tenues que salen de la llama (se ven en el polvo del aire)
        g.setPaint(new RadialGradientPaint(flame, 900, new float[] { 0f, 1f },
                new Color[] { new Color(255, 248, 214, 44), new Color(255, 248, 214, 0) }));
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI * 2 / 18 + (rnd.nextDouble() - 0.5) * 0.25;
            double half = 0.025 + rnd.nextDouble() * 0.06;
            Path2D ray = new Path2D.Double();
            ray.moveTo(flame.getX(), flame.getY());
            ray.lineTo(flame.getX() + 1000 * Math.cos(a - half), flame.getY() + 1000 * Math.sin(a - half));
            ray.lineTo(flame.getX() + 1000 * Math.cos(a + half), flame.getY() + 1000 * Math.sin(a + half));
            ray.closePath();
            g.fill(ray);
        }

        // 3. Capa lejana: el techo y el piso de una caverna enorme, con estalactitas finas
        g.setColor(FAR);
        double[] ceiling = Background.midpointDisplacement(rnd, 64, 38, 0.55);
        double[] floor = Background.midpointDisplacement(rnd, 64, 34, 0.55);
        g.fill(ridge(ceiling, 92, true));
        g.fill(ridge(floor, 512, false));
        for (int i = 0; i < 30; i++) {
            double x = rnd.nextDouble() * W;
            g.fill(spike(x, heightAt(ceiling, 92, x) - 4, 8 + rnd.nextDouble() * 16, 30 + rnd.nextDouble() * 110, true));
        }
        for (int i = 0; i < 9; i++) {
            double x = rnd.nextDouble() * W;
            if (Math.abs(x - LANTERN_X) < 150) continue;   // nada detrás del Farol
            g.fill(spike(x, heightAt(floor, 512, x) + 4, 22 + rnd.nextDouble() * 30, 50 + rnd.nextDouble() * 100, false));
        }

        // 4. Capa media: paredes con las bocas de dos túneles, estalactitas y las ruinas de
        //    columnas y arcos de quienes vivían a la luz del Farol (chiquitas: el Farol es gigante)
        g.setColor(MID);
        ceiling = Background.midpointDisplacement(rnd, 64, 30, 0.6);
        floor = Background.midpointDisplacement(rnd, 64, 22, 0.6);
        g.fill(ridge(ceiling, 46, true));
        g.fill(ridge(floor, 566, false));
        g.fill(wall(rnd, true));
        g.fill(wall(rnd, false));
        for (int i = 0; i < 22; i++) {
            double x = rnd.nextDouble() * W;
            g.fill(spike(x, heightAt(ceiling, 46, x) - 4, 12 + rnd.nextDouble() * 24, 40 + rnd.nextDouble() * 130, true));
        }
        g.setStroke(new BasicStroke(7, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        for (double x = 168; x < W - 160; x += 70 + rnd.nextDouble() * 80) {
            if (Math.abs(x - LANTERN_X) < 215) continue;
            double span = 34 + rnd.nextDouble() * 22, height = 46 + rnd.nextDouble() * 52;
            double base = heightAt(floor, 566, x) + 6;
            boolean arch = rnd.nextDouble() < 0.6;
            for (int k = 0; k < 2; k++) {
                double h = arch ? height : height * (0.45 + rnd.nextDouble() * 0.55);
                g.fill(new Rectangle2D.Double(x + k * span - 5.5, base - h, 11, h + 12));
                g.fill(new Rectangle2D.Double(x + k * span - 8.5, base - h, 17, 5));
            }
            if (arch) g.draw(new Arc2D.Double(x, base - height - span / 2, span, span, 0, 180, Arc2D.OPEN));
            x += span;
        }
        // Las bocas de los túneles y las chimeneas del techo: huecos, pero no negros (hay luz)
        g.setColor(HOLLOW);
        g.fill(tunnelMouth(HOLES[0].x, HOLES[0].y, 46, 62));
        g.fill(tunnelMouth(HOLES[1].x, HOLES[1].y, 50, 66));
        g.fill(new Ellipse2D.Double(HOLES[4].x - 34, -30, 68, 74));
        g.fill(new Ellipse2D.Double(HOLES[5].x - 38, -34, 76, 82));

        // 5. Capa cercana: el piso, con la terraza escalonada donde se para el Farol y dos pozos
        floor = Background.midpointDisplacement(rnd, 128, 12, 0.6);
        Path2D ground = ridge(floor, 624, false);
        g.setPaint(new GradientPaint(0, 600, NEAR_TOP, 0, H, NEAR_BOTTOM));
        g.fill(ground);
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
            double x = W * (double) i / (floor.length - 1), y = 624 + floor[i];
            if (i == 0) edge.moveTo(x, y); else edge.lineTo(x, y);
        }
        g.draw(edge);
        g.setColor(HOLLOW);
        g.fill(new Ellipse2D.Double(HOLES[2].x - 62, HOLES[2].y - 17, 124, 34));
        g.fill(new Ellipse2D.Double(HOLES[3].x - 70, HOLES[3].y - 18, 140, 36));
        g.dispose();
        return img;
    }

    /** Rocas del primer plano: estalactitas grandes en las esquinas de arriba y peñascos abajo. */
    private void buildFront(Random rnd) {
        double[] xs = { -10, 70, 150, 1130, 1215, 1290 };
        for (double x : xs) {
            front.add(spike(x, -20, 90 + rnd.nextDouble() * 70, 150 + rnd.nextDouble() * 130, true));
        }
        front.add(new Ellipse2D.Double(-150, 640, 330, 190));
        front.add(new Ellipse2D.Double(1085, 650, 340, 180));
        front.add(spike(60, 760, 130, 170, false));
        front.add(spike(1225, 760, 120, 150, false));
    }

    /** Pared de un lado de la caverna: una franja de roca con la orilla irregular. */
    private static Path2D wall(Random rnd, boolean left) {
        double[] h = Background.midpointDisplacement(rnd, 32, 26, 0.6);
        Path2D p = new Path2D.Double();
        double edge = left ? 0 : W;
        p.moveTo(edge, 0);
        for (int i = 0; i < h.length; i++) {
            double y = H * (double) i / (h.length - 1);
            double depth = 150 + h[i] + 40 * Math.cos(y / H * Math.PI * 2);   // más ancha arriba y abajo
            p.lineTo(left ? depth : W - depth, y);
        }
        p.lineTo(edge, H);
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

    /** Altura del perfil en x (interpolación lineal entre los dos puntos vecinos). */
    private static double heightAt(double[] h, double base, double x) {
        double t = Ease.clamp01(x / W) * (h.length - 1);
        int i = Math.min((int) t, h.length - 2);
        return base + Ease.lerp(h[i], h[i + 1], t - i);
    }

    /** Perfil de techo (cerrado contra el borde de arriba) o de piso (contra el de abajo). */
    private static Path2D ridge(double[] h, double base, boolean ceiling) {
        Path2D p = new Path2D.Double();
        double edge = ceiling ? 0 : H;
        p.moveTo(0, edge);
        for (int i = 0; i < h.length; i++) p.lineTo(W * (double) i / (h.length - 1), base + h[i]);
        p.lineTo(W, edge);
        p.closePath();
        return p;
    }

    /** Estalactita (hacia abajo) o estalagmita (hacia arriba), con lados curvos. */
    private static Path2D spike(double x, double baseY, double w, double len, boolean down) {
        double dir = down ? 1 : -1;
        Path2D p = new Path2D.Double();
        p.moveTo(x - w / 2, baseY);
        p.quadTo(x - w / 5, baseY + dir * len * 0.55, x, baseY + dir * len);
        p.quadTo(x + w / 5, baseY + dir * len * 0.55, x + w / 2, baseY);
        p.closePath();
        return p;
    }
}
