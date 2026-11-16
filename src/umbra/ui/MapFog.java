package umbra.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.HashMap;
import java.util.Map;

import umbra.world.Exploration;

/**
 * La neblina de los mapas: cubre del todo las zonas que Candil no ha recorrido, y la usan el
 * mapa grande (Tab) y el círculo de la esquina, así que no se duplica.
 *
 * Máscara bilineal: cada zona (de CELL x CELL px) vale "lit" = 1 si ya se recorrió y 0 si no.
 * Para que las orillas salgan suaves, la opacidad de cada punto del nivel es
 *     alfa = 1 - lit(u, v),   lit(u, v) = interpolación bilineal de las 4 zonas más cercanas,
 * donde (u, v) = (x / CELL - 0.5, y / CELL - 0.5) es la posición del punto medida en zonas,
 * con los centros de zona en enteros. Lejos de lo recorrido las cuatro zonas valen 0 y
 * el alfa es 1: opaca, sin ver nada debajo.
 *
 * La textura es ruido de valor, calculado una sola vez y repetible en los dos ejes (como un
 * mosaico): en cada cuadro solo se desplaza con el tiempo, así que la neblina se mueve despacio.
 * El cálculo se hace en una imagen chica (K x K píxeles por zona) que luego se estira
 * con interpolación bilineal: nunca se crean imágenes grandes.
 */
final class MapFog {

    /** Píxeles de la imagen de neblina por zona, en cada eje. */
    private static final int K = 4;
    /** Píxeles de margen alrededor de la imagen de neblina: la interpolación bilineal mezcla la orilla
     *  con lo transparente de afuera, y sin margen el borde del nivel quedaría translúcido. */
    private static final int PAD = 1;
    /** Lado (px del mundo) que tarda en repetirse la textura, y su resolución. */
    private static final double PERIOD = 640;
    private static final int TEX = 64;
    /** Velocidad a la que se desliza la neblina (px del mundo por segundo). */
    private static final double DRIFT_X = 14, DRIFT_Y = 5;

    private static final int FOG_DARK = rgb(8, 11, 20), FOG_LIGHT = rgb(34, 42, 64);
    private static final Color GLOW = new Color(255, 200, 110);
    private static final int GLOW_ALPHA = 95;

    private static final float[] NOISE = makeNoise();
    /** Imágenes de trabajo, una por tamaño de cuadrícula (se dibujan al instante, así que se reusan). */
    private static final Map<Long, BufferedImage> FOG_IMAGES = new HashMap<>();
    private static final Map<Long, BufferedImage> GLOW_IMAGES = new HashMap<>();

    private MapFog() { }

    /** Neblina sobre un nivel donde todo lo revelado cuenta como encendido. */
    static void paint(Graphics2D g, Exploration ex, double time) {
        paint(g, ex, time, 1);
    }

    /**
     * En coordenadas del nivel (quien llama pone la transformación y el recorte). 'light'
     * (0 a 1) es qué tan encendidas están las zonas reveladas que aún no se mostraban.
     */
    static void paint(Graphics2D g, Exploration ex, double time, double light) {
        int cols = ex.cols(), rows = ex.rows();
        float[] lit = litZones(ex, light);
        BufferedImage img = scratch(FOG_IMAGES, cols * K + 2 * PAD, rows * K + 2 * PAD);
        int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        int w = cols * K + 2 * PAD, h = rows * K + 2 * PAD;
        double shiftX = time * DRIFT_X, shiftY = time * DRIFT_Y;
        for (int y = 0; y < h; y++) {
            double wy = (y - PAD + 0.5) * Exploration.CELL / K;   // el margen cae fuera del nivel
            double v = wy / Exploration.CELL - 0.5;
            for (int x = 0; x < w; x++) {
                double wx = (x - PAD + 0.5) * Exploration.CELL / K;
                double u = wx / Exploration.CELL - 0.5;
                int a = (int) Math.round(255 * (1 - bilinear(lit, cols, rows, u, v)));
                double n = noise((wx + shiftX) / PERIOD, (wy + shiftY) / PERIOD);
                px[y * w + x] = (a << 24) | mix(FOG_DARK, FOG_LIGHT, n);
            }
        }
        double pad = (double) PAD * Exploration.CELL / K;
        drawStretched(g, img, -pad, -pad, cols * Exploration.CELL + 2 * pad, rows * Exploration.CELL + 2 * pad);
    }

