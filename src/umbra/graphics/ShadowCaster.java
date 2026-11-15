package umbra.graphics;

import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import umbra.entities.Block;
import umbra.entities.LightSource;
import umbra.math.Vec2;

/**
 * [AVANCE 70%] Calcula la sombra que proyecta cada bloque según la posición de la luz.
 * Las sombras que regresa este método se dibujan en pantalla (GamePanel.drawShadows)
 * y se le pasan al jugador (Player.update) para que se vuelvan sólidas.
 *
 * Algoritmo (luz puntual en 2D), para cada bloque:
 *   1. Para cada esquina C, la dirección desde la luz, normalizada (longitud 1):
 *          d = (C - luz).normalize()
 *   2. Se proyecta esa esquina lejos, en la misma dirección:
 *          C' = C + d * SHADOW_LENGTH
 *      Es un rayo que sale de la luz, pasa por la esquina y sigue de largo.
 *   3. La sombra es la envolvente convexa (convex hull) de las 4 esquinas y las
 *      4 proyecciones: el polígono convexo más pequeño que contiene a los 8 puntos.
 *      Las esquinas "de silueta" (las que tocan los rayos más abiertos) quedan
 *      en el borde; los puntos que quedan adentro se descartan solos.
 *   4. El polígono se convierte a Path2D (moveTo, lineTo, closePath).
 *
 * Caso especial: si la luz está dentro del bloque, no se proyecta sombra.
 */
public class ShadowCaster {

    /** Qué tan larga es la sombra. Ajústalo para balancear los niveles. */
    public static final double SHADOW_LENGTH = 450;

    public List<Path2D> computeShadows(LightSource light, List<Block> blocks) {
        List<Path2D> shadows = new ArrayList<>();
        Vec2 lightPos = light.getPosition();

        for (Block block : blocks) {
            // Con la luz adentro, los rayos salen hacia todos lados y no hay una
            // sombra que tenga sentido como plataforma.
            if (block.getBounds().contains(lightPos.x, lightPos.y)) continue;

            Vec2[] corners = block.getCorners();
            Vec2[] points = new Vec2[corners.length * 2];
            for (int i = 0; i < corners.length; i++) {
                Vec2 c = corners[i];
                Vec2 d = c.sub(lightPos).normalize();                       // paso 1
                points[i] = c;
                points[i + corners.length] = c.add(d.scale(SHADOW_LENGTH)); // paso 2
            }

            shadows.add(toPath(convexHull(points)));                        // pasos 3 y 4
        }
        return shadows;
    }

    /**
     * Envolvente convexa con el algoritmo "monotone chain" de Andrew, O(n log n).
     *
     * Se ordenan los puntos de izquierda a derecha y se arman dos cadenas:
     * la de abajo (recorriendo de izquierda a derecha) y la de arriba (de regreso).
     * Al agregar cada punto, mientras los dos últimos de la cadena y el nuevo
     * no formen un giro en el sentido correcto (turn <= 0), el último punto
     * sobra porque queda "hacia adentro", y se saca.
     *
     * Regresa los vértices del polígono en orden, sin repetir el primero.
     */
    static Vec2[] convexHull(Vec2[] input) {
        Vec2[] p = input.clone();
        Arrays.sort(p, (a, b) -> a.x != b.x ? Double.compare(a.x, b.x) : Double.compare(a.y, b.y));

        int n = p.length;
        Vec2[] hull = new Vec2[2 * n];
        int k = 0;

        // Cadena de abajo
        for (int i = 0; i < n; i++) {
            while (k >= 2 && turn(hull[k - 2], hull[k - 1], p[i]) <= 0) k--;
            hull[k++] = p[i];
        }
        // Cadena de arriba ('lower' evita sacar puntos de la cadena de abajo)
        for (int i = n - 2, lower = k + 1; i >= 0; i--) {
            while (k >= lower && turn(hull[k - 2], hull[k - 1], p[i]) <= 0) k--;
            hull[k++] = p[i];
        }
        // El último punto agregado es el mismo que el primero
        return Arrays.copyOf(hull, k - 1);
    }

    /**
     * Producto cruz (b - a) x (c - a). Dice hacia dónde gira el camino a -> b -> c:
     *   > 0  gira hacia un lado, < 0  gira hacia el otro, = 0  los tres son colineales.
     * (Como en pantalla la y crece hacia abajo, el "antihorario" de las matemáticas
     * se ve horario, pero al algoritmo solo le importa que el signo sea consistente.)
     */
    private static double turn(Vec2 a, Vec2 b, Vec2 c) {
        return b.sub(a).cross(c.sub(a));
    }

    private static Path2D toPath(Vec2[] polygon) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(polygon[0].x, polygon[0].y);
        for (int i = 1; i < polygon.length; i++) {
            path.lineTo(polygon[i].x, polygon[i].y);
        }
        path.closePath();
        return path;
    }
}
