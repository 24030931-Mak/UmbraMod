package umbra.ui.intro;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;

import umbra.core.Config;
import umbra.math.Vec2;

/**
 * [v0.8.6] Escena 2: el vidrio se agrieta, un destello, y la luz sale volando en pedazos que
 * caen por túneles y pozos. Sigue de corrido a la escena 1 (misma cámara, misma llama).
 *
 * [v0.8.7] El clímax en tres golpes: la implosión (la luz se encoge hacia el Farol y la caverna
 * se oscurece), el cuadro de impacto (4 cuadros en blanco con la silueta negra del Farol roto)
 * y la onda expansiva que sale de la llama. El destello que antes subía justo antes de romperse
 * se quitó: lo reemplaza la implosión, y el cuadro de impacto es ahora su punto más alto.
 */
public final class ShatterScene extends Scene {

    /** Segundo en que el Farol se rompe. */
    private static final double BREAK = 2.75;
    /** Cuándo truena cada tanda de grietas (segundos) y qué parte del total abre. */
    private static final double[] CRACK_AT = { 0.5, 1.3, 2.0 };
    private static final double[] CRACK_SHARE = { 0.3, 0.3, 0.4 };
    private static final int SHARDS = 84;
    private static final Color FLASH = new Color(255, 250, 226);

    /**
     * [v0.8.7] La implosión: de IMPLODE_AT a BREAK la luz se encoge hacia el Farol. Con
     * k = in(ramp(t, IMPLODE_AT, BREAK)) (arranca despacio y se precipita al final), la caverna
     * se oscurece k · IMPLODE_DIM y el halo y el pilar quedan a escala 1 - k · IMPLODE_SHRINK.
     */
    private static final double IMPLODE_AT = 2.35, IMPLODE_DIM = 0.45, IMPLODE_SHRINK = 0.7;
    /**
     * El cuadro de impacto dura IMPACT_FRAMES pasos de la línea de tiempo, contados desde el
     * paso en que se rompe (el plan decía 0.07 s, unos 4 cuadros). Se cuenta en pasos y no en
     * segundos para que salgan siempre 4, aunque el redondeo deje el primero apenas antes o
     * apenas después de BREAK: el límite queda a medio paso del quinto. La pantalla va toda de
     * FLASH y encima la silueta del Farol roto en #05060E (GreatLantern la tiene ya entintada).
     */
    private static final int IMPACT_FRAMES = 4;
    private static final double IMPACT = (IMPACT_FRAMES - 0.5) / Config.UPS;
    /**
     * La onda expansiva: un anillo que sale de la llama al romperse. Con s los segundos desde
     * la rotura y u = s / WAVE_LIFE (de 0 a 1),
     *     radio  = 40 + 820 · out(u)       (píxeles de pantalla: sale rápido y frena)
     *     grosor = 8 · (1 - u)
     * El centro es la llama llevada a la pantalla con la matriz de la cámara (con su temblor).
     * El radio es de pantalla, no del mundo: así, aunque la cámara se aleje, la onda llega
     * justo más allá de las esquinas (la media diagonal mide 734 px) cuando se acaba.
     */
    private static final double WAVE_LIFE = 1.1;
    private static final Color WAVE = new Color(255, 184, 72);

    private final GoldenStage stage;
    private boolean burst;   // ya salieron los pedazos
    private double breakTime;   // el tiempo del paso en que se rompió (BREAK o apenas después)

    public ShatterScene(GoldenStage stage, double length) {
        this.stage = stage;
        // Se queda cerca mientras se agrieta; al romperse se aleja para ver a dónde cae la luz
        camera.key(0, GoldenStage.CLOSE_X, GoldenStage.CLOSE_Y, GoldenStage.CLOSE_ZOOM)
              .key(BREAK, GoldenStage.CLOSE_X, GoldenStage.CLOSE_Y - 4, GoldenStage.CLOSE_ZOOM * 1.05)
              .key(BREAK + 2.3, GoldenStage.WIDE_X, GoldenStage.WIDE_Y + 4, 1.03)
              .key(length, GoldenStage.WIDE_X, GoldenStage.WIDE_Y, GoldenStage.WIDE_ZOOM);
    }

    private static final SubtitleTrack SUBTITLES = SubtitleTrack.shatter();

    @Override
    protected String voiceClip() { return "/umbra/res/voz_2.wav"; }

    @Override
    protected double voiceAt() { return 0.40; }

