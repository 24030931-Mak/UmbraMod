package umbra.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import umbra.audio.FinaleAudio;
import umbra.audio.VoiceOver;
import umbra.core.Config;
import umbra.entities.LightSource;
import umbra.entities.LogiTemper;
import umbra.entities.Player;
import umbra.graphics.CandelabraArt;
import umbra.graphics.CandelabraState;
import umbra.graphics.ThroneHallArt;
import umbra.math.Vec2;
import umbra.ui.intro.Ease;
import umbra.ui.intro.SubtitleRenderer;
import umbra.ui.intro.SubtitleTrack;
import umbra.world.Level;

/**
 * [v0.9.5] La animación final: al tocar el Gran Farol, un guion por pasos mueve a Candil y a Logi
 * en la Cúpula real (la sala del trono, con la cámara fija arriba) y cambia el candelabro, el
 * rosetón y los braseros. No es una escena aparte: Candil es un Player (con su dibujo de siempre),
 * Logi se dibuja con LightSource.drawDark y el candelabro es el estado del propio nivel
 * (Level.getCandelabra), así que la pantalla final muestra el candelabro como quedó.
 *
 * Antes del primer paso hay una espera de 2 s (WAIT): Candil baja al estrado, Logi se queda donde
 * estaba y, entre 0,7 s y 1,5 s, el corazón de vidrio y las copas encendidas dan un destello (sin voz).
 *
 * Hay dos guiones. Con los 6 fragmentos el sello aguanta; con 0 a 5, falla:
 *
 *   aguanta                                   falla
 *   1. LUZ        5 s  voz_13                 1. LUZ        5 s  voz_18
 *   2. OSCURECE   5 s  voz_14                 2. OSCURECE   5 s  voz_14
 *   3. SE ABALANZA 2 s (rugido)               3. SE ABALANZA 2 s (rugido)
 *   4. EL TRONO   4 s  voz_15                 4. ESCAPA     4 s  voz_19
 *   5. GOLPES     3 s (tres golpes)           5. A MEDIAS   4 s  voz_20
 *   6. CALMA      5 s  voz_16
 *   7. BRILLA     3 s  voz_17
 *
 * Los segundos son la duración mínima de cada paso. Un paso termina cuando pasó su mínimo y su voz
 * terminó (VoiceOver deja de sonar y ya corrió lo que dura el clip), y nunca espera más de su mínimo
 * más VOICE_WAIT (12 s): si una voz no termina nunca, o si no hay tarjeta de sonido, el guion sigue.
 * Sin archivos de voz el aguanta dura 29 s y el falla 22 s (con la espera), más el fundido del final.
 *
 * Lo que pasa en cada paso (todo es función del tiempo del paso, t, con las curvas de Ease):
 *   (espera)   Candil, que pudo tocar la salida en el aire, baja al estrado (0,45 s).
 *   LUZ        Logi vuela a su lugar junto a la luz, abajo a la izquierda del candelabro. Los brazos con
 *              fragmento se apagan un instante y se encienden uno por uno, en el orden fijo
 *              (CandelabraState.lightOrder); cada pétalo del rosetón responde con su brazo.
 *   OSCURECE   Logi tiembla y pasa de naranja a rojo y a negro (de su oscuridad de partida, n/6, a 1), y crece.
 *   SE ABALANZA La llama negra se lanza hacia Candil y rebota; Candil corre bajo la punta del
 *              candelabro, donde aparece su sombra (un cono oscuro hasta el estrado).
 *   EL TRONO   Logi sube en curva hasta el corazón de vidrio y los 6 brazos se cierran sobre él.
 *   GOLPES     Logi golpea el vidrio tres veces: cada golpe sacude el candelabro, agrieta el
 *              vidrio y suelta polvo. Se encienden los braseros.
 *   CALMA      Logi se queda abajo en el vidrio, frente a Candil. BRILLA: todo quieto.
 *   ESCAPA     Logi entra al corazón; se cierran solo los brazos con fragmento y Logi se escurre
 *              por el lado del primer brazo que falta, hacia lo alto del domo, fuera de la vista.
 *   A MEDIAS   El candelabro a medio encender; los braseros siguen apagados.
 *
 * Al terminar (o al saltar) se oscurece la pantalla (1 s, o 0,35 s al saltar) y queda terminado:
 * GamePanel pasa entonces a la pantalla final. Lo que se mueve no crea objetos en cada cuadro:
 * las medidas se calculan una vez en start() y se guardan en números.
 */
