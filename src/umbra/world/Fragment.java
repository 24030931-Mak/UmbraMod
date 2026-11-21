package umbra.world;

import java.awt.geom.Rectangle2D;

/**
 * [B4] Un pedazo del Gran Farol: una pieza de la zona, como las losas. Tiene su posición y un
 * número del 0 al 5 que solo lo identifica (el orden de las líneas de historia lo da cuántos
 * se llevan, no cuál se tomó). Que ya se haya tomado lo recuerda World, no el fragmento.
 */
public final class Fragment {

    /** Lado de su caja (px). */
    public static final double SIZE = 24;

    private final int id;
    private final double cx, cy;   // centro

    /** (cx, cy) es el centro del fragmento. */
    public Fragment(int id, double cx, double cy) {
        this.id = id;
        this.cx = cx;
        this.cy = cy;
    }

    public int getId() { return id; }
    public double getCenterX() { return cx; }
    public double getCenterY() { return cy; }

    /** La caja de SIZE x SIZE centrada en (cx, cy): con ella se revisa si Candil lo toca. */
    public Rectangle2D getBounds() {
        return new Rectangle2D.Double(cx - SIZE / 2, cy - SIZE / 2, SIZE, SIZE);
    }
}
