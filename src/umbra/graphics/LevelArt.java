package umbra.graphics;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import umbra.core.Config;
import umbra.entities.Block;
import umbra.entities.LightSource;
import umbra.world.Level;

/**
 * [v1.0] El aspecto del nivel: repisas de roca con musgo y hongos que brillan, bloques como
 * losas de piedra con una runa que se enciende cuando la Lumbre se acerca, y columnas rotas.
 *
 * Todo se genera con código a partir de la geometría del nivel, una sola vez al cargarlo,
 * y se guarda en imágenes (pre-renderizado). Las colisiones no cambian: siguen siendo los
 * mismos rectángulos. Regla visual: lo que tiene borde claro arriba se puede pisar; las
 * columnas son oscuras y sin borde porque son solo decoración.
 */
public class LevelArt {

    // ---- Paleta (fría, como el fondo) ----
    private static final Color ROCK_TOP = new Color(46, 54, 76);
    private static final Color ROCK_BOTTOM = new Color(22, 26, 40);
    private static final Color RIM = new Color(125, 145, 190, 170);
    private static final Color STRATA = new Color(70, 82, 110, 45);
    private static final Color PEBBLE = new Color(54, 62, 86, 120);
    private static final Color PEBBLE_LINE = new Color(12, 14, 24, 120);
    private static final Color CRACK = new Color(8, 10, 18, 170);
    private static final Color MOSS_DARK = new Color(34, 66, 70);
    private static final Color MOSS_LIGHT = new Color(78, 140, 135);
    private static final Color PROP = new Color(24, 30, 46);
    private static final Color STONE_TOP = new Color(92, 98, 124);
    private static final Color STONE_BOTTOM = new Color(62, 66, 88);
    private static final Color STONE_LIGHT = new Color(150, 158, 190, 200);
    private static final Color STONE_DARK = new Color(28, 30, 44, 220);
    private static final Color STONE_EDGE = new Color(160, 165, 200, 160);
    private static final Color CARVED = new Color(30, 32, 46, 200);
    private static final Color STEM = new Color(150, 170, 190);
    private static final Color CAP_TEAL = new Color(80, 215, 205);
    private static final Color CAP_BLUE = new Color(130, 190, 255);
    private static final Color RUNE = new Color(255, 215, 140);

    private static final int SIDE = 14;       // margen a los lados de cada repisa (musgo que cuelga)
    private static final int TOP = 26;        // margen arriba (musgo y hongos sobre la orilla)
    private static final double RUNE_SIZE = 9;
    private static final double RUNE_NEAR = 80, RUNE_FAR = 420;   // distancias a la Lumbre (px)

    /** Una imagen ya dibujada y su posición en el mundo. */
    private static class Sprite {
        final BufferedImage img;
        final double x, y;
        Sprite(BufferedImage img, double x, double y) { this.img = img; this.x = x; this.y = y; }
    }

    /** Brillo de un grupo de hongos, o de una runa. */
    private static class Glow {
        final double x, y, phase;
        final BufferedImage img;
        Glow(double x, double y, double phase, BufferedImage img) { this.x = x; this.y = y; this.phase = phase; this.img = img; }
    }

    private final List<Sprite> props = new ArrayList<>();
    private final List<Sprite> platforms = new ArrayList<>();
    private final List<Sprite> blocks = new ArrayList<>();
    private final List<Glow> mushroomGlows = new ArrayList<>();
    private final List<Glow> runes = new ArrayList<>();
    private final BufferedImage glowTeal = renderGlow(CAP_TEAL, 28);
    private final BufferedImage glowBlue = renderGlow(CAP_BLUE, 28);
    private final BufferedImage runeGlow = renderRuneGlow();
    private double time;

