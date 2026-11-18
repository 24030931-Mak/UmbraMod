package umbra.graphics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * [v1.1] Sistema de partículas extendido:
 * - Soporta primitivas circulares (Ellipse2D) y destellos poligonales rotatorios (Path2D + AffineTransform).
 * - Integra gravedad, fricción lineal (drag) y turbulencia sinusoidal.
 *
 * Efectos de Candil:
 *   emit         chispas al aterrizar
 *   emitVapor    estela de vapor al moverse (violeta dentro de la sombra)
 *   emitDust     polvo de luz que suelta mientras pierde vida fuera de la sombra
 *   emitCrumble  niebla y ceniza cuando se desmorona
 *
 * Efectos de Logi y Ambiente:
 *   emitSpark       chispa que sale de la llama y flota hacia arriba
 *   emitAsh         ceniza y brasas que va dejando atrás cuando se mueve
 *   emitPinPulse    [NUEVO] anillo radial de destellos al fijar/soltar a Logi
 *   emitShadowMote  [NUEVO] motas ambientales con oscilación armónica
 */
public class ParticleSystem {

    private static final int MAX_PARTICLES = 900;

    /** Geometría de la partícula. */
    private enum ShapeType { CIRCLE, SPARKLE }

    /** Geometría precalculada de un destello de 4 puntas (radio unitario = 1.0). */
    private static final Path2D SPARKLE_SHAPE = createSparkleShape();

    private static Path2D createSparkleShape() {
        Path2D p = new Path2D.Double();
        double inner = 0.25;
        p.moveTo(0, -1);
        p.lineTo(inner, -inner);
        p.lineTo(1, 0);
        p.lineTo(inner, inner);
        p.lineTo(0, 1);
        p.lineTo(-inner, inner);
        p.lineTo(-1, 0);
        p.lineTo(-inner, -inner);
        p.closePath();
        return p;
    }

    private static class Particle {
        double x, y;
        double vx, vy;
        double life, maxLife;
        double size, gravity;
        double drag = 1.0;          // factor de amortiguamiento (1.0 = sin fricción)
        double angle, angularVel;   // rotación en radianes para primitivas poligonales
        double waveAmp, waveFreq;   // turbulencia sinusoidal horizontal
        double age;
        Color color;
        boolean halo;
        ShapeType shape = ShapeType.CIRCLE;
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Random rnd = new Random();

    /** Chispas al aterrizar en el punto (x, y). */
    public void emit(double x, double y, int count) {
        for (int i = 0; i < count; i++) {
            add(x + rand(-6, 6), y - 1, rand(-120, 120), rand(-160, -40), rand(0.3, 0.6),
                    rand(1.5, 2.5), 600, new Color(200, 235, 255), true);
        }
    }

    /** Estela de vapor de Candil. */
    public void emitVapor(double x, double y, boolean inShadow) {
        Color c = inShadow ? new Color(170, 150, 235, 120) : new Color(185, 200, 228, 130);
        add(x + rand(-5, 5), y + rand(-6, 6), rand(-14, 14), rand(-26, -6), rand(0.45, 0.8),
                rand(2.5, 4), -10, c, true);
    }

    /** Polvo de luz que se desprende de Candil fuera de la sombra. */
    public void emitDust(double x, double y) {
        add(x + rand(-9, 9), y + rand(-12, 12), rand(-15, 15), rand(-45, -20), rand(0.5, 0.9),
                rand(1.2, 2.2), -20, new Color(190, 220, 245), false);
    }

    /** Candil se desmorona en 40 partículas. */
    public void emitCrumble(Rectangle2D body) {
        double eyeX = body.getCenterX(), eyeY = body.getY() + 9;
        Color eye = new Color(235, 245, 255);
        add(eyeX - 2.5, eyeY, 0, 0, 1.2, 1.6, 0, eye, true);
        add(eyeX + 2.5, eyeY, 0, 0, 1.2, 1.6, 0, eye, true);
        for (int i = 0; i < 38; i++) {
            double px = body.getX() + rnd.nextDouble() * body.getWidth();
            double py = body.getY() + rnd.nextDouble() * body.getHeight();
            if (i % 3 == 0) {
                add(px, py, rand(-60, 60), rand(-90, -20), rand(0.7, 1.15), rand(1, 1.8), 220,
                        new Color(122, 118, 120), false);
            } else {
                add(px, py, rand(-70, 70), rand(-80, 10), rand(0.6, 1.1), rand(2.5, 4.5), -40,
                        new Color(170, 186, 214, 150), true);
            }
        }
    }