    @Override
    public SubtitleTrack getSubtitles() { return SUBTITLES; }

    @Override
    public void enter() {
        GreatLantern lantern = stage.lantern;
        lantern.setBroken(false);
        lantern.setCrack(0);
        lantern.setEmber(0);
        lantern.setHaloScale(1);
        stage.shards.clear();
        burst = false;
    }

    @Override
    protected void update(double dt) {
        stage.update(dt);
        GreatLantern lantern = stage.lantern;
        if (time < BREAK) {
            // Las grietas avanzan en tres tandas; la llama se agita
            double crack = 0;
            for (int i = 0; i < CRACK_AT.length; i++) {
                crack += CRACK_SHARE[i] * Ease.out(Ease.ramp(time, CRACK_AT[i], CRACK_AT[i] + 0.55));
            }
            lantern.setCrack(crack);
            // Sigue desde la agitación con que terminó la escena 1 (el presagio)
            lantern.setAgitation(Ease.lerp(LanternScene.OMEN, 1, Ease.smooth(time, 0.4, 2.4)));
            lantern.setRingPulse(1);
            lantern.setHaloScale(1 - IMPLODE_SHRINK * implosion(time));
        } else {
            if (!burst) {
                burst = true;
                breakTime = time;
                lantern.setBroken(true);
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

    /** Avance de la implosión, de 0 (en IMPLODE_AT) a 1 (en BREAK): in(ramp), cada vez más rápido. */
    private static double implosion(double t) {
        return Ease.in(Ease.ramp(t, IMPLODE_AT, BREAK));
    }

    /** Sin subtítulos durante el cuadro de impacto: su franja oscura se vería como una banda gris sobre el blanco. */
    @Override
    public double subtitleAlpha() {
        return burst && time - breakTime < IMPACT ? 0 : 1;
    }

    @Override
    public void draw(Graphics2D g) {
        if (!burst) {
            // Antes de romperse: la implosión oscurece la caverna mientras la luz se encoge
            stage.draw(g, camera, IMPLODE_DIM * implosion(time), 1);
            return;
        }
        double since = time - breakTime;
        if (since < IMPACT) {
            // El cuadro de impacto (tapa todo, también los pedazos que apenas salen)
            stage.drawImpact(g, camera, FLASH);
            drawShockwave(g, since);
            return;
        }
        // Al romperse, la caverna pierde su luz: primero de golpe (tapado por el destello) y
        // luego poco a poco, conforme los pedazos se van por los túneles
        double dim = Ease.lerp(0.36, GoldenStage.DUSK, Ease.smooth(time, BREAK + 0.6, BREAK + 4.6));
        stage.draw(g, camera, dim, 0.35);
        stage.shards.draw(g, camera.getViewMatrix(), camera.getZoom(), time);

        // El destello: el cuadro de impacto deja la pantalla en blanco y de ahí se apaga como
        // e^(-3·s), con s contado desde que termina el cuadro de impacto (empieza en 1: sin brinco)
        double flash = Math.exp(-3 * (since - IMPACT));
        if (flash > 0.01) {
            g.setColor(Ease.fade(FLASH, flash));
            g.fillRect(0, 0, Config.WIDTH, Config.HEIGHT);
        }
        drawShockwave(g, since);
    }

    /**
     * La onda expansiva (ver WAVE_LIFE): un solo trazo de círculo, más un halo ancho y tenue
     * del mismo trazo, que se desvanecen conforme crece. Va encima del destello para que se vea
     * salir de él.
     */
    private void drawShockwave(Graphics2D g, double since) {
        double u = since / WAVE_LIFE;
        if (u < 0 || u >= 1) return;
        Vec2 c = camera.getViewMatrix().apply(
                new Vec2(GoldenCavern.LANTERN_X, GoldenCavern.LANTERN_Y + GreatLantern.FLAME_Y));
        double radius = 40 + 820 * Ease.out(u), width = 8 * (1 - u);
        double alpha = 1 - Ease.smooth(u);   // se desvanece mientras se adelgaza
        Ellipse2D ring = new Ellipse2D.Double(c.x - radius, c.y - radius, 2 * radius, 2 * radius);
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke((float) (3 * width)));
        g.setColor(Ease.fade(WAVE, 0.22 * alpha));
        g.draw(ring);
        g.setStroke(new BasicStroke((float) width));
        g.setColor(Ease.fade(WAVE, 0.8 * alpha));
        g.draw(ring);
        g.setStroke(old);
    }
}
