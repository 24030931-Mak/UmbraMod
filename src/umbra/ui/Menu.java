package umbra.ui;

import java.awt.Color;
import java.awt.Graphics2D;

import umbra.core.Config;

/**
 * [v1.0] Menú de inicio: el título UMBRA, una frase y las opciones.
 *
 * [v0.8.6] La historia ya no se lee aquí línea por línea: la cuenta la intro (ui.Intro), que
 * sale al abrir el juego y se puede volver a ver con la tecla I.
 */
public class Menu {

    private static final double TITLE_FADE = 1.2;   // s que tarda en aparecer el título
    private static final double READY_AT = 0.7;     // s en que empiezan a aparecer las opciones
    private static final Color VEIL = new Color(4, 6, 12, 125);

    private double time;

    /** Vuelve a empezar: el título y las opciones aparecen otra vez. */
    public void restart() {
        time = 0;
    }

    public void update(double dt) {
        time += dt;
    }

    /** Ya empezaron a aparecer las opciones: desde este momento el menú acepta teclas. */
    public boolean isReady() {
        return time >= READY_AT;
    }

    /** Todo el menú: el velo y encima el texto. */
    public void draw(Graphics2D g) {
        drawVeil(g);
        drawText(g);
    }

    /** El velo que oscurece la cueva para que el texto se lea. Va aparte para poder poner a Logi entre el velo y el texto. */
    public void drawVeil(Graphics2D g) {
        Text.veil(g, VEIL, 1, Config.WIDTH, Config.HEIGHT);
    }

    /** El título, la frase y las indicaciones. */
    public void drawText(Graphics2D g) {
        Text.smooth(g);
        double cx = Config.WIDTH / 2.0;

        // El espaciado entre letras también se agrega después de la última: se compensa
        g.setFont(Text.TITLE);
        Text.centered(g, "UMBRA", cx + 14, 300, Text.BRIGHT, time / TITLE_FADE, 3);

        // Una línea fina a cada lado de la frase, como en el título de los niveles
        double fadeIn = Text.clamp01((time - READY_AT) / 0.8);
        g.setFont(Text.STORY);
        Text.centered(g, "La sombra es el \u00fanico refugio", cx, 366, Text.STORY_COLOR, fadeIn, 0);
        g.setColor(Text.withAlpha(Text.HINT_COLOR, fadeIn * 0.6));
        g.fillRect((int) cx - 330, 358, 120, 1);
        g.fillRect((int) cx + 210, 358, 120, 1);

        double pulse = 0.6 + 0.4 * Math.sin((time - READY_AT) * 3);
        g.setFont(Text.HINT);
        Text.centered(g, "Enter o clic: jugar", cx, 500, Text.BRIGHT, fadeIn * pulse, 1.2);
        Text.centered(g, "I: ver la intro            Esc: salir", cx, 540, Text.HINT_COLOR, fadeIn * 0.9, 0);
        g.setFont(Text.SMALL);
        Text.centered(g, "A/D: moverse   \u00b7   W/S: subir y bajar dentro de la sombra   \u00b7   "
                + "Rat\u00f3n: mover a Logi   \u00b7   Clic: fijarla   \u00b7   Tab: mapa   \u00b7   M: m\u00fasica",
                cx, Config.HEIGHT - 50, Text.HINT_COLOR, fadeIn * 0.8, 0);
    }
}
