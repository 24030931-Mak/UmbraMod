package umbra.graphics;

import umbra.core.Config;
import umbra.math.Mat3;
import umbra.math.Vec2;

/**
 * [AVANCE 70%] Cámara 2D con desplazamiento horizontal (scroll).
 *
 * El nivel es más ancho que la ventana. La cámara guarda qué parte se ve: 'x' es la
 * coordenada del mundo que queda en el borde izquierdo de la pantalla.
 *
 *   mundo -> pantalla:  matriz de vista   V    = T(-x, 0)   (se usa para dibujar)
 *   pantalla -> mundo:  su inversa        V^-1 = T( x, 0)   (se usa para el ratón)
 *
 * Sigue al jugador con una "zona libre": mientras el jugador se vea entre LEFT y RIGHT
 * de la pantalla, la cámara no se mueve. Si avanza más allá de RIGHT, la cámara avanza
 * con él; si regresa más allá de LEFT, la cámara regresa.
 */
public class Camera {

    /** Límites de la zona libre, en píxeles de pantalla. */
    private static final double LEFT = 0.30 * Config.WIDTH;
    private static final double RIGHT = 0.50 * Config.WIDTH;

    /** Qué tan rápido alcanza su destino (mayor = más rápido). */
    private static final double SMOOTHING = 8.0;

    private final double maxX;   // lo más a la derecha que puede llegar
    private double x;

    public Camera(double worldWidth) {
        this.maxX = Math.max(0, worldWidth - Config.WIDTH);
    }

    /** Sigue al punto 'targetX' del mundo (el centro del jugador). */
    public void follow(double targetX, double dt) {
        double onScreen = targetX - x;   // dónde se ve el jugador en la pantalla
        double goal = x;
        if (onScreen > RIGHT)     goal = targetX - RIGHT;
        else if (onScreen < LEFT) goal = targetX - LEFT;
        goal = Math.max(0, Math.min(goal, maxX));   // no mostrar más allá del nivel

        // Mismo suavizado exponencial que usa la luz (LightSource.update)
        double t = 1 - Math.exp(-SMOOTHING * dt);
        x += (goal - x) * t;
    }

    /**
     * Coloca la cámara de inmediato, sin suavizado (al cambiar de nivel o reiniciar): deja al
     * jugador en el borde izquierdo de la zona libre (LEFT), x = targetX - LEFT, sin importar
     * de qué lado venía. Respeta los límites del nivel (0 .. maxX).
     */
    public void snapTo(double targetX) {
        double goal = targetX - LEFT;   // el jugador queda en el borde de la zona libre
        x = Math.max(0, Math.min(goal, maxX));
    }

    /** Matriz de vista: convierte coordenadas del mundo a coordenadas de pantalla. */
    public Mat3 getViewMatrix() {
        return Mat3.translation(-pixelX(), 0);
    }

    /** Inversa de la vista: convierte un punto de la pantalla (el ratón) al mundo. */
    public Vec2 screenToWorld(double screenX, double screenY) {
        return Mat3.translation(pixelX(), 0).apply(new Vec2(screenX, screenY));
    }

    /** Se redondea a píxeles enteros para que los bordes no "tiemblen" al desplazarse. */
    private double pixelX() {
        return Math.round(x);
    }

    public double getX() { return x; }
}
