package umbra.ui.intro;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import umbra.entities.Block;
import umbra.entities.LightSource;
import umbra.entities.Player;
import umbra.graphics.ParticleSystem;
import umbra.graphics.ShadowCaster;
import umbra.math.Mat3;
import umbra.math.Vec2;
import umbra.world.Level;

/**
 * [v0.8.6] El escenario de las tres últimas escenas de la intro: un rincón de la cueva ya a
 * oscuras, con dos repisas separadas por un hueco y dos rocas, y los dos personajes.
 *
 * Aquí no hay nada dibujado "de mentira": es un nivel chiquito del juego.
 *   - Las repisas y las rocas son un Level con sus plataformas y sus Block.
 *   - Candil es un Player: se mueve con la física del juego, leyendo un teclado de guion
 *     (ScriptedInput), y se dibuja con Player.draw.
 *   - Logi es un LightSource: sigue un destino como sigue al ratón, y se dibuja con drawCore.
 *   - Las sombras las calcula ShadowCaster con la posición de Logi y los Block, y Candil flota
 *     dentro de ellas igual que en el juego.
 *   - Las partículas (vapor, polvo, chispas, ceniza) son las de ParticleSystem.
 * Mientras Logi no ha llegado no hay luz, así que todo es sombra: la escena pasa su propia
 * lista de "sombras" (toda la cueva menos el rayo de luz).
 *
 * El fondo (la cueva, las repisas y las rocas ya dibujadas, las esporas y la niebla) es un
 * DarkCavern; aquí van el nivel, los personajes y lo que se mueve.
 */
public final class DarkStage {

    /** Tamaño del escenario, en píxeles del juego (Candil mide 22 x 30). */
    public static final double W = 1120, H = 600;
    /** La cara de arriba de las dos repisas. */
    public static final double GROUND = 440;
    /** Orilla de la repisa izquierda y de la derecha: entre las dos está el hueco. */
    public static final double GAP_LEFT = 420, GAP_RIGHT = 680;
    /** El brillo lejano, arriba: lo que queda encendido del Gran Farol. */
    public static final double FAR_GLOW_X = 590, FAR_GLOW_Y = 118;
    /** Hasta dónde llega el rayo de luz de la escena 4 a la altura de Candil: a su izquierda hay luz. */
    public static final double BEAM_EDGE = 200;

    /** Las dos rocas: sus rectángulos de colisión (DarkCavern las dibuja encima). */
    static final Rectangle2D ROCK_1 = new Rectangle2D.Double(310, 396, 50, 44);
    static final Rectangle2D ROCK_2 = new Rectangle2D.Double(810, 398, 46, 42);

    // ---- Colores de lo que se mueve: el rayo de luz y las sombras ----
    private static final Color BEAM = new Color(255, 238, 190);
    /** Las sombras, como en el juego: casi negras, con una orilla tenue para ver dónde terminan. */
    private static final Color SHADOW = new Color(6, 6, 10, 235);
    private static final Color SHADOW_EDGE = new Color(140, 160, 200, 90);
    private static final Stroke SHADOW_STROKE = new BasicStroke(1.2f);

    /** Partículas por segundo, como en GamePanel: estela de vapor, ceniza de Logi y chispas. */
    private static final double TRAIL_RATE = 40, ASH_RATE = 30, SPARK_RATE = 12, DISSOLVE_RATE = 70;

    private final DarkCavern cavern;
    private final Glow logiGlow = new Glow(512, new Color(255, 200, 124), 0.22, 0.5);

    // ---- El nivel y los personajes ----
    private final Level level = new Level("intro", W, new Vec2(0, 0), null);
    private final ShadowCaster caster = new ShadowCaster();
    private final ParticleSystem particles = new ParticleSystem();
    private final ScriptedInput input = new ScriptedInput();
    private final Random rnd = new Random(3);
    private Player candil;
    private LightSource logi;
    private List<Path2D> shadows = new ArrayList<>();
    private List<Path2D> darkness = new ArrayList<>();   // las "sombras" cuando Logi no está
    private boolean logiPresent;
    private double logiLight;       // qué tanto alumbra Logi (0 a 1): crece cuando llega
    private boolean afraid;         // Candil toca la luz: se asusta
    private boolean dissolving;     // y su orilla se deshace en niebla
    private double trailTimer, ashTimer, sparkTimer, dissolveTimer;
    private double clock;

    public DarkStage() {
        level.addPlatform(0, GROUND, GAP_LEFT, H - GROUND);
        level.addPlatform(GAP_RIGHT, GROUND, W - GAP_RIGHT, H - GROUND);
        level.addBlock(new Block(ROCK_1.getX(), ROCK_1.getY(), ROCK_1.getWidth(), ROCK_1.getHeight()));
        level.addBlock(new Block(ROCK_2.getX(), ROCK_2.getY(), ROCK_2.getWidth(), ROCK_2.getHeight()));

        cavern = new DarkCavern(new Random(19));
        reset(290);
    }

    // =========================================================
    //  LOS PERSONAJES
    // =========================================================

