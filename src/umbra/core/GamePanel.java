package umbra.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
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

import umbra.audio.Music;
import umbra.entities.LightSource;
import umbra.entities.Player;
import umbra.graphics.Background;
import umbra.graphics.Camera;
import umbra.graphics.LevelArt;
import umbra.graphics.ParticleSystem;
import umbra.graphics.ShadowCaster;
import umbra.math.Mat3;
import umbra.math.Vec2;
import umbra.ui.LevelTitle;
import umbra.ui.MapView;
import umbra.ui.MiniMap;
import umbra.ui.Menu;
import umbra.ui.Victory;
import umbra.world.Level;
import umbra.world.World;

/**
 * Corazón del juego: contiene el game loop, actualiza la lógica y dibuja todo.
 *
 * Game loop con paso de tiempo fijo:
 *   - Un Timer de Swing "despierta" cada 10 ms.
 *   - Se mide cuánto tiempo real pasó y se ejecutan tantos update(STEP) como hagan falta.
 *   - Así la física (saltos, gravedad) se comporta igual en cualquier computadora.
 * Todo corre en el hilo de Swing, así que no hay problemas de concurrencia.
 *
 * [v1.0] Pantallas (máquina de estados): MENU (título e historia) -> PLAYING (el nivel)
 * -> VICTORY (cierre). Cada cambio pasa por un fundido a negro.
 */
