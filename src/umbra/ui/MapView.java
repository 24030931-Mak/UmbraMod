package umbra.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import umbra.core.Config;
import umbra.math.Mat3;
import umbra.math.Vec2;
import umbra.world.Exploration;
import umbra.world.Fragment;
import umbra.world.Level;
import umbra.world.World;

/**
 * El mapa (Tab): los niveles uno junto al otro, dibujados con sus repisas y bloques reales,
 * y lo que Candil ya recorrió iluminado con la luz cálida de Logi.
 *
 * Vista: otra matriz de mundo a pantalla (escala + traslación), igual que la cámara pero fija:
 * cada zona va en su lugar del plano de la cueva (worldX, worldY) con su propio alto; el rectángulo
 * que las envuelve se escala para caber en MAP_WIDTH de ancho y en el alto de pantalla menos
 * los márgenes (lo que limite más).
 *     pantalla = T(x0, y0) * S(s) * T(posición de la zona - esquina del rectángulo) * mundo de la zona
 * (cada zona se dibuja en su propio espacio, 0..ancho x 0..alto, y esa traslación la pone en su lugar).
 *
 * Lo no recorrido lo tapa del todo la neblina (MapFog, la misma del círculo de la esquina),
 * con máscara bilineal de zonas: las orillas de lo recorrido salen suaves, no cuadradas.
 */
public class MapView {

    private static final double MAP_WIDTH = 1160;
    /** Segundos que tardan en encenderse las zonas descubiertas desde la última vez. */
    private static final double LIGHT_UP = 0.6;

    /** Alto de pantalla que se reserva (nombres de zona debajo y la ayuda de abajo) al escalar el mapa. */
    private static final double VERTICAL_MARGINS = 260;
    /**
     * Hueco máximo, medido en px de PANTALLA (hueco en el mundo por la escala del mapa), entre una zona y
     * la que está debajo para considerar que hay otra "justo debajo": el nombre ocupa px de pantalla y
     * no cabría entre las dos, así que va dentro de la zona.
     */
    private static final double LABEL_GAP_PX = 50;

    private static final Color FRAME = new Color(90, 105, 140);
    private static final Color GOLD = new Color(255, 205, 80);   // [B4] los fragmentos tomados

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
        g.setColor(MapPainter.BACKDROP);
        g.fillRect(0, 0, Config.WIDTH, Config.HEIGHT);

