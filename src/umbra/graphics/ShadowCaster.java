package umbra.graphics;

import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

import umbra.entities.Block;
import umbra.entities.LightSource;
import umbra.math.Vec2;

/**
 * [AVANCE 70%] Calcula la sombra que proyecta cada bloque según la posición de la luz.
 * Las sombras que regresa este método ya se dibujan en pantalla (GamePanel.drawShadows)
 * y ya se le pasan al jugador (Player.update) para que se vuelvan sólidas.
 *
 * Algoritmo sugerido (luz puntual en 2D):
 *   1. Para cada esquina C del bloque, calcula la dirección desde la luz y normalízala:
 *          d = (C - luz).normalize()
 *   2. Proyecta esa esquina lejos, en la misma dirección:
 *          C' = C + d * SHADOW_LENGTH
 *   3. La sombra es el polígono que une las esquinas "de silueta" del bloque con sus
 *      proyecciones. Forma fácil de obtenerlo: toma las 4 esquinas + las 4 proyectadas
 *      y calcula su envolvente convexa (convex hull, por ejemplo con el algoritmo
 *      "monotone chain" de Andrew).
 *   4. Convierte el polígono a Path2D (moveTo, lineTo, closePath) y agrégalo a la lista.
 *
 * Mientras no esté implementado regresa una lista vacía y el juego sigue funcionando.
 */
public class ShadowCaster {

    /** Qué tan larga es la sombra. Ajústalo para balancear los niveles. */
    public static final double SHADOW_LENGTH = 450;

    public List<Path2D> computeShadows(LightSource light, List<Block> blocks) {
        List<Path2D> shadows = new ArrayList<>();
        Vec2 lightPos = light.getPosition();

        for (Block block : blocks) {
            Vec2[] corners = block.getCorners();
            // TODO (v0.7): con 'lightPos' y 'corners' construir el polígono de sombra
            //   de este bloque y agregarlo a 'shadows'.
        }
        return shadows;
    }
}