    public LevelArt(Level level, long seed) {
        Random rnd = new Random(seed);
        int i = 0;
        for (Rectangle2D r : level.getPlatforms()) {
            if (r.getWidth() >= 400) props.add(renderColumn(r, rnd));
            platforms.add(renderPlatform(r, rnd, i++ % 2 == 0));
        }
        for (Block b : level.getBlocks()) {
            Rectangle2D r = b.getBounds();
            blocks.add(renderBlock(r, rnd));
            runes.add(new Glow(r.getCenterX(), r.getCenterY(), rnd.nextDouble() * Math.PI * 2, runeGlow));
        }
    }

    public void update(double dt) {
        time += dt;
    }

    /** Decoración que va detrás de las sombras: columnas rotas. */
    public void drawBehind(Graphics2D g, double camX) {
        drawSprites(g, props, camX);
    }

    /** Repisas, hongos, losas y runas. La Lumbre enciende las runas cercanas. */
    public void drawFront(Graphics2D g, double camX, LightSource light) {
        drawSprites(g, platforms, camX);
        Composite old = g.getComposite();
        for (Glow m : mushroomGlows) {
            if (m.x < camX - 40 || m.x > camX + Config.WIDTH + 40) continue;
            float a = (float) (0.55 + 0.3 * Math.sin(time * 1.3 + m.phase));   // pulso lento
            drawCentered(g, m, a);
        }
        drawSprites(g, blocks, camX);
        for (Glow r : runes) {
            // Entre más cerca está la Lumbre, más brilla: 1 a RUNE_NEAR px o menos, 0 a RUNE_FAR o más
            double d = Math.hypot(light.getX() - r.x, light.getY() - r.y);
            double near = Math.max(0, Math.min(1, (RUNE_FAR - d) / (RUNE_FAR - RUNE_NEAR)));
            if (near < 0.01) continue;
            drawCentered(g, r, (float) (near * (0.8 + 0.2 * Math.sin(time * 2.4 + r.phase))));
        }
        g.setComposite(old);
    }

