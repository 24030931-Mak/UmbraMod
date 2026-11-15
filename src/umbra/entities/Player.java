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
 * Además puede pararse sobre las sombras que proyectan los bloques (paso 5 de update).
 */
public class Player {

    public static final double W = 22;
    public static final double H = 30;
    private static final double EPS = 0.001;

    /**
     * El escalón más alto que puede subir caminando (px), sea de sombra o de un sólido.
     * También es lo más que se le sube para sacarlo de una sombra. Tiene que ser mayor
     * que lo que baja en un cuadro a velocidad máxima (MAX_FALL_SPEED / UPS = 900 / 60
     * = 15 px); si no, al caer rápido atravesaría las sombras.
     */
    private static final int MAX_STEP = 20;

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

        // 5. Sombras sólidas [AVANCE 70%]
        // Las sombras son plataformas "de un solo sentido": se atraviesan al ir subiendo
        // (saltando) o desde los lados, pero sostienen al jugador cuando no va subiendo.
        // Es vy >= 0 y no vy > 0 porque al pararse en una repisa o un bloque el paso 4
        // deja vy = 0; así también puede subir a una rampa de sombra que empieza en el suelo.
        if (vy >= 0 && inShadow(shadows, getCenterX(), getBottom())) {
            standOnShadow(shadows, solids);
        }
    }

    /**
     * Escalón: si al caminar choca con un sólido cuya cara de arriba queda a MAX_STEP px
     * o menos sobre sus pies, lo sube en lugar de detenerlo. Sin esto se atoraría al
     * pasar de una rampa de sombra a su bloque, porque la rampa nace en la esquina
     * del bloque y los pies llegan unos píxeles abajo de ella.
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

    /**
     * Saca al jugador de la sombra hacia arriba y lo deja parado justo en su borde.
     * Solo se revisa el punto de los pies: (centro, abajo).
     */
    private void standOnShadow(List<Path2D> shadows, List<Rectangle2D> solids) {
        double cx = getCenterX();
        double inside = getBottom();   // altura de los pies: está dentro de la sombra
        double outside = inside;

        // a) Subir de 1 en 1 px hasta que los pies salgan. Si después de MAX_STEP
        //    siguen adentro, el jugador está metido en la sombra, no encima de ella:
        //    no es suelo y sigue cayendo.
        for (int step = 0; inShadow(shadows, cx, outside); step++) {
            if (step == MAX_STEP) return;
            inside = outside;
            outside -= 1;
        }

        // b) El borde está entre 'outside' (afuera) e 'inside' (adentro), a 1 px o menos.
        //    Búsqueda binaria: cada vuelta parte el intervalo a la mitad (10 vueltas: ~0.001 px).
        //    Si los pies quedaran hasta 1 px arriba del borde, en el siguiente cuadro la
        //    gravedad (solo 0.5 px) no alcanzaría a meterlos otra vez: el jugador parpadearía
        //    entre "en el aire" y "en el suelo" y a veces no podría saltar.
        for (int i = 0; i < 10; i++) {
            double mid = (inside + outside) / 2;
            if (inShadow(shadows, cx, mid)) inside = mid;
            else                            outside = mid;
        }

        // c) No subirlo si la cabeza quedaría metida en una repisa o un bloque;
        //    si no, el paso 4 del siguiente cuadro lo "teletransportaría" encima de ese sólido.
        double oldY = y;
        y = outside - H;
        for (Rectangle2D r : solids) {
            if (overlaps(r)) {
                y = oldY;
                return;
            }
        }

        vy = 0;
        onGround = true;
    }

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
