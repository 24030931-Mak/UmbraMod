package umbra.ui.intro;

import java.awt.Graphics2D;

/**
 * [v0.8.6] Escena 1: la caverna dorada, sin una sola sombra, y al centro el Gran Farol. La
 * cámara se acerca despacio a la llama de adentro, rodeada por sus anillos (sin ojos desde la v0.8.7).
 */
public final class LanternScene extends Scene {

    /**
     * [v0.8.7] El presagio: desde OMEN_AT (cuando la cámara ya está casi encima de la llama) la
     * llama se inquieta un poco y los anillos empiezan a latir. Con u el avance de OMEN_AT al
     * final de la escena y s = smooth(u),
     *     agitación = OMEN · s · (1 + 0.4 · sin(π·u) · sin(4.1·(t - OMEN_AT)))
     * La fluctuación (el segundo factor) se apaga en las dos puntas porque sin(π·u) vale 0 en
     * u = 0 y en u = 1: la escena termina con la agitación justo en OMEN, que es de donde arranca
     * la escena 2. Así no hay brinco entre las dos tomas.
     */
    public static final double OMEN = 0.12;
    private static final double OMEN_AT = 4.5;

    private final GoldenStage stage;
    private final double length;

    public LanternScene(GoldenStage stage, double length) {
        this.stage = stage;
        this.length = length;
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
        lantern.setRingPulse(0);
        lantern.setHaloScale(1);
        stage.shards.clear();
    }

    @Override
    protected void update(double dt) {
        double u = Ease.ramp(time, OMEN_AT, length), s = Ease.smooth(u);
        GreatLantern lantern = stage.lantern;
        lantern.setAgitation(OMEN * s * (1 + 0.4 * Math.sin(Math.PI * u) * Math.sin(4.1 * (time - OMEN_AT))));
        lantern.setRingPulse(s);
        stage.update(dt);
    }

    @Override
    public void draw(Graphics2D g) {
        stage.draw(g, camera, 0, 1);
    }

    @Override
    public double fadeIn() { return 1.5; }
}
