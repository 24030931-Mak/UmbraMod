package umbra.ui.intro;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import umbra.graphics.Background;

/**
 * [v0.8.6] Las grietas del vidrio del Gran Farol.
 *
 * Cada grieta es una línea quebrada hecha con desplazamiento del punto medio, la misma técnica
 * de las montañas del fondo (Background.midpointDisplacement), pero puesta de lado: en vez de
 * subir o bajar alturas sobre una línea horizontal, cada punto de la recta que va del origen al
 * extremo de la grieta se mueve sobre la perpendicular a esa recta:
 *     punto_i = origen + (extremo - origen) · (i / n) + normal · d_i
 * con normal = (-dir.y, dir.x) y d_i el desplazamiento fractal (grande en los tramos largos y
 * cada vez más chico en los detalles). Al desplazamiento se le resta la recta que une sus dos
 * extremos para que valga cero en el origen y en el extremo: la grieta sale y llega justo donde
 * se pidió.
 *
 * De cada grieta principal salen ramas más cortas, giradas entre 30° y 60°.
 *
 * Las grietas "avanzan": draw recibe un progreso de 0 a 1 y dibuja de cada una solo el tramo
 * que ya se abrió. Para eso se guarda el largo acumulado hasta cada punto y se corta la línea
 * donde ese largo alcanza al avance (interpolando dentro del último tramo). Una rama empieza
 * cuando la grieta principal llega al punto del que sale.
 */
public final class Cracks {

    private static final int SEGMENTS = 16;
    private static final Color GLOW = new Color(255, 244, 196, 80);
    private static final Color CORE = new Color(255, 254, 240, 235);
    private static final Stroke GLOW_STROKE = new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke CORE_STROKE = new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke DARK_STROKE = new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    /** Una grieta: sus puntos, el largo acumulado hasta cada uno y cuándo se abre. */
    private static final class Crack {
        final double[] xs, ys, length;
        final double start, span;   // empieza a abrirse en 'start' y termina en 'start + span' (del progreso)

        Crack(double[] xs, double[] ys, double start, double span) {
            this.xs = xs;
            this.ys = ys;
            this.start = start;
            this.span = span;
            length = new double[xs.length];
            for (int i = 1; i < xs.length; i++) {
                length[i] = length[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
            }
        }
    }

    private final List<Crack> cracks = new ArrayList<>();

    /**
     * @param ox,oy   de dónde salen todas las grietas
     * @param targets a dónde llega cada grieta principal: { x, y } por cada una
     */
    public Cracks(long seed, double ox, double oy, double[][] targets) {
        Random rnd = new Random(seed);
        for (int i = 0; i < targets.length; i++) {
            double start = 0.62 * i / targets.length + rnd.nextDouble() * 0.04;
            Crack main = build(rnd, ox, oy, targets[i][0], targets[i][1], SEGMENTS, start, 0.26 + rnd.nextDouble() * 0.1);
            cracks.add(main);
            int branches = 1 + rnd.nextInt(2);
            for (int b = 0; b < branches; b++) {
                int k = 4 + rnd.nextInt(8);   // de qué punto de la principal sale la rama
                double dx = targets[i][0] - ox, dy = targets[i][1] - oy;
                double turn = Math.toRadians(30 + rnd.nextDouble() * 30) * (rnd.nextBoolean() ? 1 : -1);
                double len = 0.3 + rnd.nextDouble() * 0.25;
                // La dirección de la rama es la de la principal girada 'turn' (matriz de rotación)
                double bx = (dx * Math.cos(turn) - dy * Math.sin(turn)) * len;
                double by = (dx * Math.sin(turn) + dy * Math.cos(turn)) * len;
                cracks.add(build(rnd, main.xs[k], main.ys[k], main.xs[k] + bx, main.ys[k] + by, SEGMENTS / 2,
                        main.start + main.span * k / SEGMENTS, 0.14));
            }
        }
    }

    private static Crack build(Random rnd, double x0, double y0, double x1, double y1, int n,
                               double start, double span) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        double nx = -(y1 - y0) / len, ny = (x1 - x0) / len;   // la normal: la dirección girada 90°
        double[] d = Background.midpointDisplacement(rnd, n, 0.09 * len, 0.55);
        double[] xs = new double[n + 1], ys = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double u = (double) i / n;
            double off = d[i] - Ease.lerp(d[0], d[n], u);   // cero en los dos extremos
            xs[i] = Ease.lerp(x0, x1, u) + nx * off;
            ys[i] = Ease.lerp(y0, y1, u) + ny * off;
        }
        return new Crack(xs, ys, start, span);
    }

    /**
     * Dibuja lo que ya se abrió de cada grieta. 'light' (0 a 1) dice si por la grieta sale luz
     * (un trazo ancho y tenue con un núcleo casi blanco) o si ya es solo una raya oscura en el
     * vidrio apagado.
     */
    public void draw(Graphics2D g, double progress, double light) {
        if (progress <= 0) return;
        Stroke old = g.getStroke();
        for (Crack c : cracks) {
            double open = Ease.clamp01((progress - c.start) / c.span);
            if (open <= 0) continue;
            Path2D path = partial(c, open * c.length[c.length.length - 1]);
            if (light > 0.02) {
                g.setStroke(GLOW_STROKE);
                g.setColor(Ease.fade(GLOW, light));
                g.draw(path);
                g.setStroke(CORE_STROKE);
                g.setColor(Ease.fade(CORE, light));
                g.draw(path);
            }
            if (light < 0.98) {
                g.setStroke(DARK_STROKE);
                g.setColor(new Color(10, 12, 24, (int) Math.round(170 * (1 - light))));
                g.draw(path);
            }
        }
        g.setStroke(old);
    }

    /** La grieta desde su origen hasta donde su largo acumulado llega a 'reach'. */
    private static Path2D partial(Crack c, double reach) {
        Path2D p = new Path2D.Double();
        p.moveTo(c.xs[0], c.ys[0]);
        for (int i = 1; i < c.xs.length; i++) {
            if (c.length[i] <= reach) {
                p.lineTo(c.xs[i], c.ys[i]);
            } else {
                double u = (reach - c.length[i - 1]) / (c.length[i] - c.length[i - 1]);   // dentro del tramo
                p.lineTo(Ease.lerp(c.xs[i - 1], c.xs[i], u), Ease.lerp(c.ys[i - 1], c.ys[i], u));
                break;
            }
        }
        return p;
    }
}
