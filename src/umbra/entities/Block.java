package umbra.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import umbra.math.Vec2;

/**
 * Objeto sólido que proyecta sombra cuando la luz lo ilumina.
 * Es la pieza clave de cada nivel: su sombra es la que el jugador usa como plataforma.
 */
public class Block {

    private static final Color FILL = new Color(70, 70, 92);
    private static final Color EDGE = new Color(140, 140, 170);

    private final Rectangle2D.Double bounds;

    public Block(double x, double y, double w, double h) {
        bounds = new Rectangle2D.Double(x, y, w, h);
    }

    public Rectangle2D getBounds() {
        return bounds;
    }

    /**
     * Las 4 esquinas en orden: superior-izq, superior-der, inferior-der, inferior-izq.
     * Las vas a necesitar en el avance 70% para calcular la sombra.
     */
    public Vec2[] getCorners() {
        double x = bounds.x, y = bounds.y, w = bounds.width, h = bounds.height;
        return new Vec2[] {
            new Vec2(x, y),
            new Vec2(x + w, y),
            new Vec2(x + w, y + h),
            new Vec2(x, y + h)
        };
    }

    public void draw(Graphics2D g) {
        g.setColor(FILL);
        g.fill(bounds);
        g.setColor(EDGE);
        g.draw(bounds);
    }
}
