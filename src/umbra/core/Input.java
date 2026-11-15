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

    private int mouseX = Config.WIDTH / 2;
    private int mouseY = Config.HEIGHT / 4;
    private boolean mouseDown;

    /** true mientras la tecla esté presionada (para caminar). */
    public boolean isDown(int keyCode) {
        return valid(keyCode) && down[keyCode];
    }

    /** true solo en el update en que se presionó (para saltar, reiniciar, etc.). */
    public boolean wasPressed(int keyCode) {
        return valid(keyCode) && pressed[keyCode];
    }

    /** Se llama al final de cada update para "olvidar" las teclas recién presionadas. */
    public void endUpdate() {
        Arrays.fill(pressed, false);
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
        if (valid(k)) down[k] = false;
    }

    @Override public void keyTyped(KeyEvent e) { }

    // ---- Ratón ----

    @Override public void mouseMoved(MouseEvent e)    { mouseX = e.getX(); mouseY = e.getY(); }
    @Override public void mouseDragged(MouseEvent e)  { mouseX = e.getX(); mouseY = e.getY(); }
    @Override public void mousePressed(MouseEvent e)  { mouseDown = true; }
    @Override public void mouseReleased(MouseEvent e) { mouseDown = false; }
    @Override public void mouseClicked(MouseEvent e)  { }
    @Override public void mouseEntered(MouseEvent e)  { }
    @Override public void mouseExited(MouseEvent e)   { }
}
