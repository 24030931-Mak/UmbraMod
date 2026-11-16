package umbra.ui.intro;

import umbra.core.Input;

/**
 * [v0.8.6] Un teclado "de guion": en la intro nadie toca las teclas, pero Candil es el mismo
 * Player del juego y se mueve leyendo un Input. Esta clase contesta lo que Player pregunta
 * (isDown, wasPressed) con las teclas que la escena dice que están presionadas. Así Candil
 * camina, flota y se asusta con su física y sus formas de siempre, sin copiar nada.
 */
public final class ScriptedInput extends Input {

    private static final int MAX_KEYS = 512;

    private final boolean[] down = new boolean[MAX_KEYS];
    private final boolean[] pressed = new boolean[MAX_KEYS];

    /** Presiona o suelta una tecla (KeyEvent.VK_A, VK_D...), como si lo hiciera el jugador. */
    public void hold(int keyCode, boolean isDown) {
        if (isDown && !down[keyCode]) pressed[keyCode] = true;   // "recién presionada" solo el primer update
        down[keyCode] = isDown;
    }

    /** Suelta todas las teclas. */
    public void releaseAll() {
        java.util.Arrays.fill(down, false);
        java.util.Arrays.fill(pressed, false);
    }

    @Override
    public boolean isDown(int keyCode) {
        return keyCode >= 0 && keyCode < MAX_KEYS && down[keyCode];
    }

    @Override
    public boolean wasPressed(int keyCode) {
        return keyCode >= 0 && keyCode < MAX_KEYS && pressed[keyCode];
    }

    @Override
    public void endUpdate() {
        java.util.Arrays.fill(pressed, false);
    }
}
