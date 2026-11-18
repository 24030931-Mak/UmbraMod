package umbra.graphics;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
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

import umbra.core.Config;
import umbra.math.Mat3;

/**
 * [v1.0] Ambiente de cueva: fondo por capas con parallax, niebla, esporas y viñeta.
 * Todo se genera con código (sin imágenes) y con una semilla fija, para que la cueva
 * se vea igual cada vez que se juega.
 *
 * Parallax: cada capa se desplaza con la cámara multiplicada por un factor de profundidad,
 * es decir, con la matriz de traslación  T(-camX * factor, -camY * factor).
 *   factor < 1: la capa está lejos y se mueve más lento que el nivel.
 *   factor > 1: la capa está entre el jugador y la pantalla (primer plano) y se mueve más rápido.
 *   factor = 1: sería la misma matriz de vista de la cámara (Camera.getViewMatrix).
 *
 * Las capas lejanas son más claras que las cercanas (perspectiva atmosférica: entre más
 * lejos, más niebla hay en medio), y eso da sensación de profundidad.
 *
 * [B1] Zonas altas: si la zona mide más de 720 px de alto, cada capa mide de alto lo que la
 * cámara recorre con su factor (720 + (alto - 720) * factor). El techo y sus estalactitas quedan
 * arriba, el piso y sus picos abajo, y en medio hay paredes de pozo, pilares de roca y
 * salientes con estalactitas (drawShaft). La niebla se repite a lo alto, la del abismo y el
 * abismo oscurecido solo se ven cerca del fondo de la zona, y la viñeta es de la pantalla.
 * Con 720 de alto todo sale igual que antes, píxel a píxel.
 */
public class Background {

    // ---- Paleta fría ----
    private static final Color SKY_TOP = new Color(5, 8, 16);
    private static final Color SKY_BOTTOM = new Color(17, 24, 41);
    private static final Color FAR = new Color(28, 38, 60);
    private static final Color MID = new Color(20, 27, 45);
    private static final Color NEAR = new Color(12, 17, 29);
    private static final Color FRONT = new Color(3, 5, 10);
    private static final Color FOG = new Color(110, 135, 180);
    private static final Color SPORE = new Color(150, 215, 255);

    // ---- Factores de profundidad (parallax) ----
    private static final double FAR_DEPTH = 0.15;
    private static final double MID_DEPTH = 0.35;
    private static final double FOG_DEPTH = 0.5;
    private static final double NEAR_DEPTH = 0.6;
    private static final double PIT_FOG_DEPTH = 0.9;
    private static final double FRONT_DEPTH = 1.3;

    /** Esporas pre-dibujadas: 3 tamaños x 5 brillos, para no escalar ni cambiar transparencias al dibujar. */
    private static final int[] SPORE_SIZES = { 6, 9, 12 };
    private static final float[] SPORE_ALPHAS = { 0.15f, 0.3f, 0.45f, 0.6f, 0.75f };

    /** [B1] Separación aproximada entre dos franjas de niebla de fondo en una zona alta (px de la capa). */
    private static final double FOG_BAND_GAP = 600;

    // [B1] Las paredes de pozo de cada capa (ver Shaft): grosor de la pared, cada cuánto (en X) hay
    // un pilar, ancho del pilar, cada cuánto (en Y) sale una saliente, cuánto sale, y el tamaño
    // de sus estalactitas. Las capas cercanas tienen paredes más delgadas y salientes más grandes.
    private static final Shaft FAR_SHAFT = new Shaft(115, 720, 80, 250, 95, 18, 110, false, false);
    private static final Shaft MID_SHAFT = new Shaft(70, 1150, 60, 320, 120, 24, 150, true, false);
    private static final Shaft NEAR_SHAFT = new Shaft(34, 2300, 46, 420, 150, 42, 210, false, true);

    private final double worldWidth, worldHeight;
    private final double maxY;        // lo más abajo que llega la cámara (0 en una zona de 720 de alto)
    private final double ruinScale;   // tamaño de las ruinas de la capa media (1 = las del nivel 1)
    private final BufferedImage sky, far, mid, near, fogBack, fogPit, overlay;
    private final BufferedImage abyss;   // [B1] el abismo oscurecido, aparte de la viñeta (solo en zonas altas)
    private final BufferedImage[][] sporeSprites;
    private final List<Shape> front = new ArrayList<>();
    private final List<Spore> spores = new ArrayList<>();
    private double time;

    /** Una espora: sube despacio, se mece de lado y titila. Vive en coordenadas del mundo. */
    private static class Spore {
        double x, y, speed, phase, sway;
        int size;   // índice en SPORE_SIZES
    }

    /** Las paredes de pozo de una capa (solo en zonas altas). */
    private static final class Shaft {
        final double wall, pillarEvery, pillarW, ledgeEvery, ledgeDepth, spikeW, spikeLen;
        final boolean ruins;            // columnas rotas sobre las salientes (capa media, como sus ruinas)
        final boolean partialPillars;   // pilares que cuelgan del techo o suben del piso (solo la cercana)
        Shaft(double wall, double pillarEvery, double pillarW, double ledgeEvery, double ledgeDepth,
                double spikeW, double spikeLen, boolean ruins, boolean partialPillars) {
            this.wall = wall; this.pillarEvery = pillarEvery; this.pillarW = pillarW;
            this.ledgeEvery = ledgeEvery; this.ledgeDepth = ledgeDepth;
            this.spikeW = spikeW; this.spikeLen = spikeLen; this.ruins = ruins;
            this.partialPillars = partialPillars;
        }
    }

