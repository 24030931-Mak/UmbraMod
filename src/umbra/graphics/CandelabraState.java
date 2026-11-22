package umbra.graphics;

/**
 * [v0.9.5] El estado del Gran Farol, el candelabro de 6 brazos que dibuja CandelabraArt. Es
 * mutable y se reutiliza: cada zona con candelabro guarda uno solo, y quien lo anima (el juego o
 * la animación final) cambia sus números en vez de crear otro en cada cuadro.
 *
 * Los brazos se numeran 0 a 2 a la izquierda y 3 a 5 a la derecha; en cada lado, 0 (o 3) es el
 * de arriba, el más corto, y 2 (o 5) el de abajo, el más largo (ver CandelabraArt).
 *
 * El orden fijo en que los fragmentos encienden los brazos alterna lados y va de arriba abajo:
 * 0, 3, 1, 4, 2, 5 (el de arriba a la izquierda, el de arriba a la derecha, los de en medio y los
 * de abajo). Así, con cualquier cantidad, los dos lados quedan parejos o con uno de diferencia,
 * como en los bocetos.
 */
public final class CandelabraState {

    /** Cuántos brazos tiene el candelabro (uno por fragmento). */
    public static final int ARMS = 6;

    /** El orden en que se encienden los brazos (ver el comentario de la clase). */
    private static final int[] LIGHT_ORDER = { 0, 3, 1, 4, 2, 5 };

    /** Cuánto arde cada brazo: 0 apagado (copa vacía), 1 encendido (con su fragmento). */
    public final double[] armLit = new double[ARMS];

    /** Qué tan cerrado está cada brazo sobre el corazón de vidrio: 0 abierto, 1 cerrado. */
    public final double[] armClosed = new double[ARMS];

    /** Qué tan tibio está el vidrio del corazón: 0 frío y oscuro, 1 encendido. */
    public double glassWarm;

    /** Desplazamiento en x de todo el candelabro, en px (los golpes de la animación final). */
    public double shake;

    /** Qué tanto se ven las grietas del vidrio: 0 nada, 1 todas. */
    public double cracks;

    /** El brazo que se enciende en el lugar 'i' (0 a 5) del orden fijo. */
    public static int lightOrder(int i) {
        return LIGHT_ORDER[i];
    }

    /**
     * Cómo se ve el candelabro en el juego con 'n' fragmentos juntados (de 0 a 6; lo de fuera se
     * recorta): enciende los n primeros brazos del orden fijo y apaga los demás, deja todos los
     * brazos abiertos y pone glassWarm = n / 6. No toca 'shake' ni 'cracks' (son de la animación).
     */
    public void setFragments(int n) {
        n = Math.max(0, Math.min(ARMS, n));
        for (int i = 0; i < ARMS; i++) {
            armLit[LIGHT_ORDER[i]] = i < n ? 1 : 0;
            armClosed[i] = 0;
        }
        glassWarm = n / (double) ARMS;
    }
}
