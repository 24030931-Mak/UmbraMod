package umbra.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import umbra.core.Config;

/**
 * [B4] La línea de historia que sale al tomar un fragmento: un subtítulo abajo y centrado que se
 * ve unos 4.5 s y se desvanece (o lo que dure su voz más medio segundo, si la tiene). No detiene el juego. Si se toma otro fragmento
 * mientras hay una línea en pantalla, la nueva reemplaza a la anterior.
 *
 * Lo que empieza con raya (—) lo dice Logi y se pinta de otro color, igual que en la intro
 * (Intro.sentences parte la línea en oraciones con la misma regla). Una excepción: la raya que
 * sigue a una oración de Logi ya cerrada abre un inciso del narrador ("—Ya casi. —Pero su llama
 * se ve más oscura."), así que esa parte se pinta con el color de la narración.
 *
 * Posición: abajo, salvo que Candil esté en el tercio de abajo de la pantalla; entonces va arriba,
 * debajo del título del nivel, para no taparlo. Se elige al aparecer y no cambia mientras dura.
 */
public final class StoryLine {

    /** Segundos que la línea está en pantalla (con su entrada y su salida incluidas). */
    public static final double DURATION = 4.5;
    private static final double FADE_IN = 0.35, FADE_OUT = 0.8;
    /** Segundos que la línea sigue en pantalla después de que termina su voz. */
    private static final double VOICE_MARGIN = 0.5;

    private static final Font FONT = new Font(Font.SERIF, Font.ITALIC, 24);
    private static final Color NARRATION = new Color(225, 240, 255);
    private static final Color VOICE = new Color(255, 226, 172);   // lo que dice Logi (empieza con raya)
    private static final int MAX_WIDTH = 1000;
    private static final int LAST_BASELINE = 672, ROW_HEIGHT = 30;
    private static final int VEIL_TOP = 590;
    /** Arriba: la primera línea va justo debajo de LevelTitle (que llega a y = 236). */
    private static final int TOP_FIRST_BASELINE = 290, TOP_VEIL_START = 246;
    /** Candil está "abajo" si su centro en pantalla pasa de este y (el tercio de abajo). */
    public static final int LOW_THIRD = Config.HEIGHT * 2 / 3;

    private final List<String> lines;
    private int index = -1;   // la línea que se muestra; -1 = ninguna
    private boolean atTop;    // se elige al aparecer: la línea va arriba porque Candil está abajo
    private double time;      // segundos desde que se mostró
    private double duration = DURATION;   // lo que dura la que se muestra: DURATION o más si su voz es larga

    // Renglones ya partidos de la línea que se muestra (se calculan en el primer draw de cada show)
    private final List<String> rows = new ArrayList<>();
    private final List<Boolean> rowIsVoice = new ArrayList<>();
    private boolean rowsReady;

    /** 'lines' son las líneas de fragmentos.txt, en orden. */
    public StoryLine(List<String> lines) {
        this.lines = new ArrayList<>(lines);
    }

    /** Muestra la línea 'index' (de 0 a 5) abajo, reemplazando la que esté en pantalla. Fuera de rango no hace nada. */
    public void show(int index) {
        show(index, false);
    }

    /**
     * Igual, eligiendo la posición: 'candilY' es el centro de Candil en pantalla; si está en el
     * tercio de abajo, la línea va arriba. La posición queda fija hasta que la línea se va.
     */
    public void show(int index, double candilY) {
        show(index, candilY > LOW_THIRD);
    }

    /**
     * Igual que show(index, candilY), pero la línea dura al menos 'voiceSeconds' + 0.5 s (lo que
     * dura su voz más un respiro), y nunca menos que DURATION. Con 0 es igual a show(index, candilY).
     */
    public void show(int index, double candilY, double voiceSeconds) {
        show(index, candilY > LOW_THIRD, voiceSeconds);
    }

    private void show(int index, boolean top) {
        show(index, top, 0);
    }

    private void show(int index, boolean top, double voiceSeconds) {
        if (index < 0 || index >= lines.size()) return;
        this.index = index;
        this.duration = Math.max(DURATION, voiceSeconds + VOICE_MARGIN);
        this.atTop = top;
        time = 0;
        rowsReady = false;
    }

    /** La quita de golpe (partida nueva). */
    public void hide() {
        index = -1;
    }

    public void update(double dt) {
        if (index < 0) return;
        time += dt;
        if (time >= duration) index = -1;
    }

    public boolean isShowing() { return index >= 0; }

    /** Segundos que dura la línea que se muestra (DURATION, o más si su voz es larga). */
    public double duration() { return duration; }

    /** El número de la línea que se muestra, o -1 si no hay ninguna. */
    public int currentIndex() { return index; }

