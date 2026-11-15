package umbra.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Abre los archivos de src/umbra/res (música y textos).
 *
 * Busca primero en el classpath (así funciona dentro del .jar y desde el IDE) y, si no
 * está ahí, en la carpeta src (así funciona con run.bat y run.sh).
 */
public final class Resources {

    private Resources() { }

    /** @param path ruta como "/umbra/res/ambiente.wav" */
    public static InputStream open(String path) throws IOException {
        InputStream in = Resources.class.getResourceAsStream(path);
        if (in != null) return in;
        File file = new File("src" + path);
        if (file.isFile()) return new FileInputStream(file);
        throw new IOException("no se encontro " + path);
    }

    /**
     * Lee un archivo de texto en UTF-8 (así los acentos se ven bien en cualquier computadora).
     * Regresa una línea por elemento, sin las vacías ni las que empiezan con #.
     * Si el archivo no está, avisa en la consola y regresa una lista vacía.
     */
    public static List<String> readLines(String path) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(open(path), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                line = line.replace("﻿", "").trim();   // el Bloc de notas a veces agrega una marca BOM al inicio
                if (!line.isEmpty() && !line.startsWith("#")) lines.add(line);
            }
        } catch (IOException e) {
            System.err.println("Umbra: no se pudo leer " + path + " (" + e.getMessage() + ").");
        }
        return lines;
    }
}
