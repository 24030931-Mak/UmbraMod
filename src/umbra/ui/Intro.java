package umbra.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import umbra.core.Config;
import umbra.entities.LightSource;
import umbra.entities.Player;
import umbra.math.Mat3;
import umbra.ui.intro.BirthScene;
import umbra.ui.intro.DarkStage;
import umbra.ui.intro.DarknessScene;
import umbra.ui.intro.Ease;
import umbra.ui.intro.GoldenStage;
import umbra.ui.intro.LanternScene;
import umbra.ui.intro.Scene;
import umbra.ui.intro.ShatterScene;
import umbra.ui.intro.TitleScene;
import umbra.ui.intro.TogetherScene;

/**
 * [v0.8.6] La intro: cómo se rompió el Gran Farol y cómo nacieron Candil y Logi, en seis
 * escenas que dibuja el propio juego (nada de video ni de imágenes).
 *
 * Línea de tiempo. Hay un solo reloj, 'time', que avanza con cada update. Cada escena ocupa un
 * tramo de ese reloj: la escena i empieza en la suma de lo que duran las anteriores, y su
 * tiempo local es
 *     t = time - inicio(i)
 * Todo lo que pasa dentro de una escena depende de ese t (ver ui.intro.Scene), así que la intro
 * se ve igual cada vez. Las duraciones del guion son 7, 7, 6, 8, 8 y 5 segundos; a cada escena
 * se le suman FADE segundos para sus fundidos: 41 + 6 · 0.65 = 44.9 s.
 *
 * Fundidos. Un fundido es un rectángulo negro sobre toda la pantalla cuya transparencia cambia
 * con el tiempo. Al empezar una escena baja de 1 a 0 en fadeIn segundos, y al terminar sube de
 * 0 a 1 en fadeOut:
 *     negro = max(1 - t / fadeIn,  1 - (duración - t) / fadeOut)
 * Las escenas que siguen de corrido a la anterior (la 2 y la 3) piden fundido 0.
 *
 * La narración sale de res/intro.txt (una línea por escena, se puede editar). Cada oración
 * aparece a su tiempo y todas se desvanecen antes de que la escena termine.
 *
 * Las imágenes grandes de los escenarios se generan la primera vez que la intro se usa y se
 * sueltan cuando termina, para no ocupar memoria mientras se juega.
 */
public class Intro {

    /** Lo que dura cada escena según el guion (s), sin contar sus fundidos. */
    public static final double SCENE_1 = 7, SCENE_2 = 7, SCENE_3 = 6, SCENE_4 = 8, SCENE_5 = 8, SCENE_6 = 5;
    /** Segundos que se le suman a cada escena para sus fundidos. */
    public static final double FADE = 0.65;
    /** Segundos que tarda en oscurecerse cuando se salta (Enter, Esc, Espacio o clic). */
    public static final double SKIP_FADE = 0.35;

    private static final double[] LENGTHS = {
        SCENE_1 + FADE, SCENE_2 + FADE, SCENE_3 + FADE, SCENE_4 + FADE, SCENE_5 + FADE, SCENE_6 + FADE };

    // ---- La narración ----
    private static final double TEXT_IN = 0.7;        // cuándo aparece la primera oración (s de la escena)
    private static final double TEXT_FADE = 0.9;      // lo que tarda en aparecer cada oración
    private static final double TEXT_OUT = 1.25;      // cuánto antes del final empieza a desvanecerse
    private static final int TEXT_WIDTH = 1040;       // ancho máximo de un renglón (px)
    private static final int ROW_HEIGHT = 40;
    private static final int LAST_BASELINE = Config.HEIGHT - 54;
    private static final int BAND_HEIGHT = 210;       // franja oscura detrás del texto
    private static final Color BAND = new Color(3, 4, 10, 185);
    private static final Color BAND_CLEAR = new Color(3, 4, 10, 0);
    private static final Color VOICE = new Color(255, 226, 172);   // lo que dice Logi (empieza con raya)

    private final List<String> narration;
    private Scene[] scenes;      // null mientras no se usan (ver scenes())
    private DarkStage dark;      // el escenario de Candil y Logi (escenas 4 a 6)
    private double time;         // el reloj de la intro (s)
    private int index;           // la escena actual, de 0 a 5
    private double skipTime = -1;   // segundos desde que se pidió saltar; -1 = no se ha pedido
    private boolean finished;

