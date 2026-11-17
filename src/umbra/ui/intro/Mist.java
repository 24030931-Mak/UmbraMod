package umbra.ui.intro;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
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
 * después solo se mueve. Aquí la imagen es el frente de un banco de neblina: adelante lleva una
 * bruma azulada, deshecha en bultos, y detrás viene la oscuridad casi negra, que se vuelve
 * opaca porque sus manchas se amontonan unas sobre otras.
 *
 * Hay dos bancos, uno desde cada lado de la pantalla. Cada uno se mueve con una traslación,
 * T(frente(cover), vaivén(tiempo)). El derecho se dibuja en espejo (su rectángulo destino va de
 * derecha a izquierda, que equivale a una escala S(-1, 1)) y con una imagen distinta, para que
 * no se note la simetría.
 *
 * Detrás del frente todo es negro liso: ahí se rellena un rectángulo, que es mucho más barato
 * que una imagen con transparencia.
 */
public final class Mist {

    /** Ancho en pantalla del frente (px). La imagen se guarda a la mitad y se dibuja al doble. */
    private static final int EDGE = 600;
    private static final int HALF_W = EDGE / 2, HALF_H = (Config.HEIGHT + 80) / 2;
    /** Qué parte del frente, desde atrás, ya es oscuridad opaca. */
    private static final double SOLID = 0.2;

    private static final Color DARK = new Color(5, 6, 14);
    private static final Color HAZE = new Color(44, 54, 92);

    private final BufferedImage[] front = new BufferedImage[2];   // uno por lado

    public Mist(long seed) {
        Random rnd = new Random(seed);
        for (int side = 0; side < 2; side++) front[side] = renderFront(rnd);
    }

    /**
     * Dibuja los dos bancos. 'cover' va de 0 (todavía fuera de la pantalla) a 1 (ya se juntaron
     * en el centro y lo taparon todo); la punta de cada frente está en una interpolación lineal
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

        // Hasta dónde llega la punta del frente, medido desde la orilla de su lado de la pantalla.
        // Al final (cover = 1) la parte opaca de los dos frentes ya pasó del centro.
        double tip = Ease.lerp(-20, Config.WIDTH / 2.0 + (1 - SOLID + 0.05) * EDGE, cover);
        g.setColor(DARK);
        for (int side = 0; side < 2; side++) {
            int x0 = (int) Math.round(tip - EDGE);   // de la orilla hasta aquí ya es negro liso
            int x1 = x0 + EDGE;
            int top = (int) Math.round(-40 + 14 * Math.sin(time * 0.55 + side * 1.9));
            if (x1 <= 0) continue;
            if (side == 0) {
                if (x0 > 0) g.fillRect(0, 0, x0, Config.HEIGHT);
                g.drawImage(front[side], x0, top, x1, top + 2 * HALF_H, 0, 0, HALF_W, HALF_H, null);
            } else {   // en espejo: el rectángulo destino va de derecha a izquierda
                if (x0 > 0) g.fillRect(Config.WIDTH - x0, 0, x0, Config.HEIGHT);
                g.drawImage(front[side], Config.WIDTH - x0, top, Config.WIDTH - x1, top + 2 * HALF_H,
                        0, 0, HALF_W, HALF_H, null);
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, quality);
    }

    /**
     * La imagen de un frente (a la mitad de su tamaño en pantalla). De atrás (izquierda) hacia
     * adelante: oscuridad opaca, oscuridad en bultos y, al frente, bruma azulada. Primero se
     * pinta la bruma y encima la oscuridad, para que la tape por detrás.
     */
    private static BufferedImage renderFront(Random rnd) {
        BufferedImage img = new BufferedImage(HALF_W, HALF_H, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        blobs(g, rnd, HAZE, 54, 130, 0.3, 0.97, 1);
        Color clear = new Color(DARK.getRed(), DARK.getGreen(), DARK.getBlue(), 0);
        g.setPaint(new LinearGradientPaint(0, 0, HALF_W, 0, new float[] { 0f, (float) SOLID, 0.5f, 1f },
                new Color[] { DARK, DARK, clear, clear }));
        g.fillRect(0, 0, HALF_W, HALF_H);
        blobs(g, rnd, DARK, 120, 150, 0.1, 0.72, 1.5);
        g.dispose();
        return img;
    }

    /**
     * 'count' manchas (degradados radiales) entre las fracciones 'from' y 'to' del ancho. La
     * posición al azar se eleva a 'bias': con más de 1 las manchas se amontonan hacia atrás.
     */
    private static void blobs(Graphics2D g, Random rnd, Color color, int alpha, int count,
                              double from, double to, double bias) {
        Color solid = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
        for (int i = 0; i < count; i++) {
            double x = HALF_W * (from + (to - from) * Math.pow(rnd.nextDouble(), bias));
            double y = rnd.nextDouble() * HALF_H;
            float r = (float) (16 + rnd.nextDouble() * 34);
            if (x + r > HALF_W - 2) r = (float) (HALF_W - 2 - x);   // que ninguna toque la orilla de la imagen
            if (r < 4) continue;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), r, new float[] { 0f, 1f }, new Color[] { solid, clear }));
            g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        }
    }
}
