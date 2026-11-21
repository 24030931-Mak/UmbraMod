package umbra.graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * [B2] El Gran Farol: la salida de la Cúpula. Cuelga del techo con una cadena; es un farol grande
 * de piedra tallada, apagado, con 6 huecos vacíos alrededor de su vidrio oscuro (B4 los llenará
 * con los fragmentos). Se dibuja con la misma paleta fría que las losas de LevelArt: bisel claro
 * arriba a la izquierda, oscuro abajo a la derecha, y la runa del rombo grabada en el techo.
 *
 * No guarda nada en caché: todo se calcula y se dibuja en cada cuadro. El farol se mece un poco
 * (unos 2 grados) y el vidrio tiene un brillo frío muy tenue que respira.
 */
public final class LanternArt {

    private static final Color CHAIN = new Color(70, 76, 100);
    private static final Color CHAIN_LIGHT = new Color(130, 138, 170, 160);
    private static final Color STONE_TOP = new Color(92, 98, 124);
    private static final Color STONE_BOTTOM = new Color(52, 56, 76);
    private static final Color STONE_LIGHT = new Color(150, 158, 190, 200);
    private static final Color STONE_DARK = new Color(28, 30, 44, 220);
    private static final Color STONE_EDGE = new Color(160, 165, 200, 160);
    private static final Color CARVED = new Color(30, 32, 46, 200);
    private static final Color GLASS = new Color(10, 12, 22);
    private static final Color GLASS_RIM = new Color(110, 125, 165, 150);
    private static final Color SOCKET = new Color(6, 7, 14);
    private static final Color SOCKET_RIM = new Color(150, 160, 200, 140);

    /** Cuántos huecos tiene el farol (los fragmentos del Gran Farol). */
    public static final int SLOTS = 6;

    private LanternArt() { }

    /**
     * Dibuja el farol dentro de 'lantern' (coordenadas del mundo): la cadena arriba, el techo, el
     * cuerpo con el vidrio y los 6 huecos, y la base. 'time' en segundos (para mecerse).
     */
    public static void draw(Graphics2D g, Rectangle2D lantern, double time) {
        double x = lantern.getX(), y = lantern.getY(), w = lantern.getWidth(), h = lantern.getHeight();
        double cx = x + w / 2;
        double chainEnd = y + h * 0.22;   // donde termina la cadena y empieza el techo

        Graphics2D g2 = (Graphics2D) g.create();
        // Se mece desde el punto de donde cuelga (arriba al centro): rotación alrededor de (cx, y)
        g2.rotate(Math.toRadians(2.0) * Math.sin(time * 0.7), cx, y);

        // ---- La cadena: eslabones alternados (de frente y de canto) ----
        g2.setStroke(new BasicStroke(2.2f));
        for (double ly = y; ly < chainEnd - 4; ly += 9) {
            boolean front = ((int) ((ly - y) / 9)) % 2 == 0;
            Shape link = front ? new Ellipse2D.Double(cx - 4, ly, 8, 11) : new Line2D.Double(cx, ly, cx, ly + 11);
            g2.setColor(CHAIN);
            g2.draw(link);
            g2.setColor(CHAIN_LIGHT);
            g2.draw(new Line2D.Double(cx - 3, ly + 2, cx - 3, ly + 4));
        }

        // ---- El techo: un tejado de piedra con alero ----
        double roofTop = chainEnd, roofBottom = y + h * 0.36;
        Path2D roof = new Path2D.Double();
        roof.moveTo(cx - w * 0.16, roofTop);
        roof.lineTo(cx + w * 0.16, roofTop);
        roof.lineTo(x + w, roofBottom);
        roof.lineTo(x, roofBottom);
        roof.closePath();
        stone(g2, roof, roofTop, roofBottom);
        Path2D rune = new Path2D.Double();   // el rombo del Gran Farol, grabado en el techo
        double rs = 6, ry = (roofTop + roofBottom) / 2 + 1;
        rune.moveTo(cx, ry - rs);
        rune.lineTo(cx + rs * 0.6, ry);
        rune.lineTo(cx, ry + rs);
        rune.lineTo(cx - rs * 0.6, ry);
        rune.closePath();
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Background.alpha(STONE_LIGHT, 90));
        g2.translate(0, 1);
        g2.draw(rune);
        g2.translate(0, -1);
        g2.setColor(CARVED);
        g2.draw(rune);

