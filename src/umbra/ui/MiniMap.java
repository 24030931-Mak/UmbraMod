package umbra.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

import umbra.core.Config;
import umbra.math.Mat3;
import umbra.math.Vec2;
import umbra.world.Exploration;
import umbra.world.Level;

/**
 * El círculo de la esquina superior derecha: un mapa chico, siempre a la vista al jugar,
 * que muestra solo el nivel actual centrado en Candil.
 *
 * Vista: una matriz de mundo a pantalla que centra a Candil en el círculo y escala para que
 * VIEW px del mundo quepan en el diámetro; el mundo "se desliza" debajo del círculo:
 *     pantalla = T(centro) * S(k) * T(-candil) * mundo,   k = DIAMETER / VIEW
 * El recorte es una elipse (círculo) con el mismo centro. Adentro, el mismo estilo del mapa
 * grande: repisas y bloques, luz cálida en lo recorrido y la neblina compartida (MapFog).
 */
public class MiniMap {

    public static final int DIAMETER = 180;
    public static final int MARGIN = 16;
    /** Px del mundo que caben en el diámetro. */
    public static final int VIEW = 1000;

    private static final double CENTER_X = Config.WIDTH - MARGIN - DIAMETER / 2.0;
    private static final double CENTER_Y = MARGIN + DIAMETER / 2.0;
    private static final double SCALE = (double) DIAMETER / VIEW;

    private static final Color RING = new Color(150, 165, 200, 200);

    private final Shape circle = new Ellipse2D.Double(CENTER_X - DIAMETER / 2.0, CENTER_Y - DIAMETER / 2.0,
            DIAMETER, DIAMETER);

    /** candil y logi en coordenadas del nivel; logi puede ser null. 'time' mueve la neblina. */
    public void draw(Graphics2D g, Level level, Exploration ex, Vec2 candil, Vec2 logi, double time) {
        AffineTransform onScreen = g.getTransform();
        Shape oldClip = g.getClip();
        Stroke oldStroke = g.getStroke();

        g.setColor(MapPainter.BACKDROP);
        g.fill(circle);
        g.clip(circle);
        Mat3 view = Mat3.translation(CENTER_X, CENTER_Y).multiply(Mat3.scale(SCALE, SCALE))
                .multiply(Mat3.translation(-candil.x, -candil.y));
        g.transform(view.toAffineTransform());

        double w = level.getWidth(), h = Config.HEIGHT;
        double half = VIEW / 2.0;   // lo que se ve alrededor de Candil: un cuadro de VIEW x VIEW
        Rectangle2D visible = new Rectangle2D.Double(candil.x - half, candil.y - half, VIEW, VIEW);
        MapPainter.drawTerrain(g, level, h, visible);
        // La imagen cubre la cuadrícula completa: se recorta al nivel, con 1 px de pantalla de
        // holgura para que la neblina tape también los píxeles donde la repisa se suaviza (antialias)
        MapPainter.drawFog(g, ex, w, h, 1 / SCALE, time, 1, visible);

        if (logi != null) {
            double r = 3 / SCALE;   // 3 px de pantalla, pasados a unidades del mundo
            g.setColor(MapPainter.LOGI);
            g.fill(new Ellipse2D.Double(logi.x - r, logi.y - r, 2 * r, 2 * r));
        }

        g.setTransform(onScreen);
        g.setColor(MapPainter.CANDIL);   // Candil: el punto del centro
        g.fill(new Ellipse2D.Double(CENTER_X - 3, CENTER_Y - 3, 6, 6));

        g.setClip(oldClip);
        g.setStroke(new BasicStroke(2f));
        g.setColor(RING);
        g.draw(new Ellipse2D.Double(CENTER_X - DIAMETER / 2.0, CENTER_Y - DIAMETER / 2.0, DIAMETER, DIAMETER));
        g.setStroke(oldStroke);
    }
}