public class GamePanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final double STEP = 1.0 / Config.UPS;

    /** Las pantallas del juego. */
    private enum Screen { MENU, PLAYING, VICTORY }

    private static final Color BACKGROUND = new Color(8, 8, 16);
    private static final Color SHADOW_COLOR = new Color(40, 20, 70, 220);
    private static final Color SHADOW_EDGE = new Color(150, 110, 225, 110);
    private static final Stroke SHADOW_EDGE_STROKE = new BasicStroke(2f);
    private static final Font HUD_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 14);

    /** Al reaparecer, qué tan lejos de la orilla de la repisa se coloca al jugador (px). */
    private static final double RESPAWN_MARGIN = 12;
    /** Partículas por segundo: rastro al moverse y polvo con la vida en 0 (se reparte según la vida). */
    private static final double TRAIL_RATE = 40, DUST_RATE = 30;
    /** Segundos para oscurecer (o aclarar) la pantalla al cambiar de pantalla. */
    private static final double FADE_TIME = 0.6;
    /** El fundido, más corto, al cruzar el paso entre dos niveles. */
    private static final double PASSAGE_FADE_TIME = 0.4;
    /** Al reiniciar o reaparecer, si Logi quedó más cerca que esto de Candil (px), lo desmoronaría al instante. */
    private static final double ARRIVAL_SAFE_DISTANCE = 140;
    /** Cuánto se coloca Logi arriba de Candil al llegar a otro nivel (px). */
    private static final double ARRIVAL_LIFT = 220;
    /** Cuánto debe moverse el ratón (px en pantalla) para que Logi, en espera, vuelva a seguirlo. */
    private static final double MOUSE_WAKE_DISTANCE = 4;
    /** Qué tan cerca de Candil (px) deben quedar las zonas del nivel para contar como recorridas. */
    private static final double EXPLORE_RADIUS = 180;
    /** Segundos entre tocar la salida y pasar a la pantalla final. */
    private static final double VICTORY_DELAY = 1.0;
    /** Partículas de Logi por segundo: chispas mientras está fija (o sube rápido) y ceniza si va rápido. */
    private static final double SPARK_RATE = 12, ASH_RATE = 30;

    private final Input input = new Input();
    private final Timer timer;

    private final ShadowCaster shadowCaster = new ShadowCaster();
    private final ParticleSystem particles = new ParticleSystem();
    private final Music music = new Music(Config.MUSIC_FILE);
    private final Menu menu = new Menu(Resources.readLines("/umbra/res/historia.txt"));
    private final LevelTitle levelTitle = new LevelTitle();
    private final MapView mapView = new MapView();
    private final MiniMap miniMap = new MiniMap();
    private final Victory victory = new Victory(Resources.readLines("/umbra/res/final.txt"));
    /** Logi del menú y de la pantalla final: etérea, sigue al ratón en coordenadas de pantalla. */
    private final LightSource menuLight = new LightSource(Config.WIDTH / 2.0, Config.HEIGHT / 3.0);

    private final World world = World.createDefault();
    // Fondo y decoración de cada nivel: se generan una sola vez al abrir el juego
    private final Background[] backgrounds = new Background[world.size()];
    private final LevelArt[] arts = new LevelArt[world.size()];

    private Level level;        // el nivel actual (world.current())
    private Player player;
    private LightSource light;
    private Camera camera;
    /** Logi espera quieta arriba de Candil hasta que el jugador mueva el ratón (ver placeLogiAbove). */
    private boolean logiWaiting;
    private int waitMouseX, waitMouseY;   // dónde estaba el ratón (pantalla) cuando empezó la espera
    private Background background;
    private LevelArt levelArt;
    private Vec2 checkpoint;   // dónde reaparece si se cae: la última repisa firme que pisó
    private List<Path2D> shadows = new ArrayList<>();
    private boolean levelComplete;
    private double completeTimer;           // segundos desde que tocó la salida
    private double deathTimer;              // segundos desde que Candil se desmoronó
    private double trailTimer, dustTimer;   // acumuladores de partículas
    private double playTime;                // tiempo jugado en este intento (s)
    private int deaths;                     // veces que Candil se desmoronó en este intento
    private double sparkTimer, ashTimer;    // acumuladores de las partículas de Logi

    // Pantallas y fundido a negro
    private Screen screen = Config.SHOW_INTRO ? Screen.MENU : Screen.PLAYING;
    private double fade;        // 0 = se ve todo; 1 = negro
    private double fadeTime = FADE_TIME;   // segundos que dura el fundido en curso
    private int fadeDir;        // +1 oscureciendo, -1 aclarando, 0 quieto
    private Runnable onBlack;   // qué hacer cuando la pantalla quede negra
    private double menuTime;    // para mover despacio la cueva del menú

    private boolean debug = false;

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
        setFocusTraversalKeysEnabled(false);   // Swing se queda con Tab para cambiar el foco; el juego lo necesita

        addKeyListener(input);
        addMouseListener(input);
        addMouseMotionListener(input);
        // Si el usuario hace clic en la ventana, recuperamos el foco del teclado
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { requestFocusInWindow(); }
        });

        // Semillas fijas: cada cueva siempre es igual (7 y 11 el nivel 1; 23 y 29 el nivel 2)
        long[][] seeds = { { 7, 11 }, { 23, 29 } };
        for (int i = 0; i < world.size(); i++) {
            backgrounds[i] = new Background(world.get(i).getWidth(), seeds[i][0], i == 0 ? 1 : 1.5);   // nivel 2: ruinas más grandes
            arts[i] = new LevelArt(world.get(i), seeds[i][1]);   // roca, musgo, hongos y runas
        }
        loadLevel(0);
        if (screen == Screen.PLAYING) levelTitle.show(level.getNumber(), level.getName());
        menuLight.setEthereal(true);   // en el menú y en la pantalla final, Logi es fuego etéreo

        // El Timer "despierta" cada 10 ms. Si se le pidieran 16 ms (1000 / 60), Windows los
        // redondea a ~30 ms por la resolución de su reloj, y el juego se veía a ~34 FPS.
        // La lógica no cambia: el acumulador de tick() sigue haciendo pasos fijos de 1/60 s.
        timer = new Timer(10, e -> tick());
        timer.setCoalesce(true);
    }

    /** Arranca el game loop. */
    public void start() {
        requestFocusInWindow();
        music.play();
        lastTime = System.nanoTime();
        fpsTimer = lastTime;
        timer.start();
    }

    /** Carga el nivel 'index' del mundo desde su inicio, con un jugador nuevo. */
    private void loadLevel(int index) {
        world.enter(index);
        useCurrentLevel();
        player = new Player(level.getSpawn());
        checkpoint = level.getSpawn();
        light = new LightSource(input.getMouseX(), input.getMouseY());
        levelComplete = false;
    }

    /** Apunta level, fondo, decoración y cámara al nivel actual del mundo. */
    private void useCurrentLevel() {
        level = world.current();
        background = backgrounds[world.currentIndex()];
        levelArt = arts[world.currentIndex()];
        camera = new Camera(level.getWidth());
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
        // ---- Teclas que funcionan en todas las pantallas ----
        if (input.wasPressed(KeyEvent.VK_F3)) debug = !debug;
        if (input.wasPressed(KeyEvent.VK_M)) music.toggleMute();
        music.update(dt);   // entrada suave y silencio gradual

        updateFade(dt);
        boolean leaving = onBlack != null;   // oscureciendo para cambiar de pantalla: no se aceptan teclas

        switch (screen) {
            case MENU:    updateMenu(dt, leaving);    break;
            case PLAYING: updateGame(dt, leaving);    break;
            case VICTORY: updateVictory(dt, leaving); break;
        }
    }

    // =========================================================
    //  PANTALLAS Y FUNDIDO A NEGRO
    // =========================================================

    /** Pide un cambio de pantalla: oscurece, hace 'action' cuando ya no se ve nada, y aclara. */
    private void fadeTo(Runnable action) {
        fadeTo(action, FADE_TIME);
    }

    /** Igual, pero el fundido (cada mitad) dura 'seconds'. */
    private void fadeTo(Runnable action, double seconds) {
        if (onBlack != null) return;   // ya hay un cambio en camino
        onBlack = action;
        fadeTime = seconds;
        fadeDir = 1;
    }

    private void updateFade(double dt) {
        if (fadeDir == 0) return;
        fade += fadeDir * dt / fadeTime;
        if (fade >= 1) {
            fade = 1;
            fadeDir = -1;
            Runnable action = onBlack;
            onBlack = null;
            if (action != null) action.run();
        } else if (fade <= 0) {
            fade = 0;
            fadeDir = 0;
        }
    }

    /** Menú: la historia aparece sola; Enter o clic la completa y luego empieza el juego. */
    private void updateMenu(double dt, boolean leaving) {
        menuTime += dt;
        menu.update(dt);
        background.update(dt);
        menuLight.setTarget(input.getMouseX(), input.getMouseY());   // Logi sigue al ratón
        menuLight.update(dt);
        if (leaving) return;

        boolean confirm = input.wasPressed(KeyEvent.VK_ENTER) || input.wasPressed(KeyEvent.VK_SPACE)
                || input.wasClicked();
        if (confirm && menu.confirm()) {
            fadeTo(this::startGame);
        } else if (input.wasPressed(KeyEvent.VK_ESCAPE)) {
            System.exit(0);
        }
    }

    /** Pantalla final: Enter juega de nuevo, Esc regresa al menú. */
    private void updateVictory(double dt, boolean leaving) {
        victory.update(dt);
        background.update(dt);        // la niebla y las esporas siguen moviéndose
        levelArt.update(dt, null);    // las plantas se mecen con el aire
        particles.update(dt);
        menuLight.setTarget(input.getMouseX(), input.getMouseY());   // Logi, etérea, sigue al ratón
        menuLight.update(dt);
        if (leaving) return;

        boolean confirm = input.wasPressed(KeyEvent.VK_ENTER) || input.wasPressed(KeyEvent.VK_SPACE)
                || input.wasClicked();
        if (!victory.isReady()) {
            if (confirm) victory.skip();   // muestra todo el texto de una vez
        } else if (confirm) {
            fadeTo(this::startGame);
        } else if (input.wasPressed(KeyEvent.VK_ESCAPE)) {
            fadeTo(this::backToMenu);
        }
    }

    /** Empieza el nivel desde el principio (desde el menú o al jugar de nuevo). */
    private void startGame() {
        world.reset();
        useCurrentLevel();
        logiWaiting = false;   // partida nueva: Logi sigue al ratón desde el principio
        restart();
        particles.clear();
        playTime = 0;
        deaths = 0;
        screen = Screen.PLAYING;
        levelTitle.show(level.getNumber(), level.getName());
    }

    /**
     * Cruza el paso de una orilla: dir = -1 va al nivel anterior, +1 al siguiente. Se llama
     * con la pantalla en negro. Candil aparece en la entrada del nivel al que llega y
     * conserva su vida; esa entrada es ahora su punto de reaparición.
     *
     * Logi se suelta y aparece 220 px arriba de Candil, sin importar dónde esté el ratón, y
     * espera ahí hasta que el jugador lo mueva (ver placeLogiAbove). Si siguiera al ratón de
     * inmediato y éste quedó sobre Candil, lo desmoronaría antes de que se pueda reaccionar.
     */
    private void changeLevel(int dir) {
        int target = world.currentIndex() + dir;
        if (target < 0 || target >= world.size()) return;

        boolean first = world.enter(target);
        useCurrentLevel();
        Vec2 entry = dir > 0 ? level.getLeftEntry() : level.getRightEntry();
        player.moveTo(entry);
        camera.snapTo(player.getCenterX());
        checkpoint = entry;

        placeLogiAbove();

        particles.clear();
        String caption = first ? "Pasaste al nivel " + (target + 1) : null;
        levelTitle.show(level.getNumber(), level.getName(), caption);
    }

    /**
     * Suelta a Logi y la coloca 220 px arriba del centro de Candil (snapTo también reinicia su
     * velocidad: el salto no cuenta como movimiento). Se queda ahí mientras el ratón no se mueva
     * más de 4 px en pantalla desde este momento; en cuanto se mueve vuelve a seguirlo como
     * siempre (si el jugador la lleva hasta Candil, lo desmorona: es la mecánica normal).
     * La distancia al ratón se mide con Pitágoras: sqrt(dx² + dy²).
     */
    private void placeLogiAbove() {
        light.setPinned(false);
        light.snapTo(player.getCenterX(), player.getCenterY() - ARRIVAL_LIFT);
        logiWaiting = true;
        waitMouseX = input.getMouseX();
        waitMouseY = input.getMouseY();
    }

    /**
     * Al reiniciar o reaparecer: si Logi quedó a menos de 140 px de Candil (el ratón quieto
     * sobre el lugar de aparición) lo desmoronaría otra vez en pocos cuadros, así que se aplica
     * la misma regla que al cruzar de nivel. Si está lejos no se toca nada.
     */
    private void keepLogiAwayFromCandil() {
        double dx = light.getX() - player.getCenterX(), dy = light.getY() - player.getCenterY();
        if (dx * dx + dy * dy < ARRIVAL_SAFE_DISTANCE * ARRIVAL_SAFE_DISTANCE) placeLogiAbove();
    }

    private void backToMenu() {
        screen = Screen.MENU;
        menu.restart(false);   // la historia ya se vio: título y opciones de una vez
    }

    private void showVictory() {
        screen = Screen.VICTORY;
        victory.show(playTime, deaths);
    }

    // =========================================================
    //  EL NIVEL
    // =========================================================

    private void updateGame(double dt, boolean leaving) {
        if (leaving) return;   // oscureciendo: el nivel se queda quieto

        // ---- El mapa: pausa el juego; Tab o Esc lo cierran (Esc no va al menú) ----
        if (mapView.isOpen()) {
            mapView.update(dt);
            if (input.wasPressed(KeyEvent.VK_TAB) || input.wasPressed(KeyEvent.VK_ESCAPE)) closeMap();
            return;
        }
        // Solo se abre jugando de verdad: no durante un fundido ni con el nivel ya terminado
        if (input.wasPressed(KeyEvent.VK_TAB) && fadeDir == 0 && !levelComplete) {
            mapView.open();
            return;
        }
        if (input.wasPressed(KeyEvent.VK_ESCAPE)) {
            fadeTo(this::backToMenu);
            return;
        }
        if (input.wasPressed(KeyEvent.VK_R)) restart();
        levelTitle.update(dt);

        // ---- Luz: sigue al ratón suavemente; con un clic se fija o se suelta ----
        // El ratón está en coordenadas de pantalla y la luz vive en el mundo:
        // la cámara convierte de una a otra con la inversa de su matriz de vista.
        if (input.wasClicked()) light.togglePinned();
        Vec2 mouse = camera.screenToWorld(input.getMouseX(), input.getMouseY());
        light.setCandil(player.getCenterX(), player.getCenterY());   // lo mira, y se agita si está cerca
        if (logiWaiting && Math.hypot(input.getMouseX() - waitMouseX, input.getMouseY() - waitMouseY)
                > MOUSE_WAKE_DISTANCE) {
            logiWaiting = false;   // el jugador movió el ratón: Logi vuelve a seguirlo
        }
        if (!logiWaiting) light.setTarget(mouse.x, mouse.y);
        light.update(dt);
        emitLogiEffects(dt);   // chispas si está fija; ceniza si va rápido

        // ---- Sombras ----
        shadows = shadowCaster.computeShadows(light, level.getBlocks());

        // ---- Mundo y jugador ----
        level.update(dt);
        background.update(dt);         // niebla y esporas
        levelArt.update(dt, player);   // plantas que se mecen, hongos y runas

        if (!levelComplete && player.isDead()) {
            // Se desmoronó: espera un momento y reaparece en la última repisa firme
            deathTimer += dt;
            if (deathTimer >= Config.RESPAWN_DELAY) {
                player.respawn(checkpoint);
                deathTimer = 0;
                keepLogiAwayFromCandil();
            }
        } else if (!levelComplete) {
            boolean wasOnGround = player.isOnGround();
            // Candil se asusta si Logi está cerca: le decimos a qué distancia está
            player.setLogiDistance(Math.hypot(light.getX() - player.getCenterX(), light.getY() - player.getCenterY()));
            player.update(dt, input, level, shadows);
            // Candil vivo: lo que queda a menos de 180 px se marca como recorrido (para el mapa)
            if (!player.isDead()) {
                world.exploration(world.currentIndex()).reveal(player.getCenterX(), player.getCenterY(), EXPLORE_RADIUS);
            }

            // Aterrizaje: chispas
            if (!wasOnGround && player.isOnGround()) {
                particles.emit(player.getCenterX(), player.getBottom(), 12);
            }

            // Demasiado cerca de Logi: se desmorona [v1.0].
            // Se comparan distancias al cuadrado para no calcular la raíz.
            double dx = light.getX() - player.getCenterX();
            double dy = light.getY() - player.getCenterY();
            if (dx * dx + dy * dy <= Config.CRUMBLE_RADIUS * Config.CRUMBLE_RADIUS) {
                player.crumble();
                light.setPinned(false);   // si estaba fija ahí, no lo deshace otra vez al reaparecer
            }

            emitPlayerEffects(dt);

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
                keepLogiAwayFromCandil();
            }

            if (level.isAtExit(player.getBounds())) {
                levelComplete = true;
            } else if (!player.isDead() && onBlack == null) {
                // Tocar la orilla con paso: fundido corto y cambio de nivel (vivo y sin otro fundido)
                int dir = level.passageAt(player.getBounds());
                if (dir != 0) fadeTo(() -> changeLevel(dir), PASSAGE_FADE_TIME);
            }
        }

        // ---- La salida: un momento después, la pantalla final ----
        if (levelComplete) {
            completeTimer += dt;
            if (completeTimer >= VICTORY_DELAY) fadeTo(this::showVictory);
        } else {
            playTime += dt;
        }

        // ---- Cámara: avanza cuando el jugador avanza ----
        camera.follow(player.getCenterX(), dt);

        particles.update(dt);
    }

    /**
     * Partículas de Candil [v1.0]: niebla y ceniza si se acaba de desmoronar; si no, una
     * estela de vapor que suelta su capa cuando se mueve, y polvo de luz fuera de la sombra
     * (más entre menos vida le queda). Los acumuladores hacen que salgan a un ritmo fijo por
     * segundo, sin importar los FPS.
     */
    private void emitPlayerEffects(double dt) {
        if (player.isDead()) {
            particles.emitCrumble(player.getBounds());
            deaths++;
            return;
        }
        double cx = player.getCenterX(), cy = player.getCenterY();
        boolean moving = Math.abs(player.getVx()) > 1 || Math.abs(player.getVy()) > 60;
        trailTimer = moving ? trailTimer + dt * TRAIL_RATE : 0;
        while (trailTimer >= 1) {
            trailTimer -= 1;
            particles.emitVapor(cx, cy + 6, player.isInShadow());   // de la parte baja de la capa
        }
        dustTimer = player.isInShadow() ? 0 : dustTimer + dt * DUST_RATE * (1 - player.getLife());
        while (dustTimer >= 1) {
            dustTimer -= 1;
            particles.emitDust(cx, cy);
        }
    }

    /** Cierra el mapa: lo que se iluminó ya no se vuelve a encender la próxima vez. */
    private void closeMap() {
        mapView.close();
        for (int i = 0; i < world.size(); i++) world.exploration(i).markAllShown();
    }

    /**
     * Partículas de Logi [v1.0]: chispas que flotan mientras está fija (y al subir rápido, como
     * en la hoja de Logi) y ceniza que se queda atrás de la cola cuando va rápido (CHASE o RISING).
     * Mismos acumuladores que emitPlayerEffects: salen a un ritmo fijo por segundo, sin importar
     * los FPS.
     */
    private void emitLogiEffects(double dt) {
        LightSource.Form form = light.getForm();
        boolean sparks = light.isPinned() || form == LightSource.Form.RISING;
        boolean fast = form == LightSource.Form.CHASE || form == LightSource.Form.RISING;
        sparkTimer = sparks ? sparkTimer + dt * SPARK_RATE : 0;
        while (sparkTimer >= 1) {
            sparkTimer -= 1;
            particles.emitSpark(light.getX(), light.getY());
        }
        ashTimer = fast ? ashTimer + dt * ASH_RATE : 0;
        while (ashTimer >= 1) {
            ashTimer -= 1;
            particles.emitAsh(light.getX(), light.getY(), light.getVx(), light.getVy());
        }
    }

    private void restart() {
        player.respawn(level.getSpawn());
        checkpoint = level.getSpawn();
        light.setPinned(false);
        keepLogiAwayFromCandil();
        deathTimer = 0;
        levelComplete = false;
        completeTimer = 0;
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

        if (screen == Screen.MENU) {
            drawMenu(g2);
        } else {
            drawWorld(g2);
            if (screen == Screen.PLAYING) {
                drawHud(g2);
                levelTitle.draw(g2);
                mapView.draw(g2, world, new Vec2(player.getCenterX(), player.getCenterY()), light.getPosition());
            } else {
                victory.drawVeil(g2);
                menuLight.drawCore(g2);   // Logi etérea sigue al ratón: encima del velo, pero detrás del texto
                victory.drawText(g2);
            }
        }

        if (fade > 0) {   // fundido a negro entre pantallas
            g2.setColor(new Color(0, 0, 0, (int) Math.round(255 * fade)));
            g2.fillRect(0, 0, Config.WIDTH, Config.HEIGHT);
        }

        g2.dispose();
        countFps();
    }

    /** El menú: la cueva moviéndose despacio, Logi siguiendo al ratón, el título y la historia. */
    private void drawMenu(Graphics2D g2) {
        double camX = 300 + 250 * Math.sin(menuTime * 0.05);
        background.drawBack(g2, camX);
        AffineTransform onScreen = g2.getTransform();
        g2.transform(Mat3.translation(-Math.round(camX), 0).toAffineTransform());
        background.drawSpores(g2, camX);
        g2.setTransform(onScreen);
        menuLight.drawGlow(g2);
        background.drawFront(g2, camX);
        menu.drawVeil(g2);
        menuLight.drawCore(g2);   // Logi etérea: encima del velo (para que brille), pero detrás del texto
        menu.drawText(g2);
    }

    /** El nivel: fondo con parallax, sombras, roca, Candil, partículas y primer plano. */
    private void drawWorld(Graphics2D g2) {
        // El orden importa: lo que se dibuja después queda encima.
        // 0. Fondo de la cueva: cada capa con su propio parallax (coordenadas de pantalla)
        double camX = camera.getX();
        background.drawBack(g2, camX);

        // El mundo se dibuja con la matriz de vista de la cámara (mundo -> pantalla)
        AffineTransform onScreen = g2.getTransform();
        g2.transform(camera.getViewMatrix().toAffineTransform());
        light.drawGlow(g2);              // 1. halo de luz sobre el fondo
        levelArt.drawBehind(g2, camX);   //    columnas rotas (decoración)
        drawShadows(g2);                 // 2. sombras (el refugio de Candil)
        levelArt.drawFront(g2, camX, light); // 3. repisas de roca, hongos, losas y runas
        level.draw(g2);                  //    salida
        background.drawSpores(g2, camX); // 4. esporas flotando
        particles.draw(g2);              // 5. partículas
        player.draw(g2);                 // 6. jugador
        if (screen == Screen.PLAYING) light.drawCore(g2);   // 7. Logi (en la pantalla final, la etérea)

        // Lo demás no usa la matriz de vista: se dibuja en coordenadas de pantalla
        g2.setTransform(onScreen);
        background.drawFront(g2, camX);  // 8. neblina del abismo, primer plano y viñeta
    }

    /** Barra de vida a la derecha, justo debajo del círculo del mapa: el color pasa de rojo (vacía) a azul claro (llena). */
    private void drawLifeBar(Graphics2D g) {
        int w = 200, h = 10;
        int x = Config.WIDTH - w - 16, y = MiniMap.MARGIN + MiniMap.DIAMETER + 14;
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
        g.drawString("A/D: mover   W/Espacio: saltar o subir   S: bajar   Mouse: Logi   Clic: fijarla"
                + "   R: reiniciar   Tab: mapa   M: m\u00fasica   Esc: men\u00fa", 16, 44);
        if (light.isPinned()) {
            g.setColor(new Color(255, 230, 150));
            g.drawString("Logi est\u00e1 fija (clic para soltarla)", 16, 64);
        }
        drawLifeBar(g);
        if (!mapView.isOpen()) {   // el círculo se ve siempre al jugar, menos con el mapa grande
            miniMap.draw(g, level, world.exploration(world.currentIndex()),
                    new Vec2(player.getCenterX(), player.getCenterY()), light.getPosition(), playTime);
        }

        if (debug) {
            int y = Config.HEIGHT - 60;
            g.setColor(new Color(120, 255, 160));
            g.drawString(String.format("FPS: %d   camara: %.0f de %.0f   particulas: %d", fps,
                    camera.getX(), level.getWidth() - Config.WIDTH, particles.size()), 16, y);
            g.drawString(String.format("Jugador: (%.0f, %.0f)  en suelo: %b  en sombra: %b  vida: %.0f%%",
                    player.getX(), player.getY(), player.isOnGround(), player.isInShadow(),
                    player.getLife() * 100), 16, y + 18);
            g.drawString(String.format("Luz: (%.0f, %.0f)  fija: %b  sombras: %d",
                    light.getX(), light.getY(), light.isPinned(), shadows.size()), 16, y + 36);
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