    /** [NUEVO] Anillo radial de destellos poligonales al fijar o soltar a Logi. */
    public void emitPinPulse(double x, double y, boolean pinned) {
        int count = 16;
        Color c = pinned ? new Color(255, 225, 140) : new Color(170, 215, 255);
        for (int i = 0; i < count; i++) {
            if (particles.size() >= MAX_PARTICLES) return;
            double theta = (2 * Math.PI * i) / count + rand(-0.1, 0.1);
            double speed = rand(70, 130);
            Particle p = new Particle();
            p.x = x + Math.cos(theta) * 6;
            p.y = y + Math.sin(theta) * 6;
            p.vx = Math.cos(theta) * speed;
            p.vy = Math.sin(theta) * speed;
            p.life = rand(0.35, 0.55);
            p.maxLife = p.life;
            p.size = rand(3.5, 5.5);
            p.gravity = 0;
            p.drag = 0.90; // desaceleración exponencial suave
            p.angle = rand(0, Math.PI);
            p.angularVel = rand(-6, 6);
            p.color = c;
            p.halo = true;
            p.shape = ShapeType.SPARKLE;
            particles.add(p);
        }
    }

    /** [NUEVO] Mota ambiental flotante con oscilación sinusoidal horizontal. */
    public void emitShadowMote(double x, double y) {
        if (particles.size() >= MAX_PARTICLES) return;
        Particle p = new Particle();
        p.x = x;
        p.y = y;
        p.vx = rand(-6, 6);
        p.vy = rand(-18, -8);
        p.life = rand(1.8, 3.0);
        p.maxLife = p.life;
        p.size = rand(1.4, 2.4);
        p.gravity = 0;
        p.waveAmp = rand(12, 24);
        p.waveFreq = rand(2.5, 4.5);
        p.color = new Color(150, 140, 220, 140);
        p.halo = true;
        p.shape = ShapeType.CIRCLE;
        particles.add(p);
    }
    /** [NUEVO] Espora bioluminiscente que sueltan los hongos y plantas al interactuar con ellos. */
    public void emitBioSpore(double x, double y, Color color) {
        if (particles.size() >= MAX_PARTICLES) return;
        Particle p = new Particle();
        p.x = x + rand(-4, 4);
        p.y = y + rand(-3, 2);
        p.vx = rand(-14, 14);
        p.vy = rand(-32, -14);
        p.life = rand(0.9, 1.6);
        p.maxLife = p.life;
        p.size = rand(1.3, 2.3);
        p.gravity = -6;
        p.waveAmp = rand(8, 16);
        p.waveFreq = rand(3.0, 5.0);
        p.color = color;
        p.halo = true;
        p.shape = ShapeType.CIRCLE;
        particles.add(p);
    }
    public void update(double dt) {
        for (Particle p : particles) {
            p.age += dt;
            p.vy += p.gravity * dt;
            if (p.drag < 1.0) {
                double factor = Math.pow(p.drag, dt * 60.0);
                p.vx *= factor;
                p.vy *= factor;
            }
            double sway = (p.waveAmp > 0) ? Math.cos(p.age * p.waveFreq) * p.waveAmp : 0;
            p.x += (p.vx + sway) * dt;
            p.y += p.vy * dt;
            p.angle += p.angularVel * dt;
            p.life -= dt;
        }
        particles.removeIf(p -> p.life <= 0);
    }

    public void draw(Graphics2D g) {
        for (Particle p : particles) {
            double t = p.life / p.maxLife;
            // Si es mota ambiental (waveAmp > 0), hace fade-in al nacer y fade-out al morir
            double envelope = (p.waveAmp > 0) ? Math.sin(Math.PI * t) : (t * t);
            int alpha = (int) (255 * envelope);
            alpha = alpha * p.color.getAlpha() / 255;

            if (p.halo) {
                double h = p.size * 2.4;
                g.setColor(withAlpha(p.color, alpha / 4));
                g.fill(new Ellipse2D.Double(p.x - h, p.y - h, h * 2, h * 2));
            }

            double s = p.size * (0.5 + 0.5 * t);
            g.setColor(withAlpha(p.color, alpha));

            if (p.shape == ShapeType.SPARKLE) {
                AffineTransform prev = g.getTransform();
                g.translate(p.x, p.y);
                g.rotate(p.angle);
                g.scale(s, s);
                g.fill(SPARKLE_SHAPE);
                g.setTransform(prev);
            } else {
                g.fill(new Ellipse2D.Double(p.x - s, p.y - s, s * 2, s * 2));
            }
        }
    }

    public int size() {
        return particles.size();
    }

    public void clear() {
        particles.clear();
    }

    /** Chispa de Logi. */
    public void emitSpark(double x, double y) {
        add(x + rand(-6, 6), y + rand(-10, 2), rand(-18, 18), rand(-45, -20), rand(0.9, 1.5),
                rand(1.1, 1.9), -30, new Color(255, 215, 120), true);
    }

    /** Ceniza de la cola de Logi. */
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
