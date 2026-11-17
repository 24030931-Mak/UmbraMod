package umbra.ui.intro;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
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
 *
 * [v0.8.7] La oscuridad también baja del techo y se cierra desde las esquinas:
 * - Un tercer banco, igual a los de los lados pero girado (la imagen se pinta con x e y
 *   intercambiadas), baja desde el borde de arriba con cover^1.35 y tapa el techo, las
 *   estalactitas y las cadenas del Farol.
 * - Una viñeta: una imagen hecha una sola vez con la oscuridad que crece con la distancia al
 *   centro (elíptica, del tamaño de la pantalla) y deshecha en bultos como los bancos. Para
 *   cerrarla solo se escala, S(k), alrededor del Farol apagado, con k bajando de forma
 *   geométrica. Se dibuja en franjas horizontales: en cada una, el hueco transparente del centro
 *   no se toca, lo que ya es opaco (y lo que queda fuera de la imagen) se rellena con
 *   rectángulos y solo el borde intermedio se mezcla con la imagen.
 */
public final class Mist {

    /** Ancho en pantalla del frente (px). La imagen se guarda a la mitad y se dibuja al doble. */
    private static final int EDGE = 600;
    private static final int HALF_W = EDGE / 2, HALF_H = (Config.HEIGHT + 80) / 2;
    /** Qué parte del frente, desde atrás, ya es oscuridad opaca. */
    private static final double SOLID = 0.2;

    private static final Color DARK = new Color(5, 6, 14);
    private static final Color HAZE = new Color(44, 54, 92);

    // ---- El banco de arriba (en pantalla: todo el ancho y un poco más, para su vaivén) ----
    private static final int TOP_EDGE = 360;                         // alto en pantalla del frente
    private static final int TOP_D = TOP_EDGE / 2, TOP_A = (Config.WIDTH + 160) / 2;
    private static final double TOP_END = 470;                       // dónde queda la punta con cover = 1

    // ---- La viñeta ----
    /** Centro del Farol apagado en pantalla, hacia donde se cierra. */
    private static final double CENTER_X = Config.WIDTH / 2.0, CENTER_Y = 350;
    /** Semiejes de la elipse unidad (px de pantalla con k = 1): los de la pantalla. */
    private static final double AXIS_X = Config.WIDTH / 2.0, AXIS_Y = Config.HEIGHT / 2.0;
    /** Medida de la imagen, en esas unidades, desde el centro; se guarda a la mitad. */
    private static final double V_EXTENT = 1.4;
    private static final int VW = 2 * (int) Math.round(V_EXTENT * AXIS_X / 2),
            VH = 2 * (int) Math.round(V_EXTENT * AXIS_Y / 2);
    /** Distancias (elípticas) donde la viñeta empieza a oscurecer y donde ya es opaca. */
    private static final double V_IN = 0.8, V_OUT = 1.25;
    /** Escala al empezar (el borde apenas asoma en las esquinas) y al terminar. */
    private static final double K_START = 1.8, K_END = 0.35;

    private final BufferedImage[] front = new BufferedImage[2];   // uno por lado
    private final BufferedImage top, vignette;
    /**
     * La viñeta, partida en franjas horizontales. En cada franja (filas band[i] a band[i + 1] de
     * la imagen) las columnas [0, solid0) y [solid1, VW) son oscuridad opaca, [hole0, hole1) es
     * transparente y solo el resto tiene que mezclarse.
     */
    private static final int BANDS = 24;
    private final int[] band = new int[BANDS + 1];
    private final int[] solid0 = new int[BANDS], solid1 = new int[BANDS];
    private final int[] hole0 = new int[BANDS], hole1 = new int[BANDS];

    public Mist(long seed) {
        Random rnd = new Random(seed);
        for (int side = 0; side < 2; side++) front[side] = renderFront(rnd, HALF_W, HALF_H, false);
        top = renderFront(rnd, TOP_D, TOP_A, true);
        vignette = renderVignette(rnd);
        splitVignette();
    }

    /** Mide, franja por franja, dónde la viñeta es opaca y dónde transparente. */
    private void splitVignette() {
        int[] px = new int[VW * VH];
        vignette.getRGB(0, 0, VW, VH, px, 0, VW);
        int[] alphaMin = new int[VW], alphaMax = new int[VW];   // de cada columna, dentro de la franja
        for (int i = 0; i < BANDS; i++) {
            band[i] = VH * i / BANDS;
            band[i + 1] = VH * (i + 1) / BANDS;
            Arrays.fill(alphaMin, 255);
            Arrays.fill(alphaMax, 0);
            for (int y = band[i]; y < band[i + 1]; y++) {
                for (int x = 0; x < VW; x++) {
                    int a = px[y * VW + x] >>> 24;
                    alphaMin[x] = Math.min(alphaMin[x], a);
                    alphaMax[x] = Math.max(alphaMax[x], a);
                }
            }
            int s0 = 0, s1 = VW;
            while (s0 < VW && alphaMin[s0] == 255) s0++;
            while (s1 > s0 && alphaMin[s1 - 1] == 255) s1--;
            int h0 = VW / 2, h1 = VW / 2;   // el hueco crece desde el centro mientras sea transparente
            while (h0 > s0 && alphaMax[h0 - 1] == 0) h0--;
            while (h1 < s1 && alphaMax[h1] == 0) h1++;
            if (h0 == h1) h0 = h1 = s0;   // sin hueco: una sola parte que mezclar
            solid0[i] = s0;
            solid1[i] = s1;
            hole0[i] = h0;
            hole1[i] = h1;
        }
    }

