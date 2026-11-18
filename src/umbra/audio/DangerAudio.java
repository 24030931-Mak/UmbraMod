package umbra.audio;

import java.io.ByteArrayInputStream;
import java.util.Random;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * Sintetizador de audio procedural limpio para Umbra:
 * - Dron armónico puro con oleaje lento (LFO) y un leve susurro de brasas finas
 *   (sin saturación ni distorsión).
 * - Efecto de desmoronamiento limpio: disipación de ceniza y chispas suaves.
 */
public final class DangerAudio {

    private static final float SAMPLE_RATE = 44100f;
    private static Clip clip;
    private static Clip crumbleClip;
    private static FloatControl gainControl;
    private static boolean initialized = false;
    private static boolean muted = false;
    private static double smoothDanger = 0.0;

    private DangerAudio() { }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
            Random rng = new Random(42L);

            // 1. Dron armónico limpio + susurro fino de brasas (4.0 s en bucle estéreo)
            double duration = 4.0;
            int numSamples = (int) (SAMPLE_RATE * duration);
            byte[] pcm = new byte[numSamples * 4];
            double lastWhite = 0.0;
            double emberEnv = 0.0;
            double emberPhase = 0.0;
            double emberFreq = 3200.0;

            for (int i = 0; i < numSamples; i++) {
                double t = i / SAMPLE_RATE;

                // Oleaje lento y orgánico (0.75 Hz = 3 ciclos exactos en 4 s)
                double swell = 0.60 + 0.40 * Math.sin(2.0 * Math.PI * 0.75 * t);

                // Armónicos puros sin distorsión (110 Hz fundamental + 165 Hz quinta + 220 Hz octava)
                double drone = 0.50 * Math.sin(2.0 * Math.PI * 110.0 * t)
                             + 0.30 * Math.sin(2.0 * Math.PI * 165.0 * t)
                             + 0.20 * Math.sin(2.0 * Math.PI * 220.0 * t + 0.25 * Math.sin(2.0 * Math.PI * 0.5 * t));

                // Aire caliente limpio (pasa-altos suave, cero zumbido grave roto)
                double white = rng.nextDouble() * 2.0 - 1.0;
                double cleanAir = (white - lastWhite) * 0.5;
                lastWhite = white;

                // Micro-chispas de brasa muy sutiles dentro del dron
                if (rng.nextDouble() < 0.0008) {
                    emberEnv = 0.20 + rng.nextDouble() * 0.25;
                    emberFreq = 2800.0 + rng.nextDouble() * 2400.0;
                }
                emberPhase += (2.0 * Math.PI * emberFreq) / SAMPLE_RATE;
                double spark = Math.sin(emberPhase) * emberEnv;
                emberEnv *= 0.985;

                // Mezcla lineal limpia (sin tanh) con amplitud reducida ~15%
                double wave = ((drone * swell * 0.78) + (cleanAir * 0.08) + (spark * 0.22)) * 0.85;
                short sample = (short) Math.max(-32767, Math.min(32767, wave * 32767));

                byte lo = (byte) (sample & 0xFF);
                byte hi = (byte) ((sample >> 8) & 0xFF);
                pcm[i * 4] = lo;
                pcm[i * 4 + 1] = hi;
                pcm[i * 4 + 2] = lo;
                pcm[i * 4 + 3] = hi;
            }

