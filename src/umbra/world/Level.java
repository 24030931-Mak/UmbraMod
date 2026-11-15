package umbra.world;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import umbra.core.Config;
import umbra.entities.Block;
import umbra.math.Mat3;
import umbra.math.Vec2;

/**
 * Un nivel: repisas sólidas, bloques que proyectan sombra, punto de inicio y salida.
 */
public class Level {

    private static final Color PLATFORM_FILL = new Color(36, 36, 52);
    private static final Color PLATFORM_TOP = new Color(110, 110, 140);
    private static final Color BRIDGE = new Color(255, 255, 255, 50);

    private final String name;
    private final double width;   // ancho del mundo en px; puede ser mayor que la ventana
    private final Vec2 spawn;
    private final Rectangle2D exit;
    private final List<Rectangle2D> platforms = new ArrayList<>();
    private final List<Block> blocks = new ArrayList<>();
    private Rectangle2D tempBridge;   // solo para el avance 30%

    /** Todo lo que es sólido para el jugador (repisas + bloques + puente). */
    private final List<Rectangle2D> solids = new ArrayList<>();

    private double portalAngle;

    public Level(String name, double width, Vec2 spawn, Rectangle2D exit) {
        this.name = name;
        this.width = width;
        this.spawn = spawn;
        this.exit = exit;
    }

    /**
     * [AVANCE 70%] Nivel 1: un recorrido de tres pantallas de ancho (3840 px) que solo
     * se puede cruzar usando las sombras. Cada tramo enseña una forma de usar la luz:
     *
     *   1. El puente: hueco de 450 px. Luz a la izquierda del bloque: su sombra se
     *      estira a la derecha y sirve de puente.
     *   2. La rampa: pared de 220 px. Luz abajo a la izquierda del bloque: la sombra
     *      sube hasta lo alto de la pared.
     *   3. La sombra hacia atrás: el bloque está del otro lado del hueco. Luz a la
     *      derecha del bloque: la sombra viene hacia el jugador.
     *   4. La carpa: foso de 700 px con un bloque flotando en medio. Luz justo encima
     *      del bloque: la sombra baja por los dos lados como una montaña.
     *
     * Ningún hueco se puede saltar: el salto alcanza unos 100 px de alto y 200 de largo.
     */
    public static Level createLevel1() {
        Level lvl = new Level("Nivel 1", 3840,
            new Vec2(80, 530),
            new Rectangle2D.Double(3680, 400, 60, 80));

        //              x     y    ancho alto
        lvl.addPlatform(0,    560, 230,  160);   // A: inicio
        lvl.addPlatform(680,  560, 560,  160);   // B: después del puente
        lvl.addPlatform(1240, 340, 460,  380);   // C: la pared alta
        lvl.addPlatform(2060, 440, 440,  280);   // D: después del segundo hueco
        lvl.addPlatform(3200, 480, 640,  240);   // E: salida

        lvl.addBlock(new Block(290,  520, 120, 40));   // tramo 1: el puente
        lvl.addBlock(new Block(900,  470, 100, 40));   // tramo 2: la rampa
        lvl.addBlock(new Block(2000, 300, 80,  40));   // tramo 3: la sombra hacia atrás
        lvl.addBlock(new Block(2780, 300, 140, 40));   // tramo 4: la carpa
        return lvl;
    }

    /**
     * Nivel de prueba del avance 30%, de una sola pantalla.
     *
     *   [inicio]         bloques flotantes          [salida]
     *   ########   - - - puente temporal - - -      ########
     *
     * El puente solo aparece si Config.TEMP_BRIDGE es true.
     */
    public static Level createTestLevel() {
        Level lvl = new Level("Nivel de prueba", Config.WIDTH,
            new Vec2(80, 500),
            new Rectangle2D.Double(1150, 480, 60, 80));

        lvl.addPlatform(0, 560, 260, 160);       // repisa de inicio
        lvl.addPlatform(1020, 560, 260, 160);    // repisa de salida

        lvl.addBlock(new Block(400, 330, 90, 60));
        lvl.addBlock(new Block(690, 250, 70, 110));

        if (Config.TEMP_BRIDGE) {
            lvl.setTempBridge(260, 560, 760, 14);
        }
        return lvl;
    }

