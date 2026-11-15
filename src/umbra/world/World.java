package umbra.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import umbra.core.Config;

/**
 * El mundo: los niveles en fila, de izquierda a derecha. La salida derecha de cada nivel
 * conecta con la entrada izquierda del siguiente, y se puede volver.
 *
 * Solo guarda cuál nivel es el actual y cuáles ya se visitaron (para la leyenda
 * "Pasaste al nivel 2" y para el mapa) y qué zonas de cada nivel ya se recorrieron. Los niveles se crean una sola vez.
 */
public class World {

    private final List<Level> levels;
    private final boolean[] visited;
    private final Exploration[] explored;   // lo recorrido de cada nivel, para el mapa
    private int current;

    public World(List<Level> levels) {
        this.levels = Collections.unmodifiableList(new ArrayList<>(levels));
        this.visited = new boolean[levels.size()];
        this.explored = new Exploration[levels.size()];
        for (int i = 0; i < explored.length; i++) {
            explored[i] = new Exploration(levels.get(i).getWidth(), Config.HEIGHT);
        }
        reset();
    }

    /** El mundo del juego: el nivel 1 y el nivel 2. */
    public static World createDefault() {
        return new World(Arrays.asList(Level.createLevel1(), Level.createLevel2()));
    }

    public int size() { return levels.size(); }
    public Level get(int i) { return levels.get(i); }
    public Level current() { return levels.get(current); }
    public int currentIndex() { return current; }
    public boolean isVisited(int i) { return visited[i]; }
    /** Las zonas recorridas del nivel 'i'. */
    public Exploration exploration(int i) { return explored[i]; }

    /** Pasa al nivel 'i'. Regresa true si era la primera vez que se entra a él. */
    public boolean enter(int i) {
        boolean first = !visited[i];
        visited[i] = true;
        current = i;
        return first;
    }

    /** Partida nueva: vuelve al primer nivel y olvida lo visitado y lo recorrido. */
    public void reset() {
        Arrays.fill(visited, false);
        for (Exploration e : explored) e.reset();
        enter(0);
    }
}
