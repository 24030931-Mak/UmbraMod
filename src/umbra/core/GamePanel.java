package umbra.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.Timer;

import umbra.entities.LightSource;
import umbra.entities.Player;
import umbra.graphics.Background;
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
    private static final Color SHADOW_EDGE = new Color(150, 110, 225, 110);
    private static final Stroke SHADOW_EDGE_STROKE = new BasicStroke(2f);
    private static final Font HUD_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 14);
    private static final Font BIG_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 32);

    private final Input input = new Input();
    private final Timer timer;

    private final ShadowCaster shadowCaster = new ShadowCaster();
    private final ParticleSystem particles = new ParticleSystem();

    /** Al reaparecer, qué tan lejos de la orilla de la repisa se coloca al jugador (px). */
    private static final double RESPAWN_MARGIN = 12;

    private Level level;
    private Player player;
    private LightSource light;
    private Camera camera;
    private Background background;
    private Vec2 checkpoint;   // dónde reaparece si se cae: la última repisa firme que pisó
    private List<Path2D> shadows = new ArrayList<>();
    private boolean levelComplete;
    private double deathTimer;   // segundos desde que Candil se desmoronó
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

        loadLevel(Level.createLevel1());

        // El Timer "despierta" cada 10 ms. Si se le pidieran 16 ms (1000 / 60), Windows los
        // redondea a ~30 ms por la resolución de su reloj, y el juego se veía a ~34 FPS.
        // La lógica no cambia: el acumulador de tick() sigue haciendo pasos fijos de 1/60 s.
        timer = new Timer(10, e -> tick());
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
        background = new Background(level.getWidth(), 7L);   // semilla fija: la cueva siempre igual
        checkpoint = level.getSpawn();
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

        // ---- Luz: sigue al ratón suavemente; con un clic se fija o se suelta ----
        // El ratón está en coordenadas de pantalla y la luz vive en el mundo:
        // la cámara convierte de una a otra con la inversa de su matriz de vista.
        if (input.wasClicked()) light.togglePinned();
        Vec2 mouse = camera.screenToWorld(input.getMouseX(), input.getMouseY());
        light.setTarget(mouse.x, mouse.y);
        light.update(dt);

        // ---- Sombras [AVANCE 70%] ----
        shadows = shadowCaster.computeShadows(light, level.getBlocks());

        // ---- Mundo y jugador ----
        level.update(dt);
        background.update(dt);   // niebla y esporas

        if (!levelComplete && player.isDead()) {
            // Se desmoronó: espera un momento y reaparece en la última repisa firme
            deathTimer += dt;
            if (deathTimer >= Config.RESPAWN_DELAY) {
                player.respawn(checkpoint);
                deathTimer = 0;
            }
        } else if (!levelComplete) {
            boolean wasOnGround = player.isOnGround();
            player.update(dt, input, level, shadows);

            // Aterrizaje: chispas [ENTREGA FINAL]
            if (!wasOnGround && player.isOnGround()) {
                particles.emit(player.getCenterX(), player.getBottom(), 12);
            }

            // Demasiado cerca de la Lumbre: se desmorona [v1.0].
            // Se comparan distancias al cuadrado para no calcular la raíz.
            double dx = light.getX() - player.getCenterX();
            double dy = light.getY() - player.getCenterY();
            if (dx * dx + dy * dy <= Config.CRUMBLE_RADIUS * Config.CRUMBLE_RADIUS) {
                player.crumble();
                light.setPinned(false);   // si estaba fija ahí, no lo deshace otra vez al reaparecer
            }

            // Recuerda la última repisa firme que pisó, para reaparecer ahí
            Rectangle2D ground = level.getPlatformUnder(player.getBounds());
            if (ground != null) {
                double minX = ground.getMinX() + RESPAWN_MARGIN;
                double maxX = ground.getMaxX() - Player.W - RESPAWN_MARGIN;
                double x = Math.max(minX, Math.min(player.getX(), maxX));
                checkpoint = new Vec2(x, ground.getMinY() - Player.H);
            }

            // Se cayó al vacío: de vuelta a esa repisa
            if (player.getY() > Config.HEIGHT + 100) {
                player.respawn(checkpoint);
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
        checkpoint = level.getSpawn();
        light.setPinned(false);
        deathTimer = 0;
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

        // El orden importa: lo que se dibuja después queda encima.
        // 0. Fondo de la cueva: cada capa con su propio parallax (coordenadas de pantalla)
        double camX = camera.getX();
        background.drawBack(g2, camX);

        // El mundo se dibuja con la matriz de vista de la cámara (mundo -> pantalla)
        AffineTransform screen = g2.getTransform();
        g2.transform(camera.getViewMatrix().toAffineTransform());
        light.drawGlow(g2);              // 1. halo de luz sobre el fondo
        drawShadows(g2);                 // 2. sombras (el refugio de Candil)
        level.draw(g2);                  // 3. repisas, bloques y salida
        background.drawSpores(g2, camX); // 4. esporas flotando
        particles.draw(g2);              // 5. partículas [FINAL]
        player.draw(g2);                 // 6. jugador
        light.drawCore(g2);              // 7. punto brillante de la luz

        // Lo demás no usa la matriz de vista: se dibuja en coordenadas de pantalla
        g2.setTransform(screen);
        background.drawFront(g2, camX);  // 8. neblina del abismo, primer plano y viñeta
        drawHud(g2);                     // 9. textos

        g2.dispose();
        countFps();
    }

    /** Barra de vida arriba a la derecha: el color pasa de rojo (vacía) a azul claro (llena). */
    private void drawLifeBar(Graphics2D g) {
        int w = 200, h = 10;
        int x = Config.WIDTH - w - 16, y = 16;
        double life = player.getLife();

        g.setColor(new Color(220, 220, 235));
        g.drawString("Vida", x - 44, y + 10);
        g.setColor(new Color(30, 30, 45));
        g.fillRoundRect(x, y, w, h, h, h);
        int r = (int) (230 + (150 - 230) * life);
        int gr = (int) (70 + (225 - 70) * life);
        int b = (int) (70 + (255 - 70) * life);
        g.setColor(new Color(r, gr, b));
        g.fillRoundRect(x, y, (int) Math.round(w * life), h, h, h);
        g.setColor(new Color(150, 150, 170));
        g.drawRoundRect(x, y, w, h, h, h);
    }

    private void drawShadows(Graphics2D g) {
        g.setColor(SHADOW_COLOR);
        for (Path2D shadow : shadows) {
            g.fill(shadow);
        }
        // Borde tenue: con el fondo de cueva, ayuda a ver dónde termina el refugio
        Stroke old = g.getStroke();
        g.setStroke(SHADOW_EDGE_STROKE);
        g.setColor(SHADOW_EDGE);
        for (Path2D shadow : shadows) {
            g.draw(shadow);
        }
        g.setStroke(old);
    }

    private void drawHud(Graphics2D g) {
        g.setFont(HUD_FONT);
        g.setColor(new Color(220, 220, 235));
        g.drawString(Config.TITLE + " " + Config.VERSION, 16, 24);
        g.setColor(new Color(150, 150, 170));
        g.drawString("A/D: mover   W/Espacio: saltar o subir   S: bajar   Mouse: mover la luz"
                + "   Clic: fijar/soltar la luz   R: reiniciar   F3: debug", 16, 44);
        if (light.isPinned()) {
            g.setColor(new Color(255, 230, 150));
            g.drawString("Luz fija (clic para soltarla)", 16, 64);
        }
        drawLifeBar(g);

        if (debug) {
            int y = Config.HEIGHT - 60;
            g.setColor(new Color(120, 255, 160));
            g.drawString(String.format("FPS: %d   camara: %.0f de %.0f", fps,
                    camera.getX(), level.getWidth() - Config.WIDTH), 16, y);
            g.drawString(String.format("Jugador: (%.0f, %.0f)  en suelo: %b  en sombra: %b  vida: %.0f%%",
                    player.getX(), player.getY(), player.isOnGround(), player.isInShadow(),
                    player.getLife() * 100), 16, y + 18);
            g.drawString(String.format("Luz: (%.0f, %.0f)  fija: %b  sombras: %d",
                    light.getX(), light.getY(), light.isPinned(), shadows.size()), 16, y + 36);
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