    // ---- Construcción ----

    public void addPlatform(double x, double y, double w, double h) {
        platforms.add(new Rectangle2D.Double(x, y, w, h));
        rebuildSolids();
    }

    public void addBlock(Block block) {
        blocks.add(block);
        rebuildSolids();
    }

    public void setTempBridge(double x, double y, double w, double h) {
        tempBridge = new Rectangle2D.Double(x, y, w, h);
        rebuildSolids();
    }

    private void rebuildSolids() {
        solids.clear();
        solids.addAll(platforms);
        for (Block b : blocks) solids.add(b.getBounds());
        if (tempBridge != null) solids.add(tempBridge);
    }

    // ---- Lógica ----

    public void update(double dt) {
        portalAngle += dt * 1.6;   // la salida gira
    }

    public boolean isAtExit(Rectangle2D playerBounds) {
        return exit.intersects(playerBounds);
    }

    /**
     * La repisa sobre la que está parado el jugador, o null si no pisa ninguna
     * (está en el aire, sobre un bloque o sobre una sombra).
     * Sirve para que, si se cae, reaparezca en la última repisa firme que pisó.
     */
    public Rectangle2D getPlatformUnder(Rectangle2D playerBounds) {
        for (Rectangle2D p : platforms) {
            boolean touching = Math.abs(playerBounds.getMaxY() - p.getMinY()) < 0.01;
            boolean above = playerBounds.getMaxX() > p.getMinX() && playerBounds.getMinX() < p.getMaxX();
            if (touching && above) return p;
        }
        return null;
    }

    // ---- Dibujo ----

    public void draw(Graphics2D g) {
        for (Rectangle2D p : platforms) {
            g.setColor(PLATFORM_FILL);
            g.fill(p);
            g.setColor(PLATFORM_TOP);
            g.fill(new Rectangle2D.Double(p.getX(), p.getY(), p.getWidth(), 3));
        }

        if (tempBridge != null) {
            Stroke old = g.getStroke();
            g.setColor(BRIDGE);
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10, new float[] { 8, 6 }, 0));
            g.draw(tempBridge);
            g.setStroke(old);
        }

        for (Block b : blocks) {
            b.draw(g);
        }

        drawPortal(g);
    }

    /**
     * La salida es un rombo que gira y "respira".
     * Cada vértice se transforma con una matriz compuesta: Traslación * Rotación * Escala.
     */
    private void drawPortal(Graphics2D g) {
        double cx = exit.getCenterX();
        double cy = exit.getCenterY();
        Vec2[] diamond = {
            new Vec2(0, -26), new Vec2(18, 0), new Vec2(0, 26), new Vec2(-18, 0)
        };

        double pulse = 1 + 0.08 * Math.sin(portalAngle * 2);
        Mat3 outer = Mat3.translation(cx, cy)
                .multiply(Mat3.rotation(portalAngle))
                .multiply(Mat3.scale(pulse, pulse));
        Mat3 inner = Mat3.translation(cx, cy)
                .multiply(Mat3.rotation(-portalAngle * 1.5))
                .multiply(Mat3.scale(0.5, 0.5));

        Path2D outerShape = transformPolygon(diamond, outer);
        Path2D innerShape = transformPolygon(diamond, inner);

        g.setColor(new Color(120, 200, 255, 90));
        g.fill(outerShape);
        g.setColor(new Color(180, 230, 255));
        g.draw(outerShape);
        g.setColor(new Color(235, 250, 255));
        g.fill(innerShape);
    }

    /** Aplica la matriz a cada vértice y regresa el polígono resultante. */
    private static Path2D transformPolygon(Vec2[] points, Mat3 m) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i < points.length; i++) {
            Vec2 p = m.apply(points[i]);
            if (i == 0) path.moveTo(p.x, p.y);
            else        path.lineTo(p.x, p.y);
        }
        path.closePath();
        return path;
    }

    // ---- Getters ----

    public String getName() { return name; }
    public double getWidth() { return width; }
    public Vec2 getSpawn() { return spawn; }
    public List<Block> getBlocks() { return Collections.unmodifiableList(blocks); }
    public List<Rectangle2D> getSolids() { return Collections.unmodifiableList(solids); }
}
