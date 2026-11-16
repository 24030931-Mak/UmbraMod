package umbra.ui.intro;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Random;

import umbra.core.Config;

/**
 * [v0.8.6] La oscuridad que avanza como neblina (escena 3).
 *
 * Misma técnica de la niebla del fondo del juego (Background.renderFog): una imagen hecha una
 * sola vez con muchas manchas suaves (degradados radiales que se desvanecen hacia afuera), que
 * después solo se mueve. Aquí la imagen es el frente de un banco de neblina: del lado de atrás
 * es opaca y hacia adelante se deshace en bultos, porque las manchas se amontonan atrás (unas
 * sobre otras se vuelven opacas) y escasean adelante.
 *
 * Hay dos bancos, uno desde cada lado de la pantalla, y cada uno son dos capas: una bruma
 * azulada que va adelante y la oscuridad casi negra que viene detrás. Cada capa se mueve con
 * una traslación: T(frente(cover), vaivén(tiempo)). El banco derecho es el mismo dibujado en
 * espejo, con una escala negativa en x: S(-1, 1).
 *
 * Detrás del frente todo es negro liso: ahí se rellena un rectángulo, que es mucho más barato
 * que una imagen con transparencia.
 */
public final class Mist {

    /** Ancho en pantalla del frente (px). La imagen se guarda a la mitad y se dibuja al doble. */
    private static final int EDGE = 460;
    private static final int HALF_W = EDGE / 2, HALF_H = (Config.HEIGHT + 80) / 2;
    /** Cuánto se adelanta la bruma a la oscuridad (px). */
    private static final double LEAD = 150;

    private static final Color DARK = new Color(5, 6, 14);
    private static final Color HAZE = new Color(44, 54, 92);
    private static final Color EYE = new Color(238, 246, 255);

    private final BufferedImage dark, haze;
    private final Glow eyeGlow = new Glow(64, new Color(130, 170, 255), 0.35, 0.4);

    public Mist(long seed) {
        Random rnd = new Random(seed);
        dark = renderFront(rnd, DARK, 120);
        haze = renderFront(rnd, HAZE, 58);
    }

    /**
     * Dibuja los dos bancos. 'cover' va de 0 (todavía fuera de la pantalla) a 1 (ya se juntaron
     * en el centro y lo taparon todo); el frente de cada banco está en una interpolación lineal
     * entre esas dos posiciones.
     */
    public void draw(Graphics2D g, double cover, double time) {
        if (cover <= 0) return;
        if (cover >= 1) {   // ya lo cubrió todo: un solo relleno
            g.setColor(DARK);
            g.fillRect(0, 0, Config.WIDTH, Config.HEIGHT);
            return;
        }
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object quality = g.getRenderingHint(RenderingHints.KEY_RENDERING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        // Hasta dónde llega la punta del frente, medido desde la orilla de su lado de la pantalla
        double tip = Ease.lerp(-20, Config.WIDTH / 2.0 + 0.85 * EDGE, cover);
        for (int side = 0; side < 2; side++) {
            drawFront(g, haze, tip + LEAD, -40 + 16 * Math.sin(time * 0.45 + side * 2.1), side == 1);
        }
        for (int side = 0; side < 2; side++) {
            double solid = tip - EDGE;   // de la orilla hasta aquí ya es negro liso
            if (solid > 0) {
                g.setColor(DARK);
                int w = (int) Math.ceil(solid) + 1;
                g.fillRect(side == 0 ? 0 : Config.WIDTH - w, 0, w, Config.HEIGHT);
            }
            drawFront(g, dark, tip, -40 + 12 * Math.sin(time * 0.6 + 1 + side * 1.7), side == 1);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, quality);
    }

    /**
     * Un frente con su punta a 'tip' px de la orilla de su lado. El izquierdo: T(tip - EDGE, y) · S(2).
     * El derecho, en espejo: T(ancho - tip + EDGE, y) · S(-2, 2).
     */
    private static void drawFront(Graphics2D g, BufferedImage img, double tip, double y, boolean mirrored) {
        double x = tip - EDGE;
        if (x + EDGE <= 0) return;
        AffineTransform m = mirrored
                ? new AffineTransform(-2, 0, 0, 2, Config.WIDTH - x, y)
                : new AffineTransform(2, 0, 0, 2, x, y);
        g.drawImage(img, m, null);
    }

    /**
     * Dos ojos blancos en la neblina: dos óvalos con un halo azulado. 'open' los abre (0 a 1):
     * su alto crece con open, como un párpado.
     */
    public void drawEyes(Graphics2D g, double cx, double cy, double open) {
        if (open <= 0.02) return;
        double w = 13, h = 21 * open, gap = 21;
        for (int side = -1; side <= 1; side += 2) {
            double x = cx + side * gap;
            eyeGlow.draw(g, x, cy, 38 * Math.min(1, open * 1.5));
            g.setColor(Ease.fade(EYE, Math.min(1, open * 2.5)));
            g.fill(new Ellipse2D.Double(x - w / 2, cy - h / 2, w, h));
        }
    }

    /** La imagen de un frente: opaca del lado izquierdo y deshecha en bultos hacia la derecha. */
    private static BufferedImage renderFront(Random rnd, Color color, int blobAlpha) {
        BufferedImage img = new BufferedImage(HALF_W, HALF_H, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
        // Base: opaca atrás, y de ahí se desvanece
        g.setPaint(new LinearGradientPaint(0, 0, HALF_W, 0, new float[] { 0f, 0.22f, 0.6f, 1f },
                new Color[] { color, color, clear, clear }));
        g.fillRect(0, 0, HALF_W, HALF_H);
        // Bultos: más hacia atrás (la posición al azar se eleva a 1.5 para amontonarlos ahí)
        Color blob = new Color(color.getRed(), color.getGreen(), color.getBlue(), blobAlpha);
        for (int i = 0; i < 150; i++) {
            double x = HALF_W * (0.12 + 0.76 * Math.pow(rnd.nextDouble(), 1.5));
            double y = rnd.nextDouble() * HALF_H;
            float r = (float) (16 + rnd.nextDouble() * 34);
            if (x + r > HALF_W - 2) r = (float) (HALF_W - 2 - x);   // que ninguno toque la orilla de la imagen
            if (r < 4) continue;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), r, new float[] { 0f, 1f }, new Color[] { blob, clear }));
            g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        }
        g.dispose();
        return img;
    }
}
