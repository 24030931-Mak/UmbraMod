package umbra.ui;

import java.awt.Color;
import java.awt.Graphics2D;

import umbra.core.Config;

/**
 * [v1.0] Título del nivel ("I · Las Raíces") que aparece al empezar y se desvanece solo.
 * No detiene el juego: se puede jugar mientras se ve.
 */
public class LevelTitle {

    private static final double IN = 1.0, HOLD = 2.2, OUT = 1.2;   // segundos
    private static final Color LINE = new Color(150, 170, 205);

    private String number = "";
    private String name = "";
    private double time = -1;   // < 0: no se muestra

    public void show(String number, String name) {
        this.number = number;
        this.name = name;
        time = 0;
    }

    public void update(double dt) {
        if (time < 0) return;
        time += dt;
        if (time > IN + HOLD + OUT) time = -1;
    }

    public boolean isShowing() {
        return time >= 0;
    }

    public void draw(Graphics2D g) {
        if (time < 0) return;
        // Aparece, se queda y se desvanece
        double a = time < IN ? time / IN : time < IN + HOLD ? 1 : 1 - (time - IN - HOLD) / OUT;
        Text.smooth(g);
        double cx = Config.WIDTH / 2.0;

        g.setFont(Text.CHAPTER);
        Text.centered(g, number, cx, 176, Text.HINT_COLOR, a, 0);
        g.setColor(Text.withAlpha(LINE, a * 0.7));   // una línea fina a cada lado del número
        g.fillRect((int) cx - 130, 168, 100, 1);
        g.fillRect((int) cx + 30, 168, 100, 1);

        g.setFont(Text.LEVEL_NAME);
        Text.centered(g, name, cx, 236, Text.BRIGHT, a, 1.5);
    }
}