    // Los textos ya dibujados: los renglones de la escena 'rowsOf' y el título
    private final List<TextSprite> rows = new ArrayList<>();
    private final List<Integer> rowOwner = new ArrayList<>();   // a qué oración pertenece cada renglón
    private int rowsOf = -1, sentenceCount;
    private TextSprite title;

    /** @param narration las líneas de res/intro.txt: una por escena (la última escena no tiene) */
    public Intro(List<String> narration) {
        this.narration = narration;
    }

    /** Cuánto dura toda la intro (s): la suma de sus escenas. */
    public double getDuration() {
        double total = 0;
        for (double length : LENGTHS) total += length;
        return total;
    }

    /** La deja al principio, lista para verse otra vez. */
    public void restart() {
        time = 0;
        index = 0;
        skipTime = -1;
        finished = false;
        scenes()[0].enter();
    }

    /** La termina ya: la pantalla se oscurece en SKIP_FADE segundos y la intro queda terminada. */
    public void skip() {
        if (skipTime < 0) skipTime = 0;
    }

    public boolean isFinished() {
        return finished;
    }

    public void update(double dt) {
        if (finished) return;
        Scene[] all = scenes();
        time += dt;
        if (skipTime >= 0) skipTime += dt;
        // (1e-9: al sumar pasos de 1/60 el reloj puede quedar una pizca antes del final)
        if (time >= getDuration() - 1e-9 || skipTime >= SKIP_FADE) {
            finish();
            return;
        }
        // ¿A qué escena le toca este segundo? Se avanza mientras el reloj ya pasó el final de la actual.
        while (index < all.length - 1 && time >= sceneStart(index + 1)) {
            index++;
            all[index].enter();
        }
        all[index].advance(dt, time - sceneStart(index));
    }

    /** Termina y suelta los escenarios (sus imágenes ocupan unos 75 MB); restart() los vuelve a crear. */
    private void finish() {
        finished = true;
        scenes = null;
        dark = null;
        rows.clear();
        rowsOf = -1;
        title = null;
    }

    /** En qué segundo del reloj empieza la escena i: la suma de las duraciones anteriores. */
    private static double sceneStart(int i) {
        double start = 0;
        for (int k = 0; k < i; k++) start += LENGTHS[k];
        return start;
    }

    /** Crea las seis escenas la primera vez que hacen falta (genera las imágenes de los escenarios). */
    private Scene[] scenes() {
        if (scenes == null) {
            GoldenStage golden = new GoldenStage();
            dark = new DarkStage();
            scenes = new Scene[] {
                new LanternScene(golden, LENGTHS[0]),
                new ShatterScene(golden, LENGTHS[1]),
                new DarknessScene(golden, LENGTHS[2]),
                new BirthScene(dark, LENGTHS[3]),
                new TogetherScene(dark, LENGTHS[4]),
                new TitleScene(dark, LENGTHS[5]),
            };
            scenes[index].enter();
        }
        return scenes;
    }

    // =========================================================
    //  DIBUJO
    // =========================================================

