package umbra.ui.intro;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

import umbra.entities.Player;
import umbra.math.Mat3;
import umbra.math.Vec2;

/**
 * [v0.8.6] Escena 4: una chispa cae en la oscuridad y de ella se forma Candil. Se acerca a un
 * rayo de luz, la luz le deshace la orilla en niebla, y regresa a la sombra.
 *
 * Candil es el Player del juego. La escena solo "presiona teclas" (ScriptedInput): A para ir
 * hacia la luz, D para regresar. Lo demás lo hace Player: dentro de la oscuridad flota (FLOATING)
 * y al salir de ella queda expuesto y se asusta (SCARED).
 */
public final class BirthScene extends Scene {

    private static final double BIRTH_X = 268;        // dónde cae la chispa y nace Candil
    private static final double LAND = 1.7;           // segundo en que la chispa toca el piso
    private static final double FORM_FROM = 2.1, FORM_TO = 3.3;   // entre estos segundos se forma Candil
    private static final double GO = 3.8;             // empieza a ir hacia la luz
    private static final double LIGHT_X = 177;        // hasta dónde se mete en el rayo
    private static final double RETREAT = 5.15;       // regresa a la sombra
    private static final double HOME_X = 262;
    private static final double LOOK_BACK = 6.5;      // voltea otra vez hacia la luz

    /** Las motas de niebla que se juntan para formar a Candil. */
    private static final int MOTES = 28;
    private static final Color MOTE = new Color(176, 192, 222);

    private final DarkStage stage;
    private final LightShards spark = new LightShards(7);
    private final Glow seed = new Glow(160, new Color(170, 212, 255), 0.45, 0.5);
    private boolean home, lookedBack;

    public BirthScene(DarkStage stage, double length) {
        this.stage = stage;
        camera.key(0, 262, 378, 2.5)
              .key(3.4, 252, 384, 2.68)
              .key(5.3, 232, 386, 2.74)
              .key(length, 258, 384, 2.6)
              .depthOrigin(258, 384, 2.6);
    }

    private static final SubtitleTrack SUBTITLES = SubtitleTrack.birth();

    @Override
    protected String voiceClip() { return "/umbra/res/voz_4.wav"; }

    @Override
    protected double voiceAt() { return 0.30; }

    @Override
    public SubtitleTrack getSubtitles() { return SUBTITLES; }

    @Override
    public void enter() {
        stage.reset(BIRTH_X);
        // Todo es sombra menos el rayo de luz (entre x = 128 y BEAM_EDGE a la altura de Candil)
        stage.setDarkness(new Rectangle2D.Double(DarkStage.BEAM_EDGE, 0, DarkStage.W, DarkStage.H),
                new Rectangle2D.Double(0, 0, 128, DarkStage.H));
        spark.clear();
        spark.launch(456, 150, BIRTH_X, DarkStage.GROUND - 12, 0.3, LAND - 0.3, 1.25);
        home = false;
        lookedBack = false;
    }

    @Override
    protected void update(double dt) {
        Player candil = stage.getCandil();
        ScriptedInput keys = stage.getInput();
        double x = candil.getCenterX();

        // El guion de teclas: A hasta meterse en el rayo; después D hasta volver; al final, un
        // toque de A para voltear a ver la luz desde la sombra
        keys.hold(KeyEvent.VK_A, (time >= GO && time < RETREAT && x > LIGHT_X) || (time >= LOOK_BACK && !lookedBack));
        if (time >= LOOK_BACK) lookedBack = true;
        if (time >= RETREAT && x >= HOME_X) home = true;
        keys.hold(KeyEvent.VK_D, time >= RETREAT && !home);

        stage.setTouchingLight(time >= GO && x < DarkStage.BEAM_EDGE);
        stage.update(dt);
    }

    @Override
    public void draw(Graphics2D g) {
        double formed = Ease.smooth(time, FORM_FROM, FORM_TO);
        stage.draw(g, camera, 1, formed, Ease.lerp(0.62, 1, Ease.out(Ease.ramp(time, FORM_FROM, FORM_TO))));

        Mat3 view = camera.getViewMatrix();
        double zoom = camera.getZoom();
        spark.draw(g, view, zoom, time);
        if (time >= LAND && time < FORM_TO) {
            // La chispa queda como una semilla de luz azulada: da un destello al caer
            // (1 + 0.9·e^(-5·s)) y se va apagando mientras Candil se forma a su alrededor
            Vec2 p = view.apply(new Vec2(BIRTH_X, DarkStage.GROUND - 14));
            double since = time - LAND;
            seed.draw(g, p.x, p.y, 30 * zoom * (1 + 0.9 * Math.exp(-5 * since)) * (1 - Ease.smooth(time, 2.3, FORM_TO)));
            drawMotes(g, view, zoom);
        }
    }

    /**
     * La niebla que se junta. Cada mota empieza en un anillo alrededor del punto donde cayó la
     * chispa y llega al centro por una espiral: su radio se encoge y su ángulo gira,
     *     p(u) = centro + r·(1 - u) · (cos(a + 2.4·u), 0.8·sin(a + 2.4·u)),     u de 0 a 1
     * con u suavizado y un retraso distinto para cada una. Se ve más a la mitad del camino:
     * su transparencia es sin(π·u).
     */
    private void drawMotes(Graphics2D g, Mat3 view, double zoom) {
        for (int i = 0; i < MOTES; i++) {
            double delay = 0.55 * ((i * 37) % MOTES) / MOTES;
            double u = Ease.smooth(time, LAND + 0.1 + delay, LAND + 1.0 + delay);
            if (u <= 0 || u >= 1) continue;
            double a = i * 2.399963 + 2.4 * u;   // ángulo áureo: reparte las motas parejo alrededor
            double r = (34 + 46 * ((i * 13) % 7) / 6.0) * (1 - u);
            Vec2 p = view.apply(new Vec2(BIRTH_X + r * Math.cos(a), DarkStage.GROUND - 16 + 0.8 * r * Math.sin(a)));
            double size = zoom * (2.6 - 1.2 * u);
            g.setColor(Ease.fade(MOTE, 0.2 * Math.sin(Math.PI * u)));
            g.fill(new Ellipse2D.Double(p.x - size * 2.2, p.y - size * 2.2, size * 4.4, size * 4.4));
            g.setColor(Ease.fade(MOTE, 0.7 * Math.sin(Math.PI * u)));
            g.fill(new Ellipse2D.Double(p.x - size, p.y - size, size * 2, size * 2));
        }
    }

    @Override
    public double fadeIn() { return 0.9; }

    @Override
    public double fadeOut() { return 0.6; }
}
