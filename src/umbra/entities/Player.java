package umbra.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Random;

import umbra.core.Config;
import umbra.core.Input;
import umbra.math.Mat3;
import umbra.math.Vec2;
import umbra.world.Level;

/**
 * Candil, el fantasma de luz que controla el jugador.
 *
 * [v1.0] La sombra es el único lugar seguro:
 *   - Dentro de una sombra flota sin gravedad (W o Espacio sube, S baja) y recupera vida.
 *   - Fuera de la sombra cae con gravedad, camina y salta sobre repisas, pero pierde vida.
 *   - Si la vida llega a 0, o si se acerca demasiado a la luz (crumble), se desmorona.
 * Las colisiones con repisas y bloques son AABB (rectángulo contra rectángulo).
 *
 * [v1.0] Se dibuja encapuchado: capucha de niebla gris azulada con la punta caída, la cara
 * oscura con dos ojos blancos que brillan y una capa que termina en jirones. Cada acción tiene
 * su forma (Pose), y las formas se mezclan sin saltos. Solo cambia el dibujo: la caja de
 * colisión sigue siendo de 22 x 30.
 */
public class Player {

    public static final double W = 22;
    public static final double H = 30;
    private static final double EPS = 0.001;

    /** El escalón más alto que puede subir caminando, sin saltar (px). */
    private static final int MAX_STEP = 20;

    /** Qué tan rápido alcanza la velocidad deseada al flotar (mayor = más rápido). */
    private static final double SWIM_RESPONSE = 10.0;

    /** Segundos quieto (sin teclas) dentro de una sombra para acurrucarse a descansar. */
    public static final double REST_TIME = 6.0;
    /** Con Logi a menos de esta distancia (px), Candil se asusta. */
    public static final double FEAR_DISTANCE = 150;
    /** Con menos vida que esto, fuera de la sombra, también se asusta. */
    public static final double FEAR_LIFE = 0.35;

    /** Qué tan rápido se mezcla una forma con la siguiente: el mismo suavizado de la cámara. */
    private static final double SHAPE_SMOOTHING = 8.0;

    /**
     * Las formas de Candil, una por acción. Cada una trae la "forma meta" de su dibujo (Look);
     * los parámetros del dibujo se acercan a ella poco a poco (ver animate), así una forma se
     * mezcla con la siguiente sin saltos. Cuál toca la decide getPose().
     */
    public enum Pose {
        //                 incli. bolita miedo  opac. atrás arriba abre  pies  ojos  brillo
        IDLE     (new Look(0,     0,     0,     1,    0,    0,     0,    0,    1,    1)),     // capucha pesada
        RESTING  (new Look(0,     1,     0,     0.85, 0,    0,     0,    0,    0.4,  0.7)),   // acurrucado
        RUNNING  (new Look(0.2,   0,     0,     1,    1,    0.25,  0,    1,    1,    1)),     // inclinado, capa atrás
        JUMPING  (new Look(0,     0,     0,     1,    0,   -0.6,   1,    0,    1.1,  1)),     // la capa se abre abajo
        FALLING  (new Look(0,     0,     0,     1,    0.1,  1,     0.5,  0,    1.35, 1.1)),   // la capa se levanta
        FLOATING (new Look(0,     0,     0,     0.6,  0,    0.2,   0.2,  0,    1.1,  1.6)),   // translúcido
        SCARED   (new Look(0,     0,     1,     1,    0,    0,     0,    0,    1.45, 1.25));  // encogido

        private final Look look;

        Pose(Look look) {
            this.look = look;
        }
    }

    /**
     * Los parámetros con que se dibuja a Candil. Una forma es un punto en este espacio de
     * parámetros, y pasar de una forma a otra es moverse en línea recta entre dos puntos.
     */
    private static final class Look {
        double lean;      // inclinación hacia adelante (radianes)
        double curl;      // 0 = de pie, 1 = acurrucado en una bolita
        double fear;      // 0 = tranquilo, 1 = encogido y temblando
        double opacity;   // 1 = capa opaca; menos = más transparente, y los jirones se deshacen
        double sweep;     // jirones que vuelan hacia atrás (al correr)
        double lift;      // jirones que vuelan hacia arriba (al caer); negativo: cuelgan más
        double spread;    // la capa se abre hacia los lados
        double feet;      // 0 = pies escondidos, 1 = se asoman
        double eyes;      // tamaño de los ojos (1 = normal)
        double glow;      // brillo de los ojos (1 = normal)

