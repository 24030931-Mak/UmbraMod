package umbra.graphics;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * [ENTREGA FINAL] Sistema de partículas: chispas al aterrizar y rastro de luz del jugador.
 * Ya está conectado al GamePanel (emit, update y draw); solo falta llenarlo.
 */
public class ParticleSystem {

    /** Una partícula: posición, velocidad y vida restante (en segundos). */
    private static class Particle {
        double x, y;
        double vx, vy;
        double life, maxLife;
    }

    private final List<Particle> particles = new ArrayList<>();

    /** Crea 'count' partículas en el punto (x, y). */
    public void emit(double x, double y, int count) {
        // TODO (v1.0): crear 'count' partículas con velocidad aleatoria
        //   (hacia arriba y a los lados) y una vida corta (0.3 a 0.6 s).
    }

    public void update(double dt) {
        // TODO (v1.0): mover cada partícula (x += vx * dt ...), aplicarle un poco de
        //   gravedad, restarle vida y quitar las que lleguen a 0 (particles.removeIf(...)).
    }

    public void draw(Graphics2D g) {
        // TODO (v1.0): dibujar cada partícula como un círculo pequeño cuya transparencia
        //   (alfa) dependa de life / maxLife, para que se desvanezca al morir.
    }
}
