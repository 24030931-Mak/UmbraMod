package umbra;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import umbra.core.Config;
import umbra.core.GamePanel;

/**
 * Punto de entrada de Umbra.
 * Crea la ventana, le agrega el panel del juego y arranca el game loop.
 */
public class Main {

    public static void main(String[] args) {
        // Swing se debe crear en su propio hilo (Event Dispatch Thread)
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame(Config.TITLE + " " + Config.VERSION);
            GamePanel game = new GamePanel();

            // [B6] Al cerrar se guarda la partida (si se está jugando) y luego se sale
            window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            window.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    game.saveOnExit();
                    System.exit(0);
                }
            });
            window.setResizable(false);
            window.add(game);
            window.pack();                       // ajusta la ventana al tamaño del panel
            window.setLocationRelativeTo(null);  // centrada en la pantalla
            window.setVisible(true);

            game.start();
        });
    }
}