        Look(double lean, double curl, double fear, double opacity, double sweep, double lift,
             double spread, double feet, double eyes, double glow) {
            this.lean = lean;
            this.curl = curl;
            this.fear = fear;
            this.opacity = opacity;
            this.sweep = sweep;
            this.lift = lift;
            this.spread = spread;
            this.feet = feet;
            this.eyes = eyes;
            this.glow = glow;
        }

        Look(Look o) {
            set(o);
        }

        void set(Look o) {
            lean = o.lean;     curl = o.curl;   fear = o.fear;     opacity = o.opacity;
            sweep = o.sweep;   lift = o.lift;   spread = o.spread; feet = o.feet;
            eyes = o.eyes;     glow = o.glow;
        }

        /**
         * Se acerca a 'goal' una fracción t del camino que falta, en cada parámetro:
         *   p = p + (meta - p) * t
         * Con t = 1 - e^(-k * dt) es el mismo suavizado exponencial de la cámara y de la luz.
         */
        void approach(Look goal, double t) {
            lean    += (goal.lean    - lean)    * t;
            curl    += (goal.curl    - curl)    * t;
            fear    += (goal.fear    - fear)    * t;
            opacity += (goal.opacity - opacity) * t;
            sweep   += (goal.sweep   - sweep)   * t;
            lift    += (goal.lift    - lift)    * t;
            spread  += (goal.spread  - spread)  * t;
            feet    += (goal.feet    - feet)    * t;
            eyes    += (goal.eyes    - eyes)    * t;
            glow    += (goal.glow    - glow)    * t;
        }
    }

    /**
     * Silueta de la capa con capucha, de pie, mirando a la derecha y con los pies en (0, 0)
     * (y crece hacia abajo, como en la pantalla). Los puntos se unen con curvas de Bézier.
     */
    private static final double[][] CLOAK = {
        {   1.0, -31.0 },   //  0 coronilla
        {   8.5, -27.5 },   //  1 frente de la capucha
        {  11.0, -21.0 },   //  2 ala de la capucha, sobre la cara
        {   9.0, -16.5 },   //  3 debajo del ala, donde la capucha se recoge
        {  10.0, -10.0 },   //  4 pecho
        {  12.0,  -4.5 },   //  5 orilla de enfrente   (orilla de abajo: 5, 7, 9, 11, 13)
        {   9.2,   0.5 },   //  6 jirón de enfrente    (puntas de los jirones: 6, 8, 10, 12)
        {   6.2,  -4.8 },   //  7
        {   3.4,   1.8 },   //  8 jirón
        {   0.2,  -4.2 },   //  9
        {  -2.6,   0.8 },   // 10 jirón
        {  -5.8,  -4.8 },   // 11
        {  -9.0,   2.0 },   // 12 jirón de atrás
        { -12.0,  -4.5 },   // 13 orilla de atrás
        { -10.0, -12.0 },   // 14 espalda
        {  -8.5, -19.0 },   // 15 nuca
        { -12.0, -21.2 },   // 16 debajo de la punta
        { -15.3, -19.0 },   // 17 punta de la capucha, caída hacia atrás
        { -13.0, -27.0 },   // 18 la capucha se dobla hacia abajo
        {  -6.5, -31.5 },   // 19 capucha, atrás
    };
    private static final int HOOD_TIP = 17;
    /** Dónde queda cada punto en la bolita: ángulo en grados (0 = adelante, 90 = abajo). */
    private static final double[] BALL_ANGLES = {
        -95, -55, -20, 5, 30, 55, 68, 79, 90, 101, 112, 123, 134, 147, 172, 196, 206, 214, 228, 248 };

    // Colores: niebla gris azulada que se vuelve gris con poca vida; la cara, casi negra
    private static final Color HOOD = new Color(176, 192, 222);
    private static final Color HOOD_DIM = new Color(112, 116, 128);
    private static final Color HEM = new Color(110, 126, 166);
    private static final Color HEM_DIM = new Color(76, 80, 94);
    private static final Color FACE = new Color(16, 14, 30);
    private static final Color FEET = new Color(24, 20, 36);
    private static final Color EYE_DIM = new Color(105, 118, 140);
    private static final Color GLOW = new Color(185, 230, 255);

    private double x, y;      // esquina superior izquierda
    private double vx, vy;    // velocidad en px/s
    private boolean onGround;
    private int facing = 1;   // 1 = derecha, -1 = izquierda

