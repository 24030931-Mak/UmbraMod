package umbra.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

import umbra.core.Config;
import umbra.core.Input;
import umbra.math.Vec2;
import umbra.world.Level;

/**
 * La criatura de luz que controla el jugador.
 * Física sencilla: velocidad + gravedad, y colisiones AABB (rectángulo contra rectángulo).
 */
public class Player {

    public static final double W = 22;
    public static final double H = 30;
    private static final double EPS = 0.001;

    private double x, y;      // esquina superior izquierda
    private double vx, vy;    // velocidad en px/s
    private boolean onGround;
    private int facing = 1;   // 1 = derecha, -1 = izquierda

    public Player(Vec2 spawn) {
        respawn(spawn);
    }

    public void respawn(Vec2 spawn) {
        x = spawn.x;
        y = spawn.y;
        vx = 0;
        vy = 0;
        onGround = false;
    }

    public void update(double dt, Input in, Level level, List<Path2D> shadows) {
        // 1. Movimiento horizontal
        double dir = 0;
        if (in.isDown(KeyEvent.VK_A) || in.isDown(KeyEvent.VK_LEFT))  dir -= 1;
        if (in.isDown(KeyEvent.VK_D) || in.isDown(KeyEvent.VK_RIGHT)) dir += 1;
        vx = dir * Config.MOVE_SPEED;
        if (dir != 0) facing = (int) dir;

        // 2. Salto (solo si está parado sobre algo)
        boolean jump = in.wasPressed(KeyEvent.VK_SPACE)
                    || in.wasPressed(KeyEvent.VK_W)
                    || in.wasPressed(KeyEvent.VK_UP);
        if (jump && onGround) {
            vy = -Config.JUMP_SPEED;
            onGround = false;
        }

        // 3. Gravedad
        vy = Math.min(vy + Config.GRAVITY * dt, Config.MAX_FALL_SPEED);

        // 4. Mover y resolver colisiones, un eje a la vez
        List<Rectangle2D> solids = level.getSolids();

        x += vx * dt;
        x = Math.max(0, Math.min(x, Config.WIDTH - W));   // no salir de la pantalla
        for (Rectangle2D r : solids) {
            if (overlaps(r)) {
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

        // 5. Sombras sólidas [AVANCE 70%]
        // TODO (v0.7): si el jugador va cayendo (vy > 0) y sus pies, el punto
        //   (getCenterX(), getBottom()), quedan dentro de alguna sombra
        //   (shadow.contains(px, py)), súbelo hasta que ya no esté dentro
        //   y marca onGround = true y vy = 0.
        //   Pista: súbelo de 1 en 1 píxel con un límite (por ejemplo 20 px)
        //   para que no se "teletransporte" si cae en medio de una sombra grande.
    }

    private boolean overlaps(Rectangle2D r) {
        return x < r.getMaxX() - EPS && x + W > r.getMinX() + EPS
            && y < r.getMaxY() - EPS && y + H > r.getMinY() + EPS;
    }

    public void draw(Graphics2D g) {
        double cx = getCenterX();
        double cy = y + H / 2;

        // Brillo alrededor de la criatura
        float glowR = 40f;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), glowR,
            new float[] { 0f, 1f },
            new Color[] { new Color(170, 230, 255, 120), new Color(170, 230, 255, 0) }));
        g.fill(new Ellipse2D.Double(cx - glowR, cy - glowR, glowR * 2, glowR * 2));

        // Cuerpo
        g.setColor(new Color(225, 245, 255));
        g.fill(new RoundRectangle2D.Double(x, y, W, H, 14, 14));

        // Ojos (miran hacia donde camina)
        g.setColor(new Color(30, 40, 70));
        double eyeX = cx + facing * 4;
        double eyeY = y + 9;
        g.fill(new Ellipse2D.Double(eyeX - 5, eyeY, 4, 6));
        g.fill(new Ellipse2D.Double(eyeX + 1, eyeY, 4, 6));
    }

    public double getX()       { return x; }
    public double getY()       { return y; }
    public double getCenterX() { return x + W / 2; }
    public double getBottom()  { return y + H; }
    public boolean isOnGround() { return onGround; }
    public Rectangle2D getBounds() { return new Rectangle2D.Double(x, y, W, H); }
}
