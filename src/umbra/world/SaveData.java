package umbra.world;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import umbra.math.Vec2;

/**
 * La foto de una partida: el estado de World (zona actual, zonas visitadas, fragmentos tomados y
 * lo recorrido del mapa de cada zona) más el tiempo jugado, las muertes y el punto de reaparición.
 *
 * Se escribe como texto "clave=valor", una clave por renglón. Si el texto está dañado o es de otra
 * versión, fromText regresa null: nunca lanza excepción.
 */
public final class SaveData {

    /** Versión del formato. Un archivo con otra versión se trata como dañado. */
    public static final int VERSION = 1;

    public final int zone;
    public final boolean[] visited;
    public final boolean[] taken;
    /** Lo recorrido de cada zona, tal como lo escribe Exploration.encode(). */
    public final String[] explored;
    public final double playTime;
    public final int deaths;
    public final double spawnX, spawnY;

    public SaveData(int zone, boolean[] visited, boolean[] taken, String[] explored,
                    double playTime, int deaths, double spawnX, double spawnY) {
        this.zone = zone;
        this.visited = visited;
        this.taken = taken;
        this.explored = explored;
        this.playTime = playTime;
        this.deaths = deaths;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
    }

    /** Toma la foto del mundo en este momento. */
    public static SaveData capture(World w, double playTime, int deaths, Vec2 spawn) {
        boolean[] visited = new boolean[w.size()];
        String[] explored = new String[w.size()];
        for (int i = 0; i < w.size(); i++) {
            visited[i] = w.isVisited(i);
            explored[i] = w.exploration(i).encode();
        }
        boolean[] taken = new boolean[World.FRAGMENTS];
        for (int i = 0; i < taken.length; i++) taken[i] = w.isTaken(i);
        return new SaveData(w.currentIndex(), visited, taken, explored, playTime, deaths, spawn.x, spawn.y);
    }

    /**
     * Pone el mundo como en la foto. Primero lo deja como partida nueva (w.reset()), así que no
     * queda nada de lo que había antes. Si la foto no cuadra con este mundo (número de zonas o
     * celdas), regresa false y el mundo queda como partida nueva.
     */
    public boolean applyTo(World w) {
        w.reset();
        if (visited.length != w.size() || explored.length != w.size() || taken.length != World.FRAGMENTS
                || zone < 0 || zone >= w.size()) {
            return false;
        }
        for (int i = 0; i < w.size(); i++) {
            if (!w.exploration(i).decode(explored[i])) {
                w.reset();
                return false;
            }
        }
        w.restore(zone, visited, taken);
        return true;
    }

    /** La fila del menú: "Los Pozos  ·  4/6  ·  23 min" (o "1 h 05 min"). */
    public String summary(World w) {
        int minutes = (int) (playTime / 60);
        String time = minutes < 60 ? minutes + " min"
                : (minutes / 60) + " h " + (minutes % 60 < 10 ? "0" : "") + (minutes % 60) + " min";
        int n = 0;
        for (boolean t : taken) if (t) n++;
        String name = zone >= 0 && zone < w.size() ? w.get(zone).getName() : "?";
        return name + "  \u00b7  " + n + "/" + World.FRAGMENTS + "  \u00b7  " + time;
    }

    /** El texto que se escribe en el archivo. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("version=").append(VERSION).append('\n');
        sb.append("zones=").append(visited.length).append('\n');
        sb.append("zone=").append(zone).append('\n');
        sb.append("visited=").append(bits(visited)).append('\n');
        sb.append("taken=").append(bits(taken)).append('\n');
        for (int i = 0; i < explored.length; i++) sb.append("explored.").append(i).append('=').append(explored[i]).append('\n');
        // Double.toString no depende del idioma del sistema y se lee igual con parseDouble
        sb.append("playTime=").append(playTime).append('\n');
        sb.append("deaths=").append(deaths).append('\n');
        sb.append("spawnX=").append(spawnX).append('\n');
        sb.append("spawnY=").append(spawnY).append('\n');
        return sb.toString();
    }

    /** Lee el texto de toText(). Regresa null si está dañado, incompleto o es de otra versión. */
    public static SaveData fromText(String s) {
        if (s == null) return null;
        try {
            Map<String, String> kv = new HashMap<>();
            for (String line : s.split("\\r?\\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) kv.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
            if (Integer.parseInt(need(kv, "version")) != VERSION) return null;
            int zones = Integer.parseInt(need(kv, "zones"));
            if (zones < 1 || zones > 1000) return null;
            int zone = Integer.parseInt(need(kv, "zone"));
            boolean[] visited = parseBits(need(kv, "visited"), zones);
            boolean[] taken = parseBits(need(kv, "taken"), World.FRAGMENTS);
            String[] explored = new String[zones];
            for (int i = 0; i < zones; i++) explored[i] = need(kv, "explored." + i);
            if (!exploredFit(explored)) return null;
            double playTime = Double.parseDouble(need(kv, "playTime"));
            int deaths = Integer.parseInt(need(kv, "deaths"));
            double spawnX = Double.parseDouble(need(kv, "spawnX"));
            double spawnY = Double.parseDouble(need(kv, "spawnY"));
            if (visited == null || taken == null || zone < 0 || zone >= zones) return null;
            if (!(playTime >= 0) || Double.isInfinite(playTime) || deaths < 0) return null;
            if (Double.isNaN(spawnX) || Double.isInfinite(spawnX) || Double.isNaN(spawnY) || Double.isInfinite(spawnY)) return null;
            return new SaveData(zone, visited, taken, explored, playTime, deaths, spawnX, spawnY);
        } catch (RuntimeException e) {
            return null;   // falta una clave o un número no se puede leer
        }
    }

    /** Los largos de lo recorrido de cada zona del mundo del juego (se calculan una sola vez). */
    private static int[] exploredLengths;

    /**
     * ¿Lo recorrido cuadra con las zonas del mundo del juego (cuántas son, el largo de cada una y
     * que sea hex)? Así un archivo editado o de otra versión sale dañado desde que se lee, sin
     * esperar a applyTo.
     */
    private static synchronized boolean exploredFit(String[] explored) {
        if (exploredLengths == null) {
            World w = World.createDefault();
            exploredLengths = new int[w.size()];
            for (int i = 0; i < w.size(); i++) exploredLengths[i] = w.exploration(i).encode().length();
        }
        if (explored.length != exploredLengths.length) return false;
        for (int i = 0; i < explored.length; i++) {
            if (explored[i].length() != exploredLengths[i] || !explored[i].matches("[0-9a-f]*")) return false;
        }
        return true;
    }

    private static String need(Map<String, String> kv, String key) {
        String v = kv.get(key);
        if (v == null) throw new IllegalArgumentException("falta " + key);
        return v;
    }

    private static String bits(boolean[] b) {
        StringBuilder sb = new StringBuilder(b.length);
        for (boolean x : b) sb.append(x ? '1' : '0');
        return sb.toString();
    }

    /** Cadena de 0/1 del largo pedido, o null si no cuadra. */
    private static boolean[] parseBits(String s, int length) {
        if (s.length() != length) return null;
        boolean[] b = new boolean[length];
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if (c != '0' && c != '1') return null;
            b[i] = c == '1';
        }
        return b;
    }

    @Override
    public String toString() {
        return "SaveData[zona " + zone + ", visitadas " + Arrays.toString(visited) + "]";
    }
}
