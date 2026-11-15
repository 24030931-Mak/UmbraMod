#!/bin/sh
# Compila y ejecuta Umbra desde la terminal (Linux / macOS). Requiere JDK 8 o superior.
cd "$(dirname "$0")" || exit 1
javac -encoding UTF-8 -d out -sourcepath src src/umbra/Main.java && java -cp out umbra.Main
