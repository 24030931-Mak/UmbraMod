package umbra.math;

/**
 * Vector 2D inmutable. Se usa para posiciones y direcciones.
 * Ejemplo: Vec2 dir = esquina.sub(luz).normalize();
 */
public final class Vec2 {

    public final double x;
    public final double y;

    public Vec2(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Vec2 add(Vec2 o)     { return new Vec2(x + o.x, y + o.y); }
    public Vec2 sub(Vec2 o)     { return new Vec2(x - o.x, y - o.y); }
    public Vec2 scale(double s) { return new Vec2(x * s, y * s); }
    public double dot(Vec2 o)   { return x * o.x + y * o.y; }

    /** Producto cruz 2D (componente z). Su signo indica de qué lado de una línea está un punto. */
    public double cross(Vec2 o) { return x * o.y - y * o.x; }

    public double length()      { return Math.sqrt(x * x + y * y); }

    public Vec2 normalize() {
        double len = length();
        return len == 0 ? new Vec2(0, 0) : new Vec2(x / len, y / len);
    }

    /** Interpolación lineal: t = 0 regresa este vector, t = 1 regresa 'o'. */
    public Vec2 lerp(Vec2 o, double t) {
        return new Vec2(x + (o.x - x) * t, y + (o.y - y) * t);
    }

    @Override
    public String toString() {
        return String.format("(%.1f, %.1f)", x, y);
    }
}
