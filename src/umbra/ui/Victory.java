package umbra.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import umbra.core.Config;
import umbra.world.World;

/**
 * [v1.0] Pantalla final: el nivel se oscurece y aparece el cierre de la historia, línea por
 * línea, con el tiempo y las veces que Candil se desmoronó. El final es abierto porque el
 * juego sigue creciendo en las siguientes versiones.
 */
public class Victory {

    private static final double LINE_GAP = 1.8;   // s entre una línea y la siguiente
    private static final Pattern MARK = Pattern.compile("^\\[(\\d+)(?:-(\\d+))?\\]\\s*(.*)$");
    private static final Color VEIL = new Color(3, 4, 10, 205);

    private final List<String> allLines;   // todas las del archivo, con sus marcas
    private List<String> lines;            // las que se muestran según los fragmentos juntados
    private String stats = "";
    private double time;

    public Victory(List<String> lines) {
        this.allLines = lines;
        this.lines = select(lines, 0);
    }

    /**
     * Elige las líneas que se ven con 'fragments' fragmentos juntados. Una línea del archivo puede
     * empezar con una marca entre corchetes: "[0]" (solo con ese número), "[1-5]" (de uno a otro) o
     * "[6]". Sin marca se ve siempre. Una marca mal escrita se deja como parte del texto.
     */
    public static List<String> select(List<String> lines, int fragments) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            Matcher m = MARK.matcher(line);
            if (!m.matches()) {
                result.add(line);
                continue;
            }
            int from = Integer.parseInt(m.group(1));
            int to = m.group(2) != null ? Integer.parseInt(m.group(2)) : from;
            if (fragments >= from && fragments <= to) result.add(m.group(3).trim());
        }
        return result;
    }

    /** Las líneas que se muestran ahora (las pruebas las revisan). */
    public List<String> getLines() {
        return lines;
    }

    /** @param seconds tiempo jugado; @param deaths veces que Candil se desmoronó; @param fragments fragmentos juntados (de 6) */
    public void show(double seconds, int deaths, int fragments) {
        time = 0;
        lines = select(allLines, fragments);
        int s = (int) Math.round(seconds);
        stats = String.format("Tiempo: %d:%02d          Candil se desmoron\u00f3 %d %s          Fragmentos: %d/%d",
                s / 60, s % 60, deaths, deaths == 1 ? "vez" : "veces", fragments, World.FRAGMENTS);
    }

    public void update(double dt) {
        time += dt;
    }

    private double statsAt() {
        return 0.8 + lines.size() * LINE_GAP;
    }

    /** Ya se ve todo y se puede elegir: jugar de nuevo o volver al menú. */
    public boolean isReady() {
        return time >= statsAt() + 0.6;
    }

    /** Enter antes de tiempo: muestra todo de una vez. */
    public void skip() {
        time = Math.max(time, statsAt() + 0.6);
    }

    /** Toda la pantalla final: el velo y encima el texto. */
    public void draw(Graphics2D g) {
        drawVeil(g);
        drawText(g);
    }

    /** El velo que oscurece el nivel, que se queda quieto. Va aparte para poder poner a Logi entre el velo y el texto. */
    public void drawVeil(Graphics2D g) {
        Text.veil(g, VEIL, time / 1.0, Config.WIDTH, Config.HEIGHT);
    }

    /** Las líneas del final, las estadísticas y las indicaciones. */
    public void drawText(Graphics2D g) {
        Text.smooth(g);
        double cx = Config.WIDTH / 2.0;

        g.setFont(Text.STORY_BIG);
        FontMetrics fm = g.getFontMetrics();
        double y = 250;
        for (int i = 0; i < lines.size(); i++) {
            double alpha = (time - 0.8 - i * LINE_GAP) / 1.2;
            boolean last = i == lines.size() - 1;
            for (String row : Text.wrap(lines.get(i), fm, 900)) {
                Text.centered(g, row, cx, y, last ? Text.BRIGHT : Text.STORY_COLOR, alpha, last ? 1.5 : 0);
                y += 44;
            }
            y += 14;
        }

        g.setFont(Text.HINT);
        Text.centered(g, stats, cx, y + 36, Text.HINT_COLOR, (time - statsAt()) / 0.8, 0);

        if (isReady()) {
            double pulse = 0.6 + 0.4 * Math.sin(time * 3);
            Text.centered(g, "Enter o clic: jugar de nuevo            Esc: men\u00fa", cx, Config.HEIGHT - 86,
                    Text.BRIGHT, pulse, 1.2);
        }
    }
}
