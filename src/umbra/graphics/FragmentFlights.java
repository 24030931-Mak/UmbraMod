package umbra.graphics;

import java.awt.Graphics2D;

/**
 * [B4] El vuelo de un fragmento hacia Candil al tomarlo: dura DURATION s, el cristal se encoge
 * mientras va hacia el centro de Candil, deja chispas detrás y al llegar suelta una chispa breve
 * sobre él. Es solo visual: el fragmento ya contó en el cuadro en que se tocó.
 *
 * Sigue a Candil si éste se mueve (el destino se pide en cada cuadro). Hay pocas ranuras fijas
 * (nunca hay más de dos fragmentos a la vez en pantalla), así que no se crea nada en cada cuadro.
 * Si no hay ranura libre, el vuelo se omite sin más.
 */
public final class FragmentFlights {

    /** Segundos que dura el vuelo. */
    public static final double DURATION = 0.25;
    private static final int SLOTS = 4;
    /** Escala del cristal al salir (1 = tamaño en el mundo) y al llegar. */
    private static final double SCALE_FROM = 1.0, SCALE_TO = 0.2;

    private final boolean[] active = new boolean[SLOTS];
    private final double[] fromX = new double[SLOTS], fromY = new double[SLOTS];
    private final double[] time = new double[SLOTS];

    /** Empieza un vuelo desde (x, y) del mundo. */
    public void start(double x, double y) {
        for (int i = 0; i < SLOTS; i++) {
            if (active[i]) continue;
            active[i] = true;
            fromX[i] = x;
            fromY[i] = y;
            time[i] = 0;
            return;
        }
    }

    /** Cuántos vuelos hay en curso. */
    public int count() {
        int n = 0;
        for (boolean a : active) if (a) n++;
        return n;
    }

    /** Los quita todos de golpe (Candil murió, o se reinició el nivel o la partida, o se cambió de zona). */
    public void clear() {
        for (int i = 0; i < SLOTS; i++) active[i] = false;
    }

    /** Avanza los vuelos hacia (targetX, targetY), el centro de Candil. Deja chispas y, al llegar, una chispa breve. */
    public void update(double dt, double targetX, double targetY, ParticleSystem particles) {
        for (int i = 0; i < SLOTS; i++) {
            if (!active[i]) continue;
            time[i] += dt;
            if (time[i] >= DURATION) {
                active[i] = false;
                particles.emitFragmentArrive(targetX, targetY);
            } else {
                particles.emitFragmentTrail(x(i, targetX), y(i, targetY));
            }
        }
    }

    /** Progreso (empieza despacio y acelera hacia Candil). */
    private double progress(int i) {
        double t = Math.min(1, time[i] / DURATION);
        return t * t;
    }

    private double x(int i, double targetX) { return fromX[i] + (targetX - fromX[i]) * progress(i); }
    private double y(int i, double targetY) { return fromY[i] + (targetY - fromY[i]) * progress(i); }

    /** Dibuja los cristales en vuelo (coordenadas del mundo). */
    public void draw(Graphics2D g, double targetX, double targetY) {
        for (int i = 0; i < SLOTS; i++) {
            if (!active[i]) continue;
            double t = Math.min(1, time[i] / DURATION);
            FragmentArt.drawIcon(g, x(i, targetX), y(i, targetY), SCALE_FROM + (SCALE_TO - SCALE_FROM) * t);
        }
    }
}
