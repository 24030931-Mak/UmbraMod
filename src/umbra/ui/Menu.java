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

    /** Alto de cada fila y centros (y) de las tres filas de partida. */
    private static final int ROW_HEIGHT = 40;
    private static final int[] ROW_Y = { 470, 510, 550 };
    /** Ancho (a cada lado del centro) en que el ratón cuenta como "sobre la fila". */
    private static final int ROW_HALF_WIDTH = 330;

    private double time;
    private String[] rows = { "Partida nueva", "Partida nueva", "Partida nueva" };
    private int selected;
    private boolean confirmDelete;

    /** Los 3 textos de las filas, ya armados: "Partida nueva", "Partida da\u00f1ada" o el resumen. */
    public void setSlots(String[] rows) {
        this.rows = rows.clone();
    }

    public void setSelected(int i) {
        selected = Math.max(0, Math.min(ROW_Y.length - 1, i));
    }

    public int getSelected() {
        return selected;
    }

    /** Mientras está activa, la pregunta de borrar reemplaza a las indicaciones. */
    public void setConfirmDelete(boolean on) {
        confirmDelete = on;
    }

    /** La fila que está bajo el punto (en pantalla), o -1 si no hay ninguna. */
    public int rowAt(int mouseX, int mouseY) {
        if (Math.abs(mouseX - Config.WIDTH / 2) > ROW_HALF_WIDTH) return -1;
        for (int i = 0; i < ROW_Y.length; i++) {
            if (Math.abs(mouseY - ROW_Y[i]) <= ROW_HEIGHT / 2) return i;
        }
        return -1;
    }

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

        // Las tres filas de partida; la elegida lleva un triángulo a la izquierda y brilla más
        double pulse = 0.6 + 0.4 * Math.sin((time - READY_AT) * 3);
        g.setFont(Text.HINT);
        for (int i = 0; i < ROW_Y.length; i++) {
            boolean on = i == selected;
            Text.centered(g, rows[i], cx, ROW_Y[i], on ? Text.BRIGHT : Text.HINT_COLOR,
                    fadeIn * (on ? 0.7 + 0.3 * pulse : 0.8), on ? 1.2 : 0);
            if (on) {
                int left = (int) Math.round(cx - g.getFontMetrics().stringWidth(rows[i]) / 2.0) - 26;
                Text.centered(g, "\u25b8", left, ROW_Y[i], Text.BRIGHT, fadeIn * pulse, 0);
            }
        }
        if (confirmDelete) {
            Text.centered(g, "\u00bfBorrar esta partida?   Enter: s\u00ed   \u00b7   Esc: no", cx, 610,
                    Text.BRIGHT, fadeIn, 1.2);
        } else {
            Text.centered(g, "W/S o rat\u00f3n: elegir   \u00b7   Enter o clic: jugar   \u00b7   Supr: borrar", cx, 610,
                    Text.HINT_COLOR, fadeIn * 0.9, 0);
            Text.centered(g, "I: ver la intro            Esc: salir", cx, 640, Text.HINT_COLOR, fadeIn * 0.9, 0);
        }
        g.setFont(Text.SMALL);
        Text.centered(g, "A/D: moverse   \u00b7   W/S: subir y bajar dentro de la sombra   \u00b7   "
                + "Rat\u00f3n: mover a Logi   \u00b7   Clic: fijarla   \u00b7   Tab: mapa   \u00b7   M: m\u00fasica",
                cx, Config.HEIGHT - 30, Text.HINT_COLOR, fadeIn * 0.8, 0);
    }
}
