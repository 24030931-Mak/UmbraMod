@echo off
REM Compila y ejecuta Umbra desde la terminal (Windows). Requiere JDK 8 o superior.
cd /d "%~dp0"
javac -encoding UTF-8 -d out -sourcepath src src\umbra\Main.java || (pause & exit /b 1)
java -cp out umbra.Main
