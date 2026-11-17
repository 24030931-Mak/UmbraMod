package umbra.ui.intro;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
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
    private static final Rectangle2D[] ROCKS = { ROCK_1, ROCK_2 };

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
    /** La luz del rayo donde pega en la repisa: un Glow aplastado en vertical (ver drawBeam). */
    private final Glow floorGlow = new Glow(256, BEAM, 0.45, 0.75);

    /** El color de la niebla que se junta para formar a Candil (el de las motas de BirthScene). */
    private static final Color FORMING_MIST = new Color(176, 192, 222);
    /**
     * Mientras Candil se forma se dibuja primero aquí y luego se copia a la pantalla (ver
     * drawCandil). Se crea una vez y solo crece si hace falta: no se crean imágenes por cuadro.
     */
    private BufferedImage forming;

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
    private double pulseTime = -1, pulseX, pulseY;   // pulso de pacto: segundos desde que se disparó (-1 = ninguno)

    /** El pulso de pacto: dura PULSE_TIME segundos y su anillo crece hasta PULSE_RADIUS px. */
    private static final double PULSE_TIME = 0.6, PULSE_RADIUS = 90, REFLECT_RANGE = 420;
    private static final Stroke PULSE_STROKE = new BasicStroke(2.4f);

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
        pulseTime = -1;
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
        if (pulseTime >= 0) pulseTime += dt;
    }

    /** Pulso de pacto en (x, y): una ráfaga de 18 chispas y un anillo de luz que se expande y se apaga. */
    public void firePactPulse(double x, double y) {
        pulseTime = 0;
        pulseX = x;
        pulseY = y;
        for (int i = 0; i < 18; i++) particles.emitSpark(x, y);
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
        draw(g, camera, beam, candilAlpha, candilAlpha, candilGrow);
    }

    /**
     * Igual que el anterior, pero Candil se forma de arriba abajo: 'candilTop' es qué tan formada
     * está su capucha (con los ojos) y 'candilBottom' qué tanto sus jirones (0 a 1 cada uno).
     */
    public void draw(Graphics2D g, IntroCamera camera, double beam, double candilTop, double candilBottom,
                     double candilGrow) {
        cavern.drawBack(g, camera, clock);

        AffineTransform onScreen = g.getTransform();
        g.transform(camera.getViewMatrix().toAffineTransform());
        if (logiPresent && logiLight > 0.01) logiGlow.draw(g, logi.getX(), logi.getY(), 240 * logiLight);
        if (beam > 0.01) drawBeam(g, beam);
        if (pulseTime >= 0 && pulseTime < PULSE_TIME) drawPulse(g);   // antes de las sombras: no las aclara
        if (logiPresent) drawShadows(g);
        if (logiPresent && logiLight > 0.01) drawRockGlow(g);
        particles.draw(g);
        drawCandil(g, candilTop, candilBottom, candilGrow);
        if (logiPresent) logi.drawCore(g);
        g.setTransform(onScreen);
        cavern.drawFront(g, camera, clock);
    }

    /** El anillo del pulso: crece con salida suave hasta PULSE_RADIUS y se desvanece. */
    private void drawPulse(Graphics2D g) {
        double u = pulseTime / PULSE_TIME, r = PULSE_RADIUS * (1 - (1 - u) * (1 - u));
        Stroke old = g.getStroke();
        g.setStroke(PULSE_STROKE);
        g.setColor(new Color(255, 214, 130, (int) (200 * (1 - u) * (1 - u))));
        g.draw(new Ellipse2D.Double(pulseX - r, pulseY - r, 2 * r, 2 * r));
        g.setStroke(old);
    }

    /**
     * Reflejo cálido de Logi en las rocas: un degradado radial dorado centrado en ella, recortado
     * a cada roca. Como se apaga con la distancia al centro, solo la cara que mira a Logi se
     * alumbra; y la roca entera casi no se ve si Logi está lejos de ella.
     */
    private void drawRockGlow(Graphics2D g) {
        Shape oldClip = g.getClip();
        for (Rectangle2D rock : ROCKS) {
            double d = Math.hypot(rock.getCenterX() - logi.getX(), rock.getCenterY() - logi.getY());
            double k = Ease.clamp01(1 - d / REFLECT_RANGE) * logiLight;
            if (k < 0.02) continue;
            g.setClip(rock);
            g.setPaint(new RadialGradientPaint((float) logi.getX(), (float) logi.getY(), 300f,
                    new float[] { 0f, 1f },
                    new Color[] { new Color(255, 200, 110, (int) (110 * k)), new Color(255, 200, 110, 0) }));
            g.fill(rock);
        }
        g.setClip(oldClip);
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
     * Candil, con Player.draw, escalado desde sus pies mientras se forma:
     *     M = T(pies) · S(s) · T(-pies)
     *
     * Formado (top = bottom = 1) se dibuja directo. Mientras se forma, en cambio, se condensa de
     * la niebla de arriba abajo: se dibuja en una imagen aparte con los mismos píxeles que tendría
     * en la pantalla (la matriz de g, movida a la esquina de la imagen), y ahí
     *   1. se tiñe del color de la niebla lo que todavía no termina de formarse (SRC_ATOP: solo
     *      pinta donde ya hay Candil, sin cambiar su transparencia), y
     *   2. se multiplica su transparencia por un degradado vertical (DST_IN: resultado =
     *      Candil · alfa de la máscara), de 'top' en la punta de la capucha a 'bottom' en los
     *      jirones. Más arriba de la capucha vale 'top' y más abajo de los jirones, 'bottom'.
     * Luego la imagen se copia sin estirar a su lugar: al llegar a 1 no hay salto.
     */
    private void drawCandil(Graphics2D g, double top, double bottom, double grow) {
        top = Ease.clamp01(top);
        bottom = Ease.clamp01(bottom);
        if (top <= 0.01 && bottom <= 0.01) return;
        AffineTransform old = g.getTransform();
        double fx = candil.getCenterX(), fy = candil.getBottom();
        if (grow != 1) {
            g.transform(Mat3.translation(fx, fy).multiply(Mat3.scale(grow, grow))
                    .multiply(Mat3.translation(-fx, -fy)).toAffineTransform());
        }
        if (top >= 0.995 && bottom >= 0.995) {
            candil.draw(g);
            g.setTransform(old);
            return;
        }

        // Lo que puede ocupar Candil: su brillo es un círculo de radio 30 con centro 15 arriba
        // de los pies; con margen para el temblor, el estirón y los jirones que se levantan
        AffineTransform world = g.getTransform();
        Rectangle area = world.createTransformedShape(
                new Rectangle2D.Double(fx - 37, fy - 54, 74, 76)).getBounds();
        if (forming == null || forming.getWidth() < area.width || forming.getHeight() < area.height) {
            int w = Math.max(area.width, forming == null ? 0 : forming.getWidth());
            int h = Math.max(area.height, forming == null ? 0 : forming.getHeight());
            forming = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        }

        Graphics2D b = forming.createGraphics();
        b.setComposite(AlphaComposite.Clear);
        b.fillRect(0, 0, area.width, area.height);
        b.setComposite(AlphaComposite.SrcOver);
        b.setRenderingHints(g.getRenderingHints());
        AffineTransform toImage = AffineTransform.getTranslateInstance(-area.x, -area.y);
        toImage.concatenate(world);
        b.setTransform(toImage);
        candil.draw(b);

        // La máscara va de la punta de la capucha (31 arriba de los pies) a los jirones; se
        // dibuja en píxeles de la imagen, cubriéndola toda
        double hoodY = toImage.transform(new Point2D.Double(fx, fy - 31), null).getY();
        double hemY = toImage.transform(new Point2D.Double(fx, fy + 1), null).getY();
        b.setTransform(new AffineTransform());
        b.setComposite(AlphaComposite.SrcAtop);
        b.setPaint(new GradientPaint(0, (float) hoodY, Ease.fade(FORMING_MIST, 0.6 * (1 - top)),
                0, (float) hemY, Ease.fade(FORMING_MIST, 0.6 * (1 - bottom))));
        b.fillRect(0, 0, area.width, area.height);
        b.setComposite(AlphaComposite.DstIn);
        b.setPaint(new GradientPaint(0, (float) hoodY, new Color(1f, 1f, 1f, (float) top),
                0, (float) hemY, new Color(1f, 1f, 1f, (float) bottom)));
        b.fillRect(0, 0, area.width, area.height);
        b.dispose();

        g.setTransform(new AffineTransform());
        g.drawImage(forming, area.x, area.y, area.x + area.width, area.y + area.height,
                0, 0, area.width, area.height, null);
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
        // Donde pega en la repisa: una mancha de luz aplastada en vertical, la misma campana de
        // Glow con la matriz T(166, piso) · S(1, 0.14), que se desvanece sin orilla en la piedra
        AffineTransform old = g.getTransform();
        Composite oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Ease.clamp01(0.75 * alpha * flicker)));
        g.transform(Mat3.translation(166, GROUND).multiply(Mat3.scale(1.0, 0.14)).toAffineTransform());
        floorGlow.draw(g, 0, 0, 58);
        g.setComposite(oldComposite);
        g.setTransform(old);
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
