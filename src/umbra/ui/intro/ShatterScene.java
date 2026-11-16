package umbra.ui.intro;

import java.awt.Color;
import java.awt.Graphics2D;

import umbra.core.Config;

/**
 * [v0.8.6] Escena 2: el vidrio se agrieta, un destello, y la luz sale volando en pedazos que
 * caen por túneles y pozos. Sigue de corrido a la escena 1 (misma cámara, misma llama).
 */
public final class ShatterScene extends Scene {

    /** Segundo en que el Farol se rompe. */
    private static final double BREAK = 2.75;
    /** Cuándo truena cada tanda de grietas (segundos) y qué parte del total abre. */
    private static final double[] CRACK_AT = { 0.5, 1.3, 2.0 };
    private static final double[] CRACK_SHARE = { 0.3, 0.3, 0.4 };
    private static final int SHARDS = 84;
    private static final Color FLASH = new Color(255, 250, 226);

    private final GoldenStage stage;
    private boolean burst;   // ya salieron los pedazos

    public ShatterScene(GoldenStage stage, double length) {
        this.stage = stage;
        // Se queda cerca mientras se agrieta; al romperse se aleja para ver a dónde cae la luz
        camera.key(0, GoldenStage.CLOSE_X, GoldenStage.CLOSE_Y, GoldenStage.CLOSE_ZOOM)
              .key(BREAK, GoldenStage.CLOSE_X, GoldenStage.CLOSE_Y - 4, GoldenStage.CLOSE_ZOOM * 1.05)
              .key(BREAK + 2.3, GoldenStage.WIDE_X, GoldenStage.WIDE_Y + 4, 1.03)
              .key(length, GoldenStage.WIDE_X, GoldenStage.WIDE_Y, GoldenStage.WIDE_ZOOM);
    }

    @Override
    public void enter() {
        GreatLantern lantern = stage.lantern;
        lantern.setFlame(1);
        lantern.setEyes(1);
        lantern.setBroken(false);
        lantern.setCrack(0);
        lantern.setEmber(0);
        stage.shards.clear();
        burst = false;
    }

    @Override
    protected void update(double dt) {
        stage.update(dt);
        GreatLantern lantern = stage.lantern;
        if (time < BREAK) {
            // Las grietas avanzan en tres tandas; la llama se agita y mira hacia la grieta
            double crack = 0;
            for (int i = 0; i < CRACK_AT.length; i++) {
                crack += CRACK_SHARE[i] * Ease.out(Ease.ramp(time, CRACK_AT[i], CRACK_AT[i] + 0.55));
            }
            lantern.setCrack(crack);
            lantern.setAgitation(Ease.smooth(time, 0.4, 2.4));
            double startled = Ease.smooth(time, CRACK_AT[0], CRACK_AT[0] + 0.25);
            double shut = Ease.smooth(time, BREAK - 0.32, BREAK - 0.1);   // aprieta los ojos antes del destello
            lantern.setEyes((1 + 0.3 * startled) * (1 - shut));
            lantern.setLook(-0.9 * startled, -0.5 * startled);
        } else {
            if (!burst) {
                burst = true;
                lantern.setBroken(true);
                lantern.setFlame(0);
                lantern.setEyes(0);
                lantern.setAgitation(0);
                lantern.setCrack(1);
                stage.shards.burst(GoldenCavern.LANTERN_X, GoldenCavern.LANTERN_Y + GreatLantern.FLAME_Y,
                        stage.cavern.getHoles(), SHARDS, BREAK);
            }
            lantern.setEmber(1 - 0.7 * Ease.smooth(time, BREAK + 1, BREAK + 4.5));
        }
        camera.setShake(shake(time, 5.3), shake(time + 0.37, 4.1));
    }

    /**
     * Temblor de la cámara: cada golpe (las grietas y, mucho más fuerte, la rotura) es una
     * oscilación amortiguada, A · e^(-k·s) · sin(w·s), con s los segundos desde el golpe.
     */
    private static double shake(double t, double w) {
        double sum = 0;
        for (double at : CRACK_AT) sum += hit(t - at, 3.5, w);
        return sum + hit(t - BREAK, 20, w);
    }

    private static double hit(double since, double amplitude, double w) {
        return since < 0 ? 0 : amplitude * Math.exp(-5.5 * since) * Math.sin(since * w * 9);
    }

    @Override
    public void draw(Graphics2D g) {
        // Al romperse, la caverna pierde su luz: primero de golpe (tapado por el destello) y
        // luego poco a poco, conforme los pedazos se van por los túneles
        double dim = time < BREAK ? 0 : Ease.lerp(0.36, GoldenStage.DUSK, Ease.smooth(time, BREAK + 0.6, BREAK + 4.6));
        stage.draw(g, camera, dim, time < BREAK ? 1 : 0.35);
        stage.shards.draw(g, camera.getViewMatrix(), camera.getZoom(), time);

        // El destello: sube rápido justo antes de romperse y se apaga como e^(-3·s)
        double flash = time < BREAK ? 0.92 * Ease.in(Ease.ramp(time, BREAK - 0.16, BREAK))
                                    : Math.exp(-3 * (time - BREAK));
        if (flash > 0.01) {
            g.setColor(Ease.fade(FLASH, flash));
            g.fillRect(0, 0, Config.WIDTH, Config.HEIGHT);
        }
    }
}
