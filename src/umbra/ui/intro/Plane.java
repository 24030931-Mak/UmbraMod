package umbra.ui.intro;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import umbra.math.Mat3;

/**
 * [v0.8.7] Un plano de un escenario de la intro: una imagen ya dibujada (una sola vez) que se
 * pinta en cada cuadro con la vista de su profundidad (IntroCamera.getViewMatrix(depth)).
 *
 * Los planos de adelante son transparentes casi en todo (solo tienen rocas en las orillas), y
 * pintar un píxel transparente cuesta casi lo mismo que uno lleno. Por eso un plano
 * transparente se guarda en pedazos: la imagen se corta en franjas horizontales y de cada franja
 * se guardan solo los tramos de columnas que tienen algo (los huecos anchos se saltan). Lo que es
 * roca sólida de lado a lado de la franja se guarda aparte, sin transparencia: pintarlo es
 * copiar, no mezclar, y en software cuesta la cuarta parte.
 *
 * Los pedazos se tocan sin encimarse. Cada uno guarda además una orilla de SKIRT píxeles de sus
 * vecinos, que no se pinta pero sí se lee: el muestreo bilineal promedia cada píxel con los de
 * al lado, y en la costura entre dos pedazos esos vecinos son los de la imagen entera. Así,
 * juntos, se ven igual que la imagen entera, sin costuras.
 */
final class Plane {

    /** Alto de cada franja y hueco mínimo que se salta, en píxeles de la imagen. */
    private static final int BAND = 96, GAP = 64;
    /** Ancho mínimo de un pedazo sólido (sin transparencia), en píxeles de la imagen. */
    private static final int MIN_SOLID = 64;
    /** Píxeles de los vecinos que guarda cada pedazo alrededor (solo para leerlos). */
    private static final int SKIRT = 2;

    /** Un pedazo: su imagen (con la orilla), dónde empieza lo suyo dentro de ella, su tamaño y su esquina en el mundo. */
    private static final class Piece {
        final BufferedImage image;
        final int sx, sy, w, h;
        final double x, y;

        Piece(BufferedImage image, int sx, int sy, int w, int h, double x, double y) {
            this.image = image;
            this.sx = sx;
            this.sy = sy;
            this.w = w;
            this.h = h;
            this.x = x;
            this.y = y;
        }
    }

    private final List<Piece> pieces = new ArrayList<>();
    private final double pixelsPerUnit;

    private Plane(double pixelsPerUnit) {
        this.pixelsPerUnit = pixelsPerUnit;
    }

    /** Un plano opaco (el del fondo): la imagen entera, con su esquina en (x, y) del mundo. */
    static Plane whole(BufferedImage image, double x, double y, double pixelsPerUnit) {
        Plane p = new Plane(pixelsPerUnit);
        p.pieces.add(new Piece(image, 0, 0, image.getWidth(), image.getHeight(), x, y));
        return p;
    }

    /**
     * Un plano transparente (TYPE_INT_ARGB_PRE), recortado a lo que tiene dibujado. Un píxel está
     * vacío si su transparencia (los 8 bits de arriba) es 0, y es sólido si es 255.
     */
    static Plane cropped(BufferedImage image, double x, double y, double pixelsPerUnit) {
        Plane p = new Plane(pixelsPerUnit);
        int w = image.getWidth(), h = image.getHeight();
        // Los píxeles, fila tras fila (la imagen es nueva: cada fila mide justo w enteros)
        int[] px = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int y0 = 0; y0 < h; y0 += BAND) {
            int bh = Math.min(BAND, h - y0);
            // Columnas con algo y columnas sólidas, en la franja y en su orilla de arriba y de abajo
            // (con bilineal, lo de la franja vecina se "derrama" medio píxel sobre esta)
            boolean[] used = new boolean[w], solid = new boolean[w];
            Arrays.fill(solid, true);
            for (int j = Math.max(0, y0 - SKIRT); j < Math.min(h, y0 + bh + SKIRT); j++) {
                for (int i = 0, k = j * w; i < w; i++, k++) {
                    int a = px[k] >>> 24;
                    if (a != 0) used[i] = true;
                    if (a != 255) solid[i] = false;
                }
            }
            // Tramos de columnas con algo; dos tramos separados por menos de GAP se juntan
            int i = 0;
            while (i < w) {
                while (i < w && !used[i]) i++;
                if (i >= w) break;
                int start = i, end = i, empty = 0;
                for (; i < w && empty < GAP; i++) {
                    if (used[i]) { end = i; empty = 0; } else empty++;
                }
                int x0 = Math.max(0, start - SKIRT), x1 = Math.min(w, end + 1 + SKIRT);   // y lo que se derrama a los lados
                // Dentro del tramo, los pedazos sólidos (con su orilla también sólida)
                int from = x0;
                for (int k = x0; k < x1; ) {
                    if (!solid[k]) { k++; continue; }
                    int s0 = k;
                    while (k < x1 && solid[k]) k++;
                    int a = s0 + SKIRT, b = k - SKIRT;
                    if (b - a < MIN_SOLID) continue;
                    if (a > from) p.add(px, w, h, from, y0, a - from, bh, false, x, y);
                    p.add(px, w, h, a, y0, b - a, bh, true, x, y);
                    from = b;
                }
                if (x1 > from) p.add(px, w, h, from, y0, x1 - from, bh, false, x, y);
            }
        }
        return p;
    }

    /**
     * Copia el pedazo (x0, y0, w, h) de la imagen (sus píxeles px, de iw x ih), con su orilla.
     * Se copian los enteros tal cual (sin pasar por colores sin premultiplicar, que redondearía);
     * a uno sólido se le quita la transparencia.
     */
    private void add(int[] px, int iw, int ih, int x0, int y0, int w, int h, boolean opaque, double x, double y) {
        int ax = Math.max(0, x0 - SKIRT), ay = Math.max(0, y0 - SKIRT);
        int bx = Math.min(iw, x0 + w + SKIRT), by = Math.min(ih, y0 + h + SKIRT);
        int[] data = new int[(bx - ax) * (by - ay)];
        for (int j = ay; j < by; j++) System.arraycopy(px, j * iw + ax, data, (j - ay) * (bx - ax), bx - ax);
        if (opaque) for (int k = 0; k < data.length; k++) data[k] &= 0xFFFFFF;
        // (con setDataElements y no escribiendo en su arreglo: así Java2D la puede guardar en la tarjeta de video)
        BufferedImage piece = new BufferedImage(bx - ax, by - ay, opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB_PRE);
        piece.getRaster().setDataElements(0, 0, bx - ax, by - ay, data);
        pieces.add(new Piece(piece, x0 - ax, y0 - ay, w, h, x + x0 / pixelsPerUnit, y + y0 / pixelsPerUnit));
    }

    /** Pinta el plano con la vista 'view' (la de su profundidad): cada pedazo, sin su orilla. */
    void draw(Graphics2D g, Mat3 view) {
        for (Piece p : pieces) Scene.drawImage(g, p.image, view, p.x, p.y, pixelsPerUnit, p.sx, p.sy, p.w, p.h);
    }

    /** Cuántos pedazos tiene (para las pruebas). */
    int pieceCount() {
        return pieces.size();
    }
}
