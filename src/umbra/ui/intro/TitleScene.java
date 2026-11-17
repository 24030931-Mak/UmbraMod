package umbra.ui.intro;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import umbra.core.Config;

/**
 * [v0.8.6] Escena 6: el título. Candil descansa en la sombra y Logi flota a su lado; la cámara
 * se acerca despacio a ella mientras aparece UMBRA. Justo antes del corte, los ojos de Logi se
 * oscurecen un instante.
 */
public final class TitleScene extends Scene {

    private static final double CANDIL_X = 715, LOGI_X = 930, LOGI_Y = 408;   // donde quedaron en la escena 5
    private static final double DARK_FROM = 4.15, DARK_TO = 4.95;             // los ojos se oscurecen

    private final DarkStage stage;

    public TitleScene(DarkStage stage, double length) {
        this.stage = stage;
        camera.key(0, 815, 374, 2.3)
              .key(length, 920, 386, 3.4)
              .depthOrigin(920, 386, 3.4);
    }

    @Override
    public void enter() {
        stage.reset(CANDIL_X);
        stage.setDarkness(new Rectangle2D.Double(0, 0, DarkStage.W, DarkStage.H));
        stage.getLogi().snapTo(LOGI_X, LOGI_Y);
        stage.setLogiPresent(true);
        stage.setLogiLight(1);
        // Medio segundo de "juego" antes de que se vea nada: Candil ya está flotando en la sombra
        for (int i = 0; i < Config.UPS / 2; i++) stage.update(1.0 / Config.UPS);
    }

    @Override
    protected void update(double dt) {
        stage.getLogi().setEyeDarkness(Ease.window(time, DARK_FROM, DARK_FROM + 0.25, DARK_TO - 0.2, DARK_TO));
        stage.update(dt);
    }

    @Override
    public void draw(Graphics2D g) {
        stage.draw(g, camera, 0, 1, 1);
    }

    @Override
    public double titleAlpha() {
        return Ease.smooth(time, 0.9, 2.5);
    }

    @Override
    public double fadeIn() { return 0.55; }

    @Override
    public double fadeOut() { return 0.8; }
}
