package umbra.entities;

import java.awt.Color;
import java.awt.Graphics2D;
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
 */
public class Player {

    public static final double W = 22;
    public static final double H = 30;
    private static final double EPS = 0.001;

    /** El escalón más alto que puede subir caminando, sin saltar (px). */
    private static final int MAX_STEP = 20;

    /** Qué tan rápido alcanza la velocidad deseada al flotar (mayor = más rápido). */
    private static final double SWIM_RESPONSE = 10.0;

    private double x, y;      // esquina superior izquierda
    private double vx, vy;    // velocidad en px/s
    private boolean onGround;
    private int facing = 1;   // 1 = derecha, -1 = izquierda

    private double life;      // de 0 (sin vida) a 1 (llena)
    private boolean inShadow; // el centro del cuerpo está dentro de una sombra
    private boolean dead;     // se desmoronó; GamePanel lo hace reaparecer

    // ---- Animación [v1.0]: solo cambia cómo se dibuja, nunca la física ----
    private final Random rnd = new Random();
    private double animTime;    // reloj de la animación (s)
    private double stretch;     // > 0 estirado (salto), < 0 aplastado (al caer al suelo)
    private double lean;        // inclinación al caminar (radianes)
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
        stretch = 0;
        lean = 0;
    }

    /** Lo coloca en 'position' y lo frena, sin tocar su vida (al pasar a otro nivel). */
    public void moveTo(Vec2 position) {
        x = position.x;
        y = position.y;
        vx = 0;
        vy = 0;
        onGround = false;
    }

    public void update(double dt, Input in, Level level, List<Path2D> shadows) {
        if (dead) return;
        boolean wasOnGround = onGround;

        // 1. Movimiento horizontal (igual dentro y fuera de la sombra)
        double dir = 0;
        if (in.isDown(KeyEvent.VK_A) || in.isDown(KeyEvent.VK_LEFT))  dir -= 1;
        if (in.isDown(KeyEvent.VK_D) || in.isDown(KeyEvent.VK_RIGHT)) dir += 1;
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

        animate(dt);
    }

    /**
     * Animación: el estirón o aplastón regresa suave a 0 (en el aire se estira un poco según
     * qué tan rápido cae), se inclina hacia donde camina y parpadea cada pocos segundos.
     */
    private void animate(double dt) {
        animTime += dt;
        double t = 1 - Math.exp(-10 * dt);   // mismo suavizado exponencial de la luz
        boolean airborne = !onGround && !inShadow;
        double stretchTarget = airborne ? Math.min(0.35, Math.abs(vy) / 1500) : 0;
        stretch += (stretchTarget - stretch) * t;
        lean += (0.08 * vx / Config.MOVE_SPEED - lean) * t;

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

    public void draw(Graphics2D g) {
        if (dead) return;
        double cx = getCenterX();
        double cy = getCenterY();
        double bottom = getBottom();
        double bob = -1 - Math.sin(animTime * 3.2);   // flota: sube y baja entre 0 y 2 px

        // Estirar o aplastar e inclinar con los pies como punto fijo (escala y rotación
        // alrededor de un punto), igual que la salida usa T * R * S:
        //   M = T(cx, pies + flote) * R(inclinación) * S(sx, sy) * T(-cx, -pies)
        double sx = 1 - 0.14 * stretch, sy = 1 + 0.18 * stretch;
        Mat3 m = Mat3.translation(cx, bottom + bob)
                .multiply(Mat3.rotation(lean))
                .multiply(Mat3.scale(sx, sy))
                .multiply(Mat3.translation(-cx, -bottom));
        AffineTransform old = g.getTransform();
        g.transform(m.toAffineTransform());

        // Brillo alrededor: se apaga mientras pierde vida
        float glowR = 40f;
        int glowAlpha = (int) (30 + 90 * life);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), glowR,
            new float[] { 0f, 1f },
            new Color[] { new Color(170, 230, 255, glowAlpha), new Color(170, 230, 255, 0) }));
        g.fill(new Ellipse2D.Double(cx - glowR, cy - glowR, glowR * 2, glowR * 2));

        // Cuerpo de fantasma: con poca vida se ve gris; dentro de la sombra, un poco transparente
        Color body = mix(new Color(110, 125, 150), new Color(225, 245, 255), life);
        int alpha = inShadow ? 190 : 255;
        g.setColor(new Color(body.getRed(), body.getGreen(), body.getBlue(), alpha));
        g.fill(ghostShape());

        // Ojos: miran hacia donde camina y a veces parpadean
        g.setColor(new Color(30, 40, 70));
        double eyeX = cx + facing * 4;
        double eyeH = blinkLeft > 0 ? 1.2 : 6;
        double eyeY = y + 9 + (6 - eyeH) / 2;
        g.fill(new Ellipse2D.Double(eyeX - 5, eyeY, 4, eyeH));
        g.fill(new Ellipse2D.Double(eyeX + 1, eyeY, 4, eyeH));

        g.setTransform(old);
    }

    /**
     * Silueta de fantasma: cabeza redonda (dos curvas de Bézier que aproximan un semicírculo)
     * y una cola con tres ondas que se mueven con el tiempo.
     */
    private Path2D ghostShape() {
        double r = W / 2, k = 0.5523 * r;   // 0.5523: constante para aproximar un círculo con Bézier
        double base = y + H - 4;
        Path2D p = new Path2D.Double();
        p.moveTo(x, y + r);
        p.curveTo(x, y + r - k, x + r - k, y, x + r, y);
        p.curveTo(x + r + k, y, x + W, y + r - k, x + W, y + r);
        p.lineTo(x + W, base);
        int waves = 3;
        double ww = W / waves;
        for (int i = 0; i < waves; i++) {
            double x0 = x + W - i * ww, x1 = x0 - ww;
            double dip = 8 + 1.5 * Math.sin(animTime * 7 + i * 2.1);   // las ondas de la cola se mueven
            p.quadTo((x0 + x1) / 2, base + dip, x1, base);
        }
        p.closePath();
        return p;
    }

    /** Interpolación lineal entre dos colores: t = 0 regresa 'a', t = 1 regresa 'b'. */
    private static Color mix(Color a, Color b, double t) {
        return new Color(
            (int) Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) Math.round(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
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