    /**
     * Dibuja los tres bancos y la viñeta. 'cover' va de 0 (todavía fuera de la pantalla) a 1
     * (ya se juntaron en el centro y lo taparon todo); la punta de cada frente está en una
     * interpolación lineal entre esas dos posiciones.
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
        // El banco de arriba baja con cover^1.35: arranca más tarde que los de los lados.
        double topTip = Ease.lerp(-20, TOP_END, Math.pow(cover, 1.35));
        // El banco de arriba y la viñeta se recortan al hueco que los bancos todavía dejan abierto
        // (fuera de él, la parte SOLID ya es negra), para no pagar píxeles que no cambian nada.
        // Al final, la bruma del banco contrario pasa del centro y cae fuera del hueco: queda
        // como en v0.8.6, así que el último tramo de la escena (y su paso a la 4) no cambia;
        // para entonces la viñeta ya está tan cerrada que el corte cae en su centro transparente.
        int open = Math.max(0, Math.min(Config.WIDTH / 2, (int) (tip - (1 - SOLID) * EDGE) - 2));
        int openTop = Math.max(0, Math.min(Config.HEIGHT, (int) (topTip - (1 - SOLID) * TOP_EDGE) - 2));
        Shape clip = g.getClip();
        g.clipRect(open, 0, Config.WIDTH - 2 * open, Config.HEIGHT);
        drawTop(g, topTip, time);
        g.setClip(clip);
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
        g.clipRect(open, openTop, Config.WIDTH - 2 * open, Config.HEIGHT - openTop);
        drawVignette(g, cover);
        g.setClip(clip);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, quality);
    }

    /** El banco de arriba, con su punta en 'tip' (px desde el borde de arriba). */
    private void drawTop(Graphics2D g, double tip, double time) {
        int y0 = (int) Math.round(tip - TOP_EDGE);   // de arriba hasta aquí ya es negro liso
        int x0 = (int) Math.round(-80 + 60 * Math.sin(time * 0.4 + 0.7));
        if (y0 > 0) g.fillRect(0, 0, Config.WIDTH, y0);
        g.drawImage(top, x0, y0, x0 + 2 * TOP_A, y0 + TOP_EDGE, 0, 0, TOP_A, TOP_D, null);
    }

    /**
     * La viñeta, escalada por k alrededor del Farol: la columna sx de la imagen cae en
     * x = CENTER_X + (sx - VW/2) * 2k (y lo mismo en vertical). Todas las partes comparten esa
     * cuenta, así que sus bordes coinciden sin rendijas ni encimes. Lo opaco se rellena (mucho
     * más barato que mezclar), el hueco no se toca y solo el borde se dibuja con la imagen.
     */
    private void drawVignette(Graphics2D g, double cover) {
        double s = 2 * K_START * Math.pow(K_END / K_START, cover);
        int left = col(0, s), right = col(VW, s), top = row(0, s), bottom = row(VH, s);
        // Fuera de la imagen todo es oscuridad opaca
        if (top > 0) g.fillRect(0, 0, Config.WIDTH, top);
        if (bottom < Config.HEIGHT) g.fillRect(0, bottom, Config.WIDTH, Config.HEIGHT - bottom);
        for (int i = 0; i < BANDS; i++) {
            int y0 = row(band[i], s), y1 = row(band[i + 1], s);
            if (y1 <= 0 || y0 >= Config.HEIGHT || y1 <= y0) continue;
            int x0 = col(solid0[i], s), x1 = col(solid1[i], s);
            if (x0 > 0) g.fillRect(0, y0, x0, y1 - y0);   // lo opaco de la izquierda, y lo que hay antes de la imagen
            if (x1 < Config.WIDTH) g.fillRect(x1, y0, Config.WIDTH - x1, y1 - y0);
            part(g, y0, y1, band[i], band[i + 1], solid0[i], hole0[i], s);
            part(g, y0, y1, band[i], band[i + 1], hole1[i], solid1[i], s);
        }
    }

    private static int col(int sx, double s) { return (int) Math.round(CENTER_X + (sx - VW / 2) * s); }

    private static int row(int sy, double s) { return (int) Math.round(CENTER_Y + (sy - VH / 2) * s); }

    /** Las columnas [sx0, sx1) de una franja; si caen fuera de la pantalla (o no miden nada) no se dibujan. */
    private void part(Graphics2D g, int dy0, int dy1, int sy0, int sy1, int sx0, int sx1, double s) {
        if (sx1 <= sx0) return;
        int dx0 = col(sx0, s), dx1 = col(sx1, s);
        if (dx1 <= dx0 || dx1 <= 0 || dx0 >= Config.WIDTH) return;
        g.drawImage(vignette, dx0, dy0, dx1, dy1, sx0, sy0, sx1, sy1, null);
    }