    private double life;      // de 0 (sin vida) a 1 (llena)
    private boolean inShadow; // el centro del cuerpo está dentro de una sombra
    private boolean dead;     // se desmoronó; GamePanel lo hace reaparecer

    private double logiDistance = Double.POSITIVE_INFINITY;   // a qué distancia está Logi (px)
    private double restTime;      // segundos quieto, sin teclas, dentro de la sombra
    private boolean onLedge;      // sus pies tocan una repisa o un bloque

    // ---- Animación [v1.0]: solo cambia cómo se dibuja, nunca la física ----
    private final Random rnd = new Random();
    private final Look look = new Look(Pose.IDLE.look);   // la forma que se dibuja ahora
    private double animTime;    // reloj de la animación (s)
    private double stretch;     // > 0 estirado (salto), < 0 aplastado (al caer al suelo)
    private double seated;      // al descansar: 0 = bolita flotando, 1 = sentado en la repisa
    private double wavePhase;   // fase de las ondas de los jirones
    private double stepPhase;   // fase de los pasos
    private double blinkTimer = 3;   // segundos para el siguiente parpadeo
    private double blinkLeft;        // segundos que le quedan al parpadeo actual

    public Player(Vec2 spawn) {
        respawn(spawn);
    }

    public void respawn(Vec2 spawn) {
        x = spawn.x;
        y = spawn.y;
        vx = 0;
        vy = 0;
        onGround = false;
        life = 1;
        inShadow = false;
        dead = false;
        restTime = 0;
        stretch = 0;
        seated = 0;
        look.set(Pose.IDLE.look);
    }

    /** Lo coloca en 'position' y lo frena, sin tocar su vida (al pasar a otro nivel). */
    public void moveTo(Vec2 position) {
        x = position.x;
        y = position.y;
        vx = 0;
        vy = 0;
        onGround = false;
    }

    /** A qué distancia está Logi (px). GamePanel la da en cada cuadro; cerca, Candil se asusta. */
    public void setLogiDistance(double d) {
        logiDistance = d;
    }

    public void update(double dt, Input in, Level level, List<Path2D> shadows) {
        if (dead) return;
        boolean wasOnGround = onGround;

        // 1. Movimiento horizontal (igual dentro y fuera de la sombra)
        boolean left = in.isDown(KeyEvent.VK_A) || in.isDown(KeyEvent.VK_LEFT);
        boolean right = in.isDown(KeyEvent.VK_D) || in.isDown(KeyEvent.VK_RIGHT);
        double dir = 0;
        if (left)  dir -= 1;
        if (right) dir += 1;
        vx = dir * Config.MOVE_SPEED;
        if (dir != 0) facing = (int) dir;

        // 2. Movimiento vertical: depende de si está dentro de una sombra
        boolean up = in.isDown(KeyEvent.VK_SPACE) || in.isDown(KeyEvent.VK_W) || in.isDown(KeyEvent.VK_UP);
        boolean down = in.isDown(KeyEvent.VK_S) || in.isDown(KeyEvent.VK_DOWN);
        boolean wasInShadow = inShadow(shadows, getCenterX(), getCenterY());

        if (wasInShadow) {
            // Flotar: no hay gravedad. La velocidad se acerca a la deseada con el mismo
            // suavizado exponencial que usa la luz, para que no arranque ni frene en seco.
            double target = 0;
            if (up)   target -= Config.SWIM_SPEED;
            if (down) target += Config.SWIM_SPEED;
            vy += (target - vy) * (1 - Math.exp(-SWIM_RESPONSE * dt));
        } else {
            // Fuera de la sombra: salto (solo parado sobre algo) y gravedad
            boolean jump = in.wasPressed(KeyEvent.VK_SPACE)
                        || in.wasPressed(KeyEvent.VK_W)
                        || in.wasPressed(KeyEvent.VK_UP);
            if (jump && onGround) {
                vy = -Config.JUMP_SPEED;
                onGround = false;
                stretch = 1;   // se estira al saltar
            }
            vy = Math.min(vy + Config.GRAVITY * dt, Config.MAX_FALL_SPEED);
        }

        // 3. Mover y resolver colisiones con repisas y bloques, un eje a la vez
        List<Rectangle2D> solids = level.getSolids();

        x += vx * dt;
        x = Math.max(0, Math.min(x, level.getWidth() - W));   // no salir del nivel
        for (Rectangle2D r : solids) {
            if (overlaps(r)) {
                if (stepOnto(r, solids)) continue;      // escalón bajo: lo sube y sigue
                if (vx > 0)      x = r.getMinX() - W;   // choca con una pared a la derecha
                else if (vx < 0) x = r.getMaxX();       // choca con una pared a la izquierda
            }
        }

        y += vy * dt;
        onGround = false;
        for (Rectangle2D r : solids) {
            if (overlaps(r)) {
                if (vy > 0) {            // cayendo: aterriza encima
                    y = r.getMinY() - H;
                    onGround = true;
                } else if (vy < 0) {     // subiendo: se pega con el techo
                    y = r.getMaxY();
                }
                vy = 0;
            }
        }
        if (!wasOnGround && onGround) stretch = -1;   // se aplasta al caer al suelo

        // 4. ¿Sigue dentro de la sombra? Si sale por arriba nadando hacia arriba,
        //    sale de un salto, para alcanzar una repisa que esté sobre la sombra.
        inShadow = inShadow(shadows, getCenterX(), getCenterY());
        if (wasInShadow && !inShadow && vy < 0 && up) {
            vy = -Config.JUMP_SPEED;
            stretch = 1;
        }

        // 5. Vida: la recupera en la sombra y la pierde fuera de ella
        if (inShadow) life = Math.min(1, life + dt / Config.LIFE_REGEN_TIME);
        else          life = Math.max(0, life - dt / Config.LIFE_DRAIN_TIME);
        if (life == 0) dead = true;

        // 6. Descanso: cuenta el tiempo seguido sin teclas dentro de la sombra, y ve si sus
        //    pies tocan algo (descansando ahí, se sienta en vez de flotar hecho bolita)
        boolean anyKey = left || right || up || down;
        restTime = inShadow && !anyKey ? restTime + dt : 0;
        onLedge = feetOnSolid(solids);

        animate(dt);
    }

