package umbra.graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;

import umbra.world.Fragment;

/**
 * [B4] El dibujo de un fragmento del Gran Farol: un pedazo de vidrio dorado, con facetas y un
 * brillo que pulsa. Mismo estilo que LanternArt: bisel claro arriba a la izquierda y oscuro
 * abajo a la derecha. No guarda nada: todo se calcula en cada cuadro (solo hay uno o dos a la vez).
 *
 * El contorno es un cristal irregular de unos 26 x 26 unidades (SIZE de Fragment más un poco de
 * aire), que se escala con 'scale' para el icono del contador.
 */
public final class FragmentArt {

    private static final Color GLOW = new Color(255, 200, 90);
    private static final Color GLASS_TOP = new Color(255, 238, 170);
    private static final Color GLASS_BOTTOM = new Color(226, 150, 40);
    private static final Color FACET = new Color(255, 250, 215, 150);
    private static final Color EDGE_LIGHT = new Color(255, 248, 210);
    private static final Color EDGE_DARK = new Color(150, 92, 24);
    private static final Color DIM_FILL = new Color(60, 62, 80);
    private static final Color DIM_EDGE = new Color(120, 124, 150);

    /** Los vértices del cristal (x, y), con el origen en su centro. */
    private static final double[][] SHARD = {
        { 0, -13 }, { 8, -6 }, { 6, 10 }, { -3, 13 }, { -9, 3 }, { -6, -8 } };

    private FragmentArt() { }

    /** El fragmento en el mundo, con su halo pulsando. 'time' son los segundos de la zona. */
    public static void draw(Graphics2D g, Fragment f, double time) {
        double pulse = 0.5 + 0.5 * Math.sin(time * 3.0 + f.getId() * 1.7);
        double bob = Math.sin(time * 1.6 + f.getId()) * 1.5;   // flota un poco
        double cx = f.getCenterX(), cy = f.getCenterY() + bob;

        double r = 30 + 10 * pulse;
        g.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) r, new float[] { 0f, 1f },
                new Color[] { alpha(GLOW, 70 + 80 * pulse), alpha(GLOW, 0) }));
        g.fillOval((int) (cx - r), (int) (cy - r), (int) (2 * r), (int) (2 * r));

        shard(g, cx, cy, 1.0, true, 0.15 * Math.sin(time * 1.2 + f.getId()));
    }

    /** El icono del contador: el cristal centrado en (x, y) a escala 'scale' (1 = tamaño en el mundo), encendido. */
    public static void drawIcon(Graphics2D g, double x, double y, double scale) {
        drawIcon(g, x, y, scale, true);
    }

    /** Igual; con lit = false se ve apagado (gris), para cuando todavía no se lleva ninguno. */
    public static void drawIcon(Graphics2D g, double x, double y, double scale, boolean lit) {
        shard(g, x, y, scale, lit, 0);
    }

    private static void shard(Graphics2D g, double cx, double cy, double scale, boolean lit, double angle) {
        AffineTransform oldTransform = g.getTransform();
        Stroke oldStroke = g.getStroke();
        g.translate(cx, cy);
        g.rotate(angle);
        g.scale(scale, scale);

        Path2D.Double body = new Path2D.Double();
        for (int i = 0; i < SHARD.length; i++) {
            if (i == 0) body.moveTo(SHARD[i][0], SHARD[i][1]); else body.lineTo(SHARD[i][0], SHARD[i][1]);
        }
        body.closePath();
        g.setPaint(lit ? new GradientPaint(-8, -12, GLASS_TOP, 7, 12, GLASS_BOTTOM)
                       : new GradientPaint(-8, -12, DIM_EDGE, 7, 12, DIM_FILL));
        g.fill(body);

        if (lit) {   // una faceta y un destello que atrapan la luz
            Path2D.Double facet = new Path2D.Double();
            facet.moveTo(0, -13);
            facet.lineTo(8, -6);
            facet.lineTo(1, -1);
            facet.lineTo(-6, -8);
            facet.closePath();
            g.setColor(FACET);
            g.fill(facet);
        }

        g.setStroke(new BasicStroke((float) (1.4 / Math.max(0.3, scale))));
        g.setColor(lit ? EDGE_DARK : DIM_EDGE);
        g.draw(body);
        if (lit) {   // bisel claro arriba a la izquierda
            g.setColor(EDGE_LIGHT);
            g.drawLine(0, -13, -6, -8);
            g.drawLine(-6, -8, -9, 3);
        }
        g.setStroke(oldStroke);
        g.setTransform(oldTransform);
    }

    private static Color alpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.max(0, Math.min(255, Math.round(a))));
    }
}
