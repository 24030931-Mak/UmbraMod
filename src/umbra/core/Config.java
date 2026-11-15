package umbra.core;

/**
 * Constantes globales del juego.
 * Si algo se siente lento, rápido o raro, normalmente se ajusta aquí.
 */
public final class Config {

    private Config() { }

    public static final String TITLE = "Umbra";
    /** Cámbiala en cada entrega: v0.3 (30%), v0.7 (70%), v1.0 (final). */
    public static final String VERSION = "v0.7";

    // ---- Ventana ----
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    // ---- Game loop ----
    /** Actualizaciones de lógica por segundo (paso de tiempo fijo). */
    public static final int UPS = 60;

    // ---- Física del jugador (píxeles y segundos) ----
    public static final double GRAVITY = 1800;        // px/s^2
    public static final double MOVE_SPEED = 260;      // px/s
    public static final double JUMP_SPEED = 620;      // px/s
    public static final double MAX_FALL_SPEED = 900;  // px/s

    // ---- Luz ----
    /** Qué tan rápido la luz alcanza al ratón (mayor = más rápido). */
    public static final double LIGHT_SMOOTHING = 8.0;
    /** Radio del halo de luz en pantalla. */
    public static final double LIGHT_RADIUS = 520;

    // ---- Avances ----
    /**
     * Puente provisional del nivel de prueba, para probar el movimiento en el avance 30%.
     * Desde el avance 70% está en false: las sombras ya son sólidas.
     */
    public static final boolean TEMP_BRIDGE = false;
}
