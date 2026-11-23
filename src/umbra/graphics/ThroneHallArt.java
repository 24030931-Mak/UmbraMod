package umbra.graphics;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * [v0.9.5] El fondo de la sala del trono, arriba de la Cúpula, detrás del Gran Farol:
 *   - la pared de sillares que se pierde en la oscuridad hacia arriba, con el arco del domo;
 *   - el rosetón de 6 pétalos tallado en el domo, detrás del candelabro (un pétalo por fragmento);
 *   - dos columnas talladas, con un estandarte con la runa del rombo (roto, salvo en la época dorada);
 *   - dos relieves de criaturas pequeñas inclinadas ante una llama con ojos, mirando al trono;
 *   - dos braseros a los lados del estrado, y el piso de losas que se aleja hacia la pared.
 *
 * Lo quieto (drawStatic) se dibuja una sola vez en una imagen al entrar a la zona, como el
 * decorado de LevelArt; lo que brilla o se mueve (drawLights, drawDust) se dibuja en cada cuadro
 * con formas y pinturas fijas, cambiando solo la matriz (el rosetón encendido, que en el juego
 * solo cambia al tomar un fragmento, se guarda dibujado: ver roseCache). 'golden' es la versión de la intro: la
 * misma sala en su mejor época, con piedra cálida, oro y los estandartes enteros.
 * [v0.9.5] En la época dorada el Farol estaba encendido, así que drawStatic(golden) ya trae el
 * rosetón encendido (sus 6 pétalos y el centro) y brasas en los braseros en vez de ceniza; el fuego
 * de los braseros, que se mueve, sigue siendo de drawLights.
 *
 * Todas las medidas están en una sala de referencia de 640 x 470 (la de la Cúpula) y se reparten
 * en la sala que llegue: las posiciones con su ancho y su alto, los tamaños con la menor de las
 * dos escalas, para que nada se deforme si la sala tiene otra forma (la de la intro).
 */
public final class ThroneHallArt {

    private static final double REF_W = 640, REF_H = 470;

    // ---- Dónde va cada cosa (en la sala de referencia) ----
    private static final double ROSE_X = 320, ROSE_Y = 120, ROSE_R = 92;
    private static final double[] COLUMN_X = { 54, 586 };
    private static final double COLUMN_W = 44, CAPITAL_Y = 146;
    private static final double BANNER_Y = 172, BANNER_W = 54, BANNER_H = 126;
    private static final double RELIEF_Y = 372, RELIEF_W = 124, RELIEF_H = 64;
    private static final double[] RELIEF_X = { 82, 434 };
    private static final double[] BRAZIER_X = { 224, 416 };
    private static final double BRAZIER_Y = 442;   // el borde de arriba del cuenco
    private static final double FLOOR_DEPTH = 18;  // el piso de losas que se ve detrás de la orilla

    /** Los colores de la sala: la de hoy, fría y en ruinas, y la de la época dorada. */
    private static final class Palette {
        final Color wallTop, wallBottom, joint, bevel, stoneLo, stone, stoneHi, window, petal, tracery;
        final Color cloth, trim, carving, carvingShade, relief, flame, floorBack, floorFront;
        Palette(boolean golden) {
            if (golden) {
                wallTop = new Color(24, 17, 12); wallBottom = new Color(74, 54, 36);
                joint = new Color(20, 12, 8, 150); bevel = new Color(230, 190, 130, 60);
                stoneLo = new Color(66, 48, 32); stone = new Color(122, 94, 62); stoneHi = new Color(188, 150, 98);
                window = new Color(46, 30, 18); petal = new Color(150, 98, 46, 200); tracery = new Color(214, 168, 96);
                cloth = new Color(138, 30, 42); trim = new Color(232, 178, 82); carving = new Color(236, 196, 120, 170);
                carvingShade = new Color(30, 18, 10, 150); relief = new Color(58, 42, 28); flame = new Color(250, 196, 96, 220);
                floorBack = new Color(52, 38, 26); floorFront = new Color(118, 90, 60);
            } else {
                wallTop = new Color(5, 7, 12); wallBottom = new Color(26, 32, 48);
                joint = new Color(6, 8, 14, 170); bevel = new Color(120, 136, 172, 46);
                stoneLo = new Color(28, 33, 46); stone = new Color(56, 63, 84); stoneHi = new Color(96, 106, 134);
                window = new Color(11, 15, 26); petal = new Color(52, 64, 92, 170); tracery = new Color(92, 102, 130);
                cloth = new Color(70, 28, 38); trim = new Color(190, 146, 74, 150); carving = new Color(150, 165, 196, 120);
                carvingShade = new Color(4, 6, 12, 160); relief = new Color(17, 22, 36); flame = new Color(160, 176, 208, 140);
                floorBack = new Color(20, 24, 36); floorFront = new Color(52, 58, 78);
            }
        }
    }

