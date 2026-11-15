package umbra.world;

/**
 * Lo que Candil ya recorrió de un nivel, para el mapa.
 *
 * El nivel se divide en una cuadrícula de zonas de CELL x CELL px (80 x 80). Una zona se
 * marca como recorrida cuando su centro queda a una distancia <= radio de la posición de
 * Candil: se compara la distancia al cuadrado (dx^2 + dy^2 <= r^2), sin sacar raíz.
 *
 * Cada zona tiene dos banderas: "revelada" (Candil ya pasó cerca) y "mostrada" (el mapa ya
 * la dibujó encendida). Las reveladas que aún no se muestran son las que el mapa enciende
 * poco a poco la próxima vez que se abre.
 */
public class Exploration {

    /** Lado de cada zona (px). */
    public static final int CELL = 80;

    private final int cols, rows;
    private final boolean[][] revealed;
    private final boolean[][] shown;

    /** La cuadrícula que cubre un nivel de width x height px (las zonas del borde pueden sobrar). */
    public Exploration(double width, double height) {
        cols = Math.max(1, (int) Math.ceil(width / CELL));
        rows = Math.max(1, (int) Math.ceil(height / CELL));
        revealed = new boolean[cols][rows];
        shown = new boolean[cols][rows];
    }

    public int cols() { return cols; }
    public int rows() { return rows; }

    /**
     * Marca las zonas cuyo centro está a <= radius de (x, y). Regresa cuántas eran nuevas.
     * Solo se recorren las zonas que el cuadro del círculo toca, recortadas a la cuadrícula:
     * si (x, y) cae fuera del nivel (Candil al vacío) no se marca nada inexistente ni truena.
     */
    public int reveal(double x, double y, double radius) {
        if (Double.isNaN(x) || Double.isNaN(y) || radius < 0) return 0;
        // La zona c tiene su centro en (c + 0.5) * CELL: se despeja el rango que cae en el cuadro
        int c0 = clamp((int) Math.floor((x - radius) / CELL - 0.5), cols);
        int c1 = clamp((int) Math.ceil((x + radius) / CELL - 0.5), cols);
        int r0 = clamp((int) Math.floor((y - radius) / CELL - 0.5), rows);
        int r1 = clamp((int) Math.ceil((y + radius) / CELL - 0.5), rows);
        int fresh = 0;
        for (int c = c0; c <= c1; c++) {
            for (int r = r0; r <= r1; r++) {
                double dx = (c + 0.5) * CELL - x, dy = (r + 0.5) * CELL - y;
                if (dx * dx + dy * dy <= radius * radius && !revealed[c][r]) {
                    revealed[c][r] = true;
                    fresh++;
                }
            }
        }
        return fresh;
    }

    private static int clamp(int v, int count) {
        return Math.max(0, Math.min(count - 1, v));
    }

    /** ¿Candil ya pasó cerca de la zona? Fuera de la cuadrícula: false. */
    public boolean isRevealed(int c, int r) {
        return inside(c, r) && revealed[c][r];
    }

    /** ¿El mapa ya dibujó encendida esta zona? */
    public boolean isShown(int c, int r) {
        return inside(c, r) && shown[c][r];
    }

    /** El mapa se cerró: lo revelado hasta ahora ya no se vuelve a encender. */
    public void markAllShown() {
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                if (revealed[c][r]) shown[c][r] = true;
            }
        }
    }

    /** Partida nueva: nada recorrido. */
    public void reset() {
        for (int c = 0; c < cols; c++) {
            java.util.Arrays.fill(revealed[c], false);
            java.util.Arrays.fill(shown[c], false);
        }
    }

    private boolean inside(int c, int r) {
        return c >= 0 && c < cols && r >= 0 && r < rows;
    }
}
