package umbra.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;

import umbra.core.Config;
import umbra.math.Vec2;

/**
 * Fuente de luz puntual que el jugador controla con el ratón.
 * No salta a la posición del ratón: se acerca con interpolación (lerp) para moverse suave.
 */
public class LightSource {

    private double x, y;
    private double targetX, targetY;
    private boolean pinned;   // fija: se queda en su lugar del mundo y ya no sigue al ratón

    public LightSource(double x, double y) {
        this.x = x;
        this.y = y;
        this.targetX = x;
        this.targetY = y;
    }

    /** Nuevo destino de la luz. Si está fija se ignora: se queda donde se le dejó. */
    public void setTarget(double tx, double ty) {
        if (pinned) return;
        targetX = tx;
        targetY = ty;
    }

    /**
     * [AVANCE 70%] Fija o suelta la luz. Fija, se queda en su punto del mundo aunque
     * la pantalla avance; así las sombras no cambian mientras el jugador camina sobre ellas.
     */
    public void togglePinned() { pinned = !pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public boolean isPinned() { return pinned; }

    public void update(double dt) {
        // Suavizado exponencial: en cada update recorre una fracción 't' del camino restante.
        // Usar exp() hace que la velocidad sea la misma sin importar los FPS.
        double t = 1 - Math.exp(-Config.LIGHT_SMOOTHING * dt);
        x += (targetX - x) * t;
        y += (targetY - y) * t;
    }

    /** Halo de luz: degradado radial que se desvanece hacia afuera. */
    public void drawGlow(Graphics2D g) {
        float r = (float) Config.LIGHT_RADIUS;
        RadialGradientPaint glow = new RadialGradientPaint(
            new Point2D.Double(x, y), r,
            new float[] { 0f, 0.35f, 1f },
            new Color[] {
                new Color(255, 225, 160, 110),
                new Color(255, 200, 120, 40),
                new Color(255, 200, 120, 0)
            });
        g.setPaint(glow);
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }

    /** Núcleo brillante de la luz. */
    public void drawCore(Graphics2D g) {
        g.setColor(new Color(255, 240, 200, 90));
        g.fill(new Ellipse2D.Double(x - 14, y - 14, 28, 28));
        g.setColor(new Color(255, 250, 230));
        g.fill(new Ellipse2D.Double(x - 6, y - 6, 12, 12));
        if (pinned) {   // un anillo indica que está fija
            g.setColor(new Color(255, 240, 200, 200));
            g.draw(new Ellipse2D.Double(x - 19, y - 19, 38, 38));
        }
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public Vec2 getPosition() { return new Vec2(x, y); }
}