    /**
     * La forma que toca según lo que hace, en orden de prioridad:
     *   SCARED   Logi a menos de 150 px, o menos de 35% de vida fuera de la sombra
     *   RESTING  dentro de la sombra, 6 s seguidos sin teclas
     *   FLOATING dentro de la sombra
     *   JUMPING  en el aire, subiendo
     *   FALLING  en el aire, bajando
     *   RUNNING  caminando (vx distinto de 0)
     *   IDLE     quieto
     */
    public Pose getPose() {
        if (logiDistance < FEAR_DISTANCE || (life < FEAR_LIFE && !inShadow)) return Pose.SCARED;
        if (inShadow) return restTime >= REST_TIME ? Pose.RESTING : Pose.FLOATING;
        if (!onGround) return vy < 0 ? Pose.JUMPING : Pose.FALLING;
        return vx != 0 ? Pose.RUNNING : Pose.IDLE;
    }

    /**
     * Animación: los parámetros del dibujo se acercan a los de la forma actual; el estirón o
     * aplastón regresa suave a 0 (en el aire se estira un poco según qué tan rápido cae);
     * avanzan las ondas de los jirones y los pasos, y parpadea cada pocos segundos.
     */
    private void animate(double dt) {
        animTime += dt;
        double t = 1 - Math.exp(-SHAPE_SMOOTHING * dt);
        look.approach(getPose().look, t);
        seated += ((onLedge ? 1 : 0) - seated) * t;

        boolean airborne = !onGround && !inShadow;
        double stretchTarget = airborne ? Math.min(0.35, Math.abs(vy) / 1500) : 0;
        stretch += (stretchTarget - stretch) * t;

        // Las ondas de los jirones van más rápido al correr; los pasos, solo en el suelo
        double speed = Math.abs(vx) / Config.MOVE_SPEED;
        wavePhase += dt * (3 + 6 * speed);
        if (onGround) stepPhase += dt * 15 * speed;

        blinkTimer -= dt;
        if (blinkTimer <= 0) {
            blinkLeft = 0.12;
            blinkTimer = 2.5 + rnd.nextDouble() * 3;
        }
        blinkLeft = Math.max(0, blinkLeft - dt);
    }

    /** Se desmorona de inmediato (por ejemplo, por estar demasiado cerca de la luz). */
    public void crumble() {
        life = 0;
        dead = true;
    }

