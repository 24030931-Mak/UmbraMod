package umbra.entities;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Random;

import umbra.core.Config;
import umbra.core.Input;
import umbra.entities.CandilArt.Look;
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

    /** Lo coloca en 'position' sin tocar su velocidad (al bajar por un paso: sigue cayendo igual). */
    public void placeKeepingSpeed(Vec2 position) {
        x = position.x;
        y = position.y;
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
        if (y < 0) {             // el techo del mundo: no sale por arriba (salvo por un paso, que se dispara en y <= 1)
            y = 0;
            if (vy < 0) vy = 0;
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

    /** Dibuja a Candil (ver CandilArt). */
    public void draw(Graphics2D g) {
        CandilArt.draw(g, this);
    }

    // Lo que CandilArt necesita para dibujar (solo lectura)
    Look getLook()          { return look; }
    double getAnimTime()    { return animTime; }
    double getStretch()     { return stretch; }
    double getSeated()      { return seated; }
    double getWavePhase()   { return wavePhase; }
    double getStepPhase()   { return stepPhase; }
    double getBlinkLeft()   { return blinkLeft; }
    int getFacing()         { return facing; }

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
