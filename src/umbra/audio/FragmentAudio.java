package umbra.audio;

import java.io.ByteArrayInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/**
 * [B4] El tono al tomar un fragmento del Gran Farol: corto y suave, armado por código (sin
 * archivos), como DangerAudio. Dos notas de campana (una quinta: 880 Hz y 1318 Hz) que se
 * apagan en medio segundo y un reflejo agudo muy tenue. Si el equipo no tiene audio, no truena:
 * simplemente no suena. Respeta el silencio global (tecla M, ver DangerAudio.setMuted).
 */
public final class FragmentAudio {

    private static final float SAMPLE_RATE = 44100f;
    private static Clip clip;
    private static boolean initialized = false;

    private FragmentAudio() { }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
            int n = (int) (SAMPLE_RATE * 0.9);
            byte[] pcm = new byte[n * 4];
            for (int i = 0; i < n; i++) {
                double t = i / SAMPLE_RATE;
                double attack = Math.min(1.0, t / 0.008);        // entrada suave: sin clic
                double tail = Math.min(1.0, (0.9 - t) / 0.05);   // y salida suave al final
                double low = Math.sin(2 * Math.PI * 880.0 * t) * Math.exp(-5.0 * t);
                double t2 = Math.max(0, t - 0.07);               // la segunda nota entra 70 ms después
                double high = t >= 0.07 ? Math.sin(2 * Math.PI * 1318.5 * t2) * Math.exp(-6.0 * t2) : 0;
                double shimmer = Math.sin(2 * Math.PI * 2637.0 * t) * Math.exp(-14.0 * t) * 0.15;
                double wave = (0.55 * low + 0.45 * high + shimmer) * attack * tail * 0.5;
                short s = (short) Math.max(-32767, Math.min(32767, wave * 32767));
                byte lo = (byte) (s & 0xFF), hi = (byte) ((s >> 8) & 0xFF);
                pcm[i * 4] = lo;
                pcm[i * 4 + 1] = hi;
                pcm[i * 4 + 2] = lo;
                pcm[i * 4 + 3] = hi;
            }
            try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(pcm), format, n)) {
                clip = AudioSystem.getClip();
                clip.open(ais);
            }
        } catch (Exception | Error e) {   // sin tarjeta de audio, o sin línea libre
            System.err.println("Umbra: FragmentAudio no pudo abrir el audio (" + e.getMessage() + ")");
            clip = null;
        }
    }

    /** Genera el tono y abre el clip de antemano (no suena): hacerlo al primer fragmento congelaría un cuadro. */
    public static void preload() {
        init();
    }

    /** Suena el tono. Con el silencio activo o sin audio, no hace nada. */
    public static void playChime() {
        init();
        if (clip == null || DangerAudio.isMuted()) return;
        if (clip.isRunning()) clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }
}