public final class Finale {

    // ---- Los pasos ----
    private static final int LIGHT = 0, DARKEN = 1, LUNGE = 2, THRONE = 3, KNOCKS = 4, CALM = 5, GLOW = 6,
            ESCAPE = 7, HALF = 8;
    /** La duración mínima de cada paso (s), por número de paso. */
    private static final double[] MIN = { 5, 5, 2, 4, 3, 5, 3, 4, 4 };
    /** Lo más que un paso espera a su voz después de su mínimo (s). */
    public static final double VOICE_WAIT = 12;
    /** Los dos guiones: los pasos en orden y, para cada uno, su línea de final_animacion.txt (-1 = sin voz). */
    private static final int[] SEALED = { LIGHT, DARKEN, LUNGE, THRONE, KNOCKS, CALM, GLOW };
    private static final int[] SEALED_LINES = { 0, 1, -1, 2, -1, 3, 4 };
    private static final int[] BROKEN = { LIGHT, DARKEN, LUNGE, ESCAPE, HALF };
    private static final int[] BROKEN_LINES = { 5, 1, -1, 6, 7 };
    /** La voz de la línea n de final_animacion.txt es la n-ésima (voz_13 a voz_20). */
    private static final String[] VOICES = {
        "/umbra/res/voz_13.wav", "/umbra/res/voz_14.wav", "/umbra/res/voz_15.wav", "/umbra/res/voz_16.wav",
        "/umbra/res/voz_17.wav", "/umbra/res/voz_18.wav", "/umbra/res/voz_19.wav", "/umbra/res/voz_20.wav" };

    /** Segundos que tarda en oscurecerse la pantalla al terminar el guion, y al saltarlo. */
    public static final double END_FADE = 1.0, SKIP_FADE = 0.35;
    /** Segundos del principio en que no se puede saltar (ver skip). */
    public static final double SKIP_GUARD = 0.5;
    /** La espera antes del primer paso: Candil baja al estrado y el candelabro da un destello (s). */
    public static final double WAIT = 2.0;
    /** El destello: cuándo empieza (contado desde el inicio de la espera) y cuánto dura (s). */
    public static final double FLASH_AT = 0.7, FLASH_TIME = 0.8;

    // ---- Tiempos dentro de los pasos (s) ----
    private static final double SETTLE = 0.45;                         // Candil baja al estrado
    private static final double FLY = 2.5;                             // Logi llega a su lugar junto a la luz
    private static final double DIM = 0.3;                             // los brazos se apagan un instante...
    private static final double IGNITE_AT = 0.6, IGNITE_GAP = 0.55, IGNITE_TIME = 0.35;   // ...y se encienden uno por uno
    private static final double CLOSE_TIME = 0.8, CLOSE_GAP = 0.12;    // cada brazo tarda en cerrarse, y el siguiente empieza después
    private static final double THRONE_CLOSE = 1.9, ESCAPE_CLOSE = 1.5; // cuándo empiezan a cerrarse los brazos
    private static final double[] KNOCK_AT = { 0.35, 1.25, 2.15 };     // los golpes en el vidrio
    private static final double[] KNOCK_DX = { -0.55, 0.55, 0 }, KNOCK_DY = { 0.83, 0.83, 1 };   // hacia dónde golpea
    private static final double KNOCK_REACH = 20;                      // px que se lanza contra el vidrio
    private static final double LATER = 1e3;                           // "mucho después": el estado final de un paso

    // ---- Logi y la sombra ----
    private static final double DARK_SCALE = 1.7, INSIDE_SCALE = 1.5;  // la llama negra afuera, y dentro del vidrio
    private static final Color CONE = new Color(2, 3, 8);
    private static final double CONE_ALPHA = 0.62;

    // ---- El destello ----
    private static final int FLASH_SIZE = 256;                         // lado de la imagen del brillo (px)
    private static final double CUP_FLASH = 0.8, CUP_ALPHA = 0.7;      // el destello sobre cada copa encendida: tamaño (en radios del corazón) y fuerza
    private static BufferedImage flashImage;                           // se hace una sola vez, la primera vez que se dibuja

    private static final String SKIP_HINT = "Enter: saltar";

