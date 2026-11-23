package umbra.ui.intro;

import java.awt.Graphics2D;

/**
 * [v0.8.6] Escena 3: la oscuridad avanza como neblina desde los dos lados y lo apaga todo
 * ([v0.8.7] también baja del techo y se cierra desde las esquinas; ver Mist). Sigue de corrido
 * a la escena 2 y termina en negro (la neblina lo cubrió todo), así que no necesita fundido.
 */
public final class DarknessScene extends Scene {

    private final GoldenStage stage;
    private final double coveredAt;   // segundo en que la neblina ya cubrió todo

    public DarknessScene(GoldenStage stage, double length) {
        this.stage = stage;
        coveredAt = length - 0.7;
        camera.key(0, GoldenStage.WIDE_X, GoldenStage.WIDE_Y, GoldenStage.WIDE_ZOOM)
              .key(length, GoldenStage.WIDE_X, GoldenStage.WIDE_Y + 14, 1.14);
    }

    private static final SubtitleTrack SUBTITLES = SubtitleTrack.darkness();

    @Override
    protected String voiceClip() { return "/umbra/res/voz_3.wav"; }

    @Override
    protected double voiceAt() { return 1.10; }

    @Override
    public SubtitleTrack getSubtitles() { return SUBTITLES; }

    @Override
    public void enter() {
        GreatLantern lantern = stage.lantern;
        lantern.setBroken(true);
        lantern.setCrack(1);
        stage.shards.clear();
        stage.setBraziers(0);   // [v0.9.5] los braseros de la sala ya se apagaron en la escena 2
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
    }
}