    /**
     * Escalón: si al caminar choca con un sólido cuya cara de arriba queda a MAX_STEP px
     * o menos sobre sus pies, lo sube en lugar de detenerlo.
     */
    private boolean stepOnto(Rectangle2D r, List<Rectangle2D> solids) {
        double step = getBottom() - r.getMinY();
        if (vx == 0 || step <= 0 || step > MAX_STEP) return false;

        double oldY = y;
        y = r.getMinY() - H;
        for (Rectangle2D other : solids) {
            if (overlaps(other)) {      // arriba no cabe (hay un techo): no sube
                y = oldY;
                return false;
            }
        }
        return true;
    }

    /** ¿Hay una repisa o un bloque justo debajo de sus pies (a menos de 1 px)? */
    private boolean feetOnSolid(List<Rectangle2D> solids) {
        for (Rectangle2D r : solids) {
            boolean below = Math.abs(r.getMinY() - getBottom()) < 1;
            boolean across = x < r.getMaxX() && x + W > r.getMinX();
            if (below && across) return true;
        }
        return false;
    }

    /** Punto dentro de polígono: ¿el punto (px, py) está dentro de alguna sombra? */
    private static boolean inShadow(List<Path2D> shadows, double px, double py) {
        for (Path2D shadow : shadows) {
            if (shadow.contains(px, py)) return true;
        }
        return false;
    }

    private boolean overlaps(Rectangle2D r) {
        return x < r.getMaxX() - EPS && x + W > r.getMinX() + EPS
            && y < r.getMaxY() - EPS && y + H > r.getMinY() + EPS;
    }

    // =========================================================
    //  DIBUJO
    // =========================================================

