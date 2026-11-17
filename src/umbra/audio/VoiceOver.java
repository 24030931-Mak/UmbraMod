package umbra.audio;

import java.io.BufferedInputStream;
import java.io.InputStream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

import umbra.core.Resources;

/**
 * Narración de la intro: un clip WAV a la vez, con un reloj real (la posición del clip)
 * para sincronizar subtítulos sin depender de los FPS.
 *
 * Igual que Music: si falta el archivo o no hay tarjeta de sonido, el juego sigue sin voz.
 */
public final class VoiceOver {

    private static Clip currentClip;
    private static FloatControl gain;
    private static boolean muted;

    private VoiceOver() { }

    /** Reproduce la narración desde el principio; detiene la anterior si seguía sonando. */
    public static void play(String path) {
        stop();
        try (InputStream raw = Resources.open(path);
             AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
            Clip clip = AudioSystem.getClip();
            try {
                clip.open(in);   // Clip carga todo el audio en memoria
            } catch (Exception e) {
                clip.close();
                throw e;
            }
            currentClip = clip;
            gain = clip.isControlSupported(FloatControl.Type.MASTER_GAIN)
                    ? (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN) : null;
            applyMute();
            clip.start();
        } catch (Exception e) {
            System.err.println("Umbra: no se pudo reproducir la voz " + path + " (" + e.getMessage()
                    + "). El juego sigue sin voz.");
            currentClip = null;
            gain = null;
        }
    }

    /** Detiene y libera la narración actual. */
    public static void stop() {
        Clip c = currentClip;
        currentClip = null;
        gain = null;
        if (c != null) {
            c.stop();
            c.close();
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

    private static void applyMute() {
        if (gain == null) return;
        gain.setValue(muted ? gain.getMinimum() : Math.min(0f, gain.getMaximum()));
    }
}
