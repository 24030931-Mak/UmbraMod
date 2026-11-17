package umbra.ui.intro;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;

import umbra.entities.LightSource;
import umbra.entities.Player;

/**
 * [v0.8.6] Escena 5: llega Logi flotando. Su luz proyecta la sombra de una roca sobre el hueco y
 * esa sombra es un camino: Candil lo cruza flotando. Después Logi se adelanta, la sombra de la
 * siguiente roca lo vuelve a cubrir, y la cámara sube: arriba, lejos, algo todavía brilla.
 *
 * Es la mecánica del juego tal cual: la sombra la calcula ShadowCaster con la posición de Logi,
 * y Candil flota dentro de ella porque así lo decide Player. La escena solo mueve el destino de
 * Logi (como lo haría el ratón) y presiona D por Candil.
 */
public final class TogetherScene extends Scene {

    private static final double CANDIL_X = 395;                 // Candil espera detrás de la primera roca
    private static final double ARRIVE = 0.45, SETTLED = 2.3;   // Logi entra y llega a su lugar
    private static final double CAST_X = 215, CAST_Y = 418;     // desde aquí su luz tiende la sombra sobre el hueco
    private static final double CROSS_1 = 2.9, PAUSE_X = 505;   // primer tramo del cruce: hasta la mitad del hueco
    private static final double CROSS_2 = 4.0, FAR_X = 715;     // segundo tramo: hasta la otra repisa
    private static final double LEAP = 5.3, LEAP_TIME = 1.5;    // Logi se adelanta
    private static final double AHEAD_X = 930, AHEAD_Y = 408;   // y se queda del otro lado de la segunda roca

    private final DarkStage stage;

    public TogetherScene(DarkStage stage, double length) {
        this.stage = stage;
        camera.key(0, 400, 398, 2.25)
              .key(2.4, 420, 398, 2.2)
              .key(4.9, 480, 392, 1.8)       // se abre para ver a Logi y a Candil en las dos orillas
              .key(6.7, 640, 356, 1.6)
              .key(length, 640, 318, 1.5)     // y sube hacia el brillo lejano
              .depthOrigin(640, 318, 1.5);    // ahí el fondo queda como sin parallax: el brillo, a la vista
    }

    private static final SubtitleTrack SUBTITLES = SubtitleTrack.together();

    @Override
    protected String voiceClip() { return "/umbra/res/voz_5.wav"; }

    @Override
    protected double voiceAt() { return 0.50; }

    @Override
    public SubtitleTrack getSubtitles() { return SUBTITLES; }

    @Override
    public void enter() {
        stage.reset(CANDIL_X);
        stage.setDarkness(new Rectangle2D.Double(0, 0, DarkStage.W, DarkStage.H));   // sin Logi, todo es sombra
        stage.getLogi().snapTo(100, 236);
    }

    @Override
    protected void update(double dt) {
        Player candil = stage.getCandil();
        LightSource logi = stage.getLogi();
        ScriptedInput keys = stage.getInput();
        double x = candil.getCenterX();

        // ---- Logi: su destino se mueve como lo movería el ratón ----
        stage.setLogiPresent(time >= ARRIVE);
        stage.setLogiLight(Ease.smooth(time, ARRIVE, ARRIVE + 0.9));
        if (time < LEAP) {
            // Baja hasta su lugar con un vaivén de curiosa (una onda seno que se apaga al llegar)
            double u = Ease.smooth(time, ARRIVE, SETTLED);
            logi.setTarget(Ease.lerp(100, CAST_X, u) + 16 * Math.sin(u * Math.PI * 3) * (1 - u),
                    Ease.lerp(236, CAST_Y, u));
            logi.setPinned(time >= SETTLED + 0.35);   // se fija, como con un clic: la sombra ya no se mueve
        } else {
            // Se adelanta por arriba de Candil, sobre una curva de Bézier cuadrática:
            //     B(u) = (1-u)²·P0 + 2(1-u)u·P1 + u²·P2
            // El punto de control P1 está muy arriba para que pase lejos de él (cerca lo desharía).
            logi.setPinned(false);
            double u = Ease.smooth(time, LEAP, LEAP + LEAP_TIME), s = 1 - u;
            logi.setTarget(s * s * CAST_X + 2 * s * u * 560 + u * u * AHEAD_X,
                    s * s * CAST_Y + 2 * s * u * 20 + u * u * AHEAD_Y);
        }

        // ---- Candil: voltea a ver la luz que llega, y cruza la sombra en dos tramos ----
        keys.hold(KeyEvent.VK_A, time >= 1.2 && time < 1.2 + dt * 1.5);
        boolean firstLeg = time >= CROSS_1 && time < CROSS_2 && x < PAUSE_X;
        boolean secondLeg = time >= CROSS_2 && x < FAR_X;
        keys.hold(KeyEvent.VK_D, firstLeg || secondLeg);

        stage.update(dt);
    }

    @Override
    public void draw(Graphics2D g) {
        stage.draw(g, camera, 0.55, 1, 1);
    }

    @Override
    public double fadeIn() { return 0.6; }

    @Override
    public double fadeOut() { return 0.55; }
}
