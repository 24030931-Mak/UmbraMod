package umbra.entities;

import java.awt.geom.Rectangle2D;

import umbra.math.Vec2;

/**
 * Objeto sólido que proyecta sombra cuando la luz lo ilumina.
 * Es la pieza clave de cada nivel: su sombra es el refugio por donde avanza Candil.
 * Su aspecto (losa de piedra con una runa) lo dibuja graphics.LevelArt.
 */
public class Block {

    private final Rectangle2D.Double bounds;

    public Block(double x, double y, double w, double h) {
        bounds = new Rectangle2D.Double(x, y, w, h);
    }

    public Rectangle2D getBounds() {
        return bounds;
    }

    /** Las 4 esquinas en orden: superior-izq, superior-der, inferior-der, inferior-izq. */
    public Vec2[] getCorners() {
        double x = bounds.x, y = bounds.y, w = bounds.width, h = bounds.height;
        return new Vec2[] {
            new Vec2(x, y),
            new Vec2(x + w, y),
            new Vec2(x + w, y + h),
            new Vec2(x, y + h)
        };
    }
}
