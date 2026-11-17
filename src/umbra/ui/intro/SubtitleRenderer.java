package umbra.ui.intro;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;

/**
 * [v0.8.7] Dibuja el subtitulo activo en coordenadas de pantalla (1280 x 720): un velo oscuro
 * degradado en la franja inferior y el texto centrado, que sube unos pixeles al aparecer.
 */
public final class SubtitleRenderer {

    private static final int W = 1280;
    private static final int VEIL_TOP = 610, VEIL_BOTTOM = 700;
    private static final int MARGIN = 100;           // aire a cada lado antes de partir el texto
    private static final int LINE_H = 30;

    private static final Color VEIL = new Color(4, 6, 14, 135);
    private static final Color VEIL_CLEAR = new Color(4, 6, 14, 0);
    private static final Color INK = new Color(245, 240, 228);
    private static final Color SHADOW = new Color(0, 0, 0, 180);

    private static Font font;
    private static java.awt.image.BufferedImage veilImage;

    private SubtitleRenderer() { }

    /** El velo se pinta una sola vez; luego solo se copia con la opacidad del momento. */
    private static java.awt.image.BufferedImage veil() {
        if (veilImage == null) {
            int h = VEIL_BOTTOM - VEIL_TOP, mid = h / 2;
            veilImage = new java.awt.image.BufferedImage(W, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D v = veilImage.createGraphics();
            v.setPaint(new GradientPaint(0, 0, VEIL_CLEAR, 0, mid, VEIL));
            v.fillRect(0, 0, W, mid);
            v.setPaint(new GradientPaint(0, mid, VEIL, 0, h, VEIL_CLEAR));
            v.fillRect(0, mid, W, h - mid);
            v.dispose();
        }
        return veilImage;
    }

    /** Georgia si existe, si no la serif generica; se crea una sola vez. */
    private static Font font() {
        if (font == null) {
            String name = Font.SERIF;
            for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                if (f.equals("Georgia")) { name = f; break; }
            }
            font = new Font(name, Font.PLAIN, 23);
        }
        return font;
    }

    public static void draw(Graphics2D g, SubtitleTrack track, double audioTime) {
        draw(g, track, audioTime, 1);
    }

    /** [v0.8.7] Igual, con un factor de opacidad extra (0 a 1) que multiplica la del subtítulo. */
    public static void draw(Graphics2D g, SubtitleTrack track, double audioTime, double factor) {
        if (track == null || factor <= 0) return;
        SubtitleTrack.Segment s = track.active(audioTime);
        if (s == null) return;
        double alpha = s.alpha(audioTime) * Math.min(1, factor);
        if (alpha <= 0) return;

        int mid = (VEIL_TOP + VEIL_BOTTOM) / 2;
        // Velo: imagen ya pintada (transparente arriba y abajo, oscura al centro) con la opacidad del subtitulo.
        java.awt.Composite oldComp = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.SrcOver.derive((float) alpha));
        g.drawImage(veil(), 0, VEIL_TOP, null);
        g.setComposite(oldComp);

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font());
        FontMetrics fm = g.getFontMetrics();

        String[] lines = split(s.text, fm, W - 2 * MARGIN);
        float drift = (float) ((1 - s.rise(audioTime)) * 5.0);
        // Bloque centrado verticalmente en el velo.
        float y0 = mid - (lines.length - 1) * LINE_H / 2f + fm.getAscent() / 2f - fm.getDescent() / 2f + drift;
        for (int i = 0; i < lines.length; i++) {
            float x = (W - fm.stringWidth(lines[i])) / 2f;
            float y = y0 + i * LINE_H;
            g.setColor(Ease.fade(SHADOW, alpha));
            g.drawString(lines[i], x, y + 1.5f);
            g.setColor(Ease.fade(INK, alpha));
            g.drawString(lines[i], x, y);
        }
        if (oldAA != null) g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldAA);
    }

    /** Una linea si cabe; si no, dos, partidas en el espacio mas cercano a la mitad. */
    private static String[] split(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return new String[] { text };
        int best = -1;
        for (int i = text.indexOf(' '); i >= 0; i = text.indexOf(' ', i + 1)) {
            if (best < 0 || Math.abs(i - text.length() / 2) < Math.abs(best - text.length() / 2)) best = i;
        }
        if (best < 0) return new String[] { text };
        return new String[] { text.substring(0, best), text.substring(best + 1) };
    }
}
