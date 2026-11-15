package umbra.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ayudantes para el texto de las pantallas: fuentes, renglones, centrado, halo y transparencia. */
final class Text {

    /** TRACKING separa las letras (0.28 = 28% del tamaño de la letra). */
    static final Font TITLE = new Font(Font.SERIF, Font.BOLD, 104)
            .deriveFont(Collections.singletonMap(TextAttribute.TRACKING, 0.28f));
    static final Font STORY = new Font(Font.SERIF, Font.ITALIC, 25);
    static final Font STORY_BIG = new Font(Font.SERIF, Font.ITALIC, 30);
    static final Font CHAPTER = new Font(Font.SERIF, Font.PLAIN, 24);
    static final Font LEVEL_NAME = new Font(Font.SERIF, Font.ITALIC, 52);
    static final Font HINT = new Font(Font.SANS_SERIF, Font.PLAIN, 17);
    static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 14);

    static final Color BRIGHT = new Color(225, 240, 255);
    static final Color STORY_COLOR = new Color(200, 214, 236);
    static final Color HINT_COLOR = new Color(165, 180, 205);

    private Text() { }

    /** Parte un texto en renglones que quepan en maxWidth px, cortando entre palabras. */
    static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> rows = new ArrayList<>();
        String row = "";
        for (String word : text.split(" ")) {
            String candidate = row.isEmpty() ? word : row + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && !row.isEmpty()) {
                rows.add(row);
                row = word;
            } else {
                row = candidate;
            }
        }
        if (!row.isEmpty()) rows.add(row);
        return rows;
    }

    /**
     * Texto centrado en cx (y es la línea base) con transparencia alpha, de 0 a 1.
     * El halo es el mismo texto, muy transparente, repetido alrededor en 8 direcciones.
     */
    static void centered(Graphics2D g, String s, double cx, double y, Color c, double alpha, double halo) {
        if (alpha <= 0.005) return;
        int x = (int) Math.round(cx - g.getFontMetrics().stringWidth(s) / 2.0);
        int yy = (int) Math.round(y);
        if (halo > 0) {
            g.setColor(withAlpha(c, alpha * 0.09));
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                g.drawString(s, (int) Math.round(x + Math.cos(a) * halo), (int) Math.round(yy + Math.sin(a) * halo));
            }
        }
        g.setColor(withAlpha(c, alpha));
        g.drawString(s, x, yy);
    }

    /** Un velo de color sobre toda la pantalla. */
    static void veil(Graphics2D g, Color c, double alpha, int w, int h) {
        g.setColor(withAlpha(c, alpha));
        g.fillRect(0, 0, w, h);
    }

    static Color withAlpha(Color c, double alpha) {
        int a = (int) Math.round(clamp01(alpha) * c.getAlpha());
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
}
