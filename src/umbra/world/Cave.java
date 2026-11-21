package umbra.world;

import java.util.Arrays;
import java.util.List;

import umbra.entities.Block;
import umbra.entities.Player;
import umbra.math.Vec2;

/**
 * [B2] La cueva completa: las zonas del juego en orden y los pasos explícitos entre ellas.
 * Los niveles 1 y 2 siguen en Level; aquí se arman las zonas nuevas (Los Pozos) y se conectan.
 */
public final class Cave {

    private Cave() { }

    /**
     * Las zonas del juego: Las Raíces, Las Galerías y Los Pozos, con sus pasos. El paso entre Las
     * Galerías y Los Pozos se pone a mano (addPassage), solo a la altura de la repisa alta de Las
     * Galerías: con connect sería paso toda la orilla izquierda de Los Pozos.
     */
    public static List<Level> create() {
        Level raices = Level.createLevel1();
        Level galerias = Level.createLevel2();
        Level pozos = createPozos();

        galerias.addPassage(new Passage(Passage.Side.RIGHT, 0, 260, pozos, new Vec2(20, 260 - Player.H), null));
        pozos.addPassage(new Passage(Passage.Side.LEFT, 0, 260, galerias, new Vec2(3798, 260 - Player.H), null));
        return Arrays.asList(raices, galerias, pozos);
    }

