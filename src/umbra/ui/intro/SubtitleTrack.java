package umbra.ui.intro;

import java.util.ArrayList;
import java.util.List;

/**
 * [v0.8.7] Pista de subtitulos de un clip de voz: segmentos (inicio, fin, texto) en segundos del
 * clip, no de la escena. Con el tiempo del audio dice que frase se ve, con cuanta opacidad
 * (entra en 350 ms y sale en 450 ms) y cuanto ha subido ya (para la micro-flotacion).
 */
public final class SubtitleTrack {

    /** Segundos que tarda en aparecer una frase. */
    public static final double FADE_IN = 0.35;
    /** Segundos que tarda en desvanecerse al terminar. */
    public static final double FADE_OUT = 0.45;

    /** Un fragmento de texto visible entre start y end (segundos del clip). */
    public static final class Segment {
        public final double start, end;
        public final String text;

        Segment(double start, double end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }

        /** Opacidad 0..1 en el tiempo de audio t. */
        public double alpha(double t) {
            return Ease.window(t, start, start + FADE_IN, end - FADE_OUT, end);
        }

        /** Avance 0..1 de la subida inicial (curva out), para el desplazamiento del texto. */
        public double rise(double t) {
            return Ease.out(Ease.ramp(t, start, start + FADE_IN));
        }
    }

    private final List<Segment> segments = new ArrayList<Segment>();

    /** Agrega un segmento y devuelve la propia pista para encadenar. */
    public SubtitleTrack add(double start, double end, String text) {
        segments.add(new Segment(start, end, text));
        return this;
    }

    /** El segmento visible en el tiempo t (start <= t < end), o null si no hay ninguno. */
    public Segment active(double t) {
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            if (t >= s.start && t < s.end) return s;
        }
        return null;
    }

    // ---- Las seis pistas de la intro (tabla 5.4 de la hoja de ruta) ----

    /** Escena 1, voz_1.wav: el Gran Farol. */
    public static SubtitleTrack lantern() {
        return new SubtitleTrack()
                .add(0.00, 4.35, "Bajo la tierra, el Gran Farol lo iluminaba todo.")
                .add(4.45, 7.40, "Nada podía esconderse de su llama.");   // 7.40 y no 7.65: la escena 1 acaba en el 7.45 del clip y el fundido debe terminar antes
    }

    /** Escena 2, voz_2.wav: el farol se quiebra. */
    public static SubtitleTrack shatter() {
        return new SubtitleTrack()
                .add(0.00, 2.05, "Una noche se quebró,")
                .add(2.15, 5.65, "y su luz cayó en mil pedazos por toda la cueva.");
    }

    /** Escena 3, voz_3.wav: la oscuridad. */
    public static SubtitleTrack darkness() {
        return new SubtitleTrack()
                .add(0.00, 2.20, "La oscuridad lo llenó todo:")
                .add(2.25, 3.35, "Umbra.");
    }

    /** Escena 4, voz_4.wav: nace Candil. */
    public static SubtitleTrack birth() {
        return new SubtitleTrack()
                .add(0.00, 2.85, "De la última chispa nació Candil.")
                .add(2.95, 6.15, "La luz lo deshace; solo la sombra lo abriga.");
    }

    /** Escena 5, voz_5.wav: Logi. */
    public static SubtitleTrack together() {
        return new SubtitleTrack()
                .add(0.00, 4.65, "Entre los pedazos sobrevivió Logi, una llama que no puede andar.");
    }

    /** Escena 6, voz_6.wav: el titulo. */
    public static SubtitleTrack title() {
        return new SubtitleTrack()
                .add(0.00, 2.85, "Juntos volveremos a encender el Gran Farol.");
    }
}
