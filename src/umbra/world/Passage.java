package umbra.world;

import umbra.math.Vec2;

/**
 * Un paso en una orilla de una zona hacia otra zona. 'from' y 'to' son el tramo de la orilla
 * por donde se pasa, en coordenadas locales de la zona que tiene el paso (Y en las orillas
 * izquierda y derecha, X en la de arriba y la de abajo).
 */
public final class Passage {

    public enum Side { LEFT, RIGHT, TOP, BOTTOM }

    private final Side side;
    private final double from, to;
    private final Level target;
    private final Vec2 arrival;   // esquina superior izquierda de Candil al llegar a 'target'
    private final Vec2 respawn;   // punto de reaparición en 'target'; si se da null, es 'arrival'

    public Passage(Side side, double from, double to, Level target, Vec2 arrival, Vec2 respawn) {
        this.side = side;
        this.from = from;
        this.to = to;
        this.target = target;
        this.arrival = arrival;
        this.respawn = respawn != null ? respawn : arrival;
    }

    public Side getSide() { return side; }
    public double getFrom() { return from; }
    public double getTo() { return to; }
    public Level getTarget() { return target; }
    public Vec2 getArrival() { return arrival; }
    public Vec2 getRespawn() { return respawn; }
}
