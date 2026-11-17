package umbra.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

import umbra.entities.Block;
import umbra.world.Exploration;
import umbra.world.Level;

/**
 * Lo que comparten el mapa grande (MapView) y el círculo de la esquina (MiniMap): los colores
 * y el dibujo de un nivel en su propio espacio (fondo, repisas y bloques, y la neblina encima).
 * Quien llama pone la transformación (mundo a pantalla) y, si quiere, un recorte previo.
 */
final class MapPainter {

    static final Color BACKDROP = new Color(5, 7, 14);
    static final Color LEVEL_BG = new Color(18, 22, 38);
    static final Color ROCK = new Color(120, 130, 160);
    static final Color BLOCK = new Color(150, 120, 190);
    static final Color CANDIL = new Color(150, 225, 255);
    static final Color LOGI = new Color(255, 190, 80);

    private MapPainter() { }

    /**
     * El nivel en su espacio (0..ancho, 0..alto): fondo, repisas y bloques. Con 'visible' null
     * dibuja todo; si no, solo las repisas y bloques que tocan ese rectángulo.
     */
    static void drawTerrain(Graphics2D g, Level level, double height, Rectangle2D visible) {
        g.setColor(LEVEL_BG);
        g.fill(new Rectangle2D.Double(0, 0, level.getWidth(), height));
        g.setColor(ROCK);
        for (Rectangle2D r : level.getPlatforms()) {
            if (visible == null || r.intersects(visible)) g.fill(r);
        }
        g.setColor(BLOCK);
        for (Block b : level.getBlocks()) {
            if (visible == null || b.getBounds().intersects(visible)) g.fill(b.getBounds());
        }
    }

    /**
     * Brillo y neblina sobre el nivel. La imagen cubre la cuadrícula completa (cols*CELL x
     * rows*CELL), no solo el nivel: se recorta al rectángulo del nivel más 'slack' de holgura
     * por cada lado (en unidades del mundo), y al terminar se restaura el recorte de antes.
     */
    static void drawFog(Graphics2D g, Exploration ex, double width, double height, double slack,
                        double time, double light, Rectangle2D visible) {
        Shape oldClip = g.getClip();
        g.clip(new Rectangle2D.Double(-slack, -slack, width + 2 * slack, height + 2 * slack));
        MapFog.glow(g, ex, light);
        MapFog.paint(g, ex, time, light, visible);   // neblina opaca sobre lo no recorrido
        g.setClip(oldClip);
    }
}
