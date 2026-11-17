package umbra.ui.intro;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

import umbra.core.Config;
import umbra.math.Mat3;

/**
 * [v0.8.6] Lo que comparten las tres primeras escenas de la intro: la caverna dorada, el Gran
 * Farol, los pedazos de luz y la neblina. Las escenas 1, 2 y 3 son una sola toma seguida (sin
 * fundido entre ellas), así que usan los mismos objetos y cada una continúa donde quedó la
 * anterior.
 */
public final class GoldenStage {

    /**
     * La cámara "de lejos" (se ve toda la caverna) y la "de cerca" (la llama llena la pantalla).
     * [v0.8.7] La de cerca mira 28 unidades arriba de la llama: con el vidrio alto cabe entero,
     * del plato al collar.
     */
    public static final double WIDE_X = 640, WIDE_Y = 360, WIDE_ZOOM = 1;
    public static final double CLOSE_X = GoldenCavern.LANTERN_X,
            CLOSE_Y = GoldenCavern.LANTERN_Y + GreatLantern.FLAME_Y - 28, CLOSE_ZOOM = 2.45;
    /** Qué tan oscura queda la caverna al terminar la escena 2 (de 0 a 1); con eso empieza la 3. */
    public static final double DUSK = 0.74;

    /** El color de la noche que cae sobre la caverna: el azul casi negro del juego. */
    private static final Color NIGHT = new Color(6, 8, 20);

    public final GoldenCavern cavern = new GoldenCavern(31);
    public final GreatLantern lantern = new GreatLantern(17);
    public final LightShards shards = new LightShards(5);
    public final Mist mist = new Mist(43);
    private double clock;   // reloj que no se reinicia entre escenas: el polvo flota de corrido

    public void update(double dt) {
        clock += dt;
        lantern.update(dt);
    }

    /**
     * La caverna con el Farol. 'dim' (0 a 1) la oscurece con un velo del color de la noche:
     *     pantalla = pantalla · (1 - dim) + noche · dim
     * 'motes' (0 a 1) es qué tanto polvo dorado se ve.
     *
     * El Farol se dibuja con la matriz  vista · T(pie del Farol): sus coordenadas propias (pie
     * en el origen) pasan al mundo y de ahí a la pantalla. Detrás de él van los planos lejano y
     * medio de la caverna (cada uno con su vista) y, debajo, el piso donde se para, con la misma
     * vista que el Farol para que no se "despegue" de su terraza al moverse la cámara.
     */
    public void draw(Graphics2D g, IntroCamera camera, double dim, double motes) {
        cavern.drawBack(g, camera);
        cavern.drawGround(g, camera);

        AffineTransform old = g.getTransform();
        g.transform(camera.getViewMatrix()
                .multiply(Mat3.translation(GoldenCavern.LANTERN_X, GoldenCavern.LANTERN_Y)).toAffineTransform());
        lantern.drawHalo(g);
        lantern.draw(g);
        g.setTransform(old);

        cavern.drawMotes(g, camera, clock, motes);
        cavern.drawFront(g, camera);
        if (dim > 0.004) {
            g.setColor(Ease.fade(NIGHT, dim));
            g.fillRect(0, 0, Config.WIDTH, Config.HEIGHT);
        }
    }

    /**
     * [v0.8.7] El cuadro de impacto: toda la pantalla de un color ('paper') y encima la silueta
     * del Farol roto, sólida y casi negra, con la misma matriz vista · T(pie) que en draw. No
     * dibuja la caverna: estos pocos cuadros son los más baratos de la escena.
     */
    public void drawImpact(Graphics2D g, IntroCamera camera, Color paper) {
        g.setColor(paper);
        g.fillRect(0, 0, Config.WIDTH, Config.HEIGHT);
        AffineTransform old = g.getTransform();
        g.transform(camera.getViewMatrix()
                .multiply(Mat3.translation(GoldenCavern.LANTERN_X, GoldenCavern.LANTERN_Y)).toAffineTransform());
        lantern.drawSilhouette(g);
        g.setTransform(old);
    }
}
