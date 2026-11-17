package umbra.ui.intro;

import java.awt.Graphics2D;

/**
 * [v0.8.6] Escena 1: la caverna dorada, sin una sola sombra, y al centro el Gran Farol. La
 * cámara se acerca despacio a la llama de adentro, que abre dos ojos oscuros.
 */
public final class LanternScene extends Scene {

    private final GoldenStage stage;

    public LanternScene(GoldenStage stage, double length) {
        this.stage = stage;
        // De lejos (toda la caverna), un momento casi quieta, y luego el acercamiento a la llama
        camera.key(0, GoldenStage.WIDE_X, GoldenStage.WIDE_Y - 8, GoldenStage.WIDE_ZOOM)
              .key(1.7, GoldenStage.WIDE_X, GoldenStage.WIDE_Y - 4, 1.06)
              .key(6.4, GoldenStage.CLOSE_X, GoldenStage.CLOSE_Y + 2, GoldenStage.CLOSE_ZOOM * 0.96)
              .key(length, GoldenStage.CLOSE_X, GoldenStage.CLOSE_Y, GoldenStage.CLOSE_ZOOM);
    }

    private static final SubtitleTrack SUBTITLES = SubtitleTrack.lantern();

    @Override
    protected String voiceClip() { return "/umbra/res/voz_1.wav"; }

    @Override
    protected double voiceAt() { return 0.20; }

    @Override
    public SubtitleTrack getSubtitles() { return SUBTITLES; }

    @Override
    public void enter() {
        GreatLantern lantern = stage.lantern;
        lantern.setAgitation(0);
        lantern.setCrack(0);
        lantern.setBroken(false);
        lantern.setEmber(0);
        stage.shards.clear();
    }

    @Override
    protected void update(double dt) {
        stage.update(dt);
    }

    @Override
    public void draw(Graphics2D g) {
        stage.draw(g, camera, 0, 1);
    }

    @Override
    public double fadeIn() { return 1.5; }
}