    private static final Palette RUINED = new Palette(false), GOLDEN = new Palette(true);

    // ---- Lo que brilla (formas y pinturas fijas, con el origen en su centro) ----
    private static final Color PETAL_LIT = new Color(244, 184, 84, 225);
    private static final Color PETAL_CORE = new Color(255, 236, 180, 200);
    private static final Color BOSS_LIT = new Color(255, 217, 138);
    private static final Color LIT_TRACERY = new Color(84, 78, 92);
    private static final Color FIRE_OUT = new Color(255, 160, 64);
    private static final Color FIRE_IN = new Color(255, 232, 170);
    private static final Color DUST = new Color(240, 206, 140);
    private static final Path2D.Double PETAL = new Path2D.Double();        // a lo largo de +x, rosetón de radio 100
    private static final Path2D.Double PETAL_INNER = new Path2D.Double();
    private static final Path2D.Double FLAME = new Path2D.Double();        // alto 2.5, origen en su centro
    private static final Rectangle2D.Double ROSE_GLOW_BOX = new Rectangle2D.Double(-150, -150, 300, 300);
    private static final Ellipse2D.Double BOSS = new Ellipse2D.Double(-16, -16, 32, 32);
    private static final Rectangle2D.Double FIRE_GLOW_BOX = new Rectangle2D.Double(-64, -86, 128, 128);
    private static final Ellipse2D.Double EMBERS = new Ellipse2D.Double(-15, -5, 30, 7);
    private static final Paint ROSE_GLOW = new RadialGradientPaint(0f, 0f, 150f, new float[] { 0f, 0.6f, 1f },
            new Color[] { new Color(255, 190, 96, 170), new Color(255, 190, 96, 70), new Color(255, 190, 96, 0) });
    private static final Paint FIRE_GLOW = new RadialGradientPaint(0f, -20f, 64f, new float[] { 0f, 1f },
            new Color[] { new Color(255, 170, 80, 150), new Color(255, 170, 80, 0) });
    private static final Paint EMBER_PAINT = new RadialGradientPaint(0f, -1f, 15f, new float[] { 0f, 1f },
            new Color[] { new Color(255, 200, 110), new Color(190, 70, 30) });
    private static final Stroke TRACERY = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final AlphaComposite[] ALPHA = new AlphaComposite[65];

    // ---- El rosetón encendido ya dibujado (uno solo: la última sala y cantidad que se repitió) ----
    private static BufferedImage roseCache;
    private static double roseW = -1, roseH = -1, askedW = -1, askedH = -1;
    private static int roseLit = -1, askedLit = -1;

