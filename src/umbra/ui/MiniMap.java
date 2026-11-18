package umbra.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import umbra.core.Config;
import umbra.math.Mat3;
import umbra.math.Vec2;
import umbra.world.Exploration;
import umbra.world.Level;

/**
 * El círculo de la esquina superior derecha: un mapa chico, siempre a la vista al jugar,
 * que muestra solo el nivel actual centrado en Candil.
 *
 * Vista: una matriz de mundo a pantalla que centra a Candil en el círculo y escala para que
 * VIEW px del mundo quepan en el diámetro; el mundo "se desliza" debajo del círculo:
 *     pantalla = T(centro) * S(k) * T(-candil) * mundo,   k = DIAMETER / VIEW
 * El recorte es una elipse (círculo) con el mismo centro. Adentro, el mismo estilo del mapa
 * grande: repisas y bloques, luz cálida en lo recorrido y la neblina compartida (MapFog).
 *
 * [v1.1] Encima se agregan: el halo de luz de Logi (un degradado radial dibujado en el mundo,
 * con su radio real), una lente que oscurece las orillas (otro degradado radial, ya en
 * pantalla), un anillo que pulsa alrededor de Candil y un marco de astrolabio: dos anillos,
 * ocho marcas de brújula y cuatro runas en los puntos cardinales, que se calculan con
 * seno y coseno del ángulo.
 */
public class MiniMap {

    public static final int DIAMETER = 180;
    public static final int MARGIN = 16;
    /** Px del mundo que caben en el diámetro. */
    public static final int VIEW = 1000;

    private static final double RADIUS = DIAMETER / 2.0;
    private static final double CENTER_X = Config.WIDTH - MARGIN - RADIUS;
    private static final double CENTER_Y = MARGIN + RADIUS;
    private static final double SCALE = (double) DIAMETER / VIEW;

    private static final Color RING_OUTER = new Color(24, 28, 44, 235);
    private static final Color RING = new Color(150, 165, 200, 210);
    private static final Color RUNE_GLOW = new Color(255, 215, 140);

    private final Shape circle = new Ellipse2D.Double(CENTER_X - RADIUS, CENTER_Y - RADIUS,
            DIAMETER, DIAMETER);