    /** El brillo cálido sobre lo recorrido: la misma máscara, pero encendida donde la neblina se retira. */
    static void glow(Graphics2D g, Exploration ex, double light) {
        int cols = ex.cols(), rows = ex.rows();
        BufferedImage img = scratch(GLOW_IMAGES, cols, rows);
        int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        float[] lit = litZones(ex, light);
        int rgb = GLOW.getRGB() & 0xFFFFFF;
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                px[r * cols + c] = (Math.round(GLOW_ALPHA * lit[c * rows + r]) << 24) | rgb;
            }
        }
        drawStretched(g, img, 0, 0, cols * Exploration.CELL, rows * Exploration.CELL);
    }

    /** Qué tan encendida está cada zona: 1 si ya se mostró, 'light' si es nueva, 0 si no se ha recorrido. */
    private static float[] litZones(Exploration ex, double light) {
        float[] lit = new float[ex.cols() * ex.rows()];
        for (int c = 0; c < ex.cols(); c++) {
            for (int r = 0; r < ex.rows(); r++) {
                lit[c * ex.rows() + r] = ex.isShown(c, r) ? 1 : ex.isRevealed(c, r) ? (float) light : 0;
            }
        }
        return lit;
    }

    /** Interpolación bilineal de las 4 zonas alrededor de (u, v); fuera de la cuadrícula repite la orilla. */
    private static double bilinear(float[] lit, int cols, int rows, double u, double v) {
        int c0 = (int) Math.floor(u), r0 = (int) Math.floor(v);
        double fu = u - c0, fv = v - r0;
        double top = zone(lit, cols, rows, c0, r0) * (1 - fu) + zone(lit, cols, rows, c0 + 1, r0) * fu;
        double bottom = zone(lit, cols, rows, c0, r0 + 1) * (1 - fu) + zone(lit, cols, rows, c0 + 1, r0 + 1) * fu;
        return top * (1 - fv) + bottom * fv;
    }

    private static float zone(float[] lit, int cols, int rows, int c, int r) {
        c = Math.max(0, Math.min(cols - 1, c));
        r = Math.max(0, Math.min(rows - 1, r));
        return lit[c * rows + r];
    }

    /** Estira la imagen sobre el rectángulo (x, y, w, h) del nivel con interpolación bilineal. */
    private static void drawStretched(Graphics2D g, BufferedImage img, double x, double y, double w, double h) {
        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                old != null ? old : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }

    private static BufferedImage scratch(Map<Long, BufferedImage> cache, int width, int height) {
        long key = ((long) width << 32) | height;
        BufferedImage img = cache.get(key);
        if (img == null) {
            img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            cache.put(key, img);
        }
        return img;
    }

    // ---------------------------------------------------------
    //  Textura: ruido de valor repetible
    // ---------------------------------------------------------

    /** Dos octavas de ruido de valor (retículas de 8 y 16 celdas) en una imagen TEX x TEX que se repite. */
    private static float[] makeNoise() {
        java.util.Random rnd = new java.util.Random(7);
        int[] cells = {8, 16};
        float[] weights = {0.65f, 0.35f};
        float[] out = new float[TEX * TEX];
        for (int o = 0; o < cells.length; o++) {
            int n = cells[o];
            float[] lattice = new float[n * n];
            for (int i = 0; i < lattice.length; i++) lattice[i] = rnd.nextFloat();
            for (int y = 0; y < TEX; y++) {
                for (int x = 0; x < TEX; x++) {
                    double u = (double) x / TEX * n, v = (double) y / TEX * n;
                    int c = (int) u, r = (int) v;
                    double fu = smooth(u - c), fv = smooth(v - r);
                    int c1 = (c + 1) % n, r1 = (r + 1) % n;   // el módulo hace que se repita
                    double top = lattice[r * n + c] * (1 - fu) + lattice[r * n + c1] * fu;
                    double bottom = lattice[r1 * n + c] * (1 - fu) + lattice[r1 * n + c1] * fu;
                    out[y * TEX + x] += weights[o] * (float) (top * (1 - fv) + bottom * fv);
                }
            }
        }
        return out;
    }

    /** Ruido en (s, t) medidos en periodos (se repite cada 1), con interpolación bilineal entre texeles. */
    private static double noise(double s, double t) {
        // Con s negativo y diminuto, s - floor(s) se redondea a exactamente 1.0 y el texel sería TEX:
        // se limita a TEX - 1 (el valor se acerca a 1 periodo, que es igual al 0 por ser repetible).
        double x = (s - Math.floor(s)) * TEX, y = (t - Math.floor(t)) * TEX;
        int c = Math.min(TEX - 1, (int) x), r = Math.min(TEX - 1, (int) y);
        double fx = Math.min(1, x - c), fy = Math.min(1, y - r);
        int c1 = (c + 1) % TEX, r1 = (r + 1) % TEX;
        double top = NOISE[r * TEX + c] * (1 - fx) + NOISE[r * TEX + c1] * fx;
        double bottom = NOISE[r1 * TEX + c] * (1 - fx) + NOISE[r1 * TEX + c1] * fx;
        return Math.max(0, Math.min(1, top * (1 - fy) + bottom * fy));
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    private static int rgb(int r, int g, int b) { return (r << 16) | (g << 8) | b; }

    /** Mezcla lineal de dos colores 0xRRGGBB. */
    private static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
