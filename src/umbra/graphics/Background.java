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
 * [v1.0] Ambiente de cueva: fondo por capas con parallax, niebla, esporas, haces de luz y viñeta.
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

    private static final int[] SPORE_SIZES = { 6, 9, 12 };
    private static final float[] SPORE_ALPHAS = { 0.15f, 0.3f, 0.45f, 0.6f, 0.75f };

    private final double worldWidth;
    private final double ruinScale;
    private final BufferedImage sky, far, mid, near, fogBack, fogPit, overlay;
    private final BufferedImage[][] sporeSprites;
    private final List<Shape> front = new ArrayList<>();
    private final List<Spore> spores = new ArrayList<>();
    private final List<GodRay> godRays = new ArrayList<>();
    private double time;
    private double lightX = -9999, lightY = -9999;

    /** Una espora: sube despacio, se mece de lado y titila. */
    private static class Spore {
        double x, y, speed, phase, sway;
        int size;
    }

    /** Haz de luz volumétrico que entra desde el techo y pulsa suavemente. */
    private static class GodRay {
        double x, topW, bottomW, skew, phase, speed;
    }

    public Background(double worldWidth, long seed) {
        this(worldWidth, seed, 1);
    }

    public Background(double worldWidth, long seed, double ruinScale) {
        this.worldWidth = worldWidth;
        this.ruinScale = ruinScale;
        Random rnd = new Random(seed);
        sky = renderSky();
        far = renderFar(rnd);
        mid = renderMid(rnd);
        near = renderNear(rnd);
        BufferedImage fog = renderFog(rnd);
        fogBack = withAlpha(fog, 0.6f);
        fogPit = withAlpha(fog, 0.45f);
        overlay = renderOverlay();
        sporeSprites = renderSporeSprites();
        buildFront(rnd);
        createSpores(rnd);
        createGodRays(rnd);
    }

    // =========================================================
    //  ACTUALIZAR Y DIBUJAR
    // =========================================================

    public void update(double dt) {
        time += dt;
        for (Spore s : spores) {
            s.y -= s.speed * dt;
            if (s.y < -20) {
                s.y = Config.HEIGHT + 20;
            }
        }
    }

    /** Recibe la posición actual de Logi para iluminar esporas cercanas. */
    public void setLight(double lx, double ly) {
        this.lightX = lx;
        this.lightY = ly;
    }

    /** Lo que va detrás del nivel: cielo, capas lejanas, rayos volumétricos animados y niebla. */
    public void drawBack(Graphics2D g, double camX) {
        g.drawImage(sky, 0, 0, null);
        drawLayer(g, far, camX, FAR_DEPTH);
        drawGodRays(g, camX);
        drawLayer(g, mid, camX, MID_DEPTH);
        drawFog(g, fogBack, camX, FOG_DEPTH, 290, 9);
        drawLayer(g, near, camX, NEAR_DEPTH);
    }

    /** Renderiza haces de luz con transparencia pulsante y parallax. */
    private void drawGodRays(Graphics2D g, double camX) {
        AffineTransform old = g.getTransform();
        g.transform(Mat3.translation(-Math.round(camX * 0.25), 0).toAffineTransform());
        for (GodRay r : godRays) {
            double sway = Math.sin(time * r.speed + r.phase) * 24.0;
            double pulse = 0.55 + 0.45 * Math.sin(time * (r.speed * 1.4) + r.phase);
            int maxAlpha = (int) (34 * pulse);

            Path2D ray = new Path2D.Double();
            ray.moveTo(r.x - r.topW / 2, 0);
            ray.lineTo(r.x + r.topW / 2, 0);
            ray.lineTo(r.x + r.bottomW / 2 + r.skew + sway, 580);
            ray.lineTo(r.x - r.bottomW / 2 + r.skew + sway, 580);
            ray.closePath();

            g.setPaint(new GradientPaint(0, 0, alpha(new Color(145, 190, 245), maxAlpha), 0, 580, alpha(FOG, 0)));
            g.fill(ray);
        }
        g.setTransform(old);
    }

    /** Las esporas, en coordenadas del mundo: titilan y brillan más cálidas cuando Logi pasa cerca. */
    public void drawSpores(Graphics2D g, double camX) {
        for (Spore s : spores) {
            double x = s.x + Math.sin(time * 0.8 + s.phase) * s.sway;
            if (x < camX - 20 || x > camX + Config.WIDTH + 20) continue;

            double dist = Math.hypot(x - lightX, s.y - lightY);
            double lit = Math.max(0, 1.0 - dist / 180.0);

            double glow = Math.min(1.0, (0.45 + 0.45 * Math.sin(time * 1.7 + s.phase * 2)) + 0.5 * lit);
            int a = (int) Math.round(glow * (SPORE_ALPHAS.length - 1));
            BufferedImage sprite = sporeSprites[s.size][a];
            int half = sprite.getWidth() / 2;
            g.drawImage(sprite, (int) Math.round(x) - half, (int) Math.round(s.y) - half, null);

            if (lit > 0.25) {
                g.setColor(alpha(new Color(255, 225, 160), (int) (130 * lit)));
                g.fill(new Ellipse2D.Double(x - 1.5, s.y - 1.5, 3, 3));
            }
        }
    }

    /** Lo que va delante del nivel: neblina del abismo, primer plano y viñeta. */
    public void drawFront(Graphics2D g, double camX) {
        drawFog(g, fogPit, camX, PIT_FOG_DEPTH, 575, -16);

        AffineTransform old = g.getTransform();
        g.transform(Mat3.translation(-Math.round(camX * FRONT_DEPTH), 0).toAffineTransform());
        g.setColor(FRONT);
        for (Shape s : front) g.fill(s);
        g.setTransform(old);

        g.drawImage(overlay, 0, 0, null);
    }

    private static void drawLayer(Graphics2D g, BufferedImage layer, double camX, double depth) {
        AffineTransform old = g.getTransform();
        g.transform(Mat3.translation(-Math.round(camX * depth), 0).toAffineTransform());
        g.drawImage(layer, 0, 0, null);
        g.setTransform(old);
    }

    private void drawFog(Graphics2D g, BufferedImage fog, double camX, double depth, int y, double speed) {
        int w = fog.getWidth();
        double offset = (time * speed - camX * depth) % w;
        if (offset > 0) offset -= w;
        for (double x = offset; x < Config.WIDTH; x += w) {
            g.drawImage(fog, (int) Math.round(x), y, null);
        }
    }

    // =========================================================
    //  GENERACIÓN (una sola vez, al cargar el nivel)
    // =========================================================

    private int layerWidth(double depth) {
        return (int) Math.ceil(Config.WIDTH + (worldWidth - Config.WIDTH) * depth) + 2;
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

    private static BufferedImage renderSky() {
        BufferedImage img = new BufferedImage(Config.WIDTH, Config.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, SKY_TOP, 0, Config.HEIGHT, SKY_BOTTOM));
        g.fillRect(0, 0, Config.WIDTH, Config.HEIGHT);
        g.dispose();
        return img;
    }

    private static BufferedImage withAlpha(BufferedImage src, float alpha) {
        BufferedImage img = newImage(src.getWidth(), src.getHeight());
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return img;
    }

    private BufferedImage renderFar(Random rnd) {
        int w = layerWidth(FAR_DEPTH);
        BufferedImage img = newImage(w, Config.HEIGHT);
        Graphics2D g = smooth(img);

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

        // Pequeños cúmulos de cristales bioluminiscentes lejanos en el suelo
        for (int i = 0; i < w / 190; i++) {
            double cx = rnd.nextDouble() * w;
            double cy = CaveShapes.heightAt(floor, 0, w, 610, cx) + 4;
            for (int c = 0; c < 3; c++) {
                double ang = -Math.PI / 2 + (rnd.nextDouble() - 0.5) * 0.9;
                double len = 8 + rnd.nextDouble() * 12;
                Path2D shard = new Path2D.Double();
                shard.moveTo(cx - 2, cy);
                shard.lineTo(cx + Math.cos(ang) * len, cy + Math.sin(ang) * len);
                shard.lineTo(cx + 2, cy);
                shard.closePath();
                g.setColor(alpha(new Color(90, 185, 210), 65));
                g.fill(shard);
            }
        }

        g.dispose();
        return img;
    }

    private BufferedImage renderNear(Random rnd) {
        int w = layerWidth(NEAR_DEPTH);
        BufferedImage img = newImage(w, Config.HEIGHT);
        Graphics2D g = smooth(img);
        g.setColor(NEAR);

        double[] floor = midpointDisplacement(rnd, 128, 45, 0.6);
        g.fill(CaveShapes.ridge(floor, 0, w, 665, Config.HEIGHT));
        for (int i = 0; i < w / 110; i++) {
            double x = rnd.nextDouble() * w;
            g.fill(CaveShapes.spike(x, -5, 24 + rnd.nextDouble() * 50, 70 + rnd.nextDouble() * 210, true));
        }
        for (int i = 0; i < w / 220; i++) {
            double x = rnd.nextDouble() * w;
            g.fill(CaveShapes.spike(x, CaveShapes.heightAt(floor, 0, w, 665, x) + 5, 40 + rnd.nextDouble() * 50, 60 + rnd.nextDouble() * 120, false));
        }
        g.dispose();
        return img;
    }

    private void buildFront(Random rnd) {
        double w = layerWidth(FRONT_DEPTH);
        for (double x = rnd.nextDouble() * 300; x < w; x += 350 + rnd.nextDouble() * 500) {
            if (rnd.nextBoolean()) {
                front.add(root(x, rnd));
            } else {
                front.add(CaveShapes.spike(x, -5, 30 + rnd.nextDouble() * 40, 50 + rnd.nextDouble() * 60, true));
            }
            double bx = x + 150 + rnd.nextDouble() * 200;
            front.add(new Ellipse2D.Double(bx - 90, Config.HEIGHT - 28 - rnd.nextDouble() * 20, 180 + rnd.nextDouble() * 120, 90));
        }
    }

    private static BufferedImage renderFog(Random rnd) {
        int w = 1024, h = 180;
        BufferedImage img = newImage(w, h);
        Graphics2D g = smooth(img);
        for (int i = 0; i < 30; i++) {
            double cx = rnd.nextDouble() * w, cy = 60 + rnd.nextDouble() * (h - 120);
            float r = (float) (35 + rnd.nextDouble() * 60);
            RadialGradientPaint p = new RadialGradientPaint(new Point2D.Double(0, 0), r,
                    new float[] { 0f, 1f }, new Color[] { alpha(FOG, 26), alpha(FOG, 0) });
            for (int k = -1; k <= 1; k++) {
                AffineTransform t = AffineTransform.getTranslateInstance(cx + k * w, cy);
                t.scale(2.2, 1);
                Graphics2D gk = (Graphics2D) g.create();
                gk.transform(t);
                gk.setPaint(p);
                gk.fill(new Ellipse2D.Double(-r, -r, r * 2, r * 2));
                gk.dispose();
            }
        }
        g.setComposite(AlphaComposite.DstIn);
        g.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[] { 0f, 0.35f, 0.65f, 1f },
                new Color[] { new Color(0, 0, 0, 0), Color.BLACK, Color.BLACK, new Color(0, 0, 0, 0) }));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static BufferedImage renderOverlay() {
        BufferedImage img = newImage(Config.WIDTH, Config.HEIGHT);
        Graphics2D g = smooth(img);
        g.setPaint(new GradientPaint(0, 600, new Color(2, 3, 8, 0), 0, Config.HEIGHT, new Color(2, 3, 8, 200)));
        g.fillRect(0, 600, Config.WIDTH, Config.HEIGHT - 600);

        double sy = (double) Config.HEIGHT / Config.WIDTH;
        g.scale(1, sy);
        float r = (float) (Config.WIDTH * 0.75);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(Config.WIDTH / 2.0, Config.HEIGHT / 2.0 / sy), r,
                new float[] { 0.55f, 1f }, new Color[] { new Color(0, 0, 0, 0), new Color(0, 0, 0, 190) }));
        g.fill(new Rectangle2D.Double(0, 0, Config.WIDTH, Config.HEIGHT / sy));
        g.dispose();
        return img;
    }

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
        int count = (int) (worldWidth / 28);
        for (int i = 0; i < count; i++) {
            Spore s = new Spore();
            s.x = rnd.nextDouble() * worldWidth;
            s.y = rnd.nextDouble() * Config.HEIGHT;
            s.speed = 6 + rnd.nextDouble() * 14;
            s.phase = rnd.nextDouble() * Math.PI * 2;
            s.size = rnd.nextInt(SPORE_SIZES.length);
            s.sway = 5 + rnd.nextDouble() * 12;
            spores.add(s);
        }
    }

    private void createGodRays(Random rnd) {
        double w = layerWidth(0.25);
        for (double x = 140 + rnd.nextDouble() * 180; x < w; x += 360 + rnd.nextDouble() * 320) {
            GodRay r = new GodRay();
            r.x = x;
            r.topW = 24 + rnd.nextDouble() * 35;
            r.bottomW = 130 + rnd.nextDouble() * 110;
            r.skew = 40 + rnd.nextDouble() * 70;
            r.phase = rnd.nextDouble() * Math.PI * 2;
            r.speed = 0.4 + rnd.nextDouble() * 0.5;
            godRays.add(r);
        }
    }

    // =========================================================
    //  FORMAS
    // =========================================================

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