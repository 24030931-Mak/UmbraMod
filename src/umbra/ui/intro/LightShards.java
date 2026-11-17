package umbra.ui.intro;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import umbra.math.Mat3;
import umbra.math.Vec2;

/**
 * [v0.8.6] Los pedazos de luz que salen volando cuando el Gran Farol se rompe: partículas con
 * gravedad, como las del juego (graphics.ParticleSystem), pero con destino.
 *
 * Tiro parabólico. Un pedazo que sale de p0 con velocidad v0 bajo una gravedad g está, s
 * segundos después, en
 *     p(s) = p0 + v0·s + ½·g·s²          (g solo jala hacia abajo: g = (0, GRAVITY))
 * Aquí se sabe a dónde debe caer (la boca de un túnel o un pozo) y cuánto debe tardar (T), así
 * que se despeja la velocidad inicial de p(T) = destino:
 *     v0 = (destino - p0) / T - ½·g·T
 * Con T grande el pedazo sale más hacia arriba y hace un arco alto; con T chico va casi derecho.
 *
 * Como la posición es una fórmula del tiempo (no se va sumando cuadro a cuadro), la estela sale
 * gratis: son las posiciones de unos instantes atrás, p(s - 0.03), p(s - 0.06)..., unidas con
 * rayas cada vez más delgadas y transparentes. Y el mismo cuadro se puede dibujar dos veces
 * sin que nada cambie.
 *
 * Además de los pedazos hay chispas: salen parejo en todas direcciones, la gravedad las jala
 * igual y se apagan en menos de un segundo.
 */
public final class LightShards {

    /** Gravedad de los pedazos (px/s²): más suave que la del juego, para que el vuelo se alcance a ver. */
    public static final double GRAVITY = 300;

    private static final int TRAIL = 5;                // puntos de la estela
    private static final double TRAIL_STEP = 0.046;    // segundos entre un punto de la estela y el siguiente
    /** Los trazos de la estela, del más grueso al más delgado (se multiplican por el tamaño al dibujar). */
    private static final float[] TRAIL_WIDTH = { 2.3f, 1.9f, 1.5f, 1.1f, 0.7f };
    /** [v0.8.7] Segundos que dura el rebote tras el impacto. */
    private static final double BOUNCE_LIFE = 0.45;
    /** [v0.8.7] Segundos que dura el destello puntual al caer. */
    private static final double FLASH = 0.22;
    private static final double VANISH = 0.25;         // segundos que tarda en apagarse al llegar
    private static final Color LIGHT = new Color(255, 232, 160);
    private static final Color WHITE = new Color(255, 252, 236);

    /** Un pedazo: de dónde sale, con qué velocidad, cuándo y cuánto tarda en llegar. */
    private static final class Shard {
        double x0, y0, vx, vy, start, flight, size;
        /** Punto de impacto y rebote amortiguado: sale de (tx, ty) y vuelve a caer en ty. */
        double tx, ty, bvx, bvy;
    }

    /** Una chispa: sale del mismo punto, sin destino, y vive poco. */
    private static final class Spark {
        double x0, y0, vx, vy, start, life, size;
    }

    private final List<Shard> shards = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final Random rnd;
    private final Glow glow = new Glow(64, new Color(255, 214, 120), 1.5, 0.55);

    public LightShards(long seed) {
        rnd = new Random(seed);
    }

    public void clear() {
        shards.clear();
        sparks.clear();
    }

    /**
     * Lanza un pedazo desde (x0, y0) en el segundo 'start' para que llegue a (tx, ty) en
     * 'flight' segundos: v0 = (destino - p0) / T - ½·g·T.
     */
    public void launch(double x0, double y0, double tx, double ty, double start, double flight, double size) {
        Shard s = new Shard();
        s.x0 = x0;
        s.y0 = y0;
        s.vx = (tx - x0) / flight;
        s.vy = (ty - y0) / flight - 0.5 * GRAVITY * flight;
        s.tx = tx;
        s.ty = ty;
        // Rebote: v_impacto = vy0 + g·T; sale con 0.4·vx0 y -0.32·v_impacto. La altura se limita
        // para que la segunda caída (2·|vy|/g) termine en ty antes de BOUNCE_LIFE, sin salto.
        double vyImpact = Math.max(0, s.vy + GRAVITY * flight);
        s.bvx = 0.4 * s.vx;
        s.bvy = -Math.min(0.32 * vyImpact, 0.5 * GRAVITY * BOUNCE_LIFE);
        s.start = start;
        s.flight = flight;
        s.size = size;
        shards.add(s);
    }

