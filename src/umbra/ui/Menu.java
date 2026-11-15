package umbra.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.List;

import umbra.core.Config;

/**
 * [v1.0] Menú de inicio: el título UMBRA y la historia, que aparece línea por línea.
 * Cada línea espera según su largo, para dar tiempo de leerla. Enter (o clic) muestra
 * toda la historia de golpe; otro Enter empieza el juego.
 */
public class Menu {

    private static final double TITLE_FADE = 1.5;   // s que tarda en aparecer el título
    private static final double LINE_FADE = 1.2;    // s que tarda en aparecer cada línea
    private static final double READ_TIME = 0.055;  // s de lectura por letra antes de la siguiente
    private static final int TEXT_WIDTH = 900;      // ancho máximo de los renglones (px)
    private static final Color VEIL = new Color(4, 6, 12, 125);

    private final List<String> story;
    private final double[] lineStart;   // cuándo empieza a aparecer cada línea
    private final double readyAt;       // cuándo aparece "Enter o clic: jugar"
    private double time;

    public Menu(List<String> story) {
        this.story = story;
        lineStart = new double[story.size()];
        double t = TITLE_FADE;
        for (int i = 0; i < story.size(); i++) {
            lineStart[i] = t;
            t += 0.8 + story.get(i).length() * READ_TIME;
        }
        readyAt = t;
    }

    /** Vuelve a empezar: con la historia desde el principio, o ya completa. */
    public void restart(boolean withStory) {
        time = withStory ? 0 : readyAt;
    }

    public void update(double dt) {
        time += dt;
    }

    /**
     * Enter o clic. Si la historia todavía está apareciendo, la muestra completa y regresa
     * false. Si ya estaba completa, regresa true: a jugar.
     */
    public boolean confirm() {
        if (time < readyAt) {
            time = readyAt;
            return false;
        }
        return true;
    }

    public boolean isReady() {
        return time >= readyAt;
    }

    public void draw(Graphics2D g) {
        Text.smooth(g);
        Text.veil(g, VEIL, 1, Config.WIDTH, Config.HEIGHT);   // para que el texto se lea sobre la cueva
        double cx = Config.WIDTH / 2.0;

        // El espaciado entre letras también se agrega después de la última: se compensa
        g.setFont(Text.TITLE);
        Text.centered(g, "UMBRA", cx + 14, 180, Text.BRIGHT, time / TITLE_FADE, 3);

        g.setFont(Text.STORY);
        FontMetrics fm = g.getFontMetrics();
        double y = 280;
        for (int i = 0; i < story.size(); i++) {
            double alpha = (time - lineStart[i]) / LINE_FADE;
            for (String row : Text.wrap(story.get(i), fm, TEXT_WIDTH)) {
                Text.centered(g, row, cx, y, Text.STORY_COLOR, alpha, 0);
                y += 33;
            }
            y += 10;
        }

        if (isReady()) {
            double fadeIn = Text.clamp01((time - readyAt) / 0.6);
            double pulse = 0.6 + 0.4 * Math.sin((time - readyAt) * 3);
            g.setFont(Text.HINT);
            Text.centered(g, "Enter o clic: jugar            Esc: salir", cx, Config.HEIGHT - 86,
                    Text.BRIGHT, fadeIn * pulse, 1.2);
            g.setFont(Text.SMALL);
            Text.centered(g, "A/D: moverse   \u00b7   W/S: subir y bajar dentro de la sombra   \u00b7   "
                    + "Rat\u00f3n: mover a Logi   \u00b7   Clic: fijarla   \u00b7   M: m\u00fasica",
                    cx, Config.HEIGHT - 50, Text.HINT_COLOR, fadeIn * 0.8, 0);
        } else {
            g.setFont(Text.SMALL);
            Text.centered(g, "Enter o clic: ver toda la historia", cx, Config.HEIGHT - 50,
                    Text.HINT_COLOR, Text.clamp01(time - 1) * 0.5, 0);
        }
    }
}