        // Rectángulo que envuelve a todas las zonas en el plano de la cueva.
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < world.size(); i++) {
            Level l = world.get(i);
            minX = Math.min(minX, l.getWorldX());
            minY = Math.min(minY, l.getWorldY());
            maxX = Math.max(maxX, l.getWorldX() + l.getWidth());
            maxY = Math.max(maxY, l.getWorldY() + l.getHeight());
        }
        double boundsW = maxX - minX, boundsH = maxY - minY;
        double sw = MAP_WIDTH / boundsW, sh = (Config.HEIGHT - VERTICAL_MARGINS) / boundsH;
        double s, mapW;
        if (sw <= sh) { s = sw; mapW = MAP_WIDTH; }   // limita el ancho (como siempre con las zonas en fila)
        else { s = sh; mapW = boundsW * s; }
        double x0 = (Config.WIDTH - mapW) / 2, y0 = (Config.HEIGHT - boundsH * s) / 2;
        Mat3 view = Mat3.translation(x0, y0).multiply(Mat3.scale(s, s));

        AffineTransform onScreen = g.getTransform();
        Stroke oldStroke = g.getStroke();
        double light = smooth(time / LIGHT_UP);
        for (int i = 0; i < world.size(); i++) {
            Level level = world.get(i);
            double h = level.getHeight();
            double ox = level.getWorldX() - minX, oy = level.getWorldY() - minY;   // dónde empieza la zona en el mapa (px del mundo)
            g.transform(view.multiply(Mat3.translation(ox, oy)).toAffineTransform());
            drawLevel(g, world, level, world.exploration(i), light, h, s);
            if (i == world.currentIndex()) drawMarks(g, candil, logi, s, level.getWidth(), h);
            g.setTransform(onScreen);

            double cx = x0 + (ox + level.getWidth() / 2) * s;
            double bottom = y0 + (oy + h) * s;
            String label = !world.isVisited(i) ? "???"
                    : level.getNumber() == null || level.getNumber().isEmpty() ? level.getName() : level.getNumber() + " \u00b7 " + level.getName();
            g.setFont(Text.CHAPTER);
            // debajo de la zona; si hay otra justo debajo, dentro de la zona, abajo
            Text.centered(g, label, cx, hasZoneBelow(world, i, s) ? bottom - 14 : bottom + 40, Text.STORY_COLOR, 1, 0);
        }
        g.setStroke(oldStroke);

        g.setFont(Text.SMALL);
        Text.centered(g, "Tab o Esc: cerrar el mapa", Config.WIDTH / 2.0, Config.HEIGHT - 50, Text.HINT_COLOR, 0.8, 0);
    }

    /** True si otra zona queda justo debajo de la zona i (se solapan en x y la otra empieza donde esta termina o más abajo, con un hueco menor que LABEL_GAP_PX en pantalla; s = escala del mapa). */
    private static boolean hasZoneBelow(World world, int i, double s) {
        Level a = world.get(i);
        double bottom = a.getWorldY() + a.getHeight();
        for (int j = 0; j < world.size(); j++) {
            if (j == i) continue;
            Level b = world.get(j);
            boolean overlapX = b.getWorldX() < a.getWorldX() + a.getWidth() && b.getWorldX() + b.getWidth() > a.getWorldX();
            if (overlapX && b.getWorldY() >= bottom - 1 && (b.getWorldY() - bottom) * s < LABEL_GAP_PX) return true;
        }
        return false;
    }

    /** Un nivel en su propio espacio (0..ancho, 0..alto): fondo, roca y bloques, brillo y oscuridad. */
    private void drawLevel(Graphics2D g, World world, Level level, Exploration ex, double light, double height, double s) {
        double w = level.getWidth();
        MapPainter.drawTerrain(g, level, height, null);
        MapPainter.drawFog(g, ex, w, height, 0, time, light, null);

        drawFragments(g, world, level, s);

        g.setStroke(new BasicStroke(3f));   // en unidades del mundo: ~0.5 px en pantalla
        g.setColor(FRAME);
        g.draw(new Rectangle2D.Double(0, 0, w, height));
    }

    /**
     * [B4] Un punto dorado donde estaba cada fragmento que ya se tomó (los que faltan no se dibujan,
     * para no delatar dónde están). Va sobre la neblina. Tamaño en px de pantalla (se divide entre la escala 's').
     */
    private void drawFragments(Graphics2D g, World world, Level level, double s) {
        double r = 4 / s;
        for (Fragment f : level.getFragments()) {
            if (!world.isTaken(f.getId())) continue;
            double x = f.getCenterX(), y = f.getCenterY();
            g.setColor(Text.withAlpha(GOLD, 0.35));
            g.fill(new Ellipse2D.Double(x - 2 * r, y - 2 * r, 4 * r, 4 * r));
            g.setColor(GOLD);
            g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        }
    }

    /**
     * Candil: un punto que brilla. Logi: una llamita. Tamaños en px de pantalla (se divide entre la escala).
     * La marca de Candil se limita al rectángulo del nivel (w x height), con su halo adentro: si
     * cae al vacío, queda pegada al borde de abajo en lugar de salirse del marco.
     */
    private void drawMarks(Graphics2D g, Vec2 candil, Vec2 logi, double s, double w, double height) {
        double pulse = 0.7 + 0.3 * Math.sin(time * 5);
        if (logi != null) {
            double r = 5 / s;
            Path2D flame = new Path2D.Double();   // gota: punta arriba, base redonda
            flame.moveTo(logi.x, logi.y - 1.8 * r);
            flame.curveTo(logi.x + 1.2 * r, logi.y - 0.6 * r, logi.x + r, logi.y + r, logi.x, logi.y + r);
            flame.curveTo(logi.x - r, logi.y + r, logi.x - 1.2 * r, logi.y - 0.6 * r, logi.x, logi.y - 1.8 * r);
            g.setColor(Text.withAlpha(MapPainter.LOGI, 0.35 * pulse));
            g.fill(new Ellipse2D.Double(logi.x - 2 * r, logi.y - 2 * r, 4 * r, 4 * r));
            g.setColor(MapPainter.LOGI);
            g.fill(flame);
        }
        if (candil != null) {
            double r = 4 / s;
            double cx = Math.max(2.5 * r, Math.min(candil.x, w - 2.5 * r));
            double cy = Math.max(2.5 * r, Math.min(candil.y, height - 2.5 * r));
            g.setColor(Text.withAlpha(MapPainter.CANDIL, 0.3 * pulse));
            g.fill(new Ellipse2D.Double(cx - 2.5 * r, cy - 2.5 * r, 5 * r, 5 * r));
            g.setColor(MapPainter.CANDIL);
            g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        }
    }

    /** Suavizado 3t^2 - 2t^3 de 0 a 1: arranca y termina despacio. */
    private static double smooth(double t) {
        t = Text.clamp01(t);
        return t * t * (3 - 2 * t);
    }
}
