package umbra.audio;

import java.io.ByteArrayInputStream;
import java.util.Random;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/**
 * Los dos sonidos del final, armados por c\u00f3digo (sin archivos), como DangerAudio y FragmentAudio:
 *   - roar(): el rugido grave de la llama negra, de 1 s;
 *   - knock(): el golpe sordo en el vidrio del candelabro, de 0,3 s.
 * Respetan el silencio global (tecla M): leen DangerAudio.isMuted(). Si el equipo no tiene audio,
 * no truenan ni se traban: simplemente no suenan.
 */
public final class FinaleAudio {

    private static final float SAMPLE_RATE = 44100f;
    private static Clip roarClip;
    private static Clip knockClip;
    private static boolean initialized = false;

    private FinaleAudio() { }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
            Random rng = new Random(7L);

            // Rugido: 1 s. Un tono grave que baja (70 a 45 Hz) con sus arm\u00f3nicos, el temblor de la garganta
            // (30 Hz) y aire grave (ruido filtrado). Entra r\u00e1pido, se sostiene y se apaga.
            int rn = (int) (SAMPLE_RATE * 1.0);
            byte[] rpcm = new byte[rn * 4];
            double phase = 0.0, lowNoise = 0.0;
            for (int i = 0; i < rn; i++) {
                double t = i / SAMPLE_RATE;
                double env = Math.min(1.0, t / 0.08) * Math.min(1.0, (1.0 - t) / 0.35);
                double f = 70.0 - 25.0 * t;
                phase += 2.0 * Math.PI * f / SAMPLE_RATE;
                double growl = 0.6 * Math.sin(phase) + 0.3 * Math.sin(2 * phase + 0.4) + 0.2 * Math.sin(3 * phase + 1.1);
                double tremor = 0.65 + 0.35 * Math.sin(2.0 * Math.PI * 30.0 * t);
                lowNoise += 0.04 * ((rng.nextDouble() * 2.0 - 1.0) - lowNoise);   // pasa-bajos
                double wave = (growl * tremor + 3.0 * lowNoise) * env * 0.55;
                put(rpcm, i, wave);
            }
            try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(rpcm), format, rn)) {
                roarClip = AudioSystem.getClip();
                roarClip.open(ais);
            }

            // Golpe en el vidrio: 0,3 s. Un "tunc" sordo (de 150 a 60 Hz) que se apaga muy r\u00e1pido,
            // un clic corto de ruido al principio y un tintineo apenas audible del vidrio.
            int kn = (int) (SAMPLE_RATE * 0.3);
            byte[] kpcm = new byte[kn * 4];
            double kPhase = 0.0, kNoise = 0.0;
            for (int i = 0; i < kn; i++) {
                double t = i / SAMPLE_RATE;
                double attack = Math.min(1.0, t / 0.002);
                double f = 60.0 + 90.0 * Math.exp(-30.0 * t);
                kPhase += 2.0 * Math.PI * f / SAMPLE_RATE;
                double thud = Math.sin(kPhase) * Math.exp(-14.0 * t);
                kNoise += 0.25 * ((rng.nextDouble() * 2.0 - 1.0) - kNoise);
                double click = kNoise * Math.exp(-90.0 * t);
                double glass = Math.sin(2.0 * Math.PI * 1760.0 * t) * Math.exp(-30.0 * t) * 0.06;
                double tail = Math.min(1.0, (0.3 - t) / 0.03);
                put(kpcm, i, (0.8 * thud + 0.5 * click + glass) * attack * tail * 0.7);
            }
            try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(kpcm), format, kn)) {
                knockClip = AudioSystem.getClip();
                knockClip.open(ais);
            }
        } catch (Exception | Error e) {   // sin tarjeta de audio, o sin l\u00ednea libre
            System.err.println("Umbra: FinaleAudio no pudo abrir el audio (" + e.getMessage() + ")");
            roarClip = null;
            knockClip = null;
        }
    }

    /** Una muestra est\u00e9reo (la misma en los dos lados), de 16 bits. */
    private static void put(byte[] pcm, int i, double wave) {
        short s = (short) Math.max(-32767, Math.min(32767, wave * 32767));
        byte lo = (byte) (s & 0xFF), hi = (byte) ((s >> 8) & 0xFF);
        pcm[i * 4] = lo;
        pcm[i * 4 + 1] = hi;
        pcm[i * 4 + 2] = lo;
        pcm[i * 4 + 3] = hi;
    }

    /** Genera los sonidos y abre los clips de antemano (no suenan): hacerlo al primer golpe congelar\u00eda un cuadro. */
    public static void preload() {
        init();
    }

    /** El rugido de la llama negra (~1 s). Con el silencio activo o sin audio, no hace nada. */
    public static void roar() {
        play(true);
    }

    /** El golpe sordo en el vidrio (~0,3 s). Con el silencio activo o sin audio, no hace nada. */
    public static void knock() {
        play(false);
    }

    private static void play(boolean roar) {
        init();
        Clip clip = roar ? roarClip : knockClip;
        if (clip == null || DangerAudio.isMuted()) return;
        try {
            if (clip.isRunning()) clip.stop();
            clip.setFramePosition(0);
            clip.start();
        } catch (IllegalStateException e) {
            // La l\u00ednea de audio se cerr\u00f3 o se perdi\u00f3 (por ejemplo, al desconectar los aud\u00edfonos): no suena y ya
        }
    }
}
