package umbra.core;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.Timer;

import umbra.entities.LightSource;
import umbra.entities.Player;
import umbra.graphics.Camera;
import umbra.graphics.ParticleSystem;
import umbra.graphics.ShadowCaster;
import umbra.math.Vec2;
import umbra.world.Level;

/**
 * Corazón del juego: contiene el game loop, actualiza la lógica y dibuja todo.
 *
 * Game loop con paso de tiempo fijo:
 *   - Un Timer de Swing "despierta" unas 60 veces por segundo.
 *   - Se mide cuánto tiempo real pasó y se ejecutan tantos update(STEP) como hagan falta.
 *   - Así la física (saltos, gravedad) se comporta igual en cualquier computadora.
 * Todo corre en el hilo de Swing, así que no hay problemas de concurrencia.
 */
public class GamePanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final double STEP = 1.0 / Config.UPS;

    private static final Color BACKGROUND = new Color(8, 8, 16);
    private static final Color SHADOW_COLOR = new Color(40, 20, 70, 220);
    private static final Font HUD_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 14);
    private static final Font BIG_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 32);

    private final Input input = new Input();
    private final Timer timer;

    private final ShadowCaster shadowCaster = new ShadowCaster();
    private final ParticleSystem particles = new ParticleSystem();

    private Level level;
    private Player player;
    private LightSource light;
    private Camera camera;
    private List<Path2D> shadows = new ArrayList<>();
    private boolean levelComplete;
    private boolean debug = true;

    // Tiempo y FPS
    private long lastTime;
    private double accumulator;
    private int frameCount;
    private long fpsTimer;
    private int fps;

    public GamePanel() {
        setPreferredSize(new Dimension(Config.WIDTH, Config.HEIGHT));
        setBackground(BACKGROUND);
        setDoubleBuffered(true);
        setFocusable(true);

        addKeyListener(input);
        addMouseListener(input);
        addMouseMotionListener(input);
        // Si el usuario hace clic en la ventana, recuperamos el foco del teclado
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { requestFocusInWindow(); }
        });

        loadLevel(Level.createTestLevel());

        timer = new Timer(1000 / Config.UPS, e -> tick());
        timer.setCoalesce(true);
    }

    /** Arranca el game loop. */
    public void start() {
        requestFocusInWindow();
        lastTime = System.nanoTime();
        fpsTimer = lastTime;
        timer.start();
    }

    private void loadLevel(Level newLevel) {
        level = newLevel;
        player = new Player(level.getSpawn());
        camera = new Camera(level.getWidth());
        light = new LightSource(input.getMouseX(), input.getMouseY());
        levelComplete = false;
    }

    // =========================================================
    //  GAME LOOP
    // =========================================================

    private void tick() {
        long now = System.nanoTime();
        double frameTime = (now - lastTime) / 1_000_000_000.0;
        lastTime = now;
        // Si la ventana se congeló (por ejemplo al arrastrarla), no intentamos "recuperar" todo
        frameTime = Math.min(frameTime, 0.25);

        accumulator += frameTime;
        while (accumulator >= STEP) {
            update(STEP);
            input.endUpdate();
            accumulator -= STEP;
        }

        repaint();
        Toolkit.getDefaultToolkit().sync();   // animación más fluida en Linux
    }

    private void update(double dt) {
        // ---- Teclas globales ----
        if (input.wasPressed(KeyEvent.VK_R)) restart();
        if (input.wasPressed(KeyEvent.VK_F3)) debug = !debug;

        // ---- Luz: sigue al ratón suavemente ----
        // El ratón está en coordenadas de pantalla y la luz vive en el mundo:
        // la cámara convierte de una a otra con la inversa de su matriz de vista.
        Vec2 mouse = camera.screenToWorld(input.getMouseX(), input.getMouseY());
        light.setTarget(mouse.x, mouse.y);
        light.update(dt);

        // ---- Sombras [AVANCE 70%] ----
        shadows = shadowCaster.computeShadows(light, level.getBlocks());

        // ---- Mundo y jugador ----
        level.update(dt);

        if (!levelComplete) {
            boolean wasOnGround = player.isOnGround();
            player.update(dt, input, level, shadows);

            // Aterrizaje: chispas [ENTREGA FINAL]
            if (!wasOnGround && player.isOnGround()) {
                particles.emit(player.getCenterX(), player.getBottom(), 12);
            }

            // Se cayó al vacío: de vuelta al inicio
            if (player.getY() > Config.HEIGHT + 100) {
                player.respawn(level.getSpawn());
            }

            if (level.isAtExit(player.getBounds())) {
                levelComplete = true;
                // TODO (v1.0): pasar al siguiente nivel en lugar de solo mostrar el mensaje
            }
        }

        // ---- Cámara: avanza cuando el jugador avanza ----
        camera.follow(player.getCenterX(), dt);

        particles.update(dt);
    }

    private void restart() {
        player.respawn(level.getSpawn());
        levelComplete = false;
    }

    // =========================================================
    //  RENDER
    // =========================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);   // pinta el fondo
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // El mundo se dibuja con la matriz de vista de la cámara (mundo -> pantalla).
        // El orden importa: lo que se dibuja después queda encima
        AffineTransform screen = g2.getTransform();
        g2.transform(camera.getViewMatrix().toAffineTransform());
        light.drawGlow(g2);       // 1. halo de luz sobre el fondo
        drawShadows(g2);          // 2. sombras (plataformas) [70%]
        level.draw(g2);           // 3. repisas, bloques y salida
        particles.draw(g2);       // 4. partículas [FINAL]
        player.draw(g2);          // 5. jugador
        light.drawCore(g2);       // 6. punto brillante de la luz

        // El HUD no se desplaza: se dibuja en coordenadas de pantalla
        g2.setTransform(screen);
        drawHud(g2);              // 7. textos

        g2.dispose();
        countFps();
    }

    private void drawShadows(Graphics2D g) {
        g.setColor(SHADOW_COLOR);
        for (Path2D shadow : shadows) {
            g.fill(shadow);
        }
    }

    private void drawHud(Graphics2D g) {
        g.setFont(HUD_FONT);
        g.setColor(new Color(220, 220, 235));
        g.drawString(Config.TITLE + " " + Config.VERSION, 16, 24);
        g.setColor(new Color(150, 150, 170));
        g.drawString("A/D: mover   Espacio: saltar   Mouse: mover la luz   R: reiniciar   F3: debug", 16, 44);

        if (debug) {
            int y = Config.HEIGHT - 60;
            g.setColor(new Color(120, 255, 160));
            g.drawString(String.format("FPS: %d   camara: %.0f de %.0f", fps,
                    camera.getX(), level.getWidth() - Config.WIDTH), 16, y);
            g.drawString(String.format("Jugador: (%.0f, %.0f)  en suelo: %b",
                    player.getX(), player.getY(), player.isOnGround()), 16, y + 18);
            g.drawString(String.format("Luz: (%.0f, %.0f)  sombras: %d",
                    light.getX(), light.getY(), shadows.size()), 16, y + 36);
        }

        if (levelComplete) {
            String msg = "¡Nivel completado!  (R para reiniciar)";
            g.setFont(BIG_FONT);
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(msg);
            int x = (Config.WIDTH - w) / 2;
            int y = Config.HEIGHT / 2;
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRoundRect(x - 24, y - 46, w + 48, 66, 20, 20);
            g.setColor(new Color(255, 230, 150));
            g.drawString(msg, x, y);
        }
    }

    private void countFps() {
        frameCount++;
        long now = System.nanoTime();
        if (now - fpsTimer >= 1_000_000_000L) {
            fps = frameCount;
            frameCount = 0;
            fpsTimer = now;
        }
    }
}