    public void draw(Graphics2D g) {
        if (finished) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, Config.WIDTH, Config.HEIGHT);
            return;
        }
        Scene scene = scenes()[index];
        double t = getSceneTime(), length = LENGTHS[index];
        scene.draw(g);

        // Fundidos: el de entrada, el de salida y el de saltar; manda el más oscuro
        double black = 0;
        if (scene.fadeIn() > 0) black = Math.max(black, 1 - t / scene.fadeIn());
        if (scene.fadeOut() > 0) black = Math.max(black, 1 - (length - t) / scene.fadeOut());
        if (skipTime >= 0) black = Math.max(black, skipTime / SKIP_FADE);
        if (black > 0.004) {
            g.setColor(new Color(0, 0, 0, (int) Math.round(255 * Ease.clamp01(black))));
            g.fillRect(0, 0, Config.WIDTH, Config.HEIGHT);
        }

        double visible = 1 - Ease.clamp01(black);   // el texto también se va con el fundido
        if (index < narration.size()) drawNarration(g, t, length, visible);
        if (scene.titleAlpha() > 0) {
            if (title == null) title = new TextSprite("UMBRA", Text.TITLE, Text.BRIGHT, 3);
            // El espaciado entre letras también va después de la última: se compensa con 14 px
            title.draw(g, Config.WIDTH / 2.0 + 14, 262, scene.titleAlpha() * visible);
        }
        double hint = 0.5 * Ease.window(time, 1.4, 2.4, 6.5, 8) * visible;
        if (hint > 0.01) {
            Text.smooth(g);
            g.setFont(Text.SMALL);
            String s = "Enter: saltar";
            Text.centered(g, s, Config.WIDTH - 26 - g.getFontMetrics().stringWidth(s) / 2.0, 30, Text.HINT_COLOR, hint, 0);
        }
    }

    /**
     * Los renglones de la narración de la escena actual, ya dibujados (TextSprite). La línea se
     * parte en oraciones (después de cada punto y antes de una raya de diálogo) y cada oración,
     * en los renglones que hagan falta para caber en TEXT_WIDTH. Se preparan una vez por escena.
     */
    private void prepareNarration(Graphics2D g) {
        if (rowsOf == index) return;
        rowsOf = index;
        rows.clear();
        rowOwner.clear();
        FontMetrics fm = g.getFontMetrics(Text.STORY_BIG);
        List<String> sentences = sentences(narration.get(index));
        sentenceCount = sentences.size();
        for (int k = 0; k < sentences.size(); k++) {
            boolean voice = sentences.get(k).startsWith("\u2014");
            for (String row : Text.wrap(sentences.get(k), fm, TEXT_WIDTH)) {
                rows.add(new TextSprite(row, Text.STORY_BIG, voice ? VOICE : Text.BRIGHT, 1.5));
                rowOwner.add(k);
            }
        }
    }

    /**
     * La narración de una escena, abajo y centrada, sobre una franja oscura que se desvanece
     * hacia arriba (un degradado vertical) para que se lea igual sobre la caverna dorada que
     * sobre la cueva oscura.
     *
     * Cada oración aparece a su tiempo: la oración k de n empieza en
     *     TEXT_IN + k · (duración - TEXT_IN - TEXT_OUT - 1.2) / n
     * así la segunda entra cuando la primera ya se leyó. Todas se van juntas al final.
     */
    private void drawNarration(Graphics2D g, double t, double length, double visible) {
        double out = 1 - Ease.smooth(t, length - TEXT_OUT, length - TEXT_OUT + 0.7);
        double band = Ease.smooth(t, TEXT_IN - 0.3, TEXT_IN + 0.6) * out * visible;
        if (band <= 0.004) return;

        g.setPaint(new GradientPaint(0, Config.HEIGHT - BAND_HEIGHT, BAND_CLEAR, 0, Config.HEIGHT - 50, Ease.fade(BAND, band)));
        g.fillRect(0, Config.HEIGHT - BAND_HEIGHT, Config.WIDTH, BAND_HEIGHT);

        prepareNarration(g);
        double gap = (length - TEXT_IN - TEXT_OUT - 1.2) / Math.max(1, sentenceCount);
        for (int r = 0; r < rows.size(); r++) {
            double start = TEXT_IN + rowOwner.get(r) * gap;
            double alpha = Ease.smooth(t, start, start + TEXT_FADE) * out * visible;
            double y = LAST_BASELINE - (rows.size() - 1 - r) * ROW_HEIGHT;
            rows.get(r).draw(g, Config.WIDTH / 2.0, y, alpha);
        }
    }

    /** Parte la línea en oraciones: corta después de ". " y antes de una raya de diálogo. */
    private static List<String> sentences(String line) {
        List<String> result = new ArrayList<>();
        for (String part : line.split("(?<=\\.)\\s+|\\s+(?=\u2014)")) {
            if (!part.trim().isEmpty()) result.add(part.trim());
        }
        return result;
    }

    // =========================================================
    //  CONSULTAS (para GamePanel y las pruebas)
    // =========================================================

    /** La escena actual, de 1 a 6. */
    public int getSceneNumber() {
        return index + 1;
    }

    /** Segundos desde que empezó la escena actual. */
    public double getSceneTime() {
        return time - sceneStart(index);
    }

    /** Matriz de vista de la cámara de la escena actual (mundo -> pantalla). */
    public Mat3 getViewMatrix() {
        return scenes()[index].getViewMatrix();
    }

    /** Candil: el mismo Player del juego, con su física y sus formas. */
    public Player getCandil() {
        scenes();
        return dark.getCandil();
    }

    /** Logi: el mismo LightSource del juego. */
    public LightSource getLogi() {
        scenes();
        return dark.getLogi();
    }
}
