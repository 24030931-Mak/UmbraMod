package umbra.graphics;

import umbra.core.Config;
import umbra.math.Mat3;
import umbra.math.Vec2;

/**
 * [AVANCE 70%] Cámara 2D con desplazamiento horizontal y vertical (scroll).
 *
 * El nivel puede ser más ancho y más alto que la ventana. La cámara guarda qué parte se ve:
 * 'x' e 'y' son las coordenadas del mundo que quedan en la esquina superior izquierda.
 *
 *   mundo -> pantalla:  matriz de vista   V    = T(-x, -y)   (se usa para dibujar)
 *   pantalla -> mundo:  su inversa        V^-1 = T( x,  y)   (se usa para el ratón)
 *
 * Sigue al jugador con una "zona libre": mientras el jugador se vea dentro de ella, la cámara
 * no se mueve. En horizontal va de LEFT a RIGHT de la pantalla; en vertical, de TOP a BOTTOM.
 * Si sale por un lado, la cámara lo acompaña. Al caer rápido se adelanta hacia abajo para
 * mostrar lo que viene (FALL_LEAD).
 */
public class Camera {

    /** Límites de la zona libre horizontal, en píxeles de pantalla. */
    private static final double LEFT = 0.30 * Config.WIDTH;
    private static final double RIGHT = 0.50 * Config.WIDTH;

    /** Límites de la zona libre vertical: entre el 35 % y el 60 % del alto de la pantalla. */
    private static final double TOP = 0.35 * Config.HEIGHT;
    private static final double BOTTOM = 0.60 * Config.HEIGHT;

    /** Qué tan rápido alcanza su destino (mayor = más rápido). */
    private static final double SMOOTHING = 8.0;

    /** Por encima de esta velocidad de caída (px/s) la cámara se adelanta; a MAX_FALL llega al máximo. */
    private static final double FALL_START = 400;
    private static final double FALL_FULL = 900;
    /** Cuánto se adelanta hacia abajo (px) a la velocidad máxima de caída. */
    private static final double FALL_LEAD = 110;

    private final double maxX;   // lo más a la derecha que puede llegar
    private final double maxY;   // lo más abajo que puede llegar
    private double x, y;
    private double lastTargetY = Double.NaN;   // para estimar la velocidad de caída

    public Camera(double worldWidth, double worldHeight) {
        this.maxX = Math.max(0, worldWidth - Config.WIDTH);
        this.maxY = Math.max(0, worldHeight - Config.HEIGHT);
    }

    /** Sigue al punto (targetX, targetY) del mundo (el centro del jugador). */
    public void follow(double targetX, double targetY, double dt) {
        double onScreen = targetX - x;   // dónde se ve el jugador en la pantalla
        double goal = x;
        if (onScreen > RIGHT)     goal = targetX - RIGHT;
        else if (onScreen < LEFT) goal = targetX - LEFT;
        goal = Math.max(0, Math.min(goal, maxX));   // no mostrar más allá del nivel

        // Vertical: lo mismo con su zona libre. Si cae rápido, la cámara se adelanta hacia
        // abajo (la velocidad se estima con el cambio de Y entre dos cuadros).
        double fall = Double.isNaN(lastTargetY) || dt <= 0 ? 0 : (targetY - lastTargetY) / dt;
        lastTargetY = targetY;
        double lead = FALL_LEAD * Math.max(0, Math.min(1, (fall - FALL_START) / (FALL_FULL - FALL_START)));
        double onScreenY = targetY - y;
        double goalY = y;
        if (onScreenY > BOTTOM)   goalY = targetY - BOTTOM;
        else if (onScreenY < TOP) goalY = targetY - TOP;
        if (lead > 0) goalY = Math.max(goalY, targetY - BOTTOM + lead);
        goalY = Math.max(0, Math.min(goalY, maxY));

        // Mismo suavizado exponencial que usa la luz (LightSource.update)
        double t = 1 - Math.exp(-SMOOTHING * dt);
        x += (goal - x) * t;
        y += (goalY - y) * t;
    }

    /**
     * Coloca la cámara de inmediato, sin suavizado (al cambiar de nivel o reiniciar): en X deja
     * al jugador en el borde izquierdo de la zona libre (LEFT), x = targetX - LEFT; en Y, en el
     * centro de la zona libre vertical. Respeta los límites del nivel.
     */
    public void snapTo(double targetX, double targetY) {
        x = Math.max(0, Math.min(targetX - LEFT, maxX));
        y = Math.max(0, Math.min(targetY - (TOP + BOTTOM) / 2, maxY));
        lastTargetY = targetY;
    }

    /** Matriz de vista: convierte coordenadas del mundo a coordenadas de pantalla. */
    public Mat3 getViewMatrix() {
        return Mat3.translation(-pixelX(), -pixelY());
    }

    /** Inversa de la vista: convierte un punto de la pantalla (el ratón) al mundo. */
    public Vec2 screenToWorld(double screenX, double screenY) {
        return Mat3.translation(pixelX(), pixelY()).apply(new Vec2(screenX, screenY));
    }

    /** Se redondea a píxeles enteros para que los bordes no "tiemblen" al desplazarse. */
    private double pixelX() {
        return Math.round(x);
    }

    private double pixelY() {
        return Math.round(y);
    }

    public double getX() { return x; }
    public double getY() { return y; }
}
