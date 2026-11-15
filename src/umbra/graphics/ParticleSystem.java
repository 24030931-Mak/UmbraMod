package umbra.graphics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * [v1.0] Sistema de partículas: muchas partículas pequeñas, cada una con posición, velocidad
 * y tiempo de vida. En cada cuadro se mueven (con su propia gravedad), pierden vida y se
 * desvanecen: su transparencia depende de life / maxLife. Las que llegan a 0 se quitan.
 *
 * Efectos de Candil:
 *   emit         chispas al aterrizar
 *   emitVapor    estela de vapor al moverse (violeta dentro de la sombra)
 *   emitDust     polvo de luz que suelta mientras pierde vida fuera de la sombra
 *   emitCrumble  fragmentos cuando se desmorona
 */
public class ParticleSystem {

    /** Tope para que nunca se acumulen demasiadas. */
    private static final int MAX_PARTICLES = 800;

    /** Una partícula: posición, velocidad y vida restante (en segundos). */
    private static class Particle {
        double x, y;
        double vx, vy;
        double life, maxLife;
        double size, gravity;
        Color color;
        boolean halo;   // con un halo suave alrededor
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Random rnd = new Random();

    /** Chispas al aterrizar en el punto (x, y): saltan hacia arriba y a los lados. */
    public void emit(double x, double y, int count) {
        for (int i = 0; i < count; i++) {
            add(x + rand(-6, 6), y - 1, rand(-120, 120), rand(-160, -40), rand(0.3, 0.6),
                    rand(1.5, 2.5), 600, new Color(200, 235, 255), true);
        }
    }

    /**
     * Una bocanada de la estela de vapor de Candil: niebla translúcida (el alfa del color es su
     * opacidad máxima) que sube despacio y se deshace. Dentro de la sombra es violeta, como
     * "sumergida".
     */
    public void emitVapor(double x, double y, boolean inShadow) {
        Color c = inShadow ? new Color(170, 150, 235, 120) : new Color(185, 200, 228, 130);
        add(x + rand(-5, 5), y + rand(-6, 6), rand(-14, 14), rand(-26, -6), rand(0.45, 0.8),
                rand(2.5, 4), -10, c, true);
    }

    /** Polvo de luz que se desprende de Candil y sube mientras pierde vida. */
    public void emitDust(double x, double y) {
        add(x + rand(-9, 9), y + rand(-12, 12), rand(-15, 15), rand(-45, -20), rand(0.5, 0.9),
                rand(1.2, 2.2), -20, new Color(190, 220, 245), false);
    }

    /**
     * Candil se desmorona en 40 partículas: se deshace en niebla (bocanadas translúcidas que
     * suben y se dispersan) y ceniza (copos grises que caen despacio). Sus dos ojos se quedan
     * quietos donde estaban y son lo último en apagarse.
     */
    public void emitCrumble(Rectangle2D body) {
        double eyeX = body.getCenterX(), eyeY = body.getY() + 9;
        Color eye = new Color(235, 245, 255);
        add(eyeX - 2.5, eyeY, 0, 0, 1.2, 1.6, 0, eye, true);
        add(eyeX + 2.5, eyeY, 0, 0, 1.2, 1.6, 0, eye, true);
        for (int i = 0; i < 38; i++) {
            double px = body.getX() + rnd.nextDouble() * body.getWidth();
            double py = body.getY() + rnd.nextDouble() * body.getHeight();
            if (i % 3 == 0) {   // ceniza
                add(px, py, rand(-60, 60), rand(-90, -20), rand(0.7, 1.15), rand(1, 1.8), 220,
                        new Color(122, 118, 120), false);
            } else {            // niebla
                add(px, py, rand(-70, 70), rand(-80, 10), rand(0.6, 1.1), rand(2.5, 4.5), -40,
                        new Color(170, 186, 214, 150), true);
            }
        }
    }

    public void update(double dt) {
        for (Particle p : particles) {
            p.vy += p.gravity * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.life -= dt;
        }
        particles.removeIf(p -> p.life <= 0);
    }

    public void draw(Graphics2D g) {
        for (Particle p : particles) {
            double t = p.life / p.maxLife;   // 1 al nacer, 0 al morir
            int alpha = (int) (255 * t * t); // se desvanece, más rápido al final
            alpha = alpha * p.color.getAlpha() / 255;   // [v1.0] el alfa del color es su opacidad máxima (el vapor)
            if (p.halo) {
                double h = p.size * 2.4;
                g.setColor(withAlpha(p.color, alpha / 4));
                g.fill(new Ellipse2D.Double(p.x - h, p.y - h, h * 2, h * 2));
            }
            double s = p.size * (0.5 + 0.5 * t);   // y se encoge
            g.setColor(withAlpha(p.color, alpha));
            g.fill(new Ellipse2D.Double(p.x - s, p.y - s, s * 2, s * 2));
        }
    }

    /** Cuántas partículas hay vivas (para el modo debug). */
    public int size() {
        return particles.size();
    }

    /** Quita todas (al empezar el nivel de nuevo). */
    public void clear() {
        particles.clear();
    }

    /**
     * [v1.0] Una chispa de Logi: sale de la llama y flota hacia arriba (la gravedad negativa la
     * acelera un poco hacia arriba) mientras se desvanece y se apaga.
     */
    public void emitSpark(double x, double y) {
        add(x + rand(-6, 6), y + rand(-10, 2), rand(-18, 18), rand(-45, -20), rand(0.9, 1.5),
                rand(1.1, 1.9), -30, new Color(255, 215, 120), true);
    }

    /**
     * [v1.0] Ceniza de la cola de Logi. (vx, vy) es la velocidad de Logi: la ceniza sale detrás de
     * ella y se va quedando atrás, despacio. Con d = v / |v| (hacia donde va) y n = (-d.y, d.x)
     * (perpendicular a d), sale en
     *     p = (x, y) - d·(4 a 24) + n·(-5 a 5)     con velocidad     -d·(30 a 90) + n·(-30 a 30).
     * Al azar, una de cada tres es una brasa naranja que brilla y flota; las demás, ceniza gris que
     * cae despacio.
     */
    public void emitAsh(double x, double y, double vx, double vy) {
        double speed = Math.hypot(vx, vy);
        double dx = speed > 0 ? vx / speed : 0, dy = speed > 0 ? vy / speed : 0;
        double nx = -dy, ny = dx;
        double back = rand(4, 24), side = rand(-5, 5);
        double drift = rand(30, 90), spread = rand(-30, 30);
        boolean ember = rnd.nextInt(3) == 0;
        add(x - dx * back + nx * side, y - dy * back + ny * side,
                -dx * drift + nx * spread, -dy * drift + ny * spread, rand(0.5, 0.9),
                ember ? rand(1.2, 2) : rand(1.4, 2.4), ember ? -15 : 50,
                ember ? new Color(255, 150, 60) : new Color(125, 110, 100), ember);
    }

    private void add(double x, double y, double vx, double vy, double life, double size,
                     double gravity, Color color, boolean halo) {
        if (particles.size() >= MAX_PARTICLES) return;
        Particle p = new Particle();
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.life = life;
        p.maxLife = life;
        p.size = size;
        p.gravity = gravity;
        p.color = color;
        p.halo = halo;
        particles.add(p);
    }

    private double rand(double min, double max) {
        return min + rnd.nextDouble() * (max - min);
    }

    private static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }
}
