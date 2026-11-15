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
 *   emitTrail    rastro de luz al moverse (violeta dentro de la sombra)
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

    /** Una partícula del rastro de luz. Dentro de la sombra es violeta, como "sumergida". */
    public void emitTrail(double x, double y, boolean inShadow) {
        Color c = inShadow ? new Color(175, 140, 255) : new Color(170, 230, 255);
        add(x + rand(-4, 4), y + rand(-7, 7), rand(-12, 12), rand(-20, 4), rand(0.35, 0.55),
                rand(2.5, 4), 0, c, true);
    }

    /** Polvo de luz que se desprende de Candil y sube mientras pierde vida. */
    public void emitDust(double x, double y) {
        add(x + rand(-9, 9), y + rand(-12, 12), rand(-15, 15), rand(-45, -20), rand(0.5, 0.9),
                rand(1.2, 2.2), -20, new Color(190, 220, 245), false);
    }

    /** Candil se desmorona: fragmentos que caen y algunas brasas que suben. */
    public void emitCrumble(Rectangle2D body) {
        for (int i = 0; i < 40; i++) {
            double px = body.getX() + rnd.nextDouble() * body.getWidth();
            double py = body.getY() + rnd.nextDouble() * body.getHeight();
            boolean ember = i % 4 == 0;
            add(px, py, rand(-90, 90), ember ? rand(-90, -30) : rand(-140, 20), rand(0.6, 1.2),
                    ember ? rand(1.5, 2.5) : rand(2, 4), ember ? -40 : 520,
                    ember ? new Color(255, 225, 160) : new Color(210, 235, 255), ember);
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