    /**
     * La imagen de un frente (a la mitad de su tamaño en pantalla), de 'depth' de hondo (de atrás
     * hacia la punta) por 'across' de ancho. De atrás hacia adelante: oscuridad opaca, oscuridad
     * en bultos y, al frente, bruma azulada. Primero se pinta la bruma y encima la oscuridad,
     * para que la tape por detrás. Con 'down' la imagen queda girada (x e y intercambiadas, una
     * reflexión sobre la diagonal): el frente avanza hacia abajo.
     */
    private static BufferedImage renderFront(Random rnd, int depth, int across, boolean down) {
        BufferedImage img = down ? new BufferedImage(across, depth, BufferedImage.TYPE_INT_ARGB_PRE)
                : new BufferedImage(depth, across, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        if (down) g.transform(new AffineTransform(0, 1, 1, 0, 0, 0));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double area = depth * (double) across / (HALF_W * HALF_H);   // las manchas, según el tamaño
        blobs(g, rnd, HAZE, 54, (int) Math.round(130 * area), 0.3, 0.97, 1, depth, across);
        Color clear = new Color(DARK.getRed(), DARK.getGreen(), DARK.getBlue(), 0);
        g.setPaint(new LinearGradientPaint(0, 0, depth, 0, new float[] { 0f, (float) SOLID, 0.5f, 1f },
                new Color[] { DARK, DARK, clear, clear }));
        g.fillRect(0, 0, depth, across);
        blobs(g, rnd, DARK, 120, (int) Math.round(150 * area), 0.1, 0.72, 1.5, depth, across);
        g.dispose();
        return img;
    }

    /**
     * 'count' manchas (degradados radiales) entre las fracciones 'from' y 'to' de 'depth'. La
     * posición al azar se eleva a 'bias': con más de 1 las manchas se amontonan hacia atrás.
     */
    private static void blobs(Graphics2D g, Random rnd, Color color, int alpha, int count,
                              double from, double to, double bias, int depth, int across) {
        Color solid = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
        for (int i = 0; i < count; i++) {
            double x = depth * (from + (to - from) * Math.pow(rnd.nextDouble(), bias));
            double y = rnd.nextDouble() * across;
            float r = (float) (16 + rnd.nextDouble() * 34);
            if (x + r > depth - 2) r = (float) (depth - 2 - x);   // que ninguna toque la orilla de la imagen
            if (r < 4) continue;
            blob(g, x, y, r, solid, clear);
        }
    }

    private static void blob(Graphics2D g, double x, double y, float r, Color solid, Color clear) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), r, new float[] { 0f, 1f }, new Color[] { solid, clear }));
        g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
    }

    /**
     * La imagen de la viñeta (a la mitad de su tamaño en pantalla con k = 1). La oscuridad base
     * sube con la distancia elíptica d al centro: smoothstep(V_IN, V_OUT, d). Encima, bultos de
     * oscuridad, como en los bancos, para que el borde no sea un degradado liso. Es solo
     * oscuridad (sin bruma): sobre lo que ya es negro no cambia nada, y por eso se puede recortar
     * donde los bancos ya son opacos sin que se note el corte.
     */
    private static BufferedImage renderVignette(Random rnd) {
        BufferedImage img = new BufferedImage(VW, VH, BufferedImage.TYPE_INT_ARGB_PRE);
        double ax = AXIS_X / 2, ay = AXIS_Y / 2;   // la elipse unidad, en px de la imagen
        int[] px = new int[VW * VH];
        for (int y = 0; y < VH; y++) {
            for (int x = 0; x < VW; x++) {
                double dx = (x + 0.5 - VW / 2) / ax, dy = (y + 0.5 - VH / 2) / ay;
                int a = (int) Math.round(255 * Ease.smooth(Ease.ramp(Math.sqrt(dx * dx + dy * dy), V_IN, V_OUT)));
                px[y * VW + x] = a << 24 | DARK.getRGB() & 0xFFFFFF;
            }
        }
        img.setRGB(0, 0, VW, VH, px, 0, VW);   // setRGB pasa de ARGB a premultiplicado
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        ring(g, rnd, DARK, 110, 260, V_IN + 0.06, V_OUT, ax, ay);
        g.dispose();
        return img;
    }

    /** 'count' manchas a una distancia elíptica entre 'from' y 'to' del centro de la viñeta. */
    private static void ring(Graphics2D g, Random rnd, Color color, int alpha, int count,
                             double from, double to, double ax, double ay) {
        Color solid = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
        for (int i = 0; i < count; i++) {
            double angle = rnd.nextDouble() * 2 * Math.PI;
            double d = from + (to - from) * rnd.nextDouble();
            float r = (float) (14 + rnd.nextDouble() * 30);
            blob(g, VW / 2 + d * ax * Math.cos(angle), VH / 2 + d * ay * Math.sin(angle), r, solid, clear);
        }
    }
}