    /** candil y logi en coordenadas del nivel; logi puede ser null. 'time' mueve la neblina y pulsos. */
    public void draw(Graphics2D g, Level level, Exploration ex, Vec2 candil, Vec2 logi, double time) {
        AffineTransform onScreen = g.getTransform();
        Shape oldClip = g.getClip();
        Stroke oldStroke = g.getStroke();
        Paint oldPaint = g.getPaint();

        // 1. Fondo y recorte circular del visor
        g.setColor(MapPainter.BACKDROP);
        g.fill(circle);
        g.clip(circle);

        // Transformación mundo -> minimapa: T(centro) * S(k) * T(-candil)
        Mat3 view = Mat3.translation(CENTER_X, CENTER_Y).multiply(Mat3.scale(SCALE, SCALE))
                .multiply(Mat3.translation(-candil.x, -candil.y));
        g.transform(view.toAffineTransform());

        double w = level.getWidth(), h = level.getHeight();
        double half = VIEW / 2.0;   // lo que se ve alrededor de Candil: un cuadro de VIEW x VIEW
        Rectangle2D visible = new Rectangle2D.Double(candil.x - half, candil.y - half, VIEW, VIEW);

        // 2. Geometría del nivel (suelo, plataformas y bloques tallados), solo lo que se ve
        MapPainter.drawTerrain(g, level, h, visible);

        // 3. Niebla de exploración compartida. La imagen cubre la cuadrícula completa: se recorta
        // al nivel, con 1 px de pantalla de holgura para que la neblina tape también los píxeles
        // donde la repisa se suaviza (antialias)
        MapPainter.drawFog(g, ex, w, h, 1 / SCALE, time, 1, visible);

        // 4. Proyección del radio real de luz de Logi y su núcleo
        if (logi != null) {
            float lightR = (float) (Config.LIGHT_RADIUS * 0.95);
            g.setPaint(new RadialGradientPaint(
                    new Point2D.Double(logi.x, logi.y), lightR,
                    new float[] { 0f, 0.45f, 1f },
                    new Color[] {
                        new Color(255, 210, 120, 110),
                        new Color(255, 190, 90, 40),
                        new Color(255, 190, 90, 0)
                    }));
            g.fill(new Ellipse2D.Double(logi.x - lightR, logi.y - lightR, lightR * 2, lightR * 2));

            double r = 3.2 / SCALE;   // 3.2 px de pantalla, pasados a unidades del mundo
            g.setColor(MapPainter.LOGI);
            g.fill(new Ellipse2D.Double(logi.x - r, logi.y - r, 2 * r, 2 * r));
        }

        // 5. Volver a coordenadas de pantalla: lente oscura en bordes y marcador pulsante de Candil
        g.setTransform(onScreen);
        // Lente: transparente hasta el 72 % del radio y cada vez más oscuro hacia la orilla
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(CENTER_X, CENTER_Y), (float) RADIUS,
                new float[] { 0.72f, 1f },
                new Color[] { new Color(0, 0, 0, 0), new Color(3, 5, 12, 165) }));
        g.fill(circle);

        // El anillo crece y se apaga con un seno: pulse va de 0 a 1 y regresa
        double pulse = 0.5 + 0.5 * Math.sin(time * 3.2);
        double ringR = 5.5 + 3.0 * pulse;
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(MapPainter.CANDIL.getRed(), MapPainter.CANDIL.getGreen(), MapPainter.CANDIL.getBlue(), (int) (150 * (1.0 - 0.5 * pulse))));
        g.draw(new Ellipse2D.Double(CENTER_X - ringR, CENTER_Y - ringR, ringR * 2, ringR * 2));

        g.setColor(MapPainter.CANDIL);   // Candil: el punto del centro
        g.fill(new Ellipse2D.Double(CENTER_X - 3, CENTER_Y - 3, 6, 6));

        // 6. Marco exterior tipo astrolabio de piedra con marcas de escala y 4 runas cardinales
        g.setClip(oldClip);
        drawAstrolabeFrame(g, time);

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    /** Dibuja el bisel doble, las 8 marcas radiales y las 4 runas cardinales alrededor del minimapa. */
    private static void drawAstrolabeFrame(Graphics2D g, double time) {
        // Bisel oscuro exterior
        g.setStroke(new BasicStroke(4.5f));
        g.setColor(RING_OUTER);
        g.draw(new Ellipse2D.Double(CENTER_X - RADIUS - 1, CENTER_Y - RADIUS - 1, DIAMETER + 2, DIAMETER + 2));

        // Anillo metálico/piedra interior
        g.setStroke(new BasicStroke(1.8f));
        g.setColor(RING);
        g.draw(new Ellipse2D.Double(CENTER_X - RADIUS, CENTER_Y - RADIUS, DIAMETER, DIAMETER));

        // 8 marcas de brújula alrededor del borde: la marca i está en el ángulo i·45°; las cardinales (pares) son más largas
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 8; i++) {
            double ang = i * (Math.PI / 4.0);
            boolean cardinal = (i % 2 == 0);
            double rIn = RADIUS - (cardinal ? 6.0 : 4.0);
            double rOut = RADIUS + (cardinal ? 2.0 : 1.0);
            g.setColor(cardinal ? new Color(200, 215, 245, 220) : new Color(130, 145, 180, 150));
            g.draw(new Line2D.Double(
                    CENTER_X + Math.cos(ang) * rIn, CENTER_Y + Math.sin(ang) * rIn,
                    CENTER_X + Math.cos(ang) * rOut, CENTER_Y + Math.sin(ang) * rOut));
        }

        // 4 pequeñas runas romboidales (igual que las losas de LevelArt) en los puntos cardinales
        // Las runas respiran: su transparencia sigue un seno del tiempo
        int runeAlpha = (int) (130 + 75 * Math.sin(time * 2.4));
        g.setColor(new Color(RUNE_GLOW.getRed(), RUNE_GLOW.getGreen(), RUNE_GLOW.getBlue(), runeAlpha));
        for (int i = 0; i < 4; i++) {
            double ang = i * (Math.PI / 2.0);
            double rx = CENTER_X + Math.cos(ang) * (RADIUS + 1.5);
            double ry = CENTER_Y + Math.sin(ang) * (RADIUS + 1.5);
            g.fill(diamondRune(rx, ry, 3.6));
        }
    }

    /** Rombo rúnico en (cx, cy) de semialtura 's'. */
    private static Path2D diamondRune(double cx, double cy, double s) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - s);
        p.lineTo(cx + s * 0.65, cy);
        p.lineTo(cx, cy + s);
        p.lineTo(cx - s * 0.65, cy);
        p.closePath();
        return p;
    }
}
