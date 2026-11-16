package umbra.ui.intro;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import umbra.math.Mat3;

/**
 * [v0.8.6] Una escena de la intro. La línea de tiempo (ui.Intro) le dice cuántos segundos lleva
 * (su tiempo local, t = 0 al empezar) y la escena coloca su cámara, mueve lo suyo y se dibuja.
 *
 * Casi todo en una escena es función directa de t (la cámara, los fundidos, cuándo pasa cada
 * cosa): así se ve igual cada vez y a cualquier velocidad de cuadros. Lo que sí guarda estado
 * (Candil, Logi, las partículas del juego) avanza con dt en update().
 */
public abstract class Scene {

    protected final IntroCamera camera = new IntroCamera();
    /** Segundos desde que empezó la escena. */
    protected double time;

    /** Deja la escena en su estado inicial. Se llama cada vez que la intro entra en ella. */
    public abstract void enter();

    /** Avanza dt segundos; 'time' ya trae el tiempo local nuevo y la cámara ya está en su lugar. */
    protected abstract void update(double dt);

    /** Dibuja la escena como está ahora. No cambia nada: se puede dibujar dos veces el mismo cuadro. */
    public abstract void draw(Graphics2D g);

    /** Segundos de fundido desde negro al empezar la escena (0 = sigue de corrido la anterior). */
    public double fadeIn() { return 0; }

    /** Segundos de fundido a negro al terminar la escena (0 = la siguiente sigue de corrido). */
    public double fadeOut() { return 0; }

    /** Qué tan visible es el título UMBRA (0 a 1). Solo la última escena lo muestra. */
    public double titleAlpha() { return 0; }

    /** La línea de tiempo llama a este método en cada paso, con el tiempo local de la escena. */
    public final void advance(double dt, double localTime) {
        time = localTime;
        camera.setTime(localTime);
        update(dt);
    }

    /** Matriz de vista de la cámara de la escena (mundo -> pantalla). */
    public Mat3 getViewMatrix() {
        return camera.getViewMatrix();
    }

    /**
     * Dibuja una imagen ya hecha con la matriz 'view' aplicada, donde un píxel de la imagen mide
     * 1 / pixelsPerUnit unidades del mundo y su esquina queda en (x, y) del mundo:
     *     M = view · T(x, y) · S(1 / pixelsPerUnit)
     * Se dibuja sin suavizado de bordes y con el vecino más cercano: una imagen grande escalada
     * así cuesta menos de medio milisegundo; con interpolación bilineal, más de siete.
     */
    public static void drawImage(Graphics2D g, BufferedImage img, Mat3 view, double x, double y,
                                    double pixelsPerUnit) {
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object quality = g.getRenderingHint(RenderingHints.KEY_RENDERING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        AffineTransform m = view.multiply(Mat3.translation(x, y))
                .multiply(Mat3.scale(1 / pixelsPerUnit, 1 / pixelsPerUnit)).toAffineTransform();
        g.drawImage(img, m, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, quality);
    }
}