    static {
        for (int i = 0; i < ALPHA.length; i++) ALPHA[i] = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, i / 64f);
        PETAL.moveTo(0, 0);
        PETAL.quadTo(35, -28, 92, 0);
        PETAL.quadTo(35, 28, 0, 0);
        PETAL.closePath();
        PETAL_INNER.moveTo(20, 0);
        PETAL_INNER.quadTo(44, -12, 74, 0);
        PETAL_INNER.quadTo(44, 12, 20, 0);
        PETAL_INNER.closePath();
        FLAME.moveTo(0, -1.5);
        FLAME.curveTo(0.9, -0.4, 1, 0.6, 0, 1);
        FLAME.curveTo(-1, 0.6, -0.9, -0.4, 0, -1.5);
        FLAME.closePath();
    }

    private ThroneHallArt() { }

    // ---- Medidas en la sala que llegue ----
    private static double sx(Rectangle2D hall) { return hall.getWidth() / REF_W; }
    private static double sy(Rectangle2D hall) { return hall.getHeight() / REF_H; }
    private static double size(Rectangle2D hall) { return Math.min(sx(hall), sy(hall)); }
    private static double px(Rectangle2D hall, double x) { return hall.getX() + x * sx(hall); }
    private static double py(Rectangle2D hall, double y) { return hall.getY() + y * sy(hall); }

    // =========================================================
    //  Lo quieto (una sola vez, en una imagen)
    // =========================================================

    /**
     * La pared, el arco del domo, el marco del rosetón con sus pétalos apagados, las columnas con
     * sus estandartes, los relieves, los braseros apagados y el piso. Rellena toda la sala.
     */
    public static void drawStatic(Graphics2D g, Rectangle2D hall, boolean golden) {
        Palette p = golden ? GOLDEN : RUINED;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.clip(hall);
        Random rnd = new Random(golden ? 1201 : 1153);   // siempre la misma sala
        wall(g2, hall, p, rnd, golden);
        vault(g2, hall, p);
        rosetteFrame(g2, hall, p);
        if (golden) {   // [v0.9.5] en la época dorada, el rosetón siempre encendido
            AffineTransform saved = g2.getTransform();
            g2.translate(px(hall, ROSE_X), py(hall, ROSE_Y));
            g2.scale(ROSE_R * size(hall) / 100, ROSE_R * size(hall) / 100);
            litRosette(g2, 6);
            g2.setTransform(saved);
        }
        floor(g2, hall, p);
        for (int i = 0; i < 2; i++) {
            column(g2, hall, p, COLUMN_X[i], rnd, golden);
            banner(g2, hall, p, COLUMN_X[i], i, golden);
            relief(g2, hall, p, RELIEF_X[i], i == 0 ? 1 : -1, golden);
            brazier(g2, hall, p, BRAZIER_X[i], golden);
        }
        g2.dispose();
    }

    /** La pared de sillares: más clara abajo; arriba se pierde en lo oscuro. */
    private static void wall(Graphics2D g, Rectangle2D hall, Palette p, Random rnd, boolean golden) {
        double x0 = hall.getX(), y0 = hall.getY(), w = hall.getWidth(), h = hall.getHeight(), k = size(hall);
        g.setPaint(new GradientPaint(0, (float) y0, p.wallTop, 0, (float) (y0 + h), p.wallBottom));
        g.fill(hall);
        double rowH = 32 * k, blockW = 66 * k;
        g.setStroke(new BasicStroke((float) Math.max(1, 1.2 * k)));
        int row = 0;
        for (double y = y0 + h - FLOOR_DEPTH * sy(hall); y > y0; y -= rowH, row++) {
            double fade = Math.min(1, Math.max(0, (y - y0) / h * 1.6));   // arriba casi no se ven
            double off = (row % 2) * blockW / 2 - rnd.nextDouble() * 10 * k;
            g.setColor(Background.alpha(p.joint, (int) (p.joint.getAlpha() * fade)));
            g.draw(new Line2D.Double(x0, y, x0 + w, y));
            for (double x = x0 + off; x < x0 + w; x += blockW * (0.8 + rnd.nextDouble() * 0.4)) {
                g.setColor(Background.alpha(p.joint, (int) (p.joint.getAlpha() * fade)));
                g.draw(new Line2D.Double(x, y - rowH, x, y));
                g.setColor(Background.alpha(p.bevel, (int) (p.bevel.getAlpha() * fade)));   // el bisel de cada sillar
                g.draw(new Line2D.Double(x + 2 * k, y - rowH + 1.5 * k, x + blockW * 0.6, y - rowH + 1.5 * k));
                if (!golden && rnd.nextDouble() < 0.12) {   // grietas de la sala en ruinas
                    g.setColor(Background.alpha(p.joint, (int) (200 * fade)));
                    double cx = x + rnd.nextDouble() * blockW, cy = y - rowH;
                    Path2D crack = new Path2D.Double();
                    crack.moveTo(cx, cy);
                    for (int i = 0; i < 3; i++) {
                        cx += (rnd.nextDouble() - 0.5) * 14 * k;
                        cy += rowH / 3;
                        crack.lineTo(cx, cy);
                    }
                    g.draw(crack);
                }
            }
        }
        // Arriba, la oscuridad
        g.setPaint(new GradientPaint(0, (float) y0, new Color(0, 0, 0, golden ? 150 : 235), 0, (float) (y0 + h * 0.42), new Color(0, 0, 0, 0)));
        g.fill(new Rectangle2D.Double(x0, y0, w, h * 0.42));
    }

    /** El arco del domo, que nace de los capiteles de las columnas; afuera del arco, más sombra. */
    private static void vault(Graphics2D g, Rectangle2D hall, Palette p) {
        double k = size(hall);
        double l = px(hall, COLUMN_X[0]), r = px(hall, COLUMN_X[1]), spring = py(hall, CAPITAL_Y), apex = py(hall, 6);
        double cx = px(hall, ROSE_X);
        Path2D arch = new Path2D.Double();
        arch.moveTo(l, hall.getMaxY());
        arch.lineTo(l, spring);
        arch.curveTo(l, spring - (spring - apex) * 0.7, cx - (cx - l) * 0.55, apex, cx, apex);
        arch.curveTo(cx + (r - cx) * 0.55, apex, r, spring - (spring - apex) * 0.7, r, spring);
        arch.lineTo(r, hall.getMaxY());
        arch.closePath();
        Area outside = new Area(hall);
        outside.subtract(new Area(arch));
        g.setColor(new Color(0, 0, 0, 110));
        g.fill(outside);
        // La moldura del arco: una banda oscura con su bisel claro por dentro
        Path2D rim = new Path2D.Double();
        rim.moveTo(l, spring);
        rim.curveTo(l, spring - (spring - apex) * 0.7, cx - (cx - l) * 0.55, apex, cx, apex);
        rim.curveTo(cx + (r - cx) * 0.55, apex, r, spring - (spring - apex) * 0.7, r, spring);
        g.setStroke(new BasicStroke((float) (9 * k), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(p.stoneLo);
        g.draw(rim);
        g.setStroke(new BasicStroke((float) Math.max(1, 1.6 * k)));
        g.setColor(Background.alpha(p.stoneHi, 110));
        g.translate(0, 4.5 * k);
        g.draw(rim);
        g.translate(0, -4.5 * k);
    }

    /** El marco de piedra del rosetón, su ventana oscura y los 6 pétalos apagados con su tracería. */
    private static void rosetteFrame(Graphics2D g, Rectangle2D hall, Palette p) {
        double k = size(hall), cx = px(hall, ROSE_X), cy = py(hall, ROSE_Y), r = ROSE_R * k;
        g.setColor(p.stoneLo);
        g.fill(new Ellipse2D.Double(cx - r - 16 * k, cy - r - 16 * k, (r + 16 * k) * 2, (r + 16 * k) * 2));
        g.setPaint(new GradientPaint(0, (float) (cy - r), p.stoneHi, 0, (float) (cy + r), p.stoneLo));
        g.setStroke(new BasicStroke((float) (7 * k)));
        g.draw(new Ellipse2D.Double(cx - r - 8 * k, cy - r - 8 * k, (r + 8 * k) * 2, (r + 8 * k) * 2));
        g.setColor(p.window);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        // 12 dientes alrededor: el borde tallado
        g.setColor(p.stone);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6, dx = Math.cos(a) * (r + 12 * k), dy = Math.sin(a) * (r + 12 * k);
            g.fill(new Ellipse2D.Double(cx + dx - 4 * k, cy + dy - 4 * k, 8 * k, 8 * k));
        }
        AffineTransform saved = g.getTransform();
        g.translate(cx, cy);
        g.scale(r / 100, r / 100);
        for (int i = 0; i < 6; i++) {
            g.rotate(petalAngle(i));
            g.setColor(p.petal);
            g.fill(PETAL);
            g.setStroke(TRACERY);
            g.setColor(p.tracery);
            g.draw(PETAL);
            g.rotate(-petalAngle(i));
        }
        for (int i = 0; i < 6; i++) {   // entre pétalo y pétalo, un ojo de vidrio chico
            double a = petalAngle(i) + Math.PI / 6;
            Ellipse2D eye = new Ellipse2D.Double(Math.cos(a) * 78 - 8, Math.sin(a) * 78 - 8, 16, 16);
            g.setColor(p.petal);
            g.fill(eye);
            g.setColor(p.tracery);
            g.draw(eye);
        }
        g.setColor(p.stoneLo);
        g.fill(BOSS);
        g.setColor(p.tracery);
        g.draw(BOSS);
        g.setTransform(saved);
    }

    /** El ángulo del pétalo i: el 0 arriba y luego en el sentido de las manecillas. */
    private static double petalAngle(int i) { return -Math.PI / 2 + i * Math.PI / 3; }

    /** El piso de losas que se aleja hacia la pared, encima de la orilla donde se pisa. */
    private static void floor(Graphics2D g, Rectangle2D hall, Palette p) {
        double k = size(hall), y1 = hall.getMaxY(), y0 = y1 - FLOOR_DEPTH * sy(hall), x0 = hall.getX(), w = hall.getWidth();
        g.setPaint(new GradientPaint(0, (float) y0, p.floorBack, 0, (float) y1, p.floorFront));
        g.fill(new Rectangle2D.Double(x0, y0, w, y1 - y0));
        g.setStroke(new BasicStroke((float) Math.max(1, 1.1 * k)));
        g.setColor(p.joint);
        g.draw(new Line2D.Double(x0, y0 + (y1 - y0) * 0.42, x0 + w, y0 + (y1 - y0) * 0.42));   // una hilera más atrás
        double vx = hall.getCenterX(), vy = y0 - 240 * k;   // las juntas van hacia el fondo, al centro
        for (double x = x0 - 40 * k; x < x0 + w + 40 * k; x += 52 * k) {
            double t = (y1 - y0) / (y1 - vy);
            g.draw(new Line2D.Double(x, y1, x + (vx - x) * t, y0));
        }
        g.setColor(Background.alpha(p.bevel, 120));   // la orilla de atrás, contra la pared
        g.draw(new Line2D.Double(x0, y0, x0 + w, y0));
    }

    /** Una columna tallada con estrías que sube y se pierde en lo oscuro, con capitel y basa. */
    private static void column(Graphics2D g, Rectangle2D hall, Palette p, double refX, Random rnd, boolean golden) {
        double k = size(hall), cx = px(hall, refX), cw = COLUMN_W * k;
        double top = hall.getY(), cap = py(hall, CAPITAL_Y), base = hall.getMaxY() - FLOOR_DEPTH * sy(hall) * 0.5;
        g.setPaint(new GradientPaint((float) (cx - cw / 2), 0, p.stoneLo, (float) (cx + cw * 0.1), 0, p.stoneHi, true));
        g.fill(new Rectangle2D.Double(cx - cw / 2, top, cw, base - top));
        g.setStroke(new BasicStroke((float) Math.max(1, 1.4 * k)));
        g.setColor(new Color(0, 0, 0, 90));
        for (int i = 1; i < 4; i++) {   // las estrías
            double fx = cx - cw / 2 + cw * i / 4;
            g.draw(new Line2D.Double(fx, cap + 10 * k, fx, base - 12 * k));
        }
        // Capitel (dos molduras) y basa
        g.setColor(p.stone);
        g.fill(new Rectangle2D.Double(cx - cw / 2 - 9 * k, cap - 4 * k, cw + 18 * k, 10 * k));
        g.fill(new Rectangle2D.Double(cx - cw / 2 - 4 * k, cap + 6 * k, cw + 8 * k, 5 * k));
        g.fill(new Rectangle2D.Double(cx - cw / 2 - 7 * k, base - 12 * k, cw + 14 * k, 12 * k));
        g.setColor(Background.alpha(p.stoneHi, 150));
        g.draw(new Line2D.Double(cx - cw / 2 - 9 * k, cap - 3.5 * k, cx + cw / 2 + 9 * k, cap - 3.5 * k));
        g.draw(new Line2D.Double(cx - cw / 2 - 7 * k, base - 11.5 * k, cx + cw / 2 + 7 * k, base - 11.5 * k));
        if (!golden) {   // un pedazo que falta en la orilla del capitel
            g.setColor(p.wallTop);
            Path2D chip = new Path2D.Double();
            double chipX = cx + (rnd.nextBoolean() ? 1 : -1) * (cw / 2 + 4 * k);
            chip.moveTo(chipX - 6 * k, cap - 4 * k);
            chip.lineTo(chipX + 6 * k, cap - 4 * k);
            chip.lineTo(chipX + 2 * k, cap + 3 * k);
            chip.closePath();
            g.fill(chip);
        }
        // Arriba se pierde en lo oscuro
        g.setPaint(new GradientPaint(0, (float) top, new Color(0, 0, 0, golden ? 160 : 250), 0, (float) (cap - 20 * k), new Color(0, 0, 0, 0)));
        g.fill(new Rectangle2D.Double(cx - cw, top, cw * 2, cap - 20 * k - top));
    }

    /**
     * El estandarte que cuelga de la columna, con la runa del rombo. Roto: deshilachado abajo, con
     * un desgarrón y desteñido. En la época dorada: entero, con cola de golondrina y fleco.
     */
    private static void banner(Graphics2D g, Rectangle2D hall, Palette p, double refX, int which, boolean golden) {
        double k = size(hall), cx = px(hall, refX), y = py(hall, BANNER_Y), w = BANNER_W * k, h = BANNER_H * k;
        double l = cx - w / 2, r = cx + w / 2;
        Path2D cloth = new Path2D.Double();
        cloth.moveTo(l, y);
        cloth.lineTo(r, y);
        if (golden) {
            cloth.lineTo(r, y + h);
            cloth.lineTo(cx, y + h * 0.82);
            cloth.lineTo(l, y + h);
        } else if (which == 0) {   // cada uno roto a su manera
            cloth.lineTo(r, y + h * 0.80);
            cloth.lineTo(cx + w * 0.22, y + h * 0.66);
            cloth.lineTo(cx + w * 0.08, y + h * 0.98);
            cloth.lineTo(cx - w * 0.12, y + h * 0.72);
            cloth.lineTo(cx - w * 0.26, y + h * 0.84);
            cloth.lineTo(l, y + h * 0.62);
        } else {
            cloth.lineTo(r, y + h * 0.58);
            cloth.lineTo(cx + w * 0.30, y + h * 0.76);
            cloth.lineTo(cx + w * 0.10, y + h * 0.64);
            cloth.lineTo(cx - w * 0.06, y + h * 0.92);
            cloth.lineTo(cx - w * 0.30, y + h * 0.70);
            cloth.lineTo(l, y + h * 0.88);
        }
        cloth.closePath();
        Area body = new Area(cloth);
        if (!golden) {   // un desgarrón
            Path2D tear = new Path2D.Double();
            double tx = cx + (which == 0 ? -w * 0.18 : w * 0.16), ty = y + h * 0.5;
            tear.moveTo(tx, ty - 9 * k);
            tear.lineTo(tx + 5 * k, ty);
            tear.lineTo(tx + 1 * k, ty + 11 * k);
            tear.lineTo(tx - 4 * k, ty + 2 * k);
            tear.closePath();
            body.subtract(new Area(tear));
        }
        g.setPaint(new GradientPaint(0, (float) y, p.cloth, 0, (float) (y + h), golden ? p.cloth.darker() : new Color(36, 16, 22)));
        g.fill(body);
        g.setStroke(new BasicStroke((float) Math.max(1, 1.5 * k)));
        g.setColor(p.trim);
        g.draw(body);
        if (golden) {   // un fleco dorado
            g.setStroke(new BasicStroke((float) Math.max(1, 1.2 * k)));
            for (double fx = l + 3 * k; fx < r - 2 * k; fx += 5 * k) {
                double fy = fx < cx ? y + h - (fx - l) / (cx - l) * h * 0.18 : y + h * 0.82 + (fx - cx) / (r - cx) * h * 0.18;
                g.draw(new Line2D.Double(fx, fy, fx, fy + 6 * k));
            }
        }
        // La runa del rombo, con su punto al centro
        double ry = y + h * 0.32, rs = 12 * k;
        Path2D rune = new Path2D.Double();
        rune.moveTo(cx, ry - rs);
        rune.lineTo(cx + rs * 0.7, ry);
        rune.lineTo(cx, ry + rs);
        rune.lineTo(cx - rs * 0.7, ry);
        rune.closePath();
        g.setStroke(new BasicStroke((float) Math.max(1, 2 * k)));
        g.setColor(golden ? p.trim : Background.alpha(p.trim, 170));
        g.draw(rune);
        g.fill(new Ellipse2D.Double(cx - rs * 0.22, ry - rs * 0.22, rs * 0.44, rs * 0.44));
        // La barra de donde cuelga
        g.setColor(p.stoneHi);
        g.fill(new RoundRectangle2D.Double(l - 6 * k, y - 4 * k, w + 12 * k, 5 * k, 3 * k, 3 * k));
    }

    /**
     * Un relieve tallado: criaturas pequeñas inclinadas ante una llama con ojos. 'dir' es hacia
     * dónde miran (+1 a la derecha): la llama queda del lado del trono.
     */
    private static void relief(Graphics2D g, Rectangle2D hall, Palette p, double refX, int dir, boolean golden) {
        double k = size(hall), w = RELIEF_W * k, h = RELIEF_H * k;
        double x = px(hall, refX + RELIEF_W / 2) - w / 2, y = py(hall, RELIEF_Y);
        g.setColor(p.relief);
        g.fill(new Rectangle2D.Double(x, y, w, h));
        g.setStroke(new BasicStroke((float) (4 * k)));
        g.setColor(p.stoneLo);
        g.draw(new Rectangle2D.Double(x, y, w, h));
        g.setStroke(new BasicStroke((float) Math.max(1, 1.2 * k)));
        g.setColor(Background.alpha(p.stoneHi, 120));   // bisel: luz arriba e izquierda
        g.draw(new Line2D.Double(x + 2 * k, y + 2 * k, x + w - 2 * k, y + 2 * k));
        g.draw(new Line2D.Double(x + 2 * k, y + 2 * k, x + 2 * k, y + h - 2 * k));

        // La llama con ojos, del lado del trono
        double fx = dir > 0 ? x + w - 24 * k : x + 24 * k, fy = y + h * 0.50, fs = 14 * k;
        carved(g, p, flameShape(fx, fy, fs), 1.2 * k, golden ? p.flame : p.carving);
        g.setColor(p.relief);
        g.fill(new Ellipse2D.Double(fx - 5 * k, fy - 1 * k, 3.6 * k, 4.4 * k));
        g.fill(new Ellipse2D.Double(fx + 1.4 * k, fy - 1 * k, 3.6 * k, 4.4 * k));
        // El suelo tallado y las criaturas, de rodillas e inclinadas hacia la llama: la espalda
        // curva, la cabeza baja por delante y los brazos estirados hacia ella
        carved(g, p, new Rectangle2D.Double(x + 8 * k, y + h - 9 * k, w - 16 * k, 2 * k), 1 * k, p.carving);
        for (int i = 0; i < 3; i++) {
            double c = 1 - i * 0.08;   // las de atrás, un poco más chicas
            double bx = dir > 0 ? x + 12 * k + i * 28 * k : x + w - 12 * k - i * 28 * k, by = y + h - 9 * k;
            Path2D creature = new Path2D.Double();
            creature.moveTo(-7, 0);
            creature.curveTo(-9, -12, -2, -20, 6, -15);   // la espalda, encorvada
            creature.curveTo(10, -12, 11, -6, 9, -3);
            creature.lineTo(7, 0);
            creature.closePath();
            creature.append(new Ellipse2D.Double(8, -12, 9, 9), false);       // la cabeza, baja
            creature.append(new Ellipse2D.Double(11, -4, 10, 3.5), false);    // los brazos, hacia la llama
            AffineTransform at = AffineTransform.getTranslateInstance(bx, by);
            at.scale(dir * k * c, k * c);
            carved(g, p, at.createTransformedShape(creature), 1.2 * k, p.carving);
        }
    }

    /** Una forma en relieve: su sombra corrida abajo a la derecha y la forma clara encima. */
    private static void carved(Graphics2D g, Palette p, Shape s, double d, Color c) {
        g.setColor(p.carvingShade);
        g.translate(d, d);
        g.fill(s);
        g.translate(-d, -d);
        g.setColor(c);
        g.fill(s);
    }

    private static Path2D flameShape(double x, double y, double s) {
        Path2D f = new Path2D.Double();
        f.moveTo(x, y - s * 1.5);
        f.curveTo(x + s * 0.9, y - s * 0.4, x + s, y + s * 0.6, x, y + s);
        f.curveTo(x - s, y + s * 0.6, x - s * 0.9, y - s * 0.4, x, y - s * 1.5);
        f.closePath();
        return f;
    }

    /**
     * Un brasero apagado: trípode y cuenco de piedra, con ceniza ([v0.9.5] en la época dorada,
     * 'golden', con brasas). El fuego lo pone drawLights.
     */
    private static void brazier(Graphics2D g, Rectangle2D hall, Palette p, double refX, boolean golden) {
        double k = size(hall), x = px(hall, refX), y = py(hall, BRAZIER_Y), floorY = hall.getMaxY() - 2 * k;
        g.setStroke(new BasicStroke((float) (3 * k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(p.stoneHi);
        Path2D legs = new Path2D.Double();
        legs.moveTo(x - 15 * k, floorY);
        legs.lineTo(x, y + 10 * k);
        legs.lineTo(x + 15 * k, floorY);
        legs.moveTo(x, y + 10 * k);
        legs.lineTo(x, floorY);
        g.draw(legs);
        Path2D bowl = new Path2D.Double();
        bowl.moveTo(x - 21 * k, y);
        bowl.lineTo(x + 21 * k, y);
        bowl.quadTo(x + 17 * k, y + 13 * k, x, y + 13 * k);
        bowl.quadTo(x - 17 * k, y + 13 * k, x - 21 * k, y);
        bowl.closePath();
        g.setPaint(new GradientPaint(0, (float) y, p.stoneHi, 0, (float) (y + 13 * k), p.stoneLo));
        g.fill(bowl);
        if (golden) {   // las brasas, como las de drawLights
            AffineTransform saved = g.getTransform();
            g.translate(x, y);
            g.scale(k, k);
            g.setPaint(EMBER_PAINT);
            g.fill(EMBERS);
            g.setTransform(saved);
        } else {
            g.setColor(new Color(24, 26, 34));   // la ceniza
            g.fill(new Ellipse2D.Double(x - 15 * k, y - 3 * k, 30 * k, 6 * k));
        }
        g.setStroke(new BasicStroke((float) Math.max(1, 1.2 * k)));
        g.setColor(Background.alpha(p.stoneHi, 200));
        g.draw(new Line2D.Double(x - 20 * k, y + 0.5 * k, x + 20 * k, y + 0.5 * k));
    }

    // =========================================================
    //  Lo que brilla (en cada cuadro)
    // =========================================================

    /**
     * Los pétalos encendidos del rosetón ('petalsLit' de 0 a 6, del de arriba en el sentido de las
     * manecillas; con los 6 se enciende también el centro) y el fuego de los braseros ('braziers'
     * de 0, apagados, a 1, ardiendo).
     */
    public static void drawLights(Graphics2D g, Rectangle2D hall, int petalsLit, double braziers, double time) {
        AffineTransform saved = g.getTransform();
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();
        Composite oldComposite = g.getComposite();
        double k = size(hall);
        int lit = Math.max(0, Math.min(6, petalsLit));
        if (lit > 0) {
            // El rosetón encendido respira; si ya está dibujado para esta sala y estos pétalos, se copia
            double cx = px(hall, ROSE_X), cy = py(hall, ROSE_Y), scale = ROSE_R * k / 100;
            g.setComposite(alpha(0.88 + 0.12 * Math.sin(time * 1.7)));
            if (roseCache(hall, lit)) {
                g.drawImage(roseCache, (int) Math.round(cx - roseCache.getWidth() / 2.0), (int) Math.round(cy - roseCache.getHeight() / 2.0), null);
            } else {
                g.translate(cx, cy);
                g.scale(scale, scale);
                litRosette(g, lit);
                g.setTransform(saved);
            }
            g.setComposite(oldComposite);
        }
        if (braziers > 0.001) {
            double b = Math.min(1, braziers);
            for (int i = 0; i < 2; i++) {
                double flick = Math.sin(time * 8.3 + i * 2.1), flick2 = Math.sin(time * 13.1 + i);
                g.translate(px(hall, BRAZIER_X[i]), py(hall, BRAZIER_Y));
                g.scale(k, k);
                g.setComposite(alpha(b * (0.85 + 0.15 * flick2)));
                g.setPaint(FIRE_GLOW);
                g.fill(FIRE_GLOW_BOX);
                g.setComposite(oldComposite);
                g.setPaint(EMBER_PAINT);
                g.fill(EMBERS);
                fire(g, -14 * b, 13 * b * (1 + 0.05 * flick), 13 * b * (1 + 0.16 * flick), FIRE_OUT);
                fire(g, -10 * b, 7 * b * (1 + 0.05 * flick2), 7 * b * (1 + 0.16 * flick2), FIRE_IN);
                g.setTransform(saved);
            }
        }
        g.setComposite(oldComposite);
        g.setStroke(oldStroke);
        g.setPaint(oldPaint);
    }

    /**
     * Los pétalos encendidos (los 'lit' primeros) sobre los apagados del fondo, con un solo
     * resplandor que crece con ellos; con los 6, también el centro. En unidades del rosetón
     * (radio 100, origen en su centro).
     */
    private static void litRosette(Graphics2D g, int lit) {
        Composite old = g.getComposite();
        g.setComposite(alpha(lit / 6.0));
        g.setPaint(ROSE_GLOW);
        g.fill(ROSE_GLOW_BOX);
        g.setComposite(old);
        g.setStroke(TRACERY);
        for (int i = 0; i < lit; i++) {
            double a = petalAngle(i);
            g.rotate(a);
            g.setColor(PETAL_LIT);
            g.fill(PETAL);
            g.setColor(PETAL_CORE);
            g.fill(PETAL_INNER);
            g.setColor(LIT_TRACERY);
            g.draw(PETAL);
            g.rotate(-a);
        }
        if (lit == 6) {
            g.setColor(BOSS_LIT);
            g.fill(BOSS);
        }
    }

    /**
     * El rosetón encendido ya dibujado, para una sala de este tamaño y 'lit' pétalos: en el juego
     * solo cambia al tomar un fragmento, así que se dibuja una vez y se copia. Como en
     * CandelabraArt, un tamaño o una cantidad nueva no se arma la primera vez, solo si se repite.
     * Regresa si se puede usar.
     */
    private static boolean roseCache(Rectangle2D hall, int lit) {
        double w = hall.getWidth(), h = hall.getHeight();
        if (roseCache != null && w == roseW && h == roseH && lit == roseLit) return true;
        if (w != askedW || h != askedH || lit != askedLit) {
            askedW = w;
            askedH = h;
            askedLit = lit;
            return false;
        }
        double scale = ROSE_R * size(hall) / 100;
        int s = 2 * (int) Math.ceil(ROSE_GLOW_BOX.getWidth() / 2 * scale + 1);
        BufferedImage img = Background.newImage(s, s);
        Graphics2D cg = Background.smooth(img);
        cg.translate(s / 2.0, s / 2.0);
        cg.scale(scale, scale);
        litRosette(cg, lit);
        cg.dispose();
        roseCache = img;
        roseW = w;
        roseH = h;
        roseLit = lit;
        return true;
    }

    private static void fire(Graphics2D g, double y, double sx, double sy, Color c) {
        if (sx < 0.05 || sy < 0.05) return;
        g.translate(0, y);
        g.scale(sx, sy);
        g.setColor(c);
        g.fill(FLAME);
        g.scale(1 / sx, 1 / sy);
        g.translate(0, -y);
    }

    /**
     * El polvo que cae del domo con los golpes: 'amount' de 0 (nada) a 1. Cada mota baja a su
     * propia velocidad y vuelve a empezar arriba; se calcula con 'time', sin guardar nada.
     */
    public static void drawDust(Graphics2D g, Rectangle2D hall, double amount, double time) {
        if (amount <= 0.001) return;
        Composite old = g.getComposite();
        double k = size(hall);
        g.setColor(DUST);
        for (int i = 0; i < 70; i++) {
            double fx = (i * 0.61803398875) % 1.0;                        // repartidas a lo ancho
            double x = hall.getX() + hall.getWidth() * (0.2 + 0.6 * fx) + Math.sin(time * 1.3 + i) * 5 * k;
            double speed = (40 + (i * 37) % 60) * k;
            double y = hall.getY() + ((i * 53 * k + time * speed) % hall.getHeight());
            double fade = 1 - (y - hall.getY()) / hall.getHeight();   // se apaga al bajar
            g.setComposite(alpha(amount * fade * 0.85));
            int d = (int) Math.max(1, Math.round((1 + i % 2) * k));
            g.fillRect((int) Math.round(x), (int) Math.round(y), d, d);
        }
        g.setComposite(old);
    }

    private static AlphaComposite alpha(double a) {
        a = a < 0 ? 0 : a > 1 ? 1 : a;
        return ALPHA[(int) Math.round(a * (ALPHA.length - 1))];
    }
}