            try (AudioInputStream ais = new AudioInputStream(
                    new ByteArrayInputStream(pcm), format, numSamples)) {
                clip = AudioSystem.getClip();
                clip.open(ais);
            }

            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(gainControl.getMinimum());
            }

            // 2. Efecto de brasas y ceniza al morir (0.60 s, limpio y cristalino)
            int cSamples = (int) (SAMPLE_RATE * 0.60);
            byte[] cPcm = new byte[cSamples * 4];
            double cEmberEnv = 0.0;
            double cEmberFreq = 3400.0;
            double cEmberPhase = 0.0;
            double cLastWhite = 0.0;

            for (int i = 0; i < cSamples; i++) {
                double t = i / SAMPLE_RATE;
                double masterEnv = Math.min(1.0, t / 0.006) * Math.exp(-5.5 * t);

                double white = rng.nextDouble() * 2.0 - 1.0;
                double highAir = (white - cLastWhite) * 0.5;
                cLastWhite = white;

                double crackleChance = 0.0032 * Math.exp(-3.5 * t);
                if (rng.nextDouble() < crackleChance) {
                    cEmberEnv = 0.40 + rng.nextDouble() * 0.45;
                    cEmberFreq = 2600.0 + rng.nextDouble() * 2600.0;
                }

                cEmberPhase += (2.0 * Math.PI * cEmberFreq) / SAMPLE_RATE;
                double spark = Math.sin(cEmberPhase) * cEmberEnv;
                cEmberEnv *= 0.988;

                double warmSub = 0.12 * Math.sin(2.0 * Math.PI * 100.0 * t) * Math.exp(-15.0 * t);
                double signal = (0.24 * highAir * masterEnv) + (0.55 * spark) + warmSub;

                short s = (short) Math.max(-32767, Math.min(32767, signal * 0.75 * 32767));
                byte lo = (byte) (s & 0xFF);
                byte hi = (byte) ((s >> 8) & 0xFF);
                cPcm[i * 4] = lo;
                cPcm[i * 4 + 1] = hi;
                cPcm[i * 4 + 2] = lo;
                cPcm[i * 4 + 3] = hi;
            }

            try (AudioInputStream cAis = new AudioInputStream(
                    new ByteArrayInputStream(cPcm), format, cSamples)) {
                crumbleClip = AudioSystem.getClip();
                crumbleClip.open(cAis);
            }

        } catch (Exception e) {
            System.err.println("Umbra: DangerAudio falló (" + e.getMessage() + ")");
            clip = null;
            crumbleClip = null;
        }
    }

    /**
     * Genera y abre los clips de antemano (no suena). Generar ~4,6 s de audio y abrir los clips
     * tarda cientos de milisegundos: si se hiciera en el primer setDanger(), congelaría un cuadro.
     */
    public static void preload() {
        init();
    }

    /** Actualiza qué tan cerca está Logi de Candil (0.0 = seguro, 1.0 = peligro crítico). */
    public static void setDanger(double level) {
        init();
        if (clip == null) return;

        double target = muted ? 0.0 : Math.max(0.0, Math.min(1.0, level));
        if (VoiceOver.isPlaying()) target *= 0.25;

        double rate = (target > smoothDanger) ? 0.18 : 0.10;
        smoothDanger += (target - smoothDanger) * rate;

        if (smoothDanger <= 0.003) {
            smoothDanger = 0.0;
            if (gainControl != null) gainControl.setValue(gainControl.getMinimum());
            if (clip.isRunning()) clip.stop();
        } else {
            if (gainControl != null) {
                double curved = smoothDanger * smoothDanger * (3.0 - 2.0 * smoothDanger);
                float minDb = Math.max(gainControl.getMinimum(), -30.0f);
                // Tope ~15% menos intenso que el máximo anterior
                float maxDb = Math.max(minDb + 6.0f, gainControl.getMaximum() - 1.5f);
                float db = (float) (minDb + (maxDb - minDb) * curved);
                gainControl.setValue(db);
            }
            if (!clip.isRunning()) {
                clip.setFramePosition(0);
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            }
        }
    }

    /**
     * Corta al instante el dron de peligro y dispara el crepitar de brasas
     * en el mismo cuadro en que Candil muere.
     */
    public static void playCrumble() {
        init();
        smoothDanger = 0.0;
        if (gainControl != null) gainControl.setValue(gainControl.getMinimum());
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }

        if (crumbleClip == null || muted) return;
        if (crumbleClip.isRunning()) crumbleClip.stop();
        crumbleClip.setFramePosition(0);
        crumbleClip.start();
    }

    /** Silencia o reactiva el audio de peligro y desmoronamiento (tecla M). */
    public static void setMuted(boolean m) {
        muted = m;
        if (muted) {
            smoothDanger = 0.0;
            if (gainControl != null) gainControl.setValue(gainControl.getMinimum());
            if (clip != null && clip.isRunning()) clip.stop();
            if (crumbleClip != null && crumbleClip.isRunning()) crumbleClip.stop();
        }
    }
}
