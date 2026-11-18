package umbra.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * El mundo: las zonas del juego, conectadas por pasos en sus cuatro orillas (izquierda,
 * derecha, arriba y abajo), cada una con su posición en el plano. Los pasos van y vuelven.
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
            explored[i] = new Exploration(levels.get(i).getWidth(), levels.get(i).getHeight());
        }
        connectLevels();
        checkTargets();
        reset();
    }

    /**
     * Convierte las entradas de connect en pasos de toda la orilla hacia el vecino: la derecha
     * de la zona i lleva a la i+1 y la izquierda a la i-1. Antes quita los pasos que otro World
     * generó con estas mismas zonas, para no acumularlos.
     */
    private void connectLevels() {
        for (Level l : levels) l.clearConnectPassages();
        for (int i = 0; i < levels.size(); i++) {
            Level l = levels.get(i);
            if (i + 1 < levels.size() && l.getRightEntry() != null && levels.get(i + 1).getLeftEntry() != null) {
                Level next = levels.get(i + 1);
                l.addConnectPassage(new Passage(Passage.Side.RIGHT, 0, l.getHeight(), next, next.getLeftEntry(), null));
            }
            if (i > 0 && l.getLeftEntry() != null && levels.get(i - 1).getRightEntry() != null) {
                Level prev = levels.get(i - 1);
                l.addConnectPassage(new Passage(Passage.Side.LEFT, 0, l.getHeight(), prev, prev.getRightEntry(), null));
            }
        }
    }

    /** Todo paso debe llevar a una zona de este mundo: si no, cruzarlo rompería el juego. */
    private void checkTargets() {
        for (int i = 0; i < levels.size(); i++) {
            for (Passage p : levels.get(i).getPassages()) {
                if (!levels.contains(p.getTarget())) {
                    throw new IllegalArgumentException("El paso " + p.getSide() + " de la zona " + i
                            + " (" + levels.get(i).getName() + ") lleva a una zona que no es de este mundo");
                }
            }
        }
    }

    /** La posición de la zona 'l' en la lista, o -1 si no es de este mundo. */
    public int indexOf(Level l) { return levels.indexOf(l); }

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