    /**
     * Personajes nuevos para empezar una escena: Candil parado en la repisa con su centro en
     * 'candilX', Logi fuera de escena, sin partículas y sin teclas presionadas.
     */
    public void reset(double candilX) {
        candil = new Player(new Vec2(candilX - Player.W / 2, GROUND - Player.H));
        logi = new LightSource(-200, -200);
        logiPresent = false;
        logiLight = 0;
        afraid = false;
        dissolving = false;
        darkness = new ArrayList<>();
        shadows = darkness;
        particles.clear();
        input.releaseAll();
        trailTimer = ashTimer = sparkTimer = dissolveTimer = 0;
    }

    public Player getCandil()      { return candil; }
    public LightSource getLogi()   { return logi; }
    public ScriptedInput getInput() { return input; }

    /** Lo que cuenta como sombra mientras Logi no está (rectángulos del mundo). */
    public void setDarkness(Rectangle2D... areas) {
        darkness = new ArrayList<>();
        for (Rectangle2D r : areas) darkness.add(new Path2D.Double(r));
    }

    /** Logi entra a escena (o sale). Con ella presente, las sombras son las que proyecta su luz. */
    public void setLogiPresent(boolean present) { logiPresent = present; }

    /** Qué tanto alumbra Logi, de 0 a 1. */
    public void setLogiLight(double light) { logiLight = Ease.clamp01(light); }

    /** Candil toca la luz: se asusta (su forma SCARED) y su orilla se deshace en niebla. */
    public void setTouchingLight(boolean touching) {
        afraid = touching;
        dissolving = touching;
    }

    /**
     * Un paso del "juego" de la intro, en el mismo orden que GamePanel.updateGame: Logi, las
     * sombras, Candil y las partículas.
     */
    public void update(double dt) {
        clock += dt;
        double cx = candil.getCenterX(), cy = candil.getCenterY();
        if (logiPresent) {
            logi.setCandil(cx, cy);   // lo mira, y se agitaría si se le acercara
            logi.update(dt);
            shadows = caster.computeShadows(logi, level.getBlocks());
            candil.setLogiDistance(Math.hypot(logi.getX() - cx, logi.getY() - cy));
            emitLogiEffects(dt);
        } else {
            shadows = darkness;
            // Sin Logi, lo que lo asusta es el rayo de luz: se le avisa como si la luz estuviera cerca
            candil.setLogiDistance(afraid ? Player.FEAR_DISTANCE * 0.6 : Double.POSITIVE_INFINITY);
        }
        candil.update(dt, input, level, shadows);
        input.endUpdate();
        emitCandilEffects(dt);
        particles.update(dt);
    }

    /**
     * Estela de vapor cuando Candil se mueve (como en GamePanel.emitPlayerEffects) y, si toca
     * la luz, la niebla y el polvo de luz en que se deshace su orilla. Los acumuladores hacen
     * que salgan a un ritmo fijo por segundo, sin importar los cuadros.
     */
    private void emitCandilEffects(double dt) {
        if (candil.isDead()) return;
        double cx = candil.getCenterX(), cy = candil.getCenterY();
        boolean moving = Math.abs(candil.getVx()) > 1 || Math.abs(candil.getVy()) > 60;
        trailTimer = moving ? trailTimer + dt * TRAIL_RATE : 0;
        while (trailTimer >= 1) {
            trailTimer -= 1;
            particles.emitVapor(cx, cy + 6, candil.isInShadow());
        }
        dissolveTimer = dissolving ? dissolveTimer + dt * DISSOLVE_RATE : 0;
        while (dissolveTimer >= 1) {
            dissolveTimer -= 1;
            // Sale de la orilla: un punto al azar sobre una elipse alrededor del cuerpo
            double a = rnd.nextDouble() * Math.PI * 2;
            double x = cx + 11 * Math.cos(a), y = cy + 15 * Math.sin(a);
            if (rnd.nextInt(3) == 0) particles.emitDust(x, y);
            else particles.emitVapor(x, y, false);
        }
    }

    /** Chispas si Logi está fija y ceniza si va rápido, como en GamePanel.emitLogiEffects. */
    private void emitLogiEffects(double dt) {
        LightSource.Form form = logi.getForm();
        boolean fast = form == LightSource.Form.CHASE || form == LightSource.Form.RISING;
        sparkTimer = logi.isPinned() ? sparkTimer + dt * SPARK_RATE : 0;
        while (sparkTimer >= 1) {
            sparkTimer -= 1;
            particles.emitSpark(logi.getX(), logi.getY());
        }
        ashTimer = fast ? ashTimer + dt * ASH_RATE : 0;
        while (ashTimer >= 1) {
            ashTimer -= 1;
            particles.emitAsh(logi.getX(), logi.getY(), logi.getVx(), logi.getVy());
        }
    }

    // =========================================================
    //  DIBUJO
    // =========================================================

