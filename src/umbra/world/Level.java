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
    private final Vec2 spawn;
    private final Rectangle2D exit;
    private final List<Rectangle2D> platforms = new ArrayList<>();
    private final List<Block> blocks = new ArrayList<>();
    private Rectangle2D tempBridge;   // solo para el avance 30%

    /** Todo lo que es sólido para el jugador (repisas + bloques + puente). */
    private final List<Rectangle2D> solids = new ArrayList<>();

    private double portalAngle;

    public Level(String name, Vec2 spawn, Rectangle2D exit) {
        this.name = name;
        this.spawn = spawn;
        this.exit = exit;
    }

    /**
     * Nivel de prueba para el avance 30%.
     *
     *   [inicio]         bloques flotantes          [salida]
     *   ########   - - - puente temporal - - -      ########
     *
     * En el 70% el puente desaparece (Config.TEMP_BRIDGE = false) y el jugador
     * tendrá que cruzar usando las sombras de los bloques.
     */
    public static Level createTestLevel() {
        Level lvl = new Level("Nivel de prueba",
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
    public Vec2 getSpawn() { return spawn; }
    public List<Block> getBlocks() { return Collections.unmodifiableList(blocks); }
    public List<Rectangle2D> getSolids() { return Collections.unmodifiableList(solids); }
}
