package umbra.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.UnsupportedAudioFileException;

import umbra.core.Config;
import umbra.core.Resources;

/**
 * [v1.0] Música de ambiente: un archivo WAV que se repite sin fin, con entrada suave
 * (fade in) y una tecla para silenciarla.
 *
 * Usa javax.sound.sampled, que ya viene con Java (sin librerías externas). Java solo lee
 * WAV, AIFF y AU: si la música está en MP3, hay que convertirla a WAV primero.
 * Recomendado: WAV mono, 22050 o 44100 Hz, de 3 minutos o menos (se carga completa en memoria).
 *
 * Si no está el archivo o no hay tarjeta de sonido, el juego sigue sin música.
 */
public class Music {

    private final String resource;   // ruta dentro del classpath, por ejemplo "/umbra/res/ambiente.wav"
    private Clip clip;
    private FloatControl gain;       // control de volumen en decibeles (puede no existir)
    private double volume;           // volumen actual como amplitud: 0 = silencio, 1 = original
    private boolean muted;
    private double duck;             // 0 = sin bajar, 1 = bajada completa (mientras habla la narración)

    /** Cuánto baja la música mientras habla la narración, en decibeles. */
    private static final double DUCK_DB = 4.5;
    /** Tiempo en segundos para bajar del todo (rápido) y para recuperarse (lento). */
    private static final double DUCK_DOWN_TIME = 0.4, DUCK_UP_TIME = 1.5;

    public Music(String resource) {
        this.resource = resource;
    }

    /** Carga la música y la empieza a repetir. Si algo falla, avisa en la consola y sigue sin música. */
    public void play() {
        if (clip != null) return;
        try (AudioInputStream in = open()) {
            clip = AudioSystem.getClip();
            clip.open(toPcm16(in));   // Clip carga todo el audio en memoria
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            }
            volume = 0;               // empieza en silencio y sube poco a poco en update()
            applyVolume();
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception e) {
            System.err.println("Umbra: no se pudo reproducir la musica (" + e.getMessage()
                    + "). El juego sigue sin musica.");
            if (clip != null) clip.close();
            clip = null;
            gain = null;
        }
    }

    /** Acerca el volumen a su meta poco a poco: entrada suave al empezar y al quitar el silencio. */
    public void update(double dt) {
        if (clip == null || gain == null) return;
        double target = muted ? 0 : Config.MUSIC_VOLUME;
        double step = Config.MUSIC_VOLUME / Config.MUSIC_FADE_TIME * dt;
        if (volume < target)      volume = Math.min(target, volume + step);
        else if (volume > target) volume = Math.max(target, volume - step * 4);   // silenciar es más rápido
        double duckTarget = VoiceOver.isPlaying() ? 1 : 0;
        if (duck < duckTarget)      duck = Math.min(duckTarget, duck + dt / DUCK_DOWN_TIME);
        else if (duck > duckTarget) duck = Math.max(duckTarget, duck - dt / DUCK_UP_TIME);
        applyVolume();
    }

    /** Silencia o vuelve a encender la música (tecla M). */
    public void toggleMute() {
        muted = !muted;
        if (clip != null && gain == null) {   // sin control de volumen: solo se puede detener o seguir
            if (muted) clip.stop();
            else       clip.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    public boolean isMuted()   { return muted; }
    public boolean isPlaying() { return clip != null && clip.isRunning(); }
    public double getVolume()  { return volume; }

    /**
     * El control de volumen de Java trabaja en decibeles: dB = 20 * log10(amplitud).
     * Amplitud 1 = 0 dB (volumen del archivo); 0.5 = unos -6 dB; 0 = silencio (el mínimo del control).
     */
    private void applyVolume() {
        if (gain == null) return;
        float db = volume <= 0.0001 ? gain.getMinimum() : (float) (20 * Math.log10(volume) - DUCK_DB * duck);
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
    }

    /** Abre el archivo con Resources (dentro del .jar, desde el IDE o desde la carpeta src). */
    private AudioInputStream open() throws IOException, UnsupportedAudioFileException {
        InputStream raw = Resources.open(resource);
        // AudioSystem necesita poder regresar al inicio del flujo (mark/reset): por eso el búfer
        return AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
    }

    /** Convierte a PCM de 16 bits si el WAV viene en otro formato (8 o 24 bits, flotante...). */
    private static AudioInputStream toPcm16(AudioInputStream in) {
        AudioFormat f = in.getFormat();
        if (f.getEncoding() == AudioFormat.Encoding.PCM_SIGNED && f.getSampleSizeInBits() == 16) return in;
        AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16,
                f.getChannels(), f.getChannels() * 2, f.getSampleRate(), false);
        return AudioSystem.getAudioInputStream(target, in);
    }
}