    /** Si la línea que se muestra está arriba (Candil estaba abajo al aparecer). */
    public boolean isAtTop() { return atTop; }

    /** Y de la base del primer renglón con la posición elegida y 'rowCount' renglones. */
    private double firstBaseline(int rowCount) {
        return atTop ? TOP_FIRST_BASELINE : LAST_BASELINE - (rowCount - 1) * ROW_HEIGHT;
    }

    /** Un trozo de la línea con su color: lo dice Logi (voice) o lo cuenta el narrador. */
    public static final class Piece {
        public final String text;
        public final boolean voice;
        Piece(String text, boolean voice) {
            this.text = text;
            this.voice = voice;
        }
    }

    /**
     * Parte una línea en oraciones y dice quién habla en cada una. Lo que empieza con raya es de
     * Logi, salvo la raya que sigue a una oración de Logi ya cerrada: abre un inciso del narrador.
     * Una raya que sigue a ese inciso vuelve a ser de Logi.
     */
    public static List<Piece> pieces(String line) {
        List<Piece> result = new ArrayList<>();
        boolean prevVoice = false;
        for (String part : line.split("(?<=\\.)\\s+|\\s+(?=\u2014)")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            boolean voice = part.startsWith("\u2014") && !prevVoice;
            result.add(new Piece(part, voice));
            prevVoice = voice;
        }
        return result;
    }

    /** Opacidad de 0 a 1: entra en FADE_IN s, se queda y sale en los últimos FADE_OUT s. */
    private double alpha() {
        double in = Math.min(1, time / FADE_IN);
        double out = Math.min(1, (duration - time) / FADE_OUT);
        return Math.max(0, Math.min(in, out));
    }

    /** Parte la línea en oraciones (ver pieces) y junta las seguidas del mismo color. */
    private void prepare(FontMetrics fm) {
        rows.clear();
        rowIsVoice.clear();
        StringBuilder block = new StringBuilder();
        boolean blockVoice = false;
        for (Piece piece : pieces(lines.get(index))) {
            if (block.length() > 0 && piece.voice != blockVoice) {
                flush(block.toString(), blockVoice, fm);
                block.setLength(0);
            }
            blockVoice = piece.voice;
            if (block.length() > 0) block.append(' ');
            block.append(piece.text);
        }
        if (block.length() > 0) flush(block.toString(), blockVoice, fm);
        rowsReady = true;
    }

    private void flush(String text, boolean voice, FontMetrics fm) {
        for (String row : Text.wrap(text, fm, MAX_WIDTH)) {
            rows.add(row);
            rowIsVoice.add(voice);
        }
    }

    /** Abajo y centrada, sobre una franja oscura que se desvanece hacia arriba. */
    public void draw(Graphics2D g) {
        if (index < 0) return;
        double a = alpha();
        if (a <= 0.005) return;

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(FONT);
        if (!rowsReady) prepare(g.getFontMetrics());

        double first = firstBaseline(rows.size());
        if (atTop) {   // la franja oscura se desvanece hacia abajo
            int veilEnd = (int) first + (rows.size() - 1) * ROW_HEIGHT + 50;
            // entra suave arriba (sin borde) y se desvanece hacia abajo
            g.setPaint(new LinearGradientPaint(0, TOP_VEIL_START, 0, veilEnd, new float[] { 0f, 0.2f, 1f },
                    new Color[] { new Color(4, 6, 14, 0), Text.withAlpha(new Color(4, 6, 14, 150), a), new Color(4, 6, 14, 0) }));
            g.fillRect(0, TOP_VEIL_START, Config.WIDTH, veilEnd - TOP_VEIL_START);
        } else {
            g.setPaint(new GradientPaint(0, VEIL_TOP, new Color(4, 6, 14, 0),
                    0, Config.HEIGHT, Text.withAlpha(new Color(4, 6, 14, 150), a)));
            g.fillRect(0, VEIL_TOP, Config.WIDTH, Config.HEIGHT - VEIL_TOP);
        }

        double drift = (1 - Math.min(1, time / FADE_IN)) * 5 * (atTop ? -1 : 1);   // se mueve unos pixeles al aparecer
        for (int i = 0; i < rows.size(); i++) {
            double y = first + i * ROW_HEIGHT + drift;
            Color c = rowIsVoice.get(i) ? VOICE : NARRATION;
            Text.centered(g, rows.get(i), Config.WIDTH / 2.0 + 1, y + 1.5, Color.BLACK, a * 0.7, 0);   // sombra
            Text.centered(g, rows.get(i), Config.WIDTH / 2.0, y, c, a, 1.2);
        }
        if (oldAA != null) g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldAA);
    }
}
