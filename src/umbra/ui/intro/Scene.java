package umbra.ui.intro;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import umbra.audio.VoiceOver;
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
    /** [v0.8.7] Si la narración de esta escena ya se disparó (se reinicia en start()). */
    private boolean spoken;

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

    /** [v0.8.7] Ruta del clip de narración de la escena (null = no habla). */
    protected String voiceClip() { return null; }

    /** [v0.8.7] En qué segundo de la escena empieza la narración. */
    protected double voiceAt() { return 0; }

    /** [v0.8.7] La pista de subtítulos del clip de la escena (null = sin subtítulos). */
    public SubtitleTrack getSubtitles() { return null; }

    /** true si la voz de esta escena ya se disparó (para el texto de respaldo cuando no suena). */
    public final boolean hasSpoken() { return spoken; }

    /**
     * Los subtítulos que se pueden dibujar ahora: solo si la voz de ESTA escena ya se disparó y
     * suena (si no, se vería la frase de la escena anterior con la posición del clip viejo).
     */
    public final SubtitleTrack activeSubtitles() {
        return spoken && VoiceOver.isPlaying() ? getSubtitles() : null;
    }

    /** La línea de tiempo entra en la escena: reinicia la bandera de la voz y llama a enter(). */
    public final void start() {
        spoken = false;
        enter();
    }

    /** La línea de tiempo llama a este método en cada paso, con el tiempo local de la escena. */
    public final void advance(double dt, double localTime) {
        time = localTime;
        camera.setTime(localTime);
        update(dt);
        String clip = voiceClip();
        if (!spoken && clip != null && localTime >= voiceAt()) {
            spoken = true;
            VoiceOver.play(clip);
        }
    }

    /** Matriz de vista de la cámara de la escena (mundo -> pantalla). */
    public Mat3 getViewMatrix() {
        return camera.getViewMatrix();
    }

    /**
     * Dibuja el rectángulo (sx, sy, w, h) de una imagen ya hecha con la matriz 'view' aplicada,
     * donde un píxel de la imagen mide 1 / pixelsPerUnit unidades del mundo y la esquina del
     * rectángulo queda en (x, y) del mundo:
     *     M = view · T(x, y) · S(1 / pixelsPerUnit)
     * Lo que rodea al rectángulo no se pinta, pero el muestreo bilineal sí lo lee (ver Plane).
     * Sin suavizado de bordes (no hace falta: la imagen ya trae sus orillas suavizadas) y con el
     * muestreo que diga smoothScaling.
     */
    public static void drawImage(Graphics2D g, BufferedImage img, Mat3 view, double x, double y,
                                    double pixelsPerUnit, int sx, int sy, int w, int h) {
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object quality = g.getRenderingHint(RenderingHints.KEY_RENDERING);
        Object sampling = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smoothScaling(g));
        AffineTransform old = g.getTransform();
        g.transform(view.multiply(Mat3.translation(x, y))
                .multiply(Mat3.scale(1 / pixelsPerUnit, 1 / pixelsPerUnit)).toAffineTransform());
        g.drawImage(img, 0, 0, w, h, sx, sy, sx + w, sy + h, null);
        g.setTransform(old);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, quality);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, sampling != null ? sampling
                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);   // sin indicación, Java2D usa el vecino
    }

    /**
     * Cómo muestrear una imagen escalada. Con el vecino más cercano, cada píxel de la pantalla
     * toma el píxel de la imagen que le cae encima; en un acercamiento lento ese píxel cambia de
     * golpe y las líneas finas "brincan" (parpadean). Con interpolación bilineal toma el promedio
     * de los cuatro vecinos, pesado por qué tan cerca está de cada uno:
     *     c = (1-u)(1-v)·c00 + u(1-v)·c10 + (1-u)v·c01 + uv·c11
     * y la imagen se desliza suave.
     *
     * La tarjeta de video hace eso casi gratis (unos 0.3 ms por plano), y en el juego se dibuja
     * en una imagen de la tarjeta de video (con Direct3D en Windows). Sin ella (sin aceleración, o
     * al dibujar en un BufferedImage, como las pruebas) Java2D la calcula en el procesador y
     * cuesta unos 9 ms por plano de pantalla completa: ahí se usa el vecino más cercano.
     */
    static Object smoothScaling(Graphics2D g) {
        return g.getDeviceConfiguration().getImageCapabilities().isAccelerated()
                ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
    }
}
