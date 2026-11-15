package umbra.math;

/**
 * Matriz 3x3 para transformaciones 2D en coordenadas homogéneas.
 *
 *     | a  b  tx |   | x |
 *     | c  d  ty | * | y |
 *     | 0  0  1  |   | 1 |
 *
 * Permite combinar traslación, rotación y escala multiplicando matrices.
 * Ejemplo (rotar un punto alrededor de un centro):
 *     Mat3 m = Mat3.translation(cx, cy).multiply(Mat3.rotation(angulo));
 *     Vec2 p = m.apply(new Vec2(10, 0));
 */
public final class Mat3 {

    private final double[] m;   // 9 valores, fila por fila

    private Mat3(double[] m) {
        this.m = m;
    }

    public static Mat3 identity() {
        return new Mat3(new double[] {
            1, 0, 0,
            0, 1, 0,
            0, 0, 1 });
    }

    public static Mat3 translation(double tx, double ty) {
        return new Mat3(new double[] {
            1, 0, tx,
            0, 1, ty,
            0, 0, 1 });
    }

    public static Mat3 rotation(double radians) {
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new Mat3(new double[] {
            c, -s, 0,
            s,  c, 0,
            0,  0, 1 });
    }

    public static Mat3 scale(double sx, double sy) {
        return new Mat3(new double[] {
            sx, 0,  0,
            0,  sy, 0,
            0,  0,  1 });
    }

    /** Regresa this * o  (al transformar un punto, primero se aplica 'o' y después 'this'). */
    public Mat3 multiply(Mat3 o) {
        double[] r = new double[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                double sum = 0;
                for (int k = 0; k < 3; k++) {
                    sum += m[row * 3 + k] * o.m[k * 3 + col];
                }
                r[row * 3 + col] = sum;
            }
        }
        return new Mat3(r);
    }

    /** Transforma el punto (x, y, 1). */
    public Vec2 apply(Vec2 p) {
        return new Vec2(
            m[0] * p.x + m[1] * p.y + m[2],
            m[3] * p.x + m[4] * p.y + m[5]);
    }
}
