package umbra.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import umbra.core.Config;

/**
 * [B4] La línea de historia que sale al tomar un fragmento: un subtítulo abajo y centrado que se
 * ve unos 4.5 s y se desvanece. No detiene el juego y no tiene voz. Si se toma otro fragmento
 * mientras hay una línea en pantalla, la nueva reemplaza a la anterior.
 *
 * Lo que empieza con raya (—) lo dice Logi y se pinta de otro color, igual que en la intro
 * (Intro.sentences parte la línea en oraciones con la misma regla).
 */
public final class StoryLine {

    /** Segundos que la línea está en pantalla (con su entrada y su salida incluidas). */
    public static final double DURATION = 4.5;
    private static final double FADE_IN = 0.35, FADE_OUT = 0.8;

    private static final Font FONT = new Font(Font.SERIF, Font.ITALIC, 24);
    private static final Color NARRATION = new Color(225, 240, 255);
    private static final Color VOICE = new Color(255, 226, 172);   // lo que dice Logi (empieza con raya)
    private static final int MAX_WIDTH = 1000;
    private static final int LAST_BASELINE = 672, ROW_HEIGHT = 30;
    private static final int VEIL_TOP = 590;

    private final List<String> lines;
    private int index = -1;   // la línea que se muestra; -1 = ninguna
    private double time;      // segundos desde que se mostró

    // Renglones ya partidos de la línea que se muestra (se calculan en el primer draw de cada show)
    private final List<String> rows = new ArrayList<>();
    private final List<Boolean> rowIsVoice = new ArrayList<>();
    private boolean rowsReady;

    /** 'lines' son las líneas de fragmentos.txt, en orden. */
    public StoryLine(List<String> lines) {
        this.lines = new ArrayList<>(lines);
    }

    /** Muestra la línea 'index' (de 0 a 5), reemplazando la que esté en pantalla. Fuera de rango no hace nada. */
    public void show(int index) {
        if (index < 0 || index >= lines.size()) return;
        this.index = index;
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
        if (time >= DURATION) index = -1;
    }

    public boolean isShowing() { return index >= 0; }

    /** El número de la línea que se muestra, o -1 si no hay ninguna. */
    public int currentIndex() { return index; }

    /** Opacidad de 0 a 1: entra en FADE_IN s, se queda y sale en los últimos FADE_OUT s. */
    private double alpha() {
        double in = Math.min(1, time / FADE_IN);
        double out = Math.min(1, (DURATION - time) / FADE_OUT);
        return Math.max(0, Math.min(in, out));
    }

    /** Parte la línea en oraciones (después de ". " y antes de una raya) y junta las seguidas del mismo color. */
    private void prepare(FontMetrics fm) {
        rows.clear();
        rowIsVoice.clear();
        StringBuilder block = new StringBuilder();
        boolean blockVoice = false;
        for (String part : lines.get(index).split("(?<=\\.)\\s+|\\s+(?=\u2014)")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            boolean voice = part.startsWith("\u2014");
            if (block.length() > 0 && voice != blockVoice) {
                flush(block.toString(), blockVoice, fm);
                block.setLength(0);
            }
            blockVoice = voice;
            if (block.length() > 0) block.append(' ');
            block.append(part);
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

        g.setPaint(new GradientPaint(0, VEIL_TOP, new Color(4, 6, 14, 0),
                0, Config.HEIGHT, Text.withAlpha(new Color(4, 6, 14, 150), a)));
        g.fillRect(0, VEIL_TOP, Config.WIDTH, Config.HEIGHT - VEIL_TOP);

        double drift = (1 - Math.min(1, time / FADE_IN)) * 5;   // sube unos pixeles al aparecer
        for (int i = 0; i < rows.size(); i++) {
            double y = LAST_BASELINE - (rows.size() - 1 - i) * ROW_HEIGHT + drift;
            Color c = rowIsVoice.get(i) ? VOICE : NARRATION;
            Text.centered(g, rows.get(i), Config.WIDTH / 2.0 + 1, y + 1.5, Color.BLACK, a * 0.7, 0);   // sombra
            Text.centered(g, rows.get(i), Config.WIDTH / 2.0, y, c, a, 1.2);
        }
        if (oldAA != null) g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldAA);
    }
}