        // ---- El cuerpo: un marco de piedra con el vidrio oscuro al centro ----
        double bodyTop = roofBottom, bodyBottom = y + h * 0.88;
        double bx = x + w * 0.08, bw = w * 0.84;
        RoundRectangle2D body = new RoundRectangle2D.Double(bx, bodyTop, bw, bodyBottom - bodyTop, 8, 8);
        stone(g2, body, bodyTop, bodyBottom);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(CARVED);
        g2.draw(new RoundRectangle2D.Double(bx + 4, bodyTop + 4, bw - 8, bodyBottom - bodyTop - 8, 6, 6));

        double gcx = cx, gcy = (bodyTop + bodyBottom) / 2;
        double ring = Math.min(bw, bodyBottom - bodyTop) * 0.34;   // radio del anillo de huecos
        double glassR = ring * 0.55;
        // El vidrio: oscuro, con un brillo frío que respira muy lento (está apagado)
        double breath = 0.5 + 0.5 * Math.sin(time * 1.3);
        g2.setColor(GLASS);
        g2.fill(new Ellipse2D.Double(gcx - glassR, gcy - glassR, glassR * 2, glassR * 2));
        float[] stops = { 0f, 1f };
        Color[] cold = { new Color(120, 150, 210, (int) (20 + 25 * breath)), new Color(120, 150, 210, 0) };
        g2.setPaint(new RadialGradientPaint((float) gcx, (float) gcy, (float) glassR, stops, cold));
        g2.fill(new Ellipse2D.Double(gcx - glassR, gcy - glassR, glassR * 2, glassR * 2));
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(GLASS_RIM);
        g2.draw(new Ellipse2D.Double(gcx - glassR, gcy - glassR, glassR * 2, glassR * 2));

        // Los 6 huecos vacíos, en hexágono alrededor del vidrio: cuencas hondas con borde tallado
        double sr = Math.max(4, ring * 0.24);
        for (int i = 0; i < SLOTS; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / SLOTS;
            double sx = gcx + Math.cos(a) * ring, sy = gcy + Math.sin(a) * ring;
            Ellipse2D socket = new Ellipse2D.Double(sx - sr, sy - sr, sr * 2, sr * 2);
            g2.setColor(SOCKET);
            g2.fill(socket);
            g2.setColor(STONE_DARK);   // sombra adentro, arriba: se ve hondo
            g2.draw(new Line2D.Double(sx - sr * 0.6, sy - sr * 0.45, sx + sr * 0.6, sy - sr * 0.45));
            g2.setColor(SOCKET_RIM);
            g2.draw(socket);
        }

        // ---- La base: una losa más ancha con una punta abajo ----
        double baseTop = bodyBottom, baseBottom = y + h * 0.96;
        Path2D base = new Path2D.Double();
        base.moveTo(x + w * 0.02, baseTop);
        base.lineTo(x + w * 0.98, baseTop);
        base.lineTo(x + w * 0.80, baseBottom);
        base.lineTo(x + w * 0.20, baseBottom);
        base.closePath();
        stone(g2, base, baseTop, baseBottom);
        Path2D tip = new Path2D.Double();
        tip.moveTo(cx - w * 0.08, baseBottom);
        tip.lineTo(cx + w * 0.08, baseBottom);
        tip.lineTo(cx, y + h);
        tip.closePath();
        stone(g2, tip, baseBottom, y + h);
        g2.dispose();
    }

    /** Rellena 'shape' como piedra tallada: degradado de arriba abajo, borde claro y bisel. */
    private static void stone(Graphics2D g, Shape shape, double top, double bottom) {
        g.setPaint(new GradientPaint(0, (float) top, STONE_TOP, 0, (float) bottom, STONE_BOTTOM));
        g.fill(shape);
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(STONE_EDGE);
        g.draw(shape);
        Rectangle2D b = shape.getBounds2D();
        g.setColor(STONE_LIGHT);   // luz arriba
        g.draw(new Line2D.Double(b.getMinX() + 3, top + 1.5, b.getMaxX() - 3, top + 1.5));
        g.setStroke(old);
    }

}