    /** Transparencias ya hechas (de 0 a 1 en 64 pasos), para no crear una en cada cuadro. */
    private static final AlphaComposite[] ALPHA = new AlphaComposite[65];

    static {
        for (int i = 0; i < ALPHA.length; i++) ALPHA[i] = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, i / 64f);
    }

    private final List<String> lines;
    private final Player candil = new Player(new Vec2(0, 0));   // el Candil del guion (el del juego no se toca)
    private final Path2D.Double coneShape = new Path2D.Double();

    // ---- El guion en curso ----
    private int[] script = SEALED, scriptLines = SEALED_LINES;
    private int beat;               // el paso actual (índice en el guion)
    private double beatTime;        // segundos desde que empezó el paso
    private double clock;           // segundos desde start(), para las llamas (sigue en la pantalla final)
    private double elapsed;         // segundos del guion (sin la pantalla final)
    private boolean voiced;         // el paso tiene voz y empezó a sonar
    private double voiceLength;     // lo que dura su clip (s)
    private SubtitleTrack subtitle; // el subtítulo del paso (null si no tiene)
    private int nextKnock;          // el siguiente golpe del paso GOLPES
    private double endTime = -1;    // segundos desde que empezó el fundido del final; -1 = todavía no
    private double endFade = END_FADE;
    private boolean started, finished;
    private boolean waiting;        // en la espera de antes del primer paso (ver WAIT)

    // ---- Medidas, calculadas en start() ----
    private Level level;
    private CandelabraState state;
    private Rectangle2D box, hall;
    private int fragments;
    private double heartX, heartY, heartR;
    private final double[] cupX = new double[CandelabraState.ARMS], cupY = new double[CandelabraState.ARMS];   // el centro de cada copa
    private double c0x, c0y, c1x, standY, refugeX;   // Candil: donde tocó, asentado y en la sombra (centros)
    private double l0x, l0y, l1x, l1y, l2y, l3x, l3y, l4x, l4y, qx, qy, restY;   // la ruta de Logi
    private double ex, ey, qex, qey;                  // la salida por el hueco (guion que falla)

    // ---- Lo que el guion pone en cada cuadro ----
    private double tcx, tcy;              // dónde va Candil (centro)
    private boolean grounded, sheltered;
    private double lx, ly, darkness, scale = 1;
    private double startDark;             // la oscuridad con que Logi llega al final: LogiTemper.darknessFor(fragmentos)
    private boolean inside;               // Logi está dentro del corazón de vidrio
    private double cone, dust, braziers;
    private int petals;

    /** @param lines las líneas de res/final_animacion.txt: una por voz, de voz_13 a voz_20 */
    public Finale(List<String> lines) {
        this.lines = new ArrayList<>(lines);
    }

    /** Abre de antemano las voces que existan y los sonidos del final (abrirlos al empezar congelaría un cuadro). */
    public static void preload() {
        VoiceOver.preloadKept(VOICES);
        FinaleAudio.preload();
    }

    /**
     * Empieza el guion. Con 6 fragmentos, el del sello que aguanta; con 0 a 5, el del sello que falla.
     * (candilX, candilY) es el centro de Candil al tocar la salida y (logiX, logiY), donde estaba Logi.
     */
    public void start(int fragments, Level cupula, double candilX, double candilY, double logiX, double logiY) {
        this.fragments = Math.max(0, Math.min(CandelabraState.ARMS, fragments));
        startDark = LogiTemper.darknessFor(this.fragments);   // [B5] Logi llega tan oscuro como venía jugando (6 fragmentos: ya negro)
        level = cupula;
        state = cupula.getCandelabra();
        Rectangle2D exit = cupula.getExit();
        box = cupula.getCandelabraBox() != null ? cupula.getCandelabraBox() : exit;
        hall = cupula.getThroneHall();
        boolean sealed = this.fragments == CandelabraState.ARMS;
        script = sealed ? SEALED : BROKEN;
        scriptLines = sealed ? SEALED_LINES : BROKEN_LINES;

        // Las medidas (una sola vez: en cada cuadro solo se usan estos números)
        Point2D heart = CandelabraArt.heartCenter(box);
        heartX = heart.getX();
        heartY = heart.getY();
        heartR = CandelabraArt.heartRadius(box);
        for (int arm = 0; arm < CandelabraState.ARMS; arm++) {
            Point2D cup = CandelabraArt.cupCenter(box, state, arm);
            cupX[arm] = cup.getX();
            cupY[arm] = cup.getY();
        }
        double floorY = exit.getMaxY();   // la cara de arriba del estrado
        c0x = candilX;
        c0y = candilY;
        c1x = Math.max(exit.getMinX(), Math.min(exit.getMaxX(), candilX));
        standY = floorY - Player.H / 2;
        refugeX = box.getCenterX();       // bajo la punta del candelabro
        l0x = logiX;
        l0y = logiY;
        l1x = heartX - 150;               // junto a la luz, abajo a la izquierda del candelabro
        l1y = heartY + 110;
        l2y = l1y - 15;
        l3x = heartX - 80;                // hasta donde llega al abalanzarse: la orilla de la sombra
        l3y = floorY - 70;
        l4x = heartX - 105;               // donde queda al rebotar
        l4y = floorY - 100;
        qx = heartX - 140;                // la curva con que sube al corazón
        qy = heartY + 34;
        restY = heartY + 16;              // quieto, abajo en el vidrio, frente a Candil
        int side = sealed ? 1 : CandelabraState.lightOrder(this.fragments) < 3 ? -1 : 1;   // el lado del hueco
        qex = heartX + side * 160;
        qey = heartY - 20;
        ex = heartX + side * 250;         // fuera de la vista, arriba del domo
        ey = box.getMinY() - 90;
        double tipY = box.getMaxY() - 10;
        coneShape.reset();                // la sombra del candelabro: de la punta al estrado
        coneShape.moveTo(refugeX - 24, tipY);
        coneShape.lineTo(refugeX + 24, tipY);
        coneShape.lineTo(refugeX + 72, floorY);
        coneShape.lineTo(refugeX - 72, floorY);
        coneShape.closePath();

        candil.respawn(new Vec2(c0x - Player.W / 2, c0y - Player.H / 2));
        candil.perform(0, c0x - Player.W / 2, c0y - Player.H / 2, c0y >= standY - 0.01, false, 0);
        started = true;
        finished = false;
        endTime = -1;
        endFade = END_FADE;
        clock = 0;
        elapsed = 0;
        // La espera: el candelabro y la sala quedan como al llegar; Candil baja al estrado y Logi se queda donde estaba
        waiting = true;
        beat = 0;
        beatTime = 0;
        nextKnock = 0;
        voiced = false;
        voiceLength = 0;
        subtitle = null;
        petals = this.fragments;
        braziers = 0;
        cone = 0;
        dust = 0;
        poseWait(0);
        level.setHallLights(petals, braziers);
    }

    /** La espera a los 't' s: Candil baja al estrado (como lo hacía el paso LUZ) y Logi queda donde estaba, con la oscuridad que traía. */
    private void poseWait(double t) {
        double s = Ease.smooth(t, 0, SETTLE);
        double y = Ease.lerp(c0y, standY, s);
        candilAt(Ease.lerp(c0x, c1x, s), y, y >= standY - 0.01, false);   // si ya estaba de pie, no "cae"
        logiAt(l0x, l0y, startDark, 1, false);
    }

    /** ¿Está en la espera de antes del primer paso? Es false si ya terminó o se saltó. */
    public boolean isWaiting() {
        return waiting && !finished && endTime < 0;
    }

    /** La fuerza del destello del candelabro, de 0 a 1: sube rápido (un cuarto del tiempo) y baja despacio. */
    public double getFlash() {
        if (!started || finished) return 0;
        double u = (elapsed - FLASH_AT) / FLASH_TIME;
        if (u <= 0 || u >= 1) return 0;
        return Ease.smooth(u, 0, 0.25) * (1 - Ease.smooth(u, 0.25, 1));
    }

    /**
     * Lo termina ya: la pantalla se oscurece en SKIP_FADE segundos y el guion queda terminado. En
     * los primeros SKIP_GUARD segundos no hace nada: Espacio también es saltar en el juego, y un
     * segundo toque al llegar de un salto no debe saltarse el final sin verlo.
     */
    public void skip() {
        if (!started || finished || elapsed < SKIP_GUARD) return;
        if (endTime < 0) {
            endTime = 0;
        } else if (endFade > SKIP_FADE) {
            endTime = endTime / endFade * SKIP_FADE;   // si ya se oscurecía, sigue desde lo oscuro que iba
        }
        endFade = SKIP_FADE;
    }

    public boolean isFinished() {
        return finished;
    }

    public void update(double dt) {
        clock += dt;
        if (!started) return;
        if (finished) {   // en la pantalla final: Candil sigue respirando donde quedó
            moveCandil(dt);
            return;
        }
        elapsed += dt;
        if (endTime >= 0) {
            endTime += dt;
            if (endTime >= endFade) {
                finish();
                return;
            }
        } else if (waiting) {
            if (elapsed >= WAIT) {
                waiting = false;
                enterBeat(0);
            }
        } else {
            beatTime += dt;
            if (beatDone()) {
                if (beat + 1 < script.length) enterBeat(beat + 1);
                else endTime = 0;   // el último paso terminó: el fundido del final
            }
        }
        if (waiting) {   // (también si se salta en la espera: el paso LUZ no se llega a ver)
            poseWait(elapsed);
            moveCandil(dt);
            level.setHallLights(petals, braziers);
            return;
        }
        int id = script[beat];
        pose(id, beatTime);
        if (id == KNOCKS && endTime < 0) {
            while (nextKnock < KNOCK_AT.length && beatTime >= KNOCK_AT[nextKnock]) {
                FinaleAudio.knock();
                nextKnock++;
            }
        }
        moveCandil(dt);
        level.setHallLights(petals, braziers);
    }

    /** ¿Ya pasó el mínimo del paso y terminó su voz? (o pasó el tope) */
    private boolean beatDone() {
        double min = MIN[script[beat]];
        if (beatTime >= min + VOICE_WAIT) return true;
        if (beatTime < min) return false;
        return !voiced || (beatTime >= voiceLength && !VoiceOver.isPlaying());
    }

    /**
     * Entra al paso i: su voz (si existe el archivo; si no, o si el paso no tiene, se calla la
     * anterior), su subtítulo (dura lo que el paso: su mínimo o su voz, sin pasar del tope) y el
     * rugido del paso en que Logi se abalanza.
     */
    private void enterBeat(int i) {
        beat = i;
        beatTime = 0;
        nextKnock = 0;
        int id = script[i], line = scriptLines[i];
        voiced = line >= 0 && VoiceOver.playIfAvailable(VOICES[line]);
        if (!voiced) VoiceOver.stop();
        voiceLength = voiced ? VoiceOver.getDurationSeconds() : 0;
        String text = line >= 0 && line < lines.size() ? lines.get(line) : null;
        double shown = Math.min(Math.max(MIN[id], voiceLength), MIN[id] + VOICE_WAIT);
        subtitle = text == null ? null : new SubtitleTrack().add(0, shown, text);
        if (id == LUNGE) FinaleAudio.roar();
    }

    /** Termina: se calla la voz y todo queda como al final del guion (también si se saltó). */
    private void finish() {
        finished = true;
        waiting = false;
        VoiceOver.stop();
        beat = script.length - 1;
        pose(script[beat], LATER);
        candil.perform(0, tcx - Player.W / 2, tcy - Player.H / 2, grounded, sheltered, 0);
        level.setHallLights(petals, braziers);
        subtitle = null;
    }

    private void moveCandil(double dt) {
        candil.setLogiDistance(Math.hypot(lx - tcx, ly - tcy));   // con Logi cerca se asusta
        int face = lx < tcx - 2 ? -1 : lx > tcx + 2 ? 1 : 0;       // quieto, mira a Logi
        candil.perform(dt, tcx - Player.W / 2, tcy - Player.H / 2, grounded, sheltered, face);
    }

    // =========================================================
    //  LOS PASOS
    // =========================================================

    /** Pone todo como va en el paso 'id' a los 't' segundos. */
    private void pose(int id, double t) {
        state.shake = 0;
        dust = 0;
        switch (id) {
            case LIGHT: {
                candilAt(c1x, standY, true, false);   // ya bajó al estrado en la espera
                double f = Ease.smooth(t, 0, FLY);
                logiAt(Ease.lerp(l0x, l1x, f), Ease.lerp(l0y, l1y, f), startDark, 1, false);
                // Los brazos con fragmento se apagan un instante y se encienden uno por uno
                int lit = 0;
                double sum = 0;
                for (int k = 0; k < CandelabraState.ARMS; k++) {
                    int arm = CandelabraState.lightOrder(k);
                    double v = 0;
                    if (k < fragments) {
                        double at = IGNITE_AT + IGNITE_GAP * k;
                        double ignite = Ease.smooth(t, at, at + IGNITE_TIME);
                        v = Math.max(1 - Ease.smooth(t, 0, DIM), ignite);
                        if (ignite >= 0.5) lit++;
                    }
                    state.armLit[arm] = v;
                    state.armClosed[arm] = 0;
                    sum += v;
                }
                state.glassWarm = sum / CandelabraState.ARMS;
                state.cracks = 0;
                petals = t < DIM / 2 ? fragments : lit;
                braziers = 0;
                cone = 0;
                break;
            }
            case DARKEN: {
                candilAt(c1x, standY, true, false);
                double d = Ease.smooth(t, 0.5, 4.5);
                double tremble = 1 + 3 * d;
                logiAt(l1x + tremble * Math.sin(t * 41), Ease.lerp(l1y, l2y, Ease.smooth(t, 0, 4.5)) + 0.5 * tremble * Math.sin(t * 53 + 1),
                        Ease.lerp(startDark, 1, d), 1 + (DARK_SCALE - 1) * Ease.smooth(t, 2.0, 4.6), false);
                gameArms(t, false, 0);
                cone = 0;
                break;
            }
            case LUNGE: {
                cone = Ease.smooth(t, 0, 0.5);
                candilAt(Ease.lerp(c1x, refugeX, Ease.smooth(t, 0.05, 0.6)), standY, true, cone > 0.5);
                double u = Ease.out(Ease.ramp(t, 0.1, 0.55)), back = Ease.smooth(t, 0.8, 1.7);
                double ax = Ease.lerp(l1x, l3x, u), ay = Ease.lerp(l2y, l3y, u);
                logiAt(Ease.lerp(ax, l4x, back), Ease.lerp(ay, l4y, back), 1, DARK_SCALE, false);
                gameArms(t, false, 0);
                break;
            }
            case THRONE: {
                cone = 1;
                candilAt(refugeX, standY, true, true);
                double u = Ease.smooth(t, 0.2, 1.8);
                logiOnCurve(l4x, l4y, qx, qy, heartX, heartY, u);
                logiAt(lx, ly, 1, Ease.lerp(DARK_SCALE, INSIDE_SCALE, u), u > 0.85);
                gameArms(t, true, THRONE_CLOSE);
                break;
            }
            case KNOCKS: {
                cone = 1;
                candilAt(refugeX, standY, true, true);
                double ox = 0, oy = 0, shake = 0, cracks = 0;
                for (int j = 0; j < KNOCK_AT.length; j++) {
                    double tk = KNOCK_AT[j];
                    double hit = Ease.smooth(t, tk - 0.22, tk) * (1 - Ease.smooth(t, tk, tk + 0.45));
                    ox += KNOCK_DX[j] * KNOCK_REACH * hit;
                    oy += KNOCK_DY[j] * KNOCK_REACH * hit;
                    if (t >= tk) shake += 6 * Math.exp(-(t - tk) * 6) * Math.sin((t - tk) * 38);
                    cracks += 0.34 * Ease.smooth(t, tk, tk + 0.08);
                }
                dust = knockDust(t);
                gameArms(t, true, -LATER);
                state.shake = shake;
                state.cracks = Math.min(1, cracks);
                logiAt(heartX + shake + ox, heartY + oy, 1, INSIDE_SCALE, true);
                braziers = Ease.smooth(t, 0.9, 2.7);
                break;
            }
            case CALM: {
                cone = 1;
                candilAt(refugeX, standY, true, true);
                logiAt(heartX, Ease.lerp(heartY, restY, Ease.smooth(t, 0.2, 1.4)), 1, INSIDE_SCALE, true);
                gameArms(t, true, -LATER);
                state.cracks = 1;
                dust = knockDust(MIN[KNOCKS] + t);   // el polvo del último golpe termina de caer
                braziers = 1;
                break;
            }
            case GLOW:
                pose(CALM, LATER);
                break;
            case ESCAPE: {
                cone = 1;
                candilAt(refugeX, standY, true, true);
                double u = Ease.smooth(t, 0.2, 1.4), v = Ease.in(Ease.ramp(t, 2.6, 3.7));
                if (v > 0) logiOnCurve(heartX, heartY, qex, qey, ex, ey, v);
                else logiOnCurve(l4x, l4y, qx, qy, heartX, heartY, u);
                double s = v > 0 ? Ease.lerp(INSIDE_SCALE, DARK_SCALE, v) : Ease.lerp(DARK_SCALE, INSIDE_SCALE, u);
                logiAt(lx, ly, 1, s, u > 0.85 && v < 0.15);
                gameArms(t, true, ESCAPE_CLOSE);
                braziers = 0;
                break;
            }
            case HALF:
                pose(ESCAPE, LATER);
                break;
            default:
                break;
        }
    }

    /** El polvo que sueltan los golpes a los 't' s del paso GOLPES: cada golpe lo levanta y tarda un segundo en caer. */
    private static double knockDust(double t) {
        double d = 0;
        for (double tk : KNOCK_AT) d = Math.max(d, Ease.smooth(t, tk, tk + 0.1) * (1 - Ease.smooth(t, tk + 0.4, tk + 1.3)));
        return d;
    }

    private void candilAt(double x, double y, boolean onFloor, boolean inShade) {
        tcx = x;
        tcy = y;
        grounded = onFloor;
        sheltered = inShade;
    }

    private void logiAt(double x, double y, double dark, double size, boolean inGlass) {
        lx = x;
        ly = y;
        darkness = dark;
        scale = size;
        inside = inGlass;
    }

    /** Logi en la curva de Bézier cuadrática a -> b -> c, en el avance u: (1-u)²·a + 2u(1-u)·b + u²·c. */
    private void logiOnCurve(double ax, double ay, double bx, double by, double cx, double cy, double u) {
        double a = (1 - u) * (1 - u), b = 2 * u * (1 - u), c = u * u;
        lx = a * ax + b * bx + c * cx;
        ly = a * ay + b * by + c * cy;
    }

    /**
     * Los brazos como en el juego (encendidos los que tienen fragmento) y el vidrio tibio; si
     * 'closing', los encendidos se cierran uno tras otro desde 'closeAt' (con -LATER ya están
     * cerrados). Los que no tienen fragmento siempre quedan abiertos y apagados. Y el rosetón con
     * un pétalo por fragmento, los braseros apagados y el vidrio sin grietas (los pasos que los
     * cambian lo hacen después).
     */
    private void gameArms(double t, boolean closing, double closeAt) {
        for (int k = 0; k < CandelabraState.ARMS; k++) {
            int arm = CandelabraState.lightOrder(k);
            boolean lit = k < fragments;
            double at = closeAt + CLOSE_GAP * k;
            state.armLit[arm] = lit ? 1 : 0;
            state.armClosed[arm] = lit && closing ? Ease.smooth(t, at, at + CLOSE_TIME) : 0;
        }
        state.glassWarm = fragments / (double) CandelabraState.ARMS;
        state.cracks = 0;
        petals = fragments;
        braziers = 0;
    }

    // =========================================================
    //  DIBUJO
    // =========================================================

    /**
     * En coordenadas de la zona, con la vista ya puesta: la sombra del candelabro, el polvo, el
     * candelabro, Candil y Logi (dentro del vidrio va entre la parte de atrás y la de adelante del
     * candelabro). La sala y sus luces las dibuja el decorado de la zona (LevelArt.drawHall), con
     * los pétalos y los braseros que el guion le pone al nivel.
     */
    public void drawWorld(Graphics2D g) {
        if (!started) return;
        double time = level.getTime();
        Composite old = g.getComposite();
        if (cone > 0.004) {
            g.setComposite(alpha(cone * CONE_ALPHA));
            g.setColor(CONE);
            g.fill(coneShape);
            g.setComposite(old);
        }
        if (dust > 0.001 && hall != null) ThroneHallArt.drawDust(g, hall, dust, clock);
        CandelabraArt.drawBack(g, box, state, time);
        if (inside) LightSource.drawDark(g, lx, ly, darkness, scale, clock);
        CandelabraArt.drawFront(g, box, state, time);
        double flash = getFlash();
        if (flash > 0.004) drawFlash(g, flash);
        candil.draw(g);
        if (!inside) LightSource.drawDark(g, lx, ly, darkness, scale, clock);
    }

    /** El destello: un brillo sobre el corazón de vidrio, que crece y se desvanece, y uno más chico sobre cada copa encendida. */
    private void drawFlash(Graphics2D g, double flash) {
        if (flashImage == null) flashImage = makeFlashImage();
        Composite old = g.getComposite();
        double u = (elapsed - FLASH_AT) / FLASH_TIME;
        double r = heartR * Ease.lerp(1, 3, Ease.clamp01(u));
        g.setComposite(alpha(flash));
        g.drawImage(flashImage, (int) Math.round(heartX - r), (int) Math.round(heartY - r), (int) Math.round(2 * r), (int) Math.round(2 * r), null);
        double c = heartR * CUP_FLASH;
        g.setComposite(alpha(CUP_ALPHA * flash));
        for (int arm = 0; arm < CandelabraState.ARMS; arm++) {
            if (state.armLit[arm] > 0.5)
                g.drawImage(flashImage, (int) Math.round(cupX[arm] - c), (int) Math.round(cupY[arm] - c), (int) Math.round(2 * c), (int) Math.round(2 * c), null);
        }
        g.setComposite(old);
    }

    /** La imagen del brillo: blanco en el núcleo, dorado hacia el medio, transparente en el borde. */
    private static BufferedImage makeFlashImage() {
        BufferedImage img = new BufferedImage(FLASH_SIZE, FLASH_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        float c = FLASH_SIZE / 2f;
        g.setPaint(new RadialGradientPaint(c, c, c, new float[] { 0f, 0.18f, 0.5f, 1f },
                new Color[] { new Color(255, 255, 255, 255), new Color(255, 236, 170, 235), new Color(255, 200, 90, 110), new Color(255, 190, 70, 0) }));
        g.fillRect(0, 0, FLASH_SIZE, FLASH_SIZE);
        g.dispose();
        return img;
    }

    /** En coordenadas de pantalla: el subtítulo, la indicación para saltar y el fundido del final. */
    public void drawOverlay(Graphics2D g) {
        if (!started) return;
        if (subtitle != null) SubtitleRenderer.draw(g, subtitle, beatTime);
        double hint = endTime < 0 ? 0.5 * Ease.window(elapsed, 1.4, 2.4, 6.5, 8) : 0;
        if (hint > 0.01) {
            Text.smooth(g);
            g.setFont(Text.SMALL);
            Text.centered(g, SKIP_HINT, Config.WIDTH - 26 - g.getFontMetrics().stringWidth(SKIP_HINT) / 2.0, 30, Text.HINT_COLOR, hint, 0);
        }
        double black = finished ? 1 : endTime >= 0 ? Ease.clamp01(endTime / endFade) : 0;
        if (black > 0.004) {
            Composite old = g.getComposite();
            g.setComposite(alpha(black));
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, Config.WIDTH, Config.HEIGHT);
            g.setComposite(old);
        }
    }

    /** Una transparencia ya hecha (0 a 1, en 64 pasos): sirve para no crear una en cada cuadro. */
    public static AlphaComposite alpha(double a) {
        return ALPHA[(int) Math.round(Ease.clamp01(a) * 64)];
    }

    // =========================================================
    //  CONSULTAS (para GamePanel y las pruebas)
    // =========================================================

    /** El paso actual, desde 0; al terminar, la cantidad de pasos del guion. */
    public int getBeat() {
        return finished ? script.length : beat;
    }

    /** El estado del candelabro que mueve el guion (el del nivel). */
    public CandelabraState getState() {
        return state;
    }

    /** Qué tan negra es la llama de Logi: 0 la de siempre, 1 la llama negra (su luz se apaga). */
    public double getLogiDarkness() {
        return darkness;
    }

    public double getLogiX() { return lx; }
    public double getLogiY() { return ly; }
    public double getCandilX() { return candil.getCenterX(); }
    public double getCandilY() { return candil.getCenterY(); }

    /** Dónde está Logi (un objeto nuevo: es para las pruebas). */
    public Point2D getLogiPosition() {
        return new Point2D.Double(lx, ly);
    }

    /** El centro de Candil (un objeto nuevo: es para las pruebas). */
    public Point2D getCandilPosition() {
        return new Point2D.Double(candil.getCenterX(), candil.getCenterY());
    }
}
