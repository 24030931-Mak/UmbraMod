package umbra.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import umbra.world.SaveData;

/**
 * Los archivos de los 3 espacios de partida: partida1.txt a partida3.txt en una carpeta.
 * Escribe con un archivo temporal y lo renombra, para que un cierre a medias no deje una partida
 * rota. Ningún método lanza excepción ni imprime trazas: si algo falla, lo dice con false o null.
 */
public final class SaveStore {

    public static final int SLOTS = 3;

    public enum Status { EMPTY, OK, DAMAGED }

    private final File dir;

    public SaveStore(File dir) {
        this.dir = dir;
    }

    /**
     * La carpeta de las partidas: la propiedad -Dumbra.saveDir (para las pruebas), después
     * %APPDATA%\Umbra y, si no hay APPDATA, ~/.umbra.
     */
    public static File defaultDir() {
        String prop = System.getProperty("umbra.saveDir");
        if (prop != null && !prop.isEmpty()) return new File(prop);
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isEmpty()) return new File(appData, "Umbra");
        return new File(System.getProperty("user.home", "."), ".umbra");
    }

    private File file(int slot) {
        if (slot < 0 || slot >= SLOTS) throw new IllegalArgumentException("espacio " + slot);
        return new File(dir, "partida" + (slot + 1) + ".txt");
    }

    /** EMPTY si no hay archivo, OK si se puede leer, DAMAGED si existe pero no se puede leer. */
    public Status status(int slot) {
        File f = file(slot);
        if (!f.exists()) return Status.EMPTY;
        return read(f) != null ? Status.OK : Status.DAMAGED;
    }

    /** La partida del espacio, o null si está vacío o dañado. */
    public SaveData load(int slot) {
        File f = file(slot);
        return f.exists() ? read(f) : null;
    }

    private static SaveData read(File f) {
        try {
            return SaveData.fromText(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Escribe la partida en el espacio. Regresa false si algo falla (sin excepción). */
    public boolean save(int slot, SaveData d) {
        File dest = file(slot);
        File tmp = new File(dir, "partida" + (slot + 1) + ".tmp");
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            Files.write(tmp.toPath(), d.toText().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(dest)) {
                // En Windows el renombre no pisa un archivo que ya existe
                dest.delete();
                if (!tmp.renameTo(dest)) {
                    tmp.delete();
                    return false;
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            tmp.delete();
            return false;
        }
    }

    /** Borra el espacio. Regresa true si al terminar ya no hay archivo. */
    public boolean delete(int slot) {
        File f = file(slot);
        try {
            return !f.exists() || f.delete();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