    private static void drawCentered(Graphics2D g, Glow glow, float alpha) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.drawImage(glow.img, (int) Math.round(glow.x - glow.img.getWidth() / 2.0),
                (int) Math.round(glow.y - glow.img.getHeight() / 2.0), null);
    }

    private static void drawSprites(Graphics2D g, List<Sprite> sprites, double camX) {
        for (Sprite s : sprites) {
            if (s.x + s.img.getWidth() < camX || s.x > camX + Config.WIDTH) continue;   // fuera de la pantalla
            g.drawImage(s.img, (int) Math.round(s.x), (int) Math.round(s.y), null);
        }
    }

    // =========================================================
    //  REPISAS DE ROCA
    // =========================================================

    private Sprite renderPlatform(Rectangle2D r, Random rnd, boolean teal) {
        int w = (int) Math.ceil(r.getWidth()), h = (int) Math.ceil(r.getHeight());
        BufferedImage img = Background.newImage(w + 2 * SIDE, h + TOP);
        Graphics2D g = Background.smooth(img);
        g.translate(SIDE, TOP);   // desde aquí, (0, 0) es la esquina de arriba a la izquierda de la repisa

        double[] left = insets(rnd), right = insets(rnd);
        Path2D body = rockBody(w, h, left, right);
        g.setPaint(new GradientPaint(0, 0, ROCK_TOP, 0, 180, ROCK_BOTTOM));
        g.fill(body);

        // Textura, recortada a la forma de la roca
        Shape oldClip = g.getClip();
        g.clip(body);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(STRATA);
        for (double y = 16 + rnd.nextDouble() * 10; y < h; y += 22 + rnd.nextDouble() * 12) {
            Path2D vein = new Path2D.Double();
            double phase = rnd.nextDouble() * 10;
            vein.moveTo(0, y);
            for (double x = 0; x <= w + 24; x += 24) vein.lineTo(x, y + Math.sin(x * 0.03 + phase) * 2.5);
            g.draw(vein);
        }
        for (int k = 0; k < w * Math.min(h, 220) / 4500; k++) {
            double pw = 6 + rnd.nextDouble() * 12, ph = 4 + rnd.nextDouble() * 6;
            Ellipse2D pebble = new Ellipse2D.Double(rnd.nextDouble() * w, 8 + rnd.nextDouble() * Math.min(h, 220), pw, ph);
            g.setColor(PEBBLE);
            g.fill(pebble);
            g.setColor(PEBBLE_LINE);
            g.draw(pebble);
        }
        g.setColor(CRACK);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int k = 0; k < 2 + w / 150; k++) {
            boolean fromTop = rnd.nextBoolean();
            double x = fromTop ? 10 + rnd.nextDouble() * (w - 20) : (rnd.nextBoolean() ? 2 : w - 2);
            double y = fromTop ? 0 : 10 + rnd.nextDouble() * Math.min(h - 10, 120);
            drawCrack(g, rnd, x, y, 30 + rnd.nextDouble() * 80, true);
        }
        g.setClip(oldClip);

        // Borde claro arriba: aquí se puede pisar
        g.setColor(RIM);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Line2D.Double(left[0] + 4, 1, w - right[0] - 4, 1));

        drawMoss(g, rnd, w, left[0], right[0]);
        for (double x = 26 + rnd.nextDouble() * 60; x < w - 20; x += 110 + rnd.nextDouble() * 120) {
            drawMushrooms(g, rnd, x, r, teal);
        }
        g.dispose();
        return new Sprite(img, r.getX() - SIDE, r.getY() - TOP);
    }

    /** Cuánto se mete cada lado de la roca (0 a 7 px), con el mismo terreno fractal del fondo. */
    private static double[] insets(Random rnd) {
        double[] d = Background.midpointDisplacement(rnd, 16, 1, 0.6);
        for (int i = 0; i < d.length; i++) d[i] = Math.max(0, Math.min(7, 3.5 + 3 * d[i]));
        return d;
    }

    /** La forma de la roca: arriba es recta (donde se pisa), los lados son irregulares. */
    private static Path2D rockBody(double w, double h, double[] left, double[] right) {
        int n = left.length - 1;
        Path2D p = new Path2D.Double();
        p.moveTo(left[0] + 5, 0);
        p.lineTo(w - right[0] - 5, 0);
        p.quadTo(w - right[0], 0, w - right[0], 5);   // esquina redondeada
        for (int i = 1; i <= n; i++) p.lineTo(w - right[i], h * i / n);
        for (int i = n; i >= 1; i--) p.lineTo(left[i], h * i / n);
        p.lineTo(left[0], 5);
        p.quadTo(left[0], 0, left[0] + 5, 0);
        p.closePath();
        return p;
    }

    /** Grieta: una caminata al azar hacia abajo; a veces se parte en dos. */
    private static void drawCrack(Graphics2D g, Random rnd, double x, double y, double len, boolean canBranch) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        double angle = Math.PI / 2 + (rnd.nextDouble() - 0.5) * 0.8;
        for (double walked = 0; walked < len; ) {
            double seg = 6 + rnd.nextDouble() * 10;
            angle = Math.max(0.35, Math.min(Math.PI - 0.35, angle + (rnd.nextDouble() - 0.5) * 0.9));
            x += Math.cos(angle) * seg;
            y += Math.sin(angle) * seg;
            p.lineTo(x, y);
            walked += seg;
            if (canBranch && rnd.nextDouble() < 0.15) drawCrack(g, rnd, x, y, len * 0.4, false);
        }
        g.draw(p);
    }

    /** Musgo en manchas sobre la orilla (con huecos para que se vea el borde) y hebras que cuelgan. */
    private static void drawMoss(Graphics2D g, Random rnd, double w, double left0, double right0) {
        for (double x = left0 + 2; x < w - right0 - 6; ) {
            double patch = 20 + rnd.nextDouble() * 60;
            for (double px = x; px < Math.min(x + patch, w - right0 - 2); px += 3 + rnd.nextDouble() * 4) {
                double mw = 5 + rnd.nextDouble() * 8, mh = 2 + rnd.nextDouble() * 4;
                g.setColor(mix(MOSS_DARK, MOSS_LIGHT, rnd.nextDouble() * 0.5));
                g.fill(new Ellipse2D.Double(px - mw / 2, -mh, mw, mh + 5));
                if (rnd.nextDouble() < 0.5) {   // briznas claras
                    g.setColor(Background.alpha(MOSS_LIGHT, 170));
                    g.setStroke(new BasicStroke(1f));
                    g.draw(new Line2D.Double(px, 0, px + rnd.nextDouble() * 2 - 1, -3 - rnd.nextDouble() * 5));
                }
            }
            x += patch + 10 + rnd.nextDouble() * 50;
        }
        // Hebras que cuelgan por los dos lados, desde la orilla de arriba
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int side = 0; side < 2; side++) {
            double baseX = side == 0 ? left0 : w - right0;
            for (int k = 0; k < 2 + rnd.nextInt(3); k++) {
                double x = baseX + (side == 0 ? -1 : 1) * rnd.nextDouble() * 3;
                double len = 14 + rnd.nextDouble() * 50, sway = 2 + rnd.nextDouble() * 4;
                Path2D strand = new Path2D.Double();
                strand.moveTo(x, 1);
                strand.curveTo(x + sway, len * 0.33, x - sway, len * 0.66, x + sway * 0.5, len);
                g.setColor(rnd.nextBoolean() ? MOSS_DARK : mix(MOSS_DARK, MOSS_LIGHT, 0.4));
                g.draw(strand);
            }
        }
        g.setStroke(old);
    }

    /** Un grupo de 1 a 3 hongos con sombrero luminoso. Su brillo se dibuja aparte, con pulso. */
    private void drawMushrooms(Graphics2D g, Random rnd, double cx, Rectangle2D r, boolean teal) {
        int n = 1 + rnd.nextInt(3);
        Color cap = teal ? CAP_TEAL : CAP_BLUE;
        double tallest = 0;
        for (int k = 0; k < n; k++) {
            double x = cx + (k - (n - 1) / 2.0) * (7 + rnd.nextDouble() * 4);
            double stem = 5 + rnd.nextDouble() * 11, cw = 6 + rnd.nextDouble() * 7;
            tallest = Math.max(tallest, stem);
            g.setColor(STEM);
            g.fill(new RoundRectangle2D.Double(x - 1.2, -stem, 2.4, stem + 3, 2, 2));
            g.setColor(cap);
            g.fill(new Arc2D.Double(x - cw / 2, -stem - cw * 0.35, cw, cw * 0.7, 0, 180, Arc2D.CHORD));
            g.setColor(new Color(235, 255, 255, 190));
            g.fill(new Ellipse2D.Double(x - cw * 0.15, -stem - cw * 0.28, cw * 0.3, cw * 0.14));
        }
        mushroomGlows.add(new Glow(r.getX() + cx, r.getY() - tallest, rnd.nextDouble() * Math.PI * 2,
                teal ? glowTeal : glowBlue));
    }

    // =========================================================
    //  LOSAS, COLUMNAS Y BRILLOS
    // =========================================================

    /** Losa de piedra tallada: bisel, marco grabado, una grieta y la runa del Gran Farol. */
    private static Sprite renderBlock(Rectangle2D r, Random rnd) {
        int w = (int) Math.round(r.getWidth()), h = (int) Math.round(r.getHeight()), pad = 2;
        BufferedImage img = Background.newImage(w + 2 * pad, h + 2 * pad);
        Graphics2D g = Background.smooth(img);
        g.translate(pad, pad);

        RoundRectangle2D body = new RoundRectangle2D.Double(0, 0, w, h, 6, 6);
        g.setPaint(new GradientPaint(0, 0, STONE_TOP, 0, h, STONE_BOTTOM));
        g.fill(body);
        g.setStroke(new BasicStroke(2f));
        g.setColor(STONE_LIGHT);                       // luz arriba y a la izquierda
        g.draw(new Line2D.Double(3, 1.5, w - 3, 1.5));
        g.draw(new Line2D.Double(1.5, 3, 1.5, h - 3));
        g.setColor(STONE_DARK);                        // sombra abajo y a la derecha
        g.draw(new Line2D.Double(3, h - 1.5, w - 3, h - 1.5));
        g.draw(new Line2D.Double(w - 1.5, 3, w - 1.5, h - 3));

        g.setStroke(new BasicStroke(1f));
        g.setColor(CARVED);
        g.draw(new RoundRectangle2D.Double(5, 5, w - 10, h - 10, 4, 4));

        Shape oldClip = g.getClip();
        g.clip(body);
        g.setColor(CRACK);
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawCrack(g, rnd, rnd.nextBoolean() ? w * 0.2 : w * 0.8, 0, h * 0.7, false);
        g.setClip(oldClip);

        // Runa grabada: una línea clara debajo de la oscura da efecto de tallado
        Path2D rune = rune(w / 2.0, h / 2.0, RUNE_SIZE);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Background.alpha(STONE_LIGHT, 90));
        g.translate(0, 1);
        g.draw(rune);
        g.translate(0, -1);
        g.setColor(CARVED);
        g.draw(rune);

        g.setStroke(new BasicStroke(1f));
        g.setColor(STONE_EDGE);
        g.draw(body);
        g.dispose();
        return new Sprite(img, r.getX() - pad, r.getY() - pad);
    }

    /** La runa: el rombo del Gran Farol, atravesado por una línea. */
    private static Path2D rune(double cx, double cy, double s) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - s);
        p.lineTo(cx + s * 0.6, cy);
        p.lineTo(cx, cy + s);
        p.lineTo(cx - s * 0.6, cy);
        p.closePath();
        p.moveTo(cx, cy - s - 4);
        p.lineTo(cx, cy + s + 4);
        return p;
    }

    /** Columna rota oscura, de pie sobre una repisa ancha. Es solo decoración. */
    private static Sprite renderColumn(Rectangle2D r, Random rnd) {
        double colW = 26 + rnd.nextDouble() * 10, colH = 70 + rnd.nextDouble() * 70;
        double cx = r.getX() + 60 + rnd.nextDouble() * (r.getWidth() - 140);
        int iw = (int) Math.ceil(colW + 24), ih = (int) Math.ceil(colH + 10);
        BufferedImage img = Background.newImage(iw, ih);
        Graphics2D g = Background.smooth(img);
        g.setColor(PROP);
        g.fill(Background.column(iw / 2.0, ih - 2, colW, colH, rnd, true));
        g.dispose();
        return new Sprite(img, cx - iw / 2.0, r.getY() + 8 - ih);
    }

    /** Brillo suave de un color, en una imagen que se reutiliza. */
    private static BufferedImage renderGlow(Color c, int radius) {
        BufferedImage img = Background.newImage(radius * 2, radius * 2);
        Graphics2D g = Background.smooth(img);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(radius, radius), radius,
                new float[] { 0f, 1f }, new Color[] { Background.alpha(c, 110), Background.alpha(c, 0) }));
        g.fillRect(0, 0, radius * 2, radius * 2);
        g.dispose();
        return img;
    }

    /** La runa encendida: un halo cálido con el trazo de la runa en color de la Lumbre. */
    private static BufferedImage renderRuneGlow() {
        int s = 48;
        BufferedImage img = Background.newImage(s, s);
        Graphics2D g = Background.smooth(img);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(s / 2.0, s / 2.0), s / 2f,
                new float[] { 0f, 1f }, new Color[] { Background.alpha(RUNE, 90), Background.alpha(RUNE, 0) }));
        g.fillRect(0, 0, s, s);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(RUNE);
        g.draw(rune(s / 2.0, s / 2.0, RUNE_SIZE));
        g.dispose();
        return img;
    }

    /** Interpolación lineal entre dos colores. */
    private static Color mix(Color a, Color b, double t) {
        return new Color(
            (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
