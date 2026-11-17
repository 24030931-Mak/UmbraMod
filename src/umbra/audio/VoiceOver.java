package umbra.audio;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

import umbra.core.Resources;

/**
 * Narración de la intro: un clip WAV a la vez, con un reloj real (la posición del clip)
 * para sincronizar subtítulos sin depender de los FPS.
 *
 * Los clips se cargan de antemano con preload() (abrir un clip tarda 13-90 ms y congelaría
 * un cuadro al empezar cada escena); release() los suelta. Si un clip no se cargó antes,
 * play() lo abre en el momento, como respaldo.
 *
 * Igual que Music: si falta el archivo o no hay tarjeta de sonido, el juego sigue sin voz.
 */
public final class VoiceOver {

    private static final Map<String, Clip> loaded = new HashMap<>();
    private static Clip currentClip;
    private static FloatControl gain;
    private static boolean muted;
    private static boolean failed;   // el último play() no pudo sonar (sin archivo o sin tarjeta de sonido)

    private VoiceOver() { }

    /** true si el último intento de reproducir falló: la intro usa entonces su texto de respaldo. */
    public static boolean failed() {
        return failed;
    }

    /** Abre de antemano los clips indicados (los que fallen se intentarán otra vez en play()). */
    public static void preload(String... paths) {
        for (String path : paths) {
            if (path == null || loaded.containsKey(path)) continue;
            try {
                loaded.put(path, open(path));
            } catch (Exception e) {
                // Sin aviso aquí: play() lo intentará de nuevo y avisará si vuelve a fallar
            }
        }
    }

    /** Detiene la narración y cierra todos los clips cargados (al terminar la intro). */
    public static void release() {
        stop();
        for (Clip c : loaded.values()) c.close();
        loaded.clear();
    }

    /** Reproduce la narración desde el principio; detiene la anterior si seguía sonando. */
    public static void play(String path) {
        stop();
        try {
            Clip clip = loaded.get(path);
            if (clip == null) {
                clip = open(path);   // no se cargó antes: se abre ahora (más lento)
            } else {
                clip.setFramePosition(0);
            }
            currentClip = clip;
            gain = clip.isControlSupported(FloatControl.Type.MASTER_GAIN)
                    ? (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN) : null;
            applyMute();
            clip.start();
            failed = false;
        } catch (Exception e) {
            failed = true;
            System.err.println("Umbra: no se pudo reproducir la voz " + path + " (" + e.getMessage()
                    + "). El juego sigue sin voz.");
            currentClip = null;
            gain = null;
        }
    }

    /** Detiene la narración actual; la cierra si no es de las cargadas de antemano. */
    public static void stop() {
        Clip c = currentClip;
        currentClip = null;
        gain = null;
        if (c != null) {
            c.stop();
            c.flush();
            if (!loaded.containsValue(c)) c.close();
        }
    }

    public static boolean isPlaying() {
        return currentClip != null && currentClip.isOpen() && currentClip.isRunning();
    }

    /** Silencia o no la voz (tecla M). El clip sigue su curso para no desfasar los subtítulos. */
    public static void setMuted(boolean m) {
        muted = m;
        applyMute();
    }

    public static double getPositionSeconds() {
        if (currentClip == null || !currentClip.isOpen()) return 0;
        return currentClip.getMicrosecondPosition() / 1_000_000.0;
    }

    public static double getDurationSeconds() {
        if (currentClip == null || !currentClip.isOpen()) return 0;
        return currentClip.getMicrosecondLength() / 1_000_000.0;
    }

    /** Abre un clip con todo su audio en memoria. */
    private static Clip open(String path) throws Exception {
        try (InputStream raw = Resources.open(path);
             AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
            Clip clip = AudioSystem.getClip();
            try {
                clip.open(in);
            } catch (Exception e) {
                clip.close();
                throw e;
            }
            return clip;
        }
    }

    private static void applyMute() {
        if (gain == null) return;
        gain.setValue(muted ? gain.getMinimum() : Math.min(0f, gain.getMaximum()));
    }
}