    /**
     * Los Pozos (III): una zona de 2560 x 2160 (dos pantallas de ancho y tres de alto) a la derecha
     * de Las Galerías. Se baja por el tiro izquierdo, se cruza el fondo subiendo y se sube por el
     * tiro derecho, que ocupa la mitad derecha hasta el techo; su techo tiene un hueco (x 1920 a
     * 2000) que da a la Cúpula. Abajo de los tiros y de los huecos está el vacío. Sin pasos:
     * Cave.create los pone.
     *
     *   1. El freno: el tiro izquierdo (x 100 a 300) baja desde la entrada hasta el vacío; el camino
     *      sigue por una abertura en su pared derecha, a 1340 px de la entrada (y 1480 a 1600). Con
     *      Logi fija arriba a la izquierda de las tres losas del tiro, en (110, 150), sus sombras
     *      forman una columna inclinada pegada a la pared: Candil pasa a la primera losa, se deja
     *      caer a la sombra por su orilla, baja flotando (S), se detiene a la altura de la abertura
     *      y sale por ella. Cayendo en la luz no se puede: la repisa de la abertura está 45 px
     *      adentro de la pared y a 900 px/s Candil cruza los 120 px de alto de la abertura en 6
     *      cuadros, en los que avanza a lo más 26 px; las losas están a 400 px o más arriba de la
     *      abertura, así que desde ellas también llega a 900 px/s.
     *   2. La luz desde abajo: un hueco de 220 px sobre el vacío, con techo (y = 1380) y dos losas
     *      cerca de él. Su cara de arriba queda 130 px sobre la abertura: el salto (107 px) no las
     *      alcanza. Con Logi abajo, en el aire sobre el vacío, en (560, 1580), las sombras suben y
     *      se abren como abanico: Candil salta a la sombra de la primera losa, nada por arriba de
     *      las dos (entre ellas y el techo), pasa de una sombra a la otra y baja (S) a la orilla.
     *   3. El columpio: de la orilla baja (y = 1600) al piso del tiro derecho (y = 1030), con una
     *      sola losa en medio, 230 px más arriba que la orilla y 300 px más abajo que el piso. Una
     *      sombra quieta no sirve: para llegar a la orilla, Logi tiene que estar arriba de la losa
     *      (la sombra baja a la izquierda); para llegar al piso, abajo de ella (la sombra sube a la
     *      derecha). Con Logi a la altura de la losa, pegada a su izquierda, la sombra cae recta
     *      bajo la losa, pero la orilla está 260 px a su izquierda: un salto no llega ahí sin caer
     *      más hondo que lo que alcanza la sombra. Con Logi suelta, llevada despacio (140 px/s)
     *      de arriba a la derecha (1045, 1295), por encima de la losa (995, 1270), a su izquierda
     *      (935, 1350) y hasta abajo a la izquierda (945, 1385), la sombra se columpia por debajo:
     *      Candil entra a ella en la orilla, nada hasta debajo de la losa mientras Logi pasa por
     *      encima, y sigue a la sombra cuando sube hasta la orilla del piso. Con la cámara siguiendo
     *      a Candil, esa ruta queda a 40 px o más de los bordes de la pantalla, y también funciona
     *      movida 15 px a cualquier lado o más despacio.
     *   4. La chimenea al revés: del piso derecho (y = 1030) a la repisa del hueco (y = 300), por
     *      tres losas escalonadas que suben hacia la derecha. Con Logi fija abajo, en el aire sobre
     *      el vacío a la izquierda del piso, en (1240, 1250), 290 px más abajo que la losa más baja,
     *      las sombras suben un poco inclinadas a la derecha. Cada losa queda en la parte izquierda
     *      de la sombra de la de abajo: Candil sube a la primera (o salta junto a ella) y ya está en
     *      su sombra, nada hacia arriba (W) por el lado derecho de la siguiente losa, entra a su
     *      sombra arriba de ella, y así hasta la de arriba, de donde sale de un salto a la repisa.
     *      Sin sombras no se sube: desde el piso solo se alcanza la primera losa (100 px arriba), y
     *      la segunda está 240 px más arriba que ella. También funciona con Logi movida 15 px.
     *   5. El hueco del techo: el techo del tiro derecho es roca de 200 px de grueso, con un hueco
     *      angosto (x 1920 a 2000) y una losa chica justo debajo (x 1940 a 1980, y = 270). Sin
     *      sombras ningún salto llega arriba: lo más alto que sube Candil es y = 138, desde la losa.
     *      Con Logi fija bien alineada abajo de la losa, en (1960, 350), la sombra sube recta por el
     *      hueco: Candil salta desde la punta de la repisa del hueco a la sombra, arriba de la losa,
     *      y sube nadando hasta la Cúpula. Pasa con Logi a ±25 px de ahí (hasta unos ±40); a más de
     *      60 px la sombra sale chueca, pega en la roca del techo y no hay camino. Quien baje de la
     *      Cúpula por el hueco cae en la losa, y abajo de todo está el piso derecho.
     *
     * Las únicas repisas que se pisan son el inicio o el fin de algún tramo (la entrada, la
     * abertura, la orilla baja, el piso derecho y la repisa del hueco): si Candil cae, reaparece en
     * una de ellas y puede seguir. Las paredes y techos suben hasta el techo del mundo y no se
     * pueden pisar. La prueba (PruebaPozos) lo comprueba todo con todos los saltos y caídas
     * posibles, cada medio píxel; en el columpio también con Logi fija en cada punto de una malla
     * de 20 px, y en el hueco del techo barriendo a Logi de lado a lado cada 5 px.
     */
    public static Level createPozos() {
        Level lvl = new Level("III", "Los Pozos", 2560, 2160, new Vec2(20, 260 - Player.H), null);
        lvl.setWorldPosition(7680, 0);
        lvl.setScenery(37, 41, 1.2);

        //              x     y     ancho  alto
        lvl.addPlatform(0,    260,  100,   1900);   // la entrada (y la pared izquierda del tiro)
        lvl.addPlatform(345,  1600, 60,    560);    // la abertura: fin del tramo 1, inicio del 2
        lvl.addPlatform(625,  1600, 80,    560);    // la orilla baja: fin del tramo 2, inicio del 3
        lvl.addPlatform(1280, 1030, 1280,  1130);   // el piso del tiro derecho: fin del tramo 3, inicio del 4
        lvl.addPlatform(300,  0,    80,    1480);   // pared derecha del tiro, sobre la abertura
        lvl.addPlatform(380,  0,    245,   1380);   // techo del hueco del tramo 2
        lvl.addPlatform(1700, 300,  160,   60);     // la repisa del hueco: fin del tramo 4, inicio del 5
        lvl.addPlatform(1280, 0,    640,   200);    // techo del tiro derecho, a la izquierda del hueco
        lvl.addPlatform(2000, 0,    560,   200);    // techo del tiro derecho, a la derecha del hueco

        lvl.addBlock(new Block(140, 330,  90,  30));   // tramo 1: las losas del tiro
        lvl.addBlock(new Block(195, 700,  60,  30));
        lvl.addBlock(new Block(205, 1080, 57,  30));
        lvl.addBlock(new Block(450, 1470, 60,  30));   // tramo 2: las losas cerca del techo
        lvl.addBlock(new Block(550, 1470, 50,  30));
        lvl.addBlock(new Block(965, 1330, 60,  40));   // tramo 3: la losa del columpio
        lvl.addBlock(new Block(1370, 930, 100, 30));   // tramo 4: las losas de la chimenea
        lvl.addBlock(new Block(1465, 690, 90,  30));
        lvl.addBlock(new Block(1565, 440, 50,  30));
        lvl.addBlock(new Block(1940, 270, 40,  30));   // tramo 5: la losa del hueco
        return lvl;
    }
}
