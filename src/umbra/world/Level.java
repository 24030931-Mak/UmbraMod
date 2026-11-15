package umbra.world;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
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

    private static final Color BRIDGE = new Color(255, 255, 255, 50);

    private final String number;  // numeral romano del título ("I", "II"); "" si no tiene
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

    // Pasos hacia los niveles vecinos [mundo conectado]: dónde aparece Candil al llegar
    // por ese lado. null = no hay paso de ese lado.
    private Vec2 leftEntry, rightEntry;

    /** 'exit' es el portal que lleva a la pantalla final; null si el nivel no tiene. */
    public Level(String number, String name, double width, Vec2 spawn, Rectangle2D exit) {
        this.number = number;
        this.name = name;
        this.width = width;
        this.spawn = spawn;
        this.exit = exit;
    }

    public Level(String name, double width, Vec2 spawn, Rectangle2D exit) {
        this("", name, width, spawn, exit);
    }

    /**
     * Abre los pasos a los niveles vecinos. Cada argumento es la posición (esquina superior
     * izquierda de Candil) donde aparece quien llega por ese lado; null = sin paso.
     */
    public void connect(Vec2 leftEntry, Vec2 rightEntry) {
        this.leftEntry = leftEntry;
        this.rightEntry = rightEntry;
    }

    /**
     * Si el jugador toca una orilla con paso: -1 = la izquierda (minX <= 1), +1 = la derecha
     * (maxX >= ancho - 1), 0 = ninguna. Es solo una comparación de coordenadas con el borde.
     */
    public int passageAt(Rectangle2D playerBounds) {
        if (leftEntry != null && playerBounds.getMinX() <= 1) return -1;
        if (rightEntry != null && playerBounds.getMaxX() >= width - 1) return 1;
        return 0;
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
        Level lvl = new Level("I", "Las Ra\u00edces", 3840,
            new Vec2(80, 530), null);   // sin portal: su salida es el paso de la orilla derecha
        lvl.connect(null, new Vec2(3758, 450));

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
     * [v1.0] Nivel 2, "Las Galerías": tres pantallas de ancho (3840 px). Su entrada izquierda
     * conecta con la salida del nivel 1 y el portal está al final, 300 px más arriba que la
     * entrada. Cada tramo agrega una idea nueva sobre lo aprendido en el nivel 1:
     *
     *   1. El refugio: una losa sobre la entrada. Con Logi arriba de ella, por ejemplo en
     *      (115, 300), la sombra baja como una carpa y cubre la entrada: Candil no pierde
     *      vida mientras se acomoda a Logi para el tramo siguiente.
     *   2. Dos sombras, un puente: hueco de 1000 px. Una sola sombra no alcanza: llega a
     *      450 px de su bloque y el salto agrega unos 180. Con Logi a la izquierda de la
     *      primera losa, a la altura de su cara de arriba (220, 462), las dos sombras salen
     *      hacia la derecha y se enciman: la de la primera llega hasta la segunda losa y la
     *      de la segunda, hasta la repisa B. Con D+W Candil va saltando por la orilla de
     *      arriba de las sombras.
     *   3. La chimenea: subida de 300 px por el pozo que queda entre dos losas apiladas y la
     *      pared de la repisa C. Con Logi arriba a la izquierda (1300, 250), las sombras de
     *      las losas caen una sobre otra dentro del pozo: Candil sube nadando (W) y, al salir
     *      por arriba de la última, sale de un salto a lo alto de C. Las losas están a 135 px
     *      una de la otra y la de abajo a 140 px del piso: sin sombra no sirven de escalera
     *      (el salto sube 107 px y, con el escalón de 20 px que se sube solo, 127).
     *   4. El pasillo bajo: un túnel de 70 px de alto (techo D) con un foso de 200 px. Con el
     *      techo tan bajo el salto apenas sube 40 px: el foso no se puede saltar. Con Logi
     *      fija lejos, atrás del túnel y un poco más arriba del dintel (1900, 170), la sombra
     *      del dintel entra casi horizontal y llena el túnel: Candil cruza flotando y se va
     *      alejando de ella. Dentro del túnel Logi no sirve: Candil tendría que pasar a menos
     *      de 70 px de ella y se desmoronaría.
     *   5. El portal: en la repisa alta del final (E).
     *
     * Ningún hueco se puede cruzar saltando sin sombras: la prueba lo comprueba con todos los
     * saltos y caídas posibles, cada medio píxel.
     */
    public static Level createLevel2() {
        Level lvl = new Level("II", "Las Galer\u00edas", 3840,
            new Vec2(80, 530),
            new Rectangle2D.Double(3300, 180, 60, 80));   // el portal, parado sobre E
        lvl.connect(new Vec2(80, 530), null);   // a la izquierda, el paso al nivel 1

        //              x     y    ancho alto
        lvl.addPlatform(0,    560, 380,  160);   // A: la entrada, con el refugio
        lvl.addPlatform(1380, 560, 280,  160);   // B: después del puente, al pie de la chimenea
        lvl.addPlatform(1660, 260, 790,  460);   // C: arriba de la chimenea; sigue dentro del túnel
        lvl.addPlatform(2200, 0,   950,  190);   // D: el techo del pasillo bajo
        lvl.addPlatform(2650, 260, 1190, 460);   // E: la repisa alta del portal

        lvl.addBlock(new Block(40,   390, 150, 30));   // tramo 1: el techo del refugio
        lvl.addBlock(new Block(440,  470, 120, 40));   // tramo 2: el puente, primera losa
        lvl.addBlock(new Block(900,  470, 110, 40));   // tramo 2: el puente, segunda losa
        lvl.addBlock(new Block(1500, 420, 60,  90));   // tramo 3: la chimenea, losa de abajo
        lvl.addBlock(new Block(1500, 285, 60,  90));   // tramo 3: la chimenea, losa de arriba
        lvl.addBlock(new Block(2200, 190, 60,  30));   // tramo 4: el dintel del pasillo
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
        return exit != null && exit.intersects(playerBounds);
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

    /**
     * Dibuja la salida (y el puente provisional, si lo hay). Las repisas y los bloques
     * los dibuja graphics.LevelArt, con su aspecto de roca y piedra tallada.
     */
    public void draw(Graphics2D g) {
        if (tempBridge != null) {
            Stroke old = g.getStroke();
            g.setColor(BRIDGE);
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10, new float[] { 8, 6 }, 0));
            g.draw(tempBridge);
            g.setStroke(old);
        }
        if (exit != null) drawPortal(g);
        if (leftEntry != null) drawPassage(g, 0, 1);
        if (rightEntry != null) drawPassage(g, width, -1);
    }

    /**
     * Un paso hacia otro nivel: una abertura oscura en la orilla del mundo, con neblina que
     * sale de ella. 'dir' apunta hacia dentro del nivel (+1 en la izquierda, -1 en la derecha).
     */
    private void drawPassage(Graphics2D g, double edgeX, int dir) {
        Vec2 entry = dir > 0 ? leftEntry : rightEntry;
        double floor = entry.y + 30;   // la repisa donde aparece Candil
        double x0 = edgeX, x1 = edgeX + dir * 70;
        g.setPaint(new GradientPaint((float) x0, 0, new Color(2, 2, 6, 235),
                (float) x1, 0, new Color(2, 2, 6, 0)));
        g.fill(new Rectangle2D.Double(Math.min(x0, x1), floor - 90, 70, 90));
        for (int i = 0; i < 3; i++) {   // neblina que se mece al salir
            double wave = Math.sin(portalAngle + i * 2.1);
            double cx = edgeX + dir * (20 + 16 * i + 6 * wave);
            double cy = floor - 22 - 24 * i;
            g.setColor(new Color(150, 170, 205, 36));
            g.fill(new Ellipse2D.Double(cx - 22, cy - 14, 44, 28));
        }
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

    public String getNumber() { return number; }
    public String getName() { return name; }
    public Rectangle2D getExit() { return exit; }
    public Vec2 getLeftEntry() { return leftEntry; }
    public Vec2 getRightEntry() { return rightEntry; }
    public double getWidth() { return width; }
    public Vec2 getSpawn() { return spawn; }
    public List<Rectangle2D> getPlatforms() { return Collections.unmodifiableList(platforms); }
    public List<Block> getBlocks() { return Collections.unmodifiableList(blocks); }
    public List<Rectangle2D> getSolids() { return Collections.unmodifiableList(solids); }
}