    /**
     * El estallido: 'count' pedazos desde (x, y), repartidos entre los destinos (cada uno cae
     * cerca de uno, no justo en el centro), que salen a lo largo de 0.3 s y tardan entre 1.3 y
     * 3.4 s en llegar; y el doble de chispas.
     */
    public void burst(double x, double y, Vec2[] targets, int count, double start) {
        for (int i = 0; i < count; i++) {
            Vec2 target = targets[i % targets.length];
            launch(x + rand(-22, 22), y + rand(-30, 30), target.x + rand(-26, 26), target.y + rand(-12, 12),
                    start + rnd.nextDouble() * 0.3, rand(1.3, 3.4), rand(0.7, 1.5));
        }
        for (int i = 0; i < count * 2; i++) {
            Spark s = new Spark();
            double a = rnd.nextDouble() * Math.PI * 2, speed = rand(120, 520);
            s.x0 = x + rand(-18, 18);
            s.y0 = y + rand(-26, 26);
            s.vx = Math.cos(a) * speed;
            s.vy = Math.sin(a) * speed - 60;
            s.start = start + rnd.nextDouble() * 0.12;
            s.life = rand(0.35, 1.0);
            s.size = rand(0.8, 1.9);
            sparks.add(s);
        }
    }

    /** Qué parte de los pedazos ya llegó a su destino en el segundo t (de 0 a 1). */
    public double landed(double t) {
        if (shards.isEmpty()) return 0;
        int n = 0;
        for (Shard s : shards) {
            if (t >= s.start + s.flight) n++;
        }
        return (double) n / shards.size();
    }

    /**
     * Dibuja los pedazos como están en el segundo t. Se dibujan en píxeles de pantalla: cada
     * posición del mundo se pasa por la matriz de vista, y los tamaños se multiplican por el
     * acercamiento de la cámara.
     */
    public void draw(Graphics2D g, Mat3 view, double zoom, double t) {
        Stroke old = g.getStroke();
        for (Spark s : sparks) {
            double age = t - s.start;
            if (age < 0 || age > s.life) continue;
            double fade = 1 - age / s.life;
            // Las chispas se frenan con el aire: avanzan según 1 - e^(-3·edad) en vez de la edad
            double run = (1 - Math.exp(-3 * age)) / 3;
            Vec2 p = view.apply(new Vec2(s.x0 + s.vx * run, s.y0 + s.vy * run + 0.5 * GRAVITY * age * age));
            double r = s.size * zoom * (0.5 + 0.5 * fade);
            g.setColor(Ease.fade(WHITE, fade * fade));
            g.fill(new Ellipse2D.Double(p.x - r, p.y - r, 2 * r, 2 * r));
        }
        for (Shard s : shards) {
            double age = t - s.start;
            double rest = s.flight + BOUNCE_LIFE;   // desde aquí se queda quieto en (tx, ty) y se apaga
            if (age < 0 || age > rest + VANISH) continue;
            double alpha = age <= rest ? Math.min(1, age / 0.08) : 1 - (age - rest) / VANISH;
            Vec2 head = view.apply(position(s, age));

            // Estela: posiciones de instantes anteriores, cada vez más delgada y transparente
            Vec2 from = head;
            for (int k = 1; k <= TRAIL; k++) {
                double past = age - k * TRAIL_STEP;
                if (past < 0) break;
                Vec2 to = view.apply(position(s, past));
                double f = 1 - (double) k / (TRAIL + 1);
                g.setStroke(new BasicStroke((float) (TRAIL_WIDTH[k - 1] * s.size * zoom), BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
                g.setColor(Ease.fade(LIGHT, alpha * 0.75 * f * f));
                g.draw(new Line2D.Double(from.x, from.y, to.x, to.y));
                from = to;
            }
            // El destello puntual al caer: ilumina la boca donde llega
            double hit = age - s.flight;
            if (hit >= 0 && hit < FLASH) {
                double k = 1 - hit / FLASH;
                Vec2 tp = view.apply(new Vec2(s.tx, s.ty));
                glow.draw(g, tp.x, tp.y, 34 * s.size * zoom * (0.5 + 0.5 * k));
            }
            // La cabeza: una mancha de brillo y un centro casi blanco
            glow.draw(g, head.x, head.y, 15 * s.size * zoom * (0.6 + 0.4 * alpha));
            double r = 2.1 * s.size * zoom;
            g.setColor(Ease.fade(WHITE, alpha));
            g.fill(new Ellipse2D.Double(head.x - r, head.y - r, 2 * r, 2 * r));
        }
        g.setStroke(old);
    }

    /** p(s) = p0 + v0·s + ½·g·s² */
    private static Vec2 position(Shard s, double age) {
        if (age > s.flight) {
            // Rebote amortiguado desde el punto de impacto; al caer otra vez se queda en (tx, ty)
            double b = age - s.flight;
            double y = s.ty + s.bvy * b + 0.5 * GRAVITY * b * b;
            return new Vec2(s.tx + s.bvx * b, Math.min(y, s.ty));
        }
        return new Vec2(s.x0 + s.vx * age, s.y0 + s.vy * age + 0.5 * GRAVITY * age * age);
    }

    private double rand(double min, double max) {
        return min + rnd.nextDouble() * (max - min);
    }
}
