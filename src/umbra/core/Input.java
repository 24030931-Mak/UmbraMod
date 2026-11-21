package umbra.core;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Arrays;

/**
 * Guarda el estado del teclado y del ratón.
 * Swing avisa de cada evento; el juego solo pregunta "¿está presionada?" en cada update.
 */
public class Input implements KeyListener, MouseListener, MouseMotionListener {

    private static final int MAX_KEYS = 512;

    private final boolean[] down = new boolean[MAX_KEYS];
    private final boolean[] pressed = new boolean[MAX_KEYS];   // solo el update en que se presionó
    private final boolean[] ignored = new boolean[MAX_KEYS];   // apretadas que no cuentan hasta soltarlas

    private int mouseX = Config.WIDTH / 2;
    private int mouseY = Config.HEIGHT / 4;
    private boolean mouseDown;
    private boolean clicked;   // solo el update en que se hizo clic

    /** true solo en el update en que se presionó un botón del ratón (para fijar la luz). */
    public boolean wasClicked() {
        return clicked;
    }

    /** true mientras la tecla esté presionada (para caminar). */
    public boolean isDown(int keyCode) {
        return valid(keyCode) && down[keyCode] && !ignored[keyCode];
    }

    /**
     * Las teclas dadas que estén apretadas ahora dejan de contar (isDown da false) hasta que se
     * suelten; al volver a apretarlas cuentan otra vez. Sirve al cruzar un paso o reiniciar.
     */
    public void ignoreUntilReleased(int... keyCodes) {
        for (int k : keyCodes) {
            if (valid(k) && down[k]) ignored[k] = true;
        }
    }

    /** true solo en el update en que se presionó (para saltar, reiniciar, etc.). */
    public boolean wasPressed(int keyCode) {
        return valid(keyCode) && pressed[keyCode];
    }

    /** Se llama al final de cada update para "olvidar" las teclas recién presionadas. */
    public void endUpdate() {
        Arrays.fill(pressed, false);
        clicked = false;
    }

    public int getMouseX() { return mouseX; }
    public int getMouseY() { return mouseY; }
    public boolean isMouseDown() { return mouseDown; }

    private boolean valid(int keyCode) {
        return keyCode >= 0 && keyCode < MAX_KEYS;
    }

    // ---- Teclado ----

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (!valid(k)) return;
        if (!down[k]) pressed[k] = true;   // evita repetir mientras se mantiene presionada
        down[k] = true;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (valid(k)) { down[k] = false; ignored[k] = false; }
    }

    @Override public void keyTyped(KeyEvent e) { }

    // ---- Ratón ----

    @Override public void mouseMoved(MouseEvent e)    { mouseX = e.getX(); mouseY = e.getY(); }
    @Override public void mouseDragged(MouseEvent e)  { mouseX = e.getX(); mouseY = e.getY(); }
    @Override public void mousePressed(MouseEvent e)  { if (!mouseDown) clicked = true; mouseDown = true; }
    @Override public void mouseReleased(MouseEvent e) { mouseDown = false; }
    @Override public void mouseClicked(MouseEvent e)  { }
    @Override public void mouseEntered(MouseEvent e)  { }
    @Override public void mouseExited(MouseEvent e)   { }
}
