package umbra.ui.intro;

import java.awt.Graphics2D;

/**
 * [v0.8.6] Escena 1: la caverna dorada, sin una sola sombra, y al centro el Gran Farol. La
 * cámara se acerca despacio a la llama de adentro, que abre dos ojos oscuros.
 */
public final class LanternScene extends Scene {

    private static final double EYES_OPEN = 4.7;   // segundo en que la llama abre los ojos

    private final GoldenStage stage;

    public LanternScene(GoldenStage stage, double length) {
        this.stage = stage;
        // De lejos (toda la caverna), un momento casi quieta, y luego el acercamiento a la llama
        camera.key(0, GoldenStage.WIDE_X, GoldenStage.WIDE_Y - 8, GoldenStage.WIDE_ZOOM)
              .key(1.7, GoldenStage.WIDE_X, GoldenStage.WIDE_Y - 4, 1.06)
              .key(6.4, GoldenStage.CLOSE_X, GoldenStage.CLOSE_Y + 2, GoldenStage.CLOSE_ZOOM * 0.96)
              .key(length, GoldenStage.CLOSE_X, GoldenStage.CLOSE_Y, GoldenStage.CLOSE_ZOOM);
    }

    @Override
    public void enter() {
        GreatLantern lantern = stage.lantern;
        lantern.setFlame(1);
        lantern.setEyes(0);
        lantern.setLook(0, 0);
        lantern.setAgitation(0);
        lantern.setCrack(0);
        lantern.setBroken(false);
        lantern.setEmber(0);
        stage.shards.clear();
    }

    @Override
    protected void update(double dt) {
        stage.update(dt);
        // Abre los ojos, parpadea una vez y mira a un lado y al otro
        double open = Ease.smooth(time, EYES_OPEN, EYES_OPEN + 0.55);
        double blink = Ease.window(time, 6.05, 6.13, 6.17, 6.27);
        stage.lantern.setEyes(open * (1 - 0.9 * blink));
        double glance = Ease.smooth(time, 5.5, 5.8);
        stage.lantern.setLook(0.85 * glance * Math.sin((time - 5.5) * 2.4), -0.2 * glance);
    }

    @Override
    public void draw(Graphics2D g) {
        stage.draw(g, camera, 0, 1);
    }

    @Override
    public double fadeIn() { return 1.5; }
}
