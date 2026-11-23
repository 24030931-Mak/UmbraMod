package umbra.ui.intro;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import umbra.core.Config;

/**
 * [v0.8.6] Escena 6: el título. Candil descansa en la sombra y Logi flota a su lado.
 *
 * [v0.8.7] La cámara empieza cerca de los dos y se aleja despacio hacia arriba (un plano general
 * ascendente) mientras aparece UMBRA: al final se ven a la vez Candil abajo, Logi alumbrando, la
 * silueta del Gran Farol apagado en lo alto de la chimenea (DarkCavern) y el título debajo de
 * esa silueta, sin taparla. Justo antes del fundido a negro, los ojos de Logi se oscurecen un
 * instante (un parpadeo de BLINK segundos): la pista de lo que es Logi se queda, pero apenas se
 * alcanza a ver.
 *
 * [v0.9.5] La silueta es la del candelabro de la sala del trono, apagado: las copas vacías y el
 * corazón oscuro (ver DarkCavern).
 */
public final class TitleScene extends Scene {

    private static final double CANDIL_X = 715, LOGI_X = 930, LOGI_Y = 408;   // donde quedaron en la escena 5
    /**
     * El parpadeo oscuro: cuánto dura, cuánto le toma oscurecerse (y lo mismo en volver) y
     * cuánto antes del fundido a negro termina, para que se vea con la pantalla todavía clara.
     */
    private static final double BLINK = 0.25, BLINK_EDGE = 0.06, BLINK_BEFORE_FADE = 0.05;
    /**
     * La pose final de la cámara. Con zoom 1.25 se ven 1024 x 576 unidades del mundo, así que
     * el centro no puede pasar de x = 638 (más a la derecha se vería la orilla del plano de las
     * repisas, que llega a x = 1150) ni subir de y = 228 (arriba se vería la del plano lejano,
     * que empieza en y = -60). Con y = 230 la chimenea, con el Farol, queda en el cuarto de
     * arriba de la pantalla, sobre el título, y las repisas abajo.
     */
    private static final double END_X = 630, END_Y = 230, END_ZOOM = 1.25;

    private final DarkStage stage;
    private final double blinkFrom;   // cuándo empieza el parpadeo (s)

    public TitleScene(DarkStage stage, double length) {
        this.stage = stage;
        camera.key(0, 815, 374, 2.1)
              .key(length, END_X, END_Y, END_ZOOM)
              .depthOrigin(END_X, END_Y, END_ZOOM);   // ahí los planos coinciden: el Farol queda donde se diseñó
        blinkFrom = length - fadeOut() - BLINK_BEFORE_FADE - BLINK;
    }

    private static final SubtitleTrack SUBTITLES = SubtitleTrack.title();

    @Override
    protected String voiceClip() { return "/umbra/res/voz_6.wav"; }

    @Override
    protected double voiceAt() { return 1.00; }

    @Override
    public SubtitleTrack getSubtitles() { return SUBTITLES; }

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
        stage.getLogi().setEyeDarkness(Ease.window(time, blinkFrom, blinkFrom + BLINK_EDGE,
                blinkFrom + BLINK - BLINK_EDGE, blinkFrom + BLINK));
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