    public Background(double worldWidth, long seed) {
        this(worldWidth, Config.HEIGHT, seed, 1);
    }

    public Background(double worldWidth, long seed, double ruinScale) {
        this(worldWidth, Config.HEIGHT, seed, ruinScale);
    }

    /**
     * 'ruinScale' agranda las columnas y los arcos de la capa media: el nivel 2, "Las
     * Galerías", tiene ruinas más grandes que el nivel 1 (1 = el tamaño del nivel 1).
     * [B1] 'worldHeight' es el alto de la zona; con más de 720 la cueva se vuelve un pozo.
     */
    public Background(double worldWidth, double worldHeight, long seed, double ruinScale) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.maxY = Math.max(0, worldHeight - Config.HEIGHT);
        this.ruinScale = ruinScale;
        Random rnd = new Random(seed);
        sky = renderSky();
        // [B1] En una zona alta, las capas lejana y media son paredes de pozo de arriba abajo
        far = maxY == 0 ? renderFar(rnd) : renderShaftLayer(rnd, FAR_DEPTH, FAR, FAR_SHAFT, true);
        mid = maxY == 0 ? renderMid(rnd) : renderShaftLayer(rnd, MID_DEPTH, MID, MID_SHAFT, false);
        near = renderNear(rnd);
        BufferedImage fog = renderFog(rnd);
        fogBack = withAlpha(fog, 0.6f);
        fogPit = withAlpha(fog, 0.45f);
        overlay = renderOverlay(maxY == 0);
        abyss = maxY == 0 ? null : renderAbyss();
        sporeSprites = renderSporeSprites();
        buildFront(rnd);
        createSpores(rnd);
    }

    // =========================================================
    //  ACTUALIZAR Y DIBUJAR
    // =========================================================

    public void update(double dt) {
        time += dt;
        for (Spore s : spores) {
            s.y -= s.speed * dt;
            if (s.y < -20) {              // salió por arriba: vuelve a nacer abajo
                s.y = worldHeight + 20;
            }
        }
    }

    /** Lo que va detrás del nivel, en coordenadas de pantalla: cielo, capas lejanas y niebla. */
    public void drawBack(Graphics2D g, double camX, double camY) {
        g.drawImage(sky, 0, 0, null);
        drawLayer(g, far, camX, camY, FAR_DEPTH);
        drawLayer(g, mid, camX, camY, MID_DEPTH);
        if (maxY == 0) {
            drawFog(g, fogBack, camX, FOG_DEPTH, 290, 9, 0);
        } else {
            // Zona alta: varias franjas repartidas a lo alto de la capa (factor 0.5), la primera a
            // 290 px del techo y la última a la misma altura sobre el piso; cada una corrida de lado
            int span = shiftY(FOG_DEPTH);
            int bands = (int) Math.max(1, Math.round(span / FOG_BAND_GAP));
            long top = Math.round(camY * FOG_DEPTH);
            for (int k = 0; k <= bands; k++) {
                long y = 290 + Math.round((double) span * k / bands) - top;
                if (y > -fogBack.getHeight() && y < Config.HEIGHT) {
                    drawFog(g, fogBack, camX, FOG_DEPTH, (int) y, 9, k * 389);
                }
            }
        }
        drawLayer(g, near, camX, camY, NEAR_DEPTH);
    }

    /** Las esporas, en coordenadas del mundo (se dibujan con la matriz de vista ya aplicada). */
    public void drawSpores(Graphics2D g, double camX, double camY) {
        for (Spore s : spores) {
            if (s.y < camY - 20 || s.y > camY + Config.HEIGHT + 20) continue;   // arriba o abajo de la pantalla
            double x = s.x + Math.sin(time * 0.8 + s.phase) * s.sway;
            if (x < camX - 20 || x > camX + Config.WIDTH + 20) continue;   // fuera de la pantalla
            // Titilan: el brillo sigue una onda seno y se elige la imagen más parecida
            double glow = 0.5 + 0.5 * Math.sin(time * 1.7 + s.phase * 2);
            int a = (int) Math.round(glow * (SPORE_ALPHAS.length - 1));
            BufferedImage sprite = sporeSprites[s.size][a];
            int half = sprite.getWidth() / 2;
            g.drawImage(sprite, (int) Math.round(x) - half, (int) Math.round(s.y) - half, null);
        }
    }

    /** Lo que va delante del nivel, en coordenadas de pantalla: neblina del abismo, primer plano y viñeta. */
    public void drawFront(Graphics2D g, double camX, double camY) {
        // La neblina del abismo va pegada al fondo de la zona (factor 0.9): en una zona alta solo
        // se ve cuando la cámara anda cerca del piso
        long pitY = 575 + shiftY(PIT_FOG_DEPTH) - Math.round(camY * PIT_FOG_DEPTH);
        if (pitY < Config.HEIGHT) drawFog(g, fogPit, camX, PIT_FOG_DEPTH, (int) pitY, -16, 0);

        // Primer plano: raíces y rocas muy oscuras que pasan más rápido que el nivel
        AffineTransform old = g.getTransform();
        g.transform(Mat3.translation(-Math.round(camX * FRONT_DEPTH), -Math.round(camY * FRONT_DEPTH)).toAffineTransform());
        g.setColor(FRONT);
        for (Shape s : front) g.fill(s);
        g.setTransform(old);

        // Abismo oscurecido abajo + viñeta en las orillas (una sola imagen ya hecha). En una zona
        // alta el abismo va aparte, pegado al fondo de la zona, y la viñeta se queda en la pantalla.
        if (abyss != null) {
            long y = 600 + Math.round(maxY) - Math.round(camY);
            if (y < Config.HEIGHT) g.drawImage(abyss, 0, (int) y, null);
        }
        g.drawImage(overlay, 0, 0, null);
    }

    /** Dibuja una capa ya generada con la matriz de parallax T(-camX * depth, -camY * depth). */
    private static void drawLayer(Graphics2D g, BufferedImage layer, double camX, double camY, double depth) {
        AffineTransform old = g.getTransform();
        // Se redondea a píxeles enteros para que la imagen no se vea borrosa al moverse
        g.transform(Mat3.translation(-Math.round(camX * depth), -Math.round(camY * depth)).toAffineTransform());
        g.drawImage(layer, 0, 0, null);
        g.setTransform(old);
    }

    /**
     * Una franja de niebla que se repite de lado a lado. Se mueve sola (speed, en px/s)
     * y además con la cámara (parallax). El operador módulo hace que se repita sin cortes.
     * 'shift' la corre de lado, para que dos franjas de la misma zona no se vean iguales.
     */
    private void drawFog(Graphics2D g, BufferedImage fog, double camX, double depth, int y, double speed, double shift) {
        int w = fog.getWidth();
        double offset = (time * speed - camX * depth + shift) % w;
        if (offset > 0) offset -= w;
        for (double x = offset; x < Config.WIDTH; x += w) {
            g.drawImage(fog, (int) Math.round(x), y, null);
        }
    }

    // =========================================================
    //  GENERACIÓN (una sola vez, al cargar el nivel)
    // =========================================================

    /** Ancho que necesita una capa para cubrir todo el nivel con su factor de profundidad. */
    private int layerWidth(double depth) {
        return (int) Math.ceil(Config.WIDTH + (worldWidth - Config.WIDTH) * depth) + 2;
    }

    /**
     * [B1] Cuánto baja una capa cuando la cámara llega al fondo de la zona: round(maxY * factor).
     * Lo que va en el piso se dibuja así de más abajo y se ve justo donde se vería en una zona
     * de 720. Es 0 en las zonas de 720 de alto.
     */
    private int shiftY(double depth) {
        return (int) Math.round(maxY * depth);
    }

    /** [B1] Alto de una capa: 720 más lo que baja (y 2 px de sobra, como en el ancho, si es alta). */
    private int layerHeight(double depth) {
        return maxY == 0 ? Config.HEIGHT : Config.HEIGHT + shiftY(depth) + 2;
    }

    static Graphics2D smooth(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    static BufferedImage newImage(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
    }

    /** Cielo de la cueva: degradado vertical de casi negro a azul oscuro. Imagen opaca: se copia muy rápido. */
    private static BufferedImage renderSky() {
        BufferedImage img = new BufferedImage(Config.WIDTH, Config.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, SKY_TOP, 0, Config.HEIGHT, SKY_BOTTOM));
        g.fillRect(0, 0, Config.WIDTH, Config.HEIGHT);
        g.dispose();
        return img;
    }

    /** Copia de una imagen con su transparencia multiplicada por 'alpha'. */
    private static BufferedImage withAlpha(BufferedImage src, float alpha) {
        BufferedImage img = newImage(src.getWidth(), src.getHeight());
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return img;
    }

    /** Capa lejana: paredes de una caverna enorme, estalactitas finas y rayos de luz. */
    private BufferedImage renderFar(Random rnd) {
        int w = layerWidth(FAR_DEPTH);
        BufferedImage img = newImage(w, Config.HEIGHT);
        Graphics2D g = smooth(img);

        // Rayos de luz que entran por grietas del techo
        for (int i = 0; i < 4; i++) {
            double x = rnd.nextDouble() * w;
            double top = 30 + rnd.nextDouble() * 40, bottom = 140 + rnd.nextDouble() * 100;
            Path2D ray = new Path2D.Double();
            ray.moveTo(x - top / 2, 0);
            ray.lineTo(x + top / 2, 0);
            ray.lineTo(x + top / 2 + bottom, 560);
            ray.lineTo(x - top / 2 + bottom * 0.3, 560);
            ray.closePath();
            g.setPaint(new GradientPaint(0, 0, alpha(FOG, 34), 0, 560, alpha(FOG, 0)));
            g.fill(ray);
        }

        g.setColor(FAR);
        double[] ceiling = midpointDisplacement(rnd, 64, 55, 0.55);
        double[] floor = midpointDisplacement(rnd, 64, 80, 0.55);
        g.fill(CaveShapes.ridge(ceiling, 0, w, 85, 0));
        g.fill(CaveShapes.ridge(floor, 0, w, 560, Config.HEIGHT));
        for (int i = 0; i < w / 45; i++) {
            double x = rnd.nextDouble() * w;
            g.fill(CaveShapes.spike(x, CaveShapes.heightAt(ceiling, 0, w, 85, x) - 5, 10 + rnd.nextDouble() * 22, 40 + rnd.nextDouble() * 150, true));
        }
        for (int i = 0; i < w / 160; i++) {
            double x = rnd.nextDouble() * w;
            g.fill(CaveShapes.spike(x, CaveShapes.heightAt(floor, 0, w, 560, x) + 5, 30 + rnd.nextDouble() * 40, 80 + rnd.nextDouble() * 160, false));
        }
        g.dispose();
        return img;
    }

    /** Capa media: ruinas de columnas y arcos sobre el suelo, y estalactitas. */
    private BufferedImage renderMid(Random rnd) {
        int w = layerWidth(MID_DEPTH);
        BufferedImage img = newImage(w, Config.HEIGHT);
        Graphics2D g = smooth(img);
        g.setColor(MID);

        double[] ceiling = midpointDisplacement(rnd, 64, 40, 0.6);
        double[] floor = midpointDisplacement(rnd, 64, 50, 0.6);
        g.fill(CaveShapes.ridge(ceiling, 0, w, 45, 0));
        g.fill(CaveShapes.ridge(floor, 0, w, 610, Config.HEIGHT));
        for (int i = 0; i < w / 60; i++) {
            double x = rnd.nextDouble() * w;
            g.fill(CaveShapes.spike(x, CaveShapes.heightAt(ceiling, 0, w, 45, x) - 5, 14 + rnd.nextDouble() * 26, 50 + rnd.nextDouble() * 170, true));
        }

        // Ruinas: pares de columnas, a veces unidas por un arco, a veces rotas. Cada ruina se
        // escala desde el pie de su primera columna:  M = T(x, pie) * S(s) * T(-x, -pie),
        // con s = ruinScale (con s = 1, M es la identidad). La segunda columna, ya escalada,
        // queda en x + span * s; para que se pare en el suelo de ese punto, su pie se regresa
        // a las coordenadas sin escalar: pie + (suelo - pie) / s.
        g.setStroke(new BasicStroke(16, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        AffineTransform plain = g.getTransform();
        for (double x = 80 + rnd.nextDouble() * 200; x < w - 200; x += 380 + rnd.nextDouble() * 420) {
            double span = 110 + rnd.nextDouble() * 90;
            double colW = 26 + rnd.nextDouble() * 14;
            double height = 160 + rnd.nextDouble() * 170;
            boolean arch = rnd.nextDouble() < 0.6;
            double foot = CaveShapes.heightAt(floor, 0, w, 610, x) + 10;
            g.transform(Mat3.translation(x, foot).multiply(Mat3.scale(ruinScale, ruinScale))
                    .multiply(Mat3.translation(-x, -foot)).toAffineTransform());
            for (int k = 0; k < 2; k++) {
                double cx = x + k * span;
                double ground = CaveShapes.heightAt(floor, 0, w, 610, x + k * span * ruinScale) + 10;
                double base = foot + (ground - foot) / ruinScale;
                double h = arch ? height : height * (0.4 + rnd.nextDouble() * 0.6);
                g.fill(column(cx, base, colW, h, rnd, !arch && rnd.nextDouble() < 0.7));
            }
            if (arch) {
                double top = foot - height;
                g.draw(new Arc2D.Double(x + colW / 2, top - span / 2, span, span, 0, 180, Arc2D.OPEN));
            }
            g.setTransform(plain);
        }
        g.dispose();
        return img;
    }

    /**
     * [B1] Capa lejana o media de una zona alta. Con un factor tan pequeño, el techo y el piso de
     * hoy se verían a casi cualquier altura (la capa lejana solo recorre 0.15 de lo que baja la
     * cámara), así que aquí la capa es el pozo mismo: paredes, pilares y salientes de arriba
     * abajo, con un techo delgado arriba y un piso bajo abajo que solo se asoman cerca del techo
     * y del fondo de la zona.
     */
    private BufferedImage renderShaftLayer(Random rnd, double depth, Color color, Shaft sh, boolean rays) {
        int w = layerWidth(depth), h = layerHeight(depth), s = shiftY(depth);
        BufferedImage img = newImage(w, h);
        Graphics2D g = smooth(img);
        g.setColor(color);
        double[] ceiling = midpointDisplacement(rnd, 64, 18, 0.6);
        double[] floor = midpointDisplacement(rnd, 64, 18, 0.6);
        g.fill(CaveShapes.ridge(ceiling, 0, w, 22, 0));
        g.fill(CaveShapes.ridge(floor, 0, w, Config.HEIGHT - 40 + s, h));
        drawShaft(g, rnd, w, h, sh, rays);
        g.dispose();
        return img;
    }

    /** Capa cercana: rocas oscuras abajo y estalactitas grandes arriba. */
    private BufferedImage renderNear(Random rnd) {
        int w = layerWidth(NEAR_DEPTH), h = layerHeight(NEAR_DEPTH), s = shiftY(NEAR_DEPTH);
        BufferedImage img = newImage(w, h);
        Graphics2D g = smooth(img);
        g.setColor(NEAR);

        double[] floor = midpointDisplacement(rnd, 128, 45, 0.6);
        g.fill(CaveShapes.ridge(floor, 0, w, 665 + s, h));
        for (int i = 0; i < w / 110; i++) {
            double x = rnd.nextDouble() * w;
            g.fill(CaveShapes.spike(x, -5, 24 + rnd.nextDouble() * 50, 70 + rnd.nextDouble() * 210, true));
        }
        for (int i = 0; i < w / 220; i++) {
            double x = rnd.nextDouble() * w;
            g.fill(CaveShapes.spike(x, CaveShapes.heightAt(floor, 0, w, 665 + s, x) + 5, 40 + rnd.nextDouble() * 50, 60 + rnd.nextDouble() * 120, false));
        }
        if (maxY > 0) drawShaft(g, rnd, w, h, NEAR_SHAFT, false);
        g.dispose();
        return img;
    }

    // =========================================================
    //  [B1] PAREDES DE POZO (solo en las zonas altas)
    // =========================================================

    /**
     * Lo que hay entre el techo y el piso de una capa alta de w x h: una pared de roca a cada
     * lado, pilares que bajan del techo al piso (si la capa es ancha) y salientes con
     * estalactitas colgando. Todo va del color de la capa, así que se une con el techo y el
     * piso sin bordes. En la capa lejana, además, rayos de luz que entran por grietas de las
     * paredes (se dibujan primero: la pared tapa dónde nacen).
     */
    private void drawShaft(Graphics2D g, Random rnd, int w, int h, Shaft sh, boolean rays) {
        Color color = g.getColor();
        if (rays) {
            for (double y = 420 + rnd.nextDouble() * 300; y < h - 520; y += 520 + rnd.nextDouble() * 420) {
                int dir = rnd.nextBoolean() ? 1 : -1;
                double x0 = dir > 0 ? sh.wall : w - sh.wall;
                double len = 320 + rnd.nextDouble() * 280, mouth = 10 + rnd.nextDouble() * 16, spread = 60 + rnd.nextDouble() * 90;
                Path2D ray = new Path2D.Double();
                ray.moveTo(x0, y - mouth);
                ray.lineTo(x0, y + mouth);
                ray.lineTo(x0 + dir * len, y + len * 0.6 + spread);
                ray.lineTo(x0 + dir * len, y + len * 0.6 - spread * 0.3);
                ray.closePath();
                g.setPaint(new GradientPaint((float) x0, (float) y, alpha(FOG, 30),
                        (float) (x0 + dir * len), (float) (y + len * 0.6), alpha(FOG, 0)));
                g.fill(ray);
            }
            g.setPaint(color);
        }

        int segs = segmentsFor(h);
        // Las dos paredes: un perfil fractal a lo alto (como el techo, pero de lado)
        for (int side = 0; side < 2; side++) {
            int dir = side == 0 ? 1 : -1;
            double edge = side == 0 ? -2 : w + 2;
            double[] prof = midpointDisplacement(rnd, segs, sh.wall * 0.45, 0.62);
            Path2D p = new Path2D.Double();
            p.moveTo(edge, 0);
            for (int i = 0; i <= segs; i++) {
                p.lineTo(edge + dir * Math.max(sh.wall * 0.3, sh.wall + prof[i]), (double) h * i / segs);
            }
            p.lineTo(edge, h);
            p.closePath();
            g.fill(p);
            for (double y = 260 + rnd.nextDouble() * sh.ledgeEvery; y < h - 300; y += sh.ledgeEvery * (0.7 + rnd.nextDouble() * 0.8)) {
                double face = edge + dir * Math.max(sh.wall * 0.3, sh.wall + CaveShapes.heightAt(prof, 0, h, 0, y));
                ledge(g, rnd, face - dir * 6, y, dir, sh);
            }
        }

        // Pilares: columnas naturales de roca que van del techo al piso, solo en capas anchas
        if (w >= sh.pillarEvery * 1.4) {
            for (double x = sh.pillarEvery * (0.5 + rnd.nextDouble() * 0.4); x < w - sh.pillarEvery * 0.4;
                    x += sh.pillarEvery * (0.75 + rnd.nextDouble() * 0.6)) {
                pillar(g, rnd, x, h, sh, segs);
            }
        }
    }

    /** Tramos del perfil de una pared: una potencia de 2, con un punto cada ~24 px. */
    private static int segmentsFor(int h) {
        int n = 16;
        while (n * 24 < h) n *= 2;
        return n;
    }

    /**
     * Pilar de roca alrededor de x = cx. Hay tres clases: columna del techo al piso (se abre al
     * llegar a los dos, como cuando una estalactita y una estalagmita se juntan), una estalactita
     * gigante que cuelga del techo, o una estalagmita gigante que sube del piso; las dos últimas
     * se afinan hasta la punta: ancho * ((distancia a la punta) / largo)^0.6. Cada orilla tiene su
     * propio perfil fractal, el ancho se abomba y el centro se tuerce con dos perfiles más lentos,
     * y salen salientes a los dos lados donde es grueso.
     */
    private void pillar(Graphics2D g, Random rnd, double cx, int h, Shaft sh, int segs) {
        // 0: del techo al piso; 1: cuelga del techo; 2: sube del piso. Solo la capa cercana tiene
        // de las que terminan en punta: en las lejanas la punta casi no se movería y se vería a
        // cualquier altura, como un techo a media zona.
        int kind = sh.partialPillars ? rnd.nextInt(3) : 0;
        double tip = kind == 1 ? h * (0.45 + rnd.nextDouble() * 0.3) : kind == 2 ? h * (0.3 + rnd.nextDouble() * 0.3) : 0;
        double[] a = midpointDisplacement(rnd, segs, sh.pillarW * 0.3, 0.6);
        double[] b = midpointDisplacement(rnd, segs, sh.pillarW * 0.3, 0.6);
        double[] bulge = midpointDisplacement(rnd, 8, 0.45, 0.5);   // abombado: el ancho cambia despacio
        double[] wander = midpointDisplacement(rnd, 8, 55, 0.5);    // el centro se tuerce despacio
        double half = sh.pillarW * (0.4 + rnd.nextDouble() * 0.3);
        double[] left = new double[segs + 1], right = new double[segs + 1], thick = new double[segs + 1];
        Path2D p = new Path2D.Double();
        boolean started = false;
        if (kind == 2) {
            p.moveTo(cx + CaveShapes.heightAt(wander, 0, h, 0, tip), tip);
            started = true;
        }
        for (int i = 0; i <= segs; i++) {
            double y = (double) h * i / segs;
            double f = kind == 1 ? (y >= tip ? 0 : Math.pow((tip - y) / tip, 0.6))
                     : kind == 2 ? (y <= tip ? 0 : Math.pow((y - tip) / (h - tip), 0.6)) : 1;
            thick[i] = f;
            if (f <= 0) continue;
            double c = cx + CaveShapes.heightAt(wander, 0, h, 0, y);
            double w = half * Math.max(0.35, 1 + CaveShapes.heightAt(bulge, 0, h, 0, y));
            double top = kind == 2 ? 0 : Math.max(0, 1 - y / 340);
            double bottom = kind == 1 ? 0 : Math.max(0, 1 - (h - y) / 420);
            double flare = (top * top + bottom * bottom) * sh.pillarW * 1.6;
            left[i] = c - Math.max(4, w + a[i]) * f - flare;
            right[i] = c + Math.max(4, w + b[i]) * f + flare;
            if (started) p.lineTo(left[i], y); else { p.moveTo(left[i], y); started = true; }
        }
        if (kind == 1) p.lineTo(cx + CaveShapes.heightAt(wander, 0, h, 0, tip), tip);
        for (int i = segs; i >= 0; i--) {
            if (thick[i] > 0) p.lineTo(right[i], (double) h * i / segs);
        }
        p.closePath();
        g.fill(p);
        for (double y = 300 + rnd.nextDouble() * sh.ledgeEvery; y < h - 340; y += sh.ledgeEvery * (0.6 + rnd.nextDouble() * 0.8)) {
            int i = (int) Math.round(y / h * segs);
            if (thick[i] < 0.45) continue;   // cerca de la punta es muy delgado para una saliente
            boolean toRight = rnd.nextBoolean();
            double face = CaveShapes.heightAt(toRight ? right : left, 0, h, 0, y);
            ledge(g, rnd, face + (toRight ? -6 : 6), y, toRight ? 1 : -1, sh);
        }
    }

    /**
     * Saliente de roca que sale de una pared en x0 hacia 'dir' (1 = derecha): lomo curvo arriba,
     * panza abajo, y de 1 a 3 estalactitas colgando. En la capa media, a veces, una columna rota
     * encima (del tamaño de las ruinas de la zona).
     */
    private void ledge(Graphics2D g, Random rnd, double x0, double y, int dir, Shaft sh) {
        double d = sh.ledgeDepth * (0.45 + rnd.nextDouble() * 0.75);   // cuánto sale de la pared
        double t = 12 + rnd.nextDouble() * 16;                         // grosor cerca de la punta
        Path2D p = new Path2D.Double();
        p.moveTo(x0, y - t * 3);                                                    // se une a la pared más arriba
        p.quadTo(x0 + dir * d * 0.55, y - t * 1.3, x0 + dir * d, y - t * 0.4);      // lomo
        p.quadTo(x0 + dir * d * 1.06, y + t * 0.3, x0 + dir * d * 0.92, y + t * 0.6); // punta redondeada
        p.quadTo(x0 + dir * d * 0.45, y + t * 0.9, x0, y + t * 2);                  // panza
        p.closePath();
        g.fill(p);
        int n = 1 + rnd.nextInt(3);
        for (int k = 0; k < n; k++) {
            double f = 0.15 + rnd.nextDouble() * 0.7;                  // qué tan lejos de la pared, de 0 a 1
            double base = y + t * (0.6 + (0.92 - f) * 1.5) - 4;        // la panza en ese punto (metida 4 px)
            g.fill(CaveShapes.spike(x0 + dir * d * f, base, sh.spikeW * (0.5 + rnd.nextDouble() * 0.6),
                    sh.spikeLen * (0.35 + rnd.nextDouble() * 0.75), true));
        }
        if (sh.ruins && d > sh.ledgeDepth * 0.75 && rnd.nextDouble() < 0.45) {
            // Columna rota sobre el lomo, escalada desde su pie: T(cx, pie) * S(s) * T(-cx, -pie)
            double cx = x0 + dir * d * 0.5, foot = y - t * 1.25 + 6;
            Graphics2D gc = (Graphics2D) g.create();
            gc.transform(Mat3.translation(cx, foot).multiply(Mat3.scale(ruinScale, ruinScale))
                    .multiply(Mat3.translation(-cx, -foot)).toAffineTransform());
            gc.fill(column(cx, foot, 18 + rnd.nextDouble() * 10, 50 + rnd.nextDouble() * 70, rnd, true));
            gc.dispose();
        }
    }

    /** Primer plano: pocas raíces y rocas, solo en las orillas de arriba y abajo para no tapar el juego. */
    private void buildFront(Random rnd) {
        double w = layerWidth(FRONT_DEPTH);
        int s = shiftY(FRONT_DEPTH);   // en una zona alta, las rocas de abajo van al fondo de la zona
        for (double x = rnd.nextDouble() * 300; x < w; x += 350 + rnd.nextDouble() * 500) {
            if (rnd.nextBoolean()) {
                front.add(root(x, rnd));
            } else {
                front.add(CaveShapes.spike(x, -5, 30 + rnd.nextDouble() * 40, 50 + rnd.nextDouble() * 60, true));
            }
            double bx = x + 150 + rnd.nextDouble() * 200;
            front.add(new Ellipse2D.Double(bx - 90, Config.HEIGHT + s - 28 - rnd.nextDouble() * 20, 180 + rnd.nextDouble() * 120, 90));
        }
    }

    /** Textura de niebla: manchas suaves que se pueden repetir de lado a lado sin que se note la unión. */
    private static BufferedImage renderFog(Random rnd) {
        int w = 1024, h = 180;
        BufferedImage img = newImage(w, h);
        Graphics2D g = smooth(img);
        for (int i = 0; i < 30; i++) {
            double cx = rnd.nextDouble() * w, cy = 60 + rnd.nextDouble() * (h - 120);
            float r = (float) (35 + rnd.nextDouble() * 60);
            RadialGradientPaint p = new RadialGradientPaint(new Point2D.Double(0, 0), r,
                    new float[] { 0f, 1f }, new Color[] { alpha(FOG, 26), alpha(FOG, 0) });
            // Se dibuja también una copia a w px de distancia: así la orilla derecha
            // continúa exactamente donde empieza la izquierda
            for (int k = -1; k <= 1; k++) {
                AffineTransform t = AffineTransform.getTranslateInstance(cx + k * w, cy);
                t.scale(2.2, 1);   // manchas alargadas, como bancos de niebla
                Graphics2D gk = (Graphics2D) g.create();
                gk.transform(t);
                gk.setPaint(p);
                gk.fill(new Ellipse2D.Double(-r, -r, r * 2, r * 2));
                gk.dispose();
            }
        }
        // Desvanecer arriba y abajo: DST_IN multiplica la transparencia de lo ya dibujado
        // por la de este degradado, así la franja no tiene orillas rectas
        g.setComposite(AlphaComposite.DstIn);
        g.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[] { 0f, 0.35f, 0.65f, 1f },
                new Color[] { new Color(0, 0, 0, 0), Color.BLACK, Color.BLACK, new Color(0, 0, 0, 0) }));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    /**
     * Capa que va encima de todo el mundo: el abismo oscurecido abajo, para que los huecos
     * se vean profundos, y la viñeta: un degradado radial (estirado a lo ancho) que
     * oscurece las orillas de la pantalla. [B1] En una zona alta el abismo va aparte
     * (renderAbyss) y esta imagen es solo la viñeta.
     */
    private static BufferedImage renderOverlay(boolean withAbyss) {
        BufferedImage img = newImage(Config.WIDTH, Config.HEIGHT);
        Graphics2D g = smooth(img);
        if (withAbyss) {
            g.setPaint(new GradientPaint(0, 600, new Color(2, 3, 8, 0), 0, Config.HEIGHT, new Color(2, 3, 8, 200)));
            g.fillRect(0, 600, Config.WIDTH, Config.HEIGHT - 600);
        }

        double sy = (double) Config.HEIGHT / Config.WIDTH;
        g.scale(1, sy);   // el círculo del degradado se vuelve una elipse del tamaño de la pantalla
        float r = (float) (Config.WIDTH * 0.75);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(Config.WIDTH / 2.0, Config.HEIGHT / 2.0 / sy), r,
                new float[] { 0.55f, 1f }, new Color[] { new Color(0, 0, 0, 0), new Color(0, 0, 0, 190) }));
        g.fill(new Rectangle2D.Double(0, 0, Config.WIDTH, Config.HEIGHT / sy));
        g.dispose();
        return img;
    }

    /** [B1] El abismo oscurecido de una zona alta: el mismo degradado, en una franja de 120 px. */
    private static BufferedImage renderAbyss() {
        int h = Config.HEIGHT - 600;
        BufferedImage img = newImage(Config.WIDTH, h);
        Graphics2D g = smooth(img);
        g.setPaint(new GradientPaint(0, 0, new Color(2, 3, 8, 0), 0, h, new Color(2, 3, 8, 200)));
        g.fillRect(0, 0, Config.WIDTH, h);
        g.dispose();
        return img;
    }

    /** Puntitos de luz suave, en varios tamaños y brillos, que se reutilizan para todas las esporas. */
    private static BufferedImage[][] renderSporeSprites() {
        BufferedImage[][] sprites = new BufferedImage[SPORE_SIZES.length][SPORE_ALPHAS.length];
        for (int i = 0; i < SPORE_SIZES.length; i++) {
            int s = SPORE_SIZES[i];
            BufferedImage base = newImage(s, s);
            Graphics2D g = smooth(base);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(s / 2.0, s / 2.0), s / 2f,
                    new float[] { 0f, 0.3f, 1f },
                    new Color[] { new Color(235, 250, 255, 255), alpha(SPORE, 140), alpha(SPORE, 0) }));
            g.fillRect(0, 0, s, s);
            g.dispose();
            for (int a = 0; a < SPORE_ALPHAS.length; a++) {
                sprites[i][a] = withAlpha(base, SPORE_ALPHAS[a]);
            }
        }
        return sprites;
    }

    private void createSpores(Random rnd) {
        // Las mismas por pantalla: en una zona alta hay más, repartidas a lo alto
        int count = maxY == 0 ? (int) (worldWidth / 28) : (int) (worldWidth / 28 * worldHeight / Config.HEIGHT);
        for (int i = 0; i < count; i++) {
            Spore s = new Spore();
            s.x = rnd.nextDouble() * worldWidth;
            s.y = rnd.nextDouble() * worldHeight;
            s.speed = 6 + rnd.nextDouble() * 14;
            s.phase = rnd.nextDouble() * Math.PI * 2;
            s.size = rnd.nextInt(SPORE_SIZES.length);
            s.sway = 5 + rnd.nextDouble() * 12;
            spores.add(s);
        }
    }

    // =========================================================
    //  FORMAS (los perfiles y las estalactitas están en CaveShapes)
    // =========================================================

    /**
     * Desplazamiento del punto medio (terreno fractal). Se empieza con una línea y, en cada
     * pasada, el punto medio de cada tramo se sube o baja un valor al azar. En cada pasada
     * el desplazamiento se multiplica por 'roughness' (< 1): quedan formas grandes con
     * detalles cada vez más pequeños encima, como las rocas de verdad.
     *
     * @param segments número de tramos (potencia de 2)
     * @return segments + 1 alturas, relativas a la línea base
     */
    public static double[] midpointDisplacement(Random rnd, int segments, double amplitude, double roughness) {
        double[] h = new double[segments + 1];
        h[0] = (rnd.nextDouble() * 2 - 1) * amplitude;
        h[segments] = (rnd.nextDouble() * 2 - 1) * amplitude;
        double disp = amplitude;
        for (int step = segments; step > 1; step /= 2) {
            for (int i = 0; i < segments; i += step) {
                int m = i + step / 2;
                h[m] = (h[i] + h[i + step]) / 2 + (rnd.nextDouble() * 2 - 1) * disp;
            }
            disp *= roughness;
        }
        return h;
    }

    /** Columna con capitel, o rota en la parte de arriba. */
    static Path2D column(double cx, double base, double w, double h, Random rnd, boolean broken) {
        Path2D p = new Path2D.Double();
        double left = cx - w / 2, right = cx + w / 2, top = base - h;
        p.moveTo(left - 6, base);
        p.lineTo(left - 6, base - 12);
        p.lineTo(left, base - 12);
        if (broken) {
            p.lineTo(left, top + 18);
            p.lineTo(left + w * 0.3, top + 6 + rnd.nextDouble() * 10);
            p.lineTo(left + w * 0.55, top + 20 + rnd.nextDouble() * 12);
            p.lineTo(left + w * 0.8, top);
            p.lineTo(right, top + 14);
        } else {
            p.lineTo(left, top + 14);
            p.lineTo(left - 8, top + 14);
            p.lineTo(left - 8, top);
            p.lineTo(right + 8, top);
            p.lineTo(right + 8, top + 14);
            p.lineTo(right, top + 14);
        }
        p.lineTo(right, base - 12);
        p.lineTo(right + 6, base - 12);
        p.lineTo(right + 6, base);
        p.closePath();
        return p;
    }

    /** Raíz colgante: una curva gruesa que baja serpenteando desde el techo. */
    private static Shape root(double x, Random rnd) {
        Path2D p = new Path2D.Double();
        double len = 70 + rnd.nextDouble() * 70;
        p.moveTo(x - 14, -5);
        p.curveTo(x - 30, len * 0.35, x + 20, len * 0.6, x, len);
        p.curveTo(x + 26, len * 0.55, x - 8, len * 0.3, x + 14, -5);
        p.closePath();
        return p;
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
