package umbra.ui.intro;

import java.awt.Graphics2D;

/**
 * [v0.8.6] Escena 3: la oscuridad avanza como neblina desde los dos lados y lo apaga todo. Por
 * un instante, dos ojos blancos miran desde adentro. Sigue de corrido a la escena 2 y termina
 * en negro (la neblina lo cubrió todo), así que no necesita fundido.
 */
public final class DarknessScene extends Scene {

    /** Los ojos: dónde aparecen (px de pantalla) y entre qué segundos. */
    private static final double EYES_X = 318, EYES_Y = 334, EYES_FROM = 3.2, EYES_TO = 5.0;

    private final GoldenStage stage;
    private final double coveredAt;   // segundo en que la neblina ya cubrió todo

    public DarknessScene(GoldenStage stage, double length) {
        this.stage = stage;
        coveredAt = length - 0.7;
        camera.key(0, GoldenStage.WIDE_X, GoldenStage.WIDE_Y, GoldenStage.WIDE_ZOOM)
              .key(length, GoldenStage.WIDE_X, GoldenStage.WIDE_Y + 14, 1.14);
    }

    @Override
    public void enter() {
        GreatLantern lantern = stage.lantern;
        lantern.setBroken(true);
        lantern.setEyes(0);
        lantern.setCrack(1);
        stage.shards.clear();
    }

    @Override
    protected void update(double dt) {
        stage.update(dt);
        stage.lantern.setEmber(0.3 * (1 - Ease.smooth(time, 0, 3)));   // la última brasa se apaga
    }

    @Override
    public void draw(Graphics2D g) {
        // La neblina avanza despacio al principio y cierra rápido al final: avance^1.7
        double cover = Math.pow(Ease.ramp(time, 0.2, coveredAt), 1.7);
        if (cover < 1) {
            stage.draw(g, camera, Ease.lerp(GoldenStage.DUSK, 0.9, Ease.smooth(time, 0, 3.5)), 0.35 * (1 - cover));
        }
        stage.mist.draw(g, cover, time);

        // Dos ojos en la neblina: se abren, parpadean una vez y se cierran
        double open = Ease.window(time, EYES_FROM, EYES_FROM + 0.35, EYES_TO - 0.3, EYES_TO);
        double blink = Ease.window(time, 4.1, 4.17, 4.21, 4.3);
        stage.mist.drawEyes(g, EYES_X + 5 * (time - EYES_FROM), EYES_Y, open * (1 - 0.92 * blink));
    }
}
