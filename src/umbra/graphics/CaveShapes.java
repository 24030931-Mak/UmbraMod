package umbra.graphics;

import java.awt.geom.Path2D;

/**
 * [v0.8.7] Las formas de roca que comparten el fondo del juego (Background) y los escenarios de
 * la intro (ui.intro.GoldenCavern y ui.intro.DarkCavern): perfiles de techo y de piso a partir
 * de las alturas de Background.midpointDisplacement, y estalactitas.
 */
public final class CaveShapes {

    private CaveShapes() { }

    /**
     * Perfil de techo o de piso entre x = left y x = right: las alturas h (relativas a 'base')
     * repartidas a lo ancho, cerrado con una orilla recta en y = edgeY (arriba para un techo,
     * abajo para un piso).
     */
    public static Path2D ridge(double[] h, double left, double right, double base, double edgeY) {
        Path2D p = new Path2D.Double();
        p.moveTo(left, edgeY);
        for (int i = 0; i < h.length; i++) {
            p.lineTo(left + (right - left) * i / (h.length - 1), base + h[i]);
        }
        p.lineTo(right, edgeY);
        p.closePath();
        return p;
    }

    /** Altura del perfil en la posición x (interpolación lineal entre los dos puntos vecinos). */
    public static double heightAt(double[] h, double left, double right, double base, double x) {
        double t = Math.max(0, Math.min(1, (x - left) / (right - left))) * (h.length - 1);
        int i = Math.min((int) t, h.length - 2);
        return base + h[i] + (h[i + 1] - h[i]) * (t - i);
    }

    /** Estalactita (hacia abajo) o estalagmita (hacia arriba), con lados curvos. */
    public static Path2D spike(double x, double baseY, double w, double len, boolean down) {
        double dir = down ? 1 : -1;
        Path2D p = new Path2D.Double();
        p.moveTo(x - w / 2, baseY);
        p.quadTo(x - w / 5, baseY + dir * len * 0.55, x, baseY + dir * len);
        p.quadTo(x + w / 5, baseY + dir * len * 0.55, x + w / 2, baseY);
        p.closePath();
        return p;
    }

    /**
     * Solo un lado de la estalactita de spike(): de la base a la punta, el izquierdo o el
     * derecho. Sirve para trazar la orilla que mira hacia una luz.
     */
    public static Path2D spikeSide(double x, double baseY, double w, double len, boolean down, boolean right) {
        double dir = down ? 1 : -1, side = right ? 1 : -1;
        Path2D p = new Path2D.Double();
        p.moveTo(x + side * w / 2, baseY);
        p.quadTo(x + side * w / 5, baseY + dir * len * 0.55, x, baseY + dir * len);
        return p;
    }
}
