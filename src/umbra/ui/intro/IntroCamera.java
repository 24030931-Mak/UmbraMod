package umbra.ui.intro;

import java.util.ArrayList;
import java.util.List;

import umbra.core.Config;
import umbra.math.Mat3;

/**
 * [v0.8.6] La cámara de la intro: se mueve y se acerca siguiendo cuadros clave.
 *
 * Un cuadro clave dice "en el segundo t, mira el punto (x, y) del mundo con este acercamiento".
 * Entre dos cuadros clave la cámara se interpola con un spline de Hermite cúbico:
 *     p(u) = (2u³ - 3u² + 1)·p0 + (u³ - 2u² + u)·m0·Δt + (-2u³ + 3u²)·p1 + (u³ - u²)·m1·Δt
 * con u de 0 a 1 dentro del tramo y Δt lo que dura el tramo. m0 y m1 son las velocidades en los
 * dos cuadros clave; se toman como en Catmull-Rom, la pendiente entre el cuadro anterior y el
 * siguiente, m_i = (p_{i+1} - p_{i-1}) / (t_{i+1} - t_{i-1}), y cero en el primero y el último.
 * Así la cámara arranca y termina quieta y pasa por los cuadros de en medio sin frenar ni dar
 * tirones (la velocidad es continua).
 *
 * El acercamiento se interpola en su logaritmo: acercarse de 1x a 2x se siente igual de rápido
 * que de 2x a 4x, así que lo que debe cambiar a ritmo parejo es log(zoom), no zoom.
 *
 * Matriz de vista (mundo -> pantalla), igual que la cámara del juego pero con escala:
 *     V = T(ancho/2 + tx, alto/2 + ty) · S(zoom) · T(-x, -y)
 * Leída de derecha a izquierda: lleva el punto que se mira al origen, escala alrededor de él y
 * lo pone en el centro de la pantalla. (tx, ty) es el temblor, en píxeles de pantalla.
 */
public final class IntroCamera {

    /** Un cuadro clave: tiempo, punto del mundo que queda al centro y logaritmo del acercamiento. */
    private static final class Key {
        final double t, x, y, logZoom;
        Key(double t, double x, double y, double zoom) {
            this.t = t;
            this.x = x;
            this.y = y;
            this.logZoom = Math.log(zoom);
        }
    }

    private final List<Key> keys = new ArrayList<>();
    private double x, y, zoom = 1;
    private double shakeX, shakeY;

    /** Agrega un cuadro clave (en orden de tiempo). Regresa la misma cámara, para encadenar. */
    public IntroCamera key(double t, double x, double y, double zoom) {
        keys.add(new Key(t, x, y, zoom));
        if (keys.size() == 1) setTime(t);
        return this;
    }

    /** Coloca la cámara donde le toca en el segundo t. */
    public void setTime(double t) {
        int n = keys.size();
        if (n == 0) return;
        int i = 0;
        while (i < n - 2 && t > keys.get(i + 1).t) i++;   // el tramo [i, i + 1] que contiene a t
        Key a = keys.get(i), b = keys.get(Math.min(i + 1, n - 1));
        Key before = keys.get(Math.max(i - 1, 0)), after = keys.get(Math.min(i + 2, n - 1));
        double span = b.t - a.t;
        double u = span > 0 ? Ease.clamp01((t - a.t) / span) : 1;
        boolean first = i == 0, last = i + 1 >= n - 1;
        x = hermite(u, span, a.x, b.x, first ? 0 : slope(before.x, b.x, before.t, b.t),
                last ? 0 : slope(a.x, after.x, a.t, after.t));
        y = hermite(u, span, a.y, b.y, first ? 0 : slope(before.y, b.y, before.t, b.t),
                last ? 0 : slope(a.y, after.y, a.t, after.t));
        zoom = Math.exp(hermite(u, span, a.logZoom, b.logZoom,
                first ? 0 : slope(before.logZoom, b.logZoom, before.t, b.t),
                last ? 0 : slope(a.logZoom, after.logZoom, a.t, after.t)));
    }

    private static double slope(double p0, double p1, double t0, double t1) {
        return t1 > t0 ? (p1 - p0) / (t1 - t0) : 0;
    }

    /** El spline de Hermite del comentario de la clase, para un solo valor. */
    private static double hermite(double u, double span, double p0, double p1, double m0, double m1) {
        double u2 = u * u, u3 = u2 * u;
        return (2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * m0 * span
             + (-2 * u3 + 3 * u2) * p1 + (u3 - u2) * m1 * span;
    }

    /** Temblor de la cámara, en píxeles de pantalla (0, 0 = quieta). */
    public void setShake(double dx, double dy) {
        shakeX = dx;
        shakeY = dy;
    }

    /** Matriz de vista: V = T(centro de la pantalla + temblor) · S(zoom) · T(-x, -y). */
    public Mat3 getViewMatrix() {
        return getViewMatrix(1);
    }

    /**
     * La vista de una capa con profundidad. 'depth' multiplica cuánto se aleja la cámara de su
     * posición "de lejos" (zoom 1, centro del escenario): con depth > 1 la capa está entre la
     * cámara y la escena, y al acercarse crece y se sale de la pantalla más rápido (parallax).
     * Con depth = 1 es la vista normal.
     */
    public Mat3 getViewMatrix(double depth) {
        double z = Math.pow(zoom, depth);
        double cx = Config.WIDTH / 2.0, cy = Config.HEIGHT / 2.0;
        double px = cx + (x - cx) * depth, py = cy + (y - cy) * depth;
        return Mat3.translation(cx + shakeX, cy + shakeY)
                .multiply(Mat3.scale(z, z))
                .multiply(Mat3.translation(-px, -py));
    }

    public double getX()    { return x; }
    public double getY()    { return y; }
    public double getZoom() { return zoom; }
}
