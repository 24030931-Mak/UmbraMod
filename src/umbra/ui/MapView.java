package umbra.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import umbra.core.Config;
import umbra.entities.Block;
import umbra.math.Mat3;
import umbra.math.Vec2;
import umbra.world.Exploration;
import umbra.world.Level;
import umbra.world.World;

/**
 * El mapa (Tab): los niveles uno junto al otro, dibujados con sus repisas y bloques reales,
 * y lo que Candil ya recorrió iluminado con la luz cálida de Logi.
 *
 * Vista: otra matriz de mundo a pantalla (escala + traslación), igual que la cámara pero fija:
 * todos los niveles juntos miden "ancho total" px y se escalan para caber en MAP_WIDTH.
 *     pantalla = T(margen, y0) * S(s) * mundo,   s = MAP_WIDTH / ancho total
 *
 * Lo no recorrido lo tapa del todo la neblina (MapFog, la misma del círculo de la esquina),
 * con máscara bilineal de zonas: las orillas de lo recorrido salen suaves, no cuadradas.
 */
public class MapView {

    private static final double MAP_WIDTH = 1160;
    /** Segundos que tardan en encenderse las zonas descubiertas desde la última vez. */
    private static final double LIGHT_UP = 0.6;

    private static final Color BACKDROP = new Color(5, 7, 14);
    private static final Color LEVEL_BG = new Color(18, 22, 38);
    private static final Color ROCK = new Color(120, 130, 160);
    private static final Color BLOCK = new Color(150, 120, 190);
    private static final Color FRAME = new Color(90, 105, 140);
    private static final Color CANDIL = new Color(150, 225, 255);
    private static final Color LOGI = new Color(255, 190, 80);

    private boolean open;
    private double time;   // segundos desde que se abrió

    public void open() {
        open = true;
        time = 0;
    }

    public void close() { open = false; }
    public boolean isOpen() { return open; }

    public void update(double dt) {
        if (open) time += dt;
    }

    /** candil y logi están en coordenadas del nivel actual (null = no se dibuja esa marca). */
    public void draw(Graphics2D g, World world, Vec2 candil, Vec2 logi) {
        if (!open) return;
        Text.smooth(g);
        g.setColor(BACKDROP);
        g.fillRect(0, 0, Config.WIDTH, Config.HEIGHT);

        double totalWidth = 0, height = Config.HEIGHT;
        for (int i = 0; i < world.size(); i++) totalWidth += world.get(i).getWidth();
        double s = MAP_WIDTH / totalWidth;
        double x0 = (Config.WIDTH - MAP_WIDTH) / 2, y0 = (Config.HEIGHT - height * s) / 2;
        Mat3 view = Mat3.translation(x0, y0).multiply(Mat3.scale(s, s));

        AffineTransform onScreen = g.getTransform();
        Stroke oldStroke = g.getStroke();
        double offset = 0;   // dónde empieza el nivel i en el mapa (px del mundo)
        double light = smooth(time / LIGHT_UP);
        for (int i = 0; i < world.size(); i++) {
            Level level = world.get(i);
            g.transform(view.multiply(Mat3.translation(offset, 0)).toAffineTransform());
            drawLevel(g, level, world.exploration(i), light, height);
            if (i == world.currentIndex()) drawMarks(g, candil, logi, s);
            g.setTransform(onScreen);

            double cx = x0 + (offset + level.getWidth() / 2) * s;
            String label = world.isVisited(i) ? level.getNumber() + " \u00b7 " + level.getName() : "???";
            g.setFont(Text.CHAPTER);
            Text.centered(g, label, cx, y0 + height * s + 40, Text.STORY_COLOR, 1, 0);
            offset += level.getWidth();
        }
        g.setStroke(oldStroke);

        g.setFont(Text.SMALL);
        Text.centered(g, "Tab o Esc: cerrar el mapa", Config.WIDTH / 2.0, Config.HEIGHT - 50, Text.HINT_COLOR, 0.8, 0);
    }

    /** Un nivel en su propio espacio (0..ancho, 0..alto): fondo, roca y bloques, brillo y oscuridad. */
    private void drawLevel(Graphics2D g, Level level, Exploration ex, double light, double height) {
        double w = level.getWidth();
        g.setColor(LEVEL_BG);
        g.fill(new Rectangle2D.Double(0, 0, w, height));

        g.setColor(ROCK);
        for (Rectangle2D r : level.getPlatforms()) g.fill(r);
        g.setColor(BLOCK);
        for (Block b : level.getBlocks()) g.fill(b.getBounds());

        // Las imágenes cubren la cuadrícula completa (cols*CELL x rows*CELL), no solo el nivel;
        // se recorta al rectángulo del nivel para que la última zona no se salga.
        Shape oldClip = g.getClip();
        g.clip(new Rectangle2D.Double(0, 0, w, height));
        MapFog.glow(g, ex, light);
        MapFog.paint(g, ex, time, light);   // neblina opaca sobre lo no recorrido
        g.setClip(oldClip);

        g.setStroke(new BasicStroke(3f));   // en unidades del mundo: ~0.5 px en pantalla
        g.setColor(FRAME);
        g.draw(new Rectangle2D.Double(0, 0, w, height));
    }

    /** Candil: un punto que brilla. Logi: una llamita. Tamaños en px de pantalla (se divide entre la escala). */
    private void drawMarks(Graphics2D g, Vec2 candil, Vec2 logi, double s) {
        double pulse = 0.7 + 0.3 * Math.sin(time * 5);
        if (logi != null) {
            double r = 5 / s;
            Path2D flame = new Path2D.Double();   // gota: punta arriba, base redonda
            flame.moveTo(logi.x, logi.y - 1.8 * r);
            flame.curveTo(logi.x + 1.2 * r, logi.y - 0.6 * r, logi.x + r, logi.y + r, logi.x, logi.y + r);
            flame.curveTo(logi.x - r, logi.y + r, logi.x - 1.2 * r, logi.y - 0.6 * r, logi.x, logi.y - 1.8 * r);
            g.setColor(Text.withAlpha(LOGI, 0.35 * pulse));
            g.fill(new Ellipse2D.Double(logi.x - 2 * r, logi.y - 2 * r, 4 * r, 4 * r));
            g.setColor(LOGI);
            g.fill(flame);
        }
        if (candil != null) {
            double r = 4 / s;
            g.setColor(Text.withAlpha(CANDIL, 0.3 * pulse));
            g.fill(new Ellipse2D.Double(candil.x - 2.5 * r, candil.y - 2.5 * r, 5 * r, 5 * r));
            g.setColor(CANDIL);
            g.fill(new Ellipse2D.Double(candil.x - r, candil.y - r, 2 * r, 2 * r));
        }
    }

    /** Suavizado 3t^2 - 2t^3 de 0 a 1: arranca y termina despacio. */
    private static double smooth(double t) {
        t = Text.clamp01(t);
        return t * t * (3 - 2 * t);
    }
}