    /**
     * Dibuja a Candil con una matriz de modelo que lleva su silueta (diseñada mirando a la
     * derecha, con los pies en el origen) a su lugar en el mundo:
     *   M = T(centro + temblor, pies + flote) * R(inclinación) * S(dirección * sx, sy)
     * S estira o aplasta (salto, aterrizaje, miedo, respiración) y con dirección = -1 lo
     * refleja para que mire a la izquierda; R lo inclina hacia donde corre.
     */
    public void draw(Graphics2D g) {
        if (dead) return;

        // Flota subiendo y bajando; al correr, en cambio, rebota un poco con cada paso
        double still = (1 - look.feet) * (1 - look.curl * seated);
        double bob = -0.8 * (1 + Math.sin(animTime * 2.4)) * still
                   - 0.9 * Math.abs(Math.sin(stepPhase)) * look.feet;
        double shakeX = look.fear * 0.6 * Math.sin(animTime * 53);
        double shakeY = look.fear * 0.3 * Math.sin(animTime * 41);
        double breath = 1 + 0.025 * Math.sin(animTime * 2.1);   // respira despacio
        double sx = (1 - 0.14 * stretch) * (1 + 0.05 * look.fear);
        double sy = (1 + 0.18 * stretch) * (1 - 0.2 * look.fear) * breath;

        Mat3 m = Mat3.translation(getCenterX() + shakeX, getBottom() + bob + shakeY)
                .multiply(Mat3.rotation(facing * look.lean))
                .multiply(Mat3.scale(facing * sx, sy));
        AffineTransform old = g.getTransform();
        g.transform(m.toAffineTransform());

        // La bolita del descanso: flotando es redonda; sentado en la repisa, un montoncito
        double ballY = -12 + 3 * seated, ballRx = 10.5 + 2 * seated, ballRy = 10 - 1.5 * seated;

        double op = look.opacity;
        drawAura(g, op);
        if (look.feet > 0.02) drawFeet(g, op);

        // La capa: degradado de arriba (capucha) a abajo (jirones), con una sombra bajo la
        // capucha (no en la bolita), y encima el brillo interior
        Path2D cloak = cloakShape(ballY, ballRx, ballRy);
        Color top = mix(HOOD_DIM, HOOD, life), bottom = mix(HEM_DIM, HEM, life);
        double a = 235 * op;
        g.setPaint(new LinearGradientPaint(0, -31, 0, 2,
            new float[] { 0f, 0.4f, 0.46f, 0.56f, 1f },
            new Color[] { withAlpha(top, a), withAlpha(top, a),
                          withAlpha(mix(top, bottom, 0.85 * (1 - look.curl)), a),
                          withAlpha(mix(top, bottom, 0.45), a),
                          withAlpha(bottom, a * (0.2 + 0.4 * op)) }));
        g.fill(cloak);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(0, -13), 15,
            new float[] { 0f, 1f },
            new Color[] { withAlpha(GLOW, 70 * life * op), withAlpha(GLOW, 0) }));
        g.fill(cloak);

        drawFace(g, ballY, ballRx, ballRy);
        g.setTransform(old);
    }

    /**
     * La silueta de la capa: los 20 puntos de CLOAK, movidos según la forma, y unidos con
     * curvas de Bézier.
     *   - Los jirones ondulan con ondas seno. Según la forma, la capa vuela hacia atrás
     *     (sweep), hacia arriba (lift) o se abre (spread); cada punto se mueve según qué tan
     *     abajo está (k = 0 en los hombros, 1 en la orilla): lo de abajo vuela más.
     *   - Para acurrucarse, cada punto se mezcla con su lugar en una bolita (una elipse):
     *     p = capa + (bolita - capa) * curl   (interpolación lineal punto por punto)
     *   - Las curvas son un spline de Catmull-Rom: la tangente en cada punto es la mitad de
     *     (siguiente - anterior), y los puntos de control de Bézier quedan a un tercio de ella.
     *     En las puntas (jirones y capucha) la tangente es 0, y la curva hace una esquina.
     */
    private Path2D cloakShape(double ballY, double ballRx, double ballRy) {
        int n = CLOAK.length;
        double[] px = new double[n], py = new double[n];
        double wave = 0.9 * (1 + 1.5 * (1 - look.opacity));   // en la sombra ondulan más
        for (int i = 0; i < n; i++) {
            double cx = CLOAK[i][0], cy = CLOAK[i][1];
            boolean tip = isTatterTip(i);
            double side = cx / 12;                            // -1 atrás ... 1 adelante
            double back = Math.max(0, -side);
            double k = Math.max(0, Math.min(1, (cy + 17) / 18));   // 0 en los hombros ... 1 en la orilla
            if (tip) {   // los jirones ondulan
                int j = (i - 6) / 2;
                cx += wave * Math.sin(wavePhase + 1.9 * j);
                cy += 0.6 * wave * Math.sin(1.3 * wavePhase + 2.3 * j);
            }
            // El viento y la caída mueven la punta de la capucha (y a medias, sus vecinos)
            double flap = i == HOOD_TIP ? 1 : Math.abs(i - HOOD_TIP) == 1 ? 0.5 : 0;
            cx -= flap * 2.5 * look.sweep;
            cy += flap * (-1.5 * look.sweep - 2.5 * look.lift + 0.5 * Math.sin(0.7 * wavePhase));
            // Corriendo, el viento empuja la capa hacia atrás y la levanta: más entre más abajo
            // (k^2) y más por la espalda
            cx -= look.sweep * (4 * k * k + 2 * back * k + (tip ? 1.5 : 0));
            cy -= look.sweep * (2 * k * k + 1.5 * back * k);
            // Cayendo, la orilla y los jirones se levantan y se abren; saltando (lift < 0) cuelgan más
            cx += look.lift * 2 * side * k;
            cy -= look.lift * (tip ? 6.5 : 3.5) * k * k;
            // La capa se abre hacia los lados
            cx += look.spread * 3.5 * side * k;
            cy += look.spread * (tip ? 2 : 0.5) * k;

            // Su lugar en la bolita: la punta de la capucha asoma un poco
            double a = Math.toRadians(BALL_ANGLES[i]);
            double r = i == HOOD_TIP ? 1.3 : tip ? 1.06 : 1;
            double bx = ballRx * r * Math.cos(a), by = ballY + ballRy * r * Math.sin(a);
            px[i] = cx + (bx - cx) * look.curl;
            py[i] = cy + (by - cy) * look.curl;
        }

        double[] mx = new double[n], my = new double[n];   // tangentes de Catmull-Rom
        for (int i = 0; i < n; i++) {
            int prev = (i + n - 1) % n, next = (i + 1) % n;
            double soft = i == HOOD_TIP ? 0 : isTatterTip(i) ? look.curl : 1;   // en la bolita ya no hay puntas
            mx[i] = (px[next] - px[prev]) / 2 * soft;
            my[i] = (py[next] - py[prev]) / 2 * soft;
        }
        Path2D p = new Path2D.Double();
        p.moveTo(px[0], py[0]);
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            p.curveTo(px[i] + mx[i] / 3, py[i] + my[i] / 3,
                      px[next] - mx[next] / 3, py[next] - my[next] / 3,
                      px[next], py[next]);
        }
        p.closePath();
        return p;
    }

    private static boolean isTatterTip(int i) {
        return i >= 6 && i <= 12 && i % 2 == 0;
    }

    /** Brillo alrededor: se apaga mientras pierde vida. */
    private void drawAura(Graphics2D g, double op) {
        float r = 30f;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(0, -15), r,
            new float[] { 0f, 1f },
            new Color[] { withAlpha(GLOW, (25 + 60 * life) * op), withAlpha(GLOW, 0) }));
        g.fill(new Ellipse2D.Double(-r, -15 - r, r * 2, r * 2));
    }

    /** Dos piecitos oscuros que asoman bajo la capa al caminar, uno adelante y otro atrás. */
    private void drawFeet(Graphics2D g, double op) {
        g.setColor(withAlpha(FEET, 255 * look.feet * op));
        double swing = 2 * Math.sin(stepPhase);
        double y0 = lerp(-3.5, -0.9, look.feet);
        double upA = Math.max(0, Math.cos(stepPhase)), upB = Math.max(0, -Math.cos(stepPhase));
        g.fill(new Ellipse2D.Double(3 + swing - 1.4, y0 - upA - 0.9, 2.8, 1.8));
        g.fill(new Ellipse2D.Double(-1 - swing - 1.4, y0 - upB - 0.9, 2.8, 1.8));
    }

    /**
     * La cara oscura dentro de la capucha y los dos ojos blancos. Los ojos brillan con un
     * degradado radial y se apagan con la vida (con raíz cuadrada: siguen claros hasta que
     * queda poca). Al descansar, la cara se mueve al frente de la bolita.
     */
    private void drawFace(Graphics2D g, double ballY, double ballRx, double ballRy) {
        double c = look.curl;
        double fx = lerp(4.5, 0.43 * ballRx, c), fy = lerp(-21.5, ballY - 0.33 * ballRy, c);
        double fw = lerp(10, 9, c), fh = lerp(9, 6.5, c);
        g.setColor(withAlpha(FACE, 90));   // orilla suave: el hueco de la capucha se hunde
        g.fill(new Ellipse2D.Double(fx - fw / 2 - 1, fy - fh / 2 - 0.8, fw + 2, fh + 1.6));
        g.setColor(FACE);
        g.fill(new Ellipse2D.Double(fx - fw / 2, fy - fh / 2, fw, fh));

        // El brillo de los ojos se queda dentro de la cara: brillar más es más intenso, no más grande
        double light = Math.sqrt(life);
        float haloR = (float) (fw / 2);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(fx, fy), haloR,
            new float[] { 0f, 1f },
            new Color[] { withAlpha(GLOW, 75 * look.glow * light), withAlpha(GLOW, 0) }));
        g.fill(new Ellipse2D.Double(fx - haloR, fy - haloR, haloR * 2, haloR * 2));

        double ew = 2.5 * (0.6 + 0.4 * look.eyes);
        double eh = 3.3 * look.eyes * (blinkLeft > 0 ? 0.25 : 1);
        double gap = 0.23 * fw;   // la mitad de la distancia entre los ojos
        g.setColor(mix(EYE_DIM, Color.WHITE, light));
        g.fill(new Ellipse2D.Double(fx - gap - ew / 2, fy - eh / 2 + 0.3, ew, eh));
        g.fill(new Ellipse2D.Double(fx + gap - ew / 2, fy - eh / 2 + 0.3, ew, eh));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Interpolación lineal entre dos colores: t = 0 regresa 'a', t = 1 regresa 'b'. */
    private static Color mix(Color a, Color b, double t) {
        return new Color(
            (int) Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) Math.round(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }

    private static Color withAlpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.max(0, Math.min(255, Math.round(a))));
    }

    public double getX()        { return x; }
    public double getY()        { return y; }
    public double getCenterX()  { return x + W / 2; }
    public double getCenterY()  { return y + H / 2; }
    public double getBottom()   { return y + H; }
    public double getVx()       { return vx; }
    public double getVy()       { return vy; }
    public boolean isOnGround() { return onGround; }
    public boolean isInShadow() { return inShadow; }
    public boolean isDead()     { return dead; }
    public double getLife()     { return life; }
    public Rectangle2D getBounds() { return new Rectangle2D.Double(x, y, W, H); }
}
