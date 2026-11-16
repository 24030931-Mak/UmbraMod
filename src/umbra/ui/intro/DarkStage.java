package umbra.ui.intro;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import umbra.core.Config;
import umbra.entities.Block;
import umbra.entities.LightSource;
import umbra.entities.Player;
import umbra.graphics.Background;
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
 * El fondo (capas de roca con desplazamiento del punto medio, como graphics.Background, y las
 * repisas con la paleta de graphics.LevelArt) se dibuja una sola vez en una imagen a 2.5 veces
 * la resolución, porque la cámara de estas escenas se acerca entre 1.6 y 3.4 veces.
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

    private static final double SCALE = 2.5;
    private static final Rectangle2D ROCK_1 = new Rectangle2D.Double(310, 396, 50, 44);
    private static final Rectangle2D ROCK_2 = new Rectangle2D.Double(810, 398, 46, 42);

    // ---- Paleta: la del fondo y las repisas del juego ----
    private static final Color SKY_TOP = new Color(5, 8, 16), SKY_BOTTOM = new Color(17, 24, 41);
    private static final Color FAR = new Color(32, 44, 70), MID = new Color(22, 30, 51), NEAR = new Color(12, 17, 29);
    private static final Color FOG = new Color(110, 135, 180);
    private static final Color ROCK_TOP = new Color(46, 54, 76), ROCK_BOTTOM = new Color(22, 26, 40);
    private static final Color RIM = new Color(125, 145, 190, 170);
    private static final Color STRATA = new Color(70, 82, 110, 45);
    private static final Color MOSS = new Color(78, 140, 135);
    private static final Color STONE_TOP = new Color(92, 98, 124), STONE_BOTTOM = new Color(56, 60, 82);
    private static final Color STONE_EDGE = new Color(160, 165, 200, 170);
    private static final Color CAP = new Color(80, 215, 205);
    private static final Color SPORE = new Color(150, 215, 255);
    private static final Color BEAM = new Color(255, 238, 190);
    /** Las sombras, como en el juego: casi negras, con una orilla tenue para ver dónde terminan. */
    private static final Color SHADOW = new Color(6, 6, 10, 235);
    private static final Color SHADOW_EDGE = new Color(140, 160, 200, 90);
    private static final Stroke SHADOW_STROKE = new BasicStroke(1.2f);

    /** Partículas por segundo, como en GamePanel: estela de vapor, ceniza de Logi y chispas. */
    private static final double TRAIL_RATE = 40, ASH_RATE = 30, SPARK_RATE = 12, DISSOLVE_RATE = 70;

    private final BufferedImage back;
    private final BufferedImage fog;
    private final Glow logiGlow = new Glow(512, new Color(255, 200, 124), 0.22, 0.5);
    private final Glow farGlow = new Glow(96, new Color(255, 206, 130), 1.0, 0.5);
    private final double[] sporeX, sporeY, sporeSpeed, sporePhase;
    /** Todo el escenario menos las repisas y las rocas: las sombras solo se pintan ahí. */
    private final Area air = new Area(new Rectangle2D.Double(-100, -100, W + 200, H + 200));

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

        Random seed = new Random(19);
        back = renderBack(seed);
        air.subtract(new Area(new Rectangle2D.Double(-100, GROUND, GAP_LEFT + 100, H)));
        air.subtract(new Area(new Rectangle2D.Double(GAP_RIGHT, GROUND, W, H)));
        air.subtract(new Area(ROCK_1));
        air.subtract(new Area(ROCK_2));
        fog = renderFog(seed);
        int n = 70;
        sporeX = new double[n];
        sporeY = new double[n];
        sporeSpeed = new double[n];
        sporePhase = new double[n];
        for (int i = 0; i < n; i++) {
            sporeX[i] = seed.nextDouble() * W;
            sporeY[i] = seed.nextDouble() * H;
            sporeSpeed[i] = 4 + seed.nextDouble() * 9;
            sporePhase[i] = seed.nextDouble() * Math.PI * 2;
        }
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
     * fondo, halo de luz, sombras, partículas, Candil y Logi; al final, la niebla y las esporas.
     *
     * @param beam        qué tanto se ve el rayo de luz (0 a 1)
     * @param candilAlpha qué tan formado está Candil (0 = todavía no se ve, 1 = completo)
     * @param candilGrow  su tamaño mientras se forma (1 = el normal): se escala desde sus pies
     */
    public void draw(Graphics2D g, IntroCamera camera, double beam, double candilAlpha, double candilGrow) {
        Mat3 view = camera.getViewMatrix();
        Scene.drawImage(g, back, view, 0, 0, SCALE);

        AffineTransform onScreen = g.getTransform();
        g.transform(view.toAffineTransform());
        double pulse = 1 + 0.08 * Math.sin(clock * 1.3) + 0.04 * Math.sin(clock * 3.1);
        farGlow.draw(g, FAR_GLOW_X, FAR_GLOW_Y, 46 * pulse);
        if (logiPresent && logiLight > 0.01) logiGlow.draw(g, logi.getX(), logi.getY(), 280 * logiLight);
        if (beam > 0.01) drawBeam(g, beam);
        if (logiPresent) drawShadows(g);
        particles.draw(g);
        drawCandil(g, candilAlpha, candilGrow);
        if (logiPresent) logi.drawCore(g);
        g.setTransform(onScreen);

        drawSpores(g, view);
        drawFog(g, camera);
    }

    /**
     * Las sombras que proyecta Logi: relleno casi negro y una orilla tenue, como en el juego. En
     * el juego las repisas y las losas se dibujan después y quedan encima de las sombras; aquí
     * ya están en la imagen del fondo, así que de cada sombra se pinta solo su intersección con
     * el aire (el escenario menos las repisas y las rocas): sombra ∩ aire, con java.awt.geom.Area.
     */
    private void drawShadows(Graphics2D g) {
        Stroke old = g.getStroke();
        g.setStroke(SHADOW_STROKE);
        for (Path2D shadow : shadows) {
            Area visible = new Area(shadow);
            visible.intersect(air);
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

    /** Esporas: puntitos de luz que suben despacio, como las del fondo del juego. En píxeles de pantalla. */
    private void drawSpores(Graphics2D g, Mat3 view) {
        for (int i = 0; i < sporeX.length; i++) {
            double y = H - ((H - sporeY[i] + clock * sporeSpeed[i]) % H);
            double x = sporeX[i] + 6 * Math.sin(clock * 0.8 + sporePhase[i]);
            Vec2 p = view.apply(new Vec2(x, y));
            if (p.x < -4 || p.x > Config.WIDTH + 4 || p.y < -4 || p.y > Config.HEIGHT + 4) continue;
            double glow = 0.5 + 0.5 * Math.sin(clock * 1.7 + sporePhase[i] * 2);
            double r = 1.2 + 0.9 * (i % 3);
            g.setColor(Ease.fade(SPORE, 0.12 + 0.3 * glow));
            g.fill(new Ellipse2D.Double(p.x - r * 2, p.y - r * 2, r * 4, r * 4));
            g.setColor(Ease.fade(SPORE, 0.3 + 0.6 * glow));
            g.fill(new Ellipse2D.Double(p.x - r * 0.6, p.y - r * 0.6, r * 1.2, r * 1.2));
        }
    }

    /**
     * Una franja de niebla delante de todo, en píxeles de pantalla. Se mueve sola y además
     * con la cámara, a la mitad de su paso (parallax); el módulo la repite sin cortes.
     */
    private void drawFog(Graphics2D g, IntroCamera camera) {
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int w = fog.getWidth();
        double[] ys = { 470 }, speeds = { 14 };
        for (int k = 0; k < ys.length; k++) {
            double offset = (clock * speeds[k] - camera.getX() * camera.getZoom() * 0.5) % w;
            if (offset > 0) offset -= w;
            int y = (int) Math.round(ys[k] - (camera.getY() - 380) * camera.getZoom() * 0.5);
            for (double x = offset; x < Config.WIDTH; x += w) g.drawImage(fog, (int) Math.round(x), y, null);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    // =========================================================
    //  GENERACIÓN (una sola vez)
    // =========================================================

    private static BufferedImage renderBack(Random rnd) {
        BufferedImage img = new BufferedImage((int) (W * SCALE), (int) (H * SCALE), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(SCALE, SCALE);

        g.setPaint(new GradientPaint(0, 0, SKY_TOP, 0, (float) H, SKY_BOTTOM));
        g.fill(new Rectangle2D.Double(0, 0, W, H));
        // El resplandor lejano, por una chimenea del techo: lo que quedó encendido allá arriba
        g.setPaint(new RadialGradientPaint(new Point2D.Double(FAR_GLOW_X, FAR_GLOW_Y - 20), 240, new float[] { 0f, 0.4f, 1f },
                new Color[] { new Color(255, 200, 120, 105), new Color(190, 130, 100, 40), new Color(120, 110, 140, 0) }));
        g.fill(new Rectangle2D.Double(0, 0, W, 380));

        // Capas de roca: lejos (más clara), en medio (con ruinas) y cerca. Los techos tienen
        // una muesca (una campana de Gauss que se resta) donde se abre la chimenea.
        g.setColor(FAR);
        double[] ceiling = Background.midpointDisplacement(rnd, 64, 30, 0.55);
        double[] floor = Background.midpointDisplacement(rnd, 64, 34, 0.55);
        g.fill(ridge(ceiling, 118, true, 230, 84));
        g.fill(ridge(floor, 492, false, 0, 1));
        for (int i = 0; i < 26; i++) {
            double x = rnd.nextDouble() * W;
            if (Math.abs(x - FAR_GLOW_X) < 100) continue;
            g.fill(spike(x, 100, 7 + rnd.nextDouble() * 14, 30 + rnd.nextDouble() * 100, true));
        }
        g.setColor(MID);
        ceiling = Background.midpointDisplacement(rnd, 64, 24, 0.6);
        floor = Background.midpointDisplacement(rnd, 64, 26, 0.6);
        g.fill(ridge(ceiling, 74, true, 190, 62));
        g.fill(ridge(floor, 536, false, 0, 1));
        g.setStroke(new BasicStroke(9, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        double[][] ruins = { { 62, 86, 150, 1 }, { 500, 70, 118, 0 }, { 1004, 92, 168, 1 } };   // x, claro, alto, ¿con arco?
        for (double[] r : ruins) {
            for (int k = 0; k < 2; k++) {
                double h = r[3] == 1 ? r[2] : r[2] * (0.5 + 0.5 * k);
                g.fill(new Rectangle2D.Double(r[0] + k * r[1] - 8, 540 - h, 16, h + 20));
                g.fill(new Rectangle2D.Double(r[0] + k * r[1] - 12, 540 - h, 24, 7));
            }
            if (r[3] == 1) g.draw(new Arc2D.Double(r[0], 540 - r[2] - r[1] / 2, r[1], r[1], 0, 180, Arc2D.OPEN));
        }
        for (int i = 0; i < 18; i++) {
            double x = rnd.nextDouble() * W;
            if (Math.abs(x - FAR_GLOW_X) < 80) continue;
            g.fill(spike(x, 58, 10 + rnd.nextDouble() * 20, 40 + rnd.nextDouble() * 110, true));
        }
        // Un banco de niebla quieto entre las capas, para separarlas
        for (int i = 0; i < 26; i++) {
            double x = rnd.nextDouble() * W, y = 330 + rnd.nextDouble() * 130;
            float r = (float) (50 + rnd.nextDouble() * 70);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), r, new float[] { 0f, 1f },
                    new Color[] { alpha(FOG, 15), alpha(FOG, 0) }));
            g.fill(new Ellipse2D.Double(x - r * 1.8, y - r * 0.6, r * 3.6, r * 1.2));
        }
        g.setColor(NEAR);
        for (double x : new double[] { 30, 130, 472, 640, 772, 1010, 1100 }) {
            g.fill(spike(x, -6, 26 + rnd.nextDouble() * 40, 60 + rnd.nextDouble() * 120, true));
        }

        // Las dos repisas y las dos rocas
        drawLedge(g, rnd, -30, GAP_LEFT, true);
        drawLedge(g, rnd, GAP_RIGHT, W + 30, false);
        drawRock(g, rnd, ROCK_1);
        drawRock(g, rnd, ROCK_2);

        // El hueco se oscurece hacia abajo, para que se vea hondo
        g.setPaint(new GradientPaint(0, 500, new Color(2, 3, 8, 0), 0, (float) H, new Color(2, 3, 8, 215)));
        g.fill(new Rectangle2D.Double(0, 500, W, H - 500));
        g.dispose();
        return img;
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /**
     * Una repisa de roca como las del juego: cara de arriba plana (es donde pisa Candil) con una
     * orilla clara, y la pared que da al hueco irregular (desplazamiento del punto medio), cada
     * vez más metida conforme baja, como un risco. Encima, vetas, musgo y unos hongos que brillan.
     */
    private static void drawLedge(Graphics2D g, Random rnd, double x0, double x1, boolean cliffOnRight) {
        double[] inset = Background.midpointDisplacement(rnd, 16, 9, 0.6);
        Path2D body = new Path2D.Double();
        double flat = cliffOnRight ? x0 : x1, cliff = cliffOnRight ? x1 : x0, dir = cliffOnRight ? -1 : 1;
        body.moveTo(flat, GROUND);
        body.lineTo(cliff, GROUND);
        for (int i = 1; i <= 16; i++) {
            double y = GROUND + (H - GROUND + 10) * i / 16.0;
            double in = 4 + 1.1 * (y - GROUND) * 0.32 + Math.abs(inset[i]) + (i == 1 ? 3 : 0);
            body.lineTo(cliff + dir * in, y);
        }
        body.lineTo(flat, H + 10);
        body.closePath();
        g.setPaint(new GradientPaint(0, (float) GROUND, ROCK_TOP, 0, (float) GROUND + 130, ROCK_BOTTOM));
        g.fill(body);

        // Vetas: rayas casi horizontales
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(STRATA);
        double left = Math.min(x0, x1), right = Math.max(x0, x1);
        for (int i = 0; i < 9; i++) {
            double y = GROUND + 12 + rnd.nextDouble() * 110;
            double a = left + rnd.nextDouble() * (right - left - 120), len = 40 + rnd.nextDouble() * 90;
            double shrink = cliffOnRight ? Math.min(a + len, x1 - 10 - (y - GROUND) * 0.4) - a : len;
            if (!cliffOnRight) a = Math.max(a, x0 + 10 + (y - GROUND) * 0.4);
            if (shrink > 8) g.draw(new Line2D.Double(a, y, a + shrink, y + rnd.nextDouble() * 4 - 2));
        }
        // Orilla clara: lo que tiene borde claro arriba se puede pisar (la regla visual del juego)
        g.setColor(RIM);
        g.fill(new Rectangle2D.Double(left, GROUND, right - left, 1.4));
        // Musgo: matas de briznas cortas sobre la orilla
        g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 16; i++) {
            double x = left + 34 + rnd.nextDouble() * (right - left - 68);
            if (ROCK_1.intersects(x - 8, GROUND - 4, 16, 4) || ROCK_2.intersects(x - 8, GROUND - 4, 16, 4)) continue;
            for (int b = 0; b < 4; b++) {
                double bx = x + (b - 1.5) * 1.8, h = 2.5 + rnd.nextDouble() * 4;
                g.setColor(Ease.fade(MOSS, 0.5 + rnd.nextDouble() * 0.4));
                g.draw(new Line2D.Double(bx, GROUND, bx + (rnd.nextDouble() - 0.5) * 3, GROUND - h));
            }
        }
        // Hongos chicos que brillan, cerca del risco
        for (int i = 0; i < 2; i++) {
            double x = cliff + dir * (16 + i * 13 + rnd.nextDouble() * 5), h = 5 + rnd.nextDouble() * 3;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x, GROUND - h), 11, new float[] { 0f, 1f },
                    new Color[] { alpha(CAP, 70), alpha(CAP, 0) }));
            g.fill(new Ellipse2D.Double(x - 11, GROUND - h - 11, 22, 22));
            g.setColor(new Color(150, 170, 190));
            g.draw(new Line2D.Double(x, GROUND, x, GROUND - h));
            g.setColor(CAP);
            g.fill(new Arc2D.Double(x - 3.6, GROUND - h - 2.6, 7.2, 5.2, 0, 180, Arc2D.CHORD));
        }
    }

    /** Una roca: su rectángulo de colisión con las esquinas cortadas al azar, de piedra más clara. */
    private static void drawRock(Graphics2D g, Random rnd, Rectangle2D r) {
        double x0 = r.getMinX(), x1 = r.getMaxX(), y0 = r.getMinY(), y1 = r.getMaxY();
        double c1 = 5 + rnd.nextDouble() * 6, c2 = 5 + rnd.nextDouble() * 6;
        Path2D p = new Path2D.Double();
        p.moveTo(x0, y1);
        p.lineTo(x0 + 0.5, y0 + c1 + 4);
        p.lineTo(x0 + c1, y0 + 0.5);
        p.lineTo(x1 - c2, y0);
        p.lineTo(x1 - 0.5, y0 + c2);
        p.lineTo(x1, y1);
        p.closePath();
        g.setPaint(new GradientPaint(0, (float) y0, STONE_TOP, 0, (float) y1, STONE_BOTTOM));
        g.fill(p);
        g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(STONE_EDGE);
        Path2D top = new Path2D.Double();
        top.moveTo(x0 + 0.5, y0 + c1 + 4);
        top.lineTo(x0 + c1, y0 + 0.5);
        top.lineTo(x1 - c2, y0);
        top.lineTo(x1 - 0.5, y0 + c2);
        g.draw(top);
        g.setColor(new Color(22, 24, 38, 190));   // dos grietas
        g.draw(new Line2D.Double(x0 + r.getWidth() * 0.35, y0 + 3, x0 + r.getWidth() * 0.45, y0 + r.getHeight() * 0.55));
        g.draw(new Line2D.Double(x0 + r.getWidth() * 0.45, y0 + r.getHeight() * 0.55, x0 + r.getWidth() * 0.3, y1 - 6));
        g.draw(new Line2D.Double(x1 - 12, y0 + r.getHeight() * 0.4, x1 - 5, y0 + r.getHeight() * 0.7));
    }

    /** Textura de niebla repetible de lado a lado: manchas alargadas, con copia a un ancho de distancia. */
    private static BufferedImage renderFog(Random rnd) {
        int w = 1024, h = 200;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < 22; i++) {
            double cx = rnd.nextDouble() * w, cy = 70 + rnd.nextDouble() * (h - 140);
            float r = (float) (30 + rnd.nextDouble() * 38);
            for (int k = -1; k <= 1; k++) {
                AffineTransform old = g.getTransform();
                g.translate(cx + k * w, cy);
                g.scale(2.6, 1);
                g.setPaint(new RadialGradientPaint(new Point2D.Double(0, 0), r, new float[] { 0f, 1f },
                        new Color[] { alpha(FOG, 17), alpha(FOG, 0) }));
                g.fill(new Ellipse2D.Double(-r, -r, 2 * r, 2 * r));
                g.setTransform(old);
            }
        }
        g.dispose();
        return img;
    }

    /**
     * Perfil de techo o de piso a lo ancho del escenario. A los techos se les resta una campana
     * de Gauss centrada en la chimenea, notch · e^(-((x - centro) / ancho)²), para abrir la muesca.
     */
    private static Path2D ridge(double[] h, double base, boolean ceiling, double notch, double notchWidth) {
        Path2D p = new Path2D.Double();
        double edge = ceiling ? -260 : H;
        p.moveTo(0, edge);
        for (int i = 0; i < h.length; i++) {
            double x = W * (double) i / (h.length - 1);
            double d = (x - FAR_GLOW_X) / notchWidth;
            p.lineTo(x, base + h[i] - notch * Math.exp(-d * d));
        }
        p.lineTo(W, edge);
        p.closePath();
        return p;
    }

    private static Path2D spike(double x, double baseY, double w, double len, boolean down) {
        double dir = down ? 1 : -1;
        Path2D p = new Path2D.Double();
        p.moveTo(x - w / 2, baseY);
        p.quadTo(x - w / 5, baseY + dir * len * 0.55, x, baseY + dir * len);
        p.quadTo(x + w / 5, baseY + dir * len * 0.55, x + w / 2, baseY);
        p.closePath();
        return p;
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