    /**
     * Todo el escenario con la cámara de la escena. El orden es el del juego (GamePanel.drawWorld):
     * fondo (sus dos planos, cada uno con su vista), halo de luz, sombras, partículas, Candil y
     * Logi; al final, las esporas y la niebla.
     *
     * @param beam        qué tanto se ve el rayo de luz (0 a 1)
     * @param candilAlpha qué tan formado está Candil (0 = todavía no se ve, 1 = completo)
     * @param candilGrow  su tamaño mientras se forma (1 = el normal): se escala desde sus pies
     */
    public void draw(Graphics2D g, IntroCamera camera, double beam, double candilAlpha, double candilGrow) {
        cavern.drawBack(g, camera, clock);

        AffineTransform onScreen = g.getTransform();
        g.transform(camera.getViewMatrix().toAffineTransform());
        if (logiPresent && logiLight > 0.01) logiGlow.draw(g, logi.getX(), logi.getY(), 240 * logiLight);
        if (beam > 0.01) drawBeam(g, beam);
        if (logiPresent) drawShadows(g);
        particles.draw(g);
        drawCandil(g, candilAlpha, candilGrow);
        if (logiPresent) logi.drawCore(g);
        g.setTransform(onScreen);
        cavern.drawFront(g, camera, clock);
    }

    /**
     * Las sombras que proyecta Logi: relleno casi negro y una orilla tenue, como en el juego. En
     * el juego las repisas y las losas se dibujan después y quedan encima de las sombras; aquí
     * ya están en la imagen del fondo, así que de cada sombra se pinta solo su intersección con
     * el aire (el escenario menos las repisas y las rocas, DarkCavern.getAir): sombra ∩ aire,
     * con java.awt.geom.Area.
     */
    private void drawShadows(Graphics2D g) {
        Stroke old = g.getStroke();
        g.setStroke(SHADOW_STROKE);
        for (Path2D shadow : shadows) {
            Area visible = new Area(shadow);
            visible.intersect(cavern.getAir());
            g.setColor(SHADOW);
            g.fill(visible);
            g.setColor(SHADOW_EDGE);
            g.draw(visible);
        }
        g.setStroke(old);
    }

    /**
     * Candil, con Player.draw. Mientras se forma se dibuja translúcido (AlphaComposite multiplica
     * la transparencia de todo lo que dibuja) y más chico, escalado desde sus pies:
     *     M = T(pies) · S(s) · T(-pies)
     */
    private void drawCandil(Graphics2D g, double alpha, double grow) {
        if (alpha <= 0.01) return;
        AffineTransform old = g.getTransform();
        Composite oldComposite = g.getComposite();
        if (grow != 1) {
            double fx = candil.getCenterX(), fy = candil.getBottom();
            g.transform(Mat3.translation(fx, fy).multiply(Mat3.scale(grow, grow))
                    .multiply(Mat3.translation(-fx, -fy)).toAffineTransform());
        }
        if (alpha < 0.995) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        candil.draw(g);
        g.setComposite(oldComposite);
        g.setTransform(old);
    }

    /**
     * El rayo de luz que entra por una grieta del techo: una franja inclinada con un degradado
     * (más intensa abajo, donde pega en la repisa), una franja más angosta adentro y la mancha
     * de luz en el piso. Titila un poco.
     */
    private void drawBeam(Graphics2D g, double alpha) {
        double flicker = 0.9 + 0.1 * Math.sin(clock * 2.3) * Math.sin(clock * 0.9);
        Path2D wide = quad(232, 140, 266, 140, BEAM_EDGE, GROUND, 132, GROUND);
        Path2D core = quad(242, 140, 256, 140, 184, GROUND, 150, GROUND);
        g.setPaint(new GradientPaint(0, 140, Ease.fade(BEAM, 0.1 * alpha * flicker), 0, (float) GROUND,
                Ease.fade(BEAM, 0.3 * alpha * flicker)));
        g.fill(wide);
        g.setPaint(new GradientPaint(0, 140, Ease.fade(BEAM, 0.08 * alpha * flicker), 0, (float) GROUND,
                Ease.fade(BEAM, 0.3 * alpha * flicker)));
        g.fill(core);
        g.setColor(Ease.fade(BEAM, 0.5 * alpha * flicker));
        g.fill(new Ellipse2D.Double(134, GROUND - 2.2, 64, 4.4));
        // Polvo que brilla al cruzar el rayo: baja despacio y regresa arriba (módulo)
        for (int i = 0; i < 9; i++) {
            double u = (i / 9.0 + clock * (0.03 + 0.004 * i)) % 1;
            double y = Ease.lerp(150, GROUND - 4, u);
            double x = Ease.lerp(249, 166, u) + 13 * Math.sin(i * 2.4 + clock * 0.5);
            double r = 0.55 + 0.25 * (i % 3);
            g.setColor(Ease.fade(BEAM, alpha * (0.35 + 0.45 * Math.sin(clock * 2 + i) * Math.sin(clock * 2 + i))));
            g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        }
    }

    private static Path2D quad(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4) {
        Path2D p = new Path2D.Double();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        p.lineTo(x3, y3);
        p.lineTo(x4, y4);
        p.closePath();
        return p;
    }
}
