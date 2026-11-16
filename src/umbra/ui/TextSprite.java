package umbra.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * [v0.8.6] Un texto ya dibujado en una imagen (con su halo), para la intro.
 *
 * Dibujar letras grandes con suavizado y halo (el mismo texto nueve veces, ver Text.centered) en
 * cada cuadro es lento. Aquí se dibujan una sola vez sobre una imagen transparente; después,
 * aparecer y desvanecerse es copiar esa imagen con una transparencia global:
 *     pantalla = imagen · alpha + pantalla · (1 - transparencia de la imagen · alpha)
 * que es lo que hace AlphaComposite.SRC_OVER con alpha.
 */
final class TextSprite {

    private final BufferedImage image;
    private final int width;      // ancho del texto, sin el margen
    private final int margin;     // margen alrededor del texto, para el halo
    private final int ascent;     // de la orilla de arriba del texto a su línea base

    TextSprite(String text, Font font, Color color, double halo) {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D pg = probe.createGraphics();
        Text.smooth(pg);
        FontMetrics fm = pg.getFontMetrics(font);
        pg.dispose();
        width = fm.stringWidth(text);
        margin = (int) Math.ceil(halo) + 3;
        ascent = fm.getAscent();

        image = new BufferedImage(width + 2 * margin, fm.getAscent() + fm.getDescent() + 2 * margin,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Text.smooth(g);
        g.setFont(font);
        Text.centered(g, text, margin + width / 2.0, margin + ascent, color, 1, halo);
        g.dispose();
    }

    /** Dibuja el texto centrado en cx con la línea base en 'baseline' y transparencia alpha (0 a 1). */
    void draw(Graphics2D g, double cx, double baseline, double alpha) {
        if (alpha <= 0.005) return;
        Composite old = g.getComposite();
        if (alpha < 0.995) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        g.drawImage(image, (int) Math.round(cx - width / 2.0) - margin, (int) Math.round(baseline) - ascent - margin, null);
        g.setComposite(old);
    }
}
