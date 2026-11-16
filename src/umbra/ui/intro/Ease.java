package umbra.ui.intro;

import java.awt.Color;

/**
 * [v0.8.6] Interpolación para la intro: las curvas con que cambia un valor a lo largo del tiempo.
 *
 * Toda la animación de la intro sale de lo mismo: un tiempo t se convierte en un avance u entre
 * 0 y 1 (ramp), ese avance se suaviza con una curva (smooth, in, out) y con él se mezcla entre
 * dos valores (lerp):
 *     valor = a + (b - a) * curva(u)
 */
public final class Ease {

    private Ease() { }

    public static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** Interpolación lineal: t = 0 regresa a, t = 1 regresa b. */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Avance lineal de 0 (en t0) a 1 (en t1), sin salirse de ese rango. */
    public static double ramp(double t, double t0, double t1) {
        return clamp01((t - t0) / (t1 - t0));
    }

    /**
     * Curva suave 3u² - 2u³ (smoothstep): arranca y termina con velocidad cero, porque su
     * derivada 6u(1 - u) vale 0 en u = 0 y en u = 1. Así nada arranca ni frena de golpe.
     */
    public static double smooth(double u) {
        u = clamp01(u);
        return u * u * (3 - 2 * u);
    }

    /** ramp y smooth juntas: de 0 a 1 entre t0 y t1, arrancando y frenando despacio. */
    public static double smooth(double t, double t0, double t1) {
        return smooth(ramp(t, t0, t1));
    }

    /** Arranca despacio y termina rápido: u³. */
    public static double in(double u) {
        u = clamp01(u);
        return u * u * u;
    }

    /** Arranca rápido y frena al final: 1 - (1 - u)³. */
    public static double out(double u) {
        u = 1 - clamp01(u);
        return 1 - u * u * u;
    }

    /**
     * Una "ventana": sube de 0 a 1 entre in0 e in1, se queda en 1 y baja a 0 entre out0 y out1.
     * Es el mínimo de dos rampas; sirve para lo que aparece y luego se desvanece (el texto).
     */
    public static double window(double t, double in0, double in1, double out0, double out1) {
        return Math.min(smooth(t, in0, in1), 1 - smooth(t, out0, out1));
    }

    /** Interpolación lineal entre dos colores, canal por canal (también la transparencia). */
    public static Color mix(Color a, Color b, double t) {
        t = clamp01(t);
        return new Color(
                (int) Math.round(lerp(a.getRed(), b.getRed(), t)),
                (int) Math.round(lerp(a.getGreen(), b.getGreen(), t)),
                (int) Math.round(lerp(a.getBlue(), b.getBlue(), t)),
                (int) Math.round(lerp(a.getAlpha(), b.getAlpha(), t)));
    }

    /** El mismo color con su transparencia multiplicada por alpha (0 a 1). */
    public static Color fade(Color c, double alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(c.getAlpha() * clamp01(alpha)));
    }
}
