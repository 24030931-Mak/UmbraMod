package umbra.ui.intro;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * [v0.8.6] Brillo aditivo: una mancha de luz ya calculada, lista para dibujarse muchas veces.
 *
 * La luz se suma. Cada mancha es la suma de dos campanas de Gauss, un núcleo chico e intenso y
 * un halo ancho y tenue:
 *     I(d) = núcleo · e^(-(d / 0.18)²) + halo · e^(-(d / 0.5)²)        (d = distancia al centro, de 0 a 1)
 * y cada canal del color se multiplica por I y se recorta en 255:
 *     canal = min(255, color · I(d))
 * Como los tres canales no se saturan a la misma distancia, el centro queda blanco (los tres
 * llegaron a 255) y hacia afuera va tomando el color de la luz: así se ve algo incandescente.
 *
 * La imagen se guarda con el color premultiplicado por la transparencia, y la transparencia es
 * el canal más brillante: alfa = max(r, g, b). Al dibujarla, Java2D calcula
 *     pantalla = mancha + pantalla · (1 - alfa)
 * que donde la mancha es tenue (alfa cerca de 0) es casi pantalla + mancha: una suma de luz.
 * Todo este cálculo se hace una sola vez al crearla; dibujarla después es copiar una imagen.
 */
public final class Glow {

    private final BufferedImage image;

    /**
     * @param size  lado de la imagen en píxeles (la mancha ocupa todo el cuadro)
     * @param color color de la luz
     * @param core  intensidad del núcleo (más de 1 lo vuelve blanco)
     * @param halo  intensidad del halo
     */
    public Glow(int size, Color color, double core, double halo) {
        image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
        int[] pixels = new int[size * size];
        double c = (size - 1) / 2.0;
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                double d = Math.hypot(px - c, py - c) / (size / 2.0);
                // La orilla se lleva a cero exacto (1 - d²) para que no se note el cuadro de la imagen
                double edge = d >= 1 ? 0 : 1 - d * d;
                double i = (core * Math.exp(-(d / 0.18) * (d / 0.18)) + halo * Math.exp(-(d / 0.5) * (d / 0.5))) * edge;
                int r = (int) Math.min(255, color.getRed() * i);
                int gr = (int) Math.min(255, color.getGreen() * i);
                int b = (int) Math.min(255, color.getBlue() * i);
                int a = Math.max(r, Math.max(gr, b));
                pixels[py * size + px] = (a << 24) | (r << 16) | (gr << 8) | b;
            }
        }
        // Se escribe directo en los píxeles de la imagen (setRGB esperaría color sin premultiplicar)
        image.getRaster().setDataElements(0, 0, size, size, pixels);
    }

    /** Dibuja la mancha con centro en (cx, cy) y este radio, en las coordenadas actuales de g. */
    public void draw(Graphics2D g, double cx, double cy, double radius) {
        if (radius < 0.5) return;
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object quality = g.getRenderingHint(RenderingHints.KEY_RENDERING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        double k = radius * 2 / image.getWidth();
        g.drawImage(image, new AffineTransform(k, 0, 0, k, cx - radius, cy - radius), null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, quality);
    }
}
