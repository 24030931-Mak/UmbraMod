package umbra.world;

import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;

import umbra.entities.Block;
import umbra.entities.Player;
import umbra.math.Vec2;

/**
 * [B2] La cueva completa: las zonas del juego en orden y los pasos explícitos entre ellas.
 * Los niveles 1 y 2 siguen en Level; aquí se arman las zonas nuevas (Los Pozos y la Cúpula) y se
 * conectan.
 */
public final class Cave {

    private Cave() { }

    /**
     * Las zonas del juego: Las Raíces, Las Galerías, Los Pozos y la Cúpula, con sus pasos. El paso
     * entre Las Galerías y Los Pozos se pone a mano (addPassage), solo a la altura de la repisa alta
     * de Las Galerías: con connect sería paso toda la orilla izquierda de Los Pozos.
     *
     * Los Pozos y la Cúpula se unen por el hueco del techo de Los Pozos (x 1920 a 2000), que en el
     * plano queda en la misma x (9600 a 9680) que el hueco del piso de la Cúpula (x 640 a 720):
     *   - subiendo, Candil aparece en el piso de la Cúpula, a la derecha del hueco;
     *   - bajando, aparece arriba del tiro derecho de Los Pozos, debajo del hueco, y sigue cayendo
     *     (conserva su velocidad) hasta la losa del hueco. Si se cae de ahí, reaparece en la repisa
     *     del hueco.
     */
    public static List<Level> create() {
        Level raices = Level.createLevel1();
        Level galerias = Level.createLevel2();
        Level pozos = createPozos();
        Level cupula = createCupula();

        galerias.addPassage(new Passage(Passage.Side.RIGHT, 0, 260, pozos, new Vec2(20, 260 - Player.H), null));
        pozos.addPassage(new Passage(Passage.Side.LEFT, 0, 260, galerias, new Vec2(3798, 260 - Player.H), null));
        pozos.addPassage(new Passage(Passage.Side.TOP, 1920, 2000, cupula, cupula.getSpawn(), null));
        cupula.addPassage(new Passage(Passage.Side.BOTTOM, 640, 720, pozos,
                new Vec2(1960 - Player.W / 2, 20), new Vec2(1800, 300 - Player.H)));
        return Arrays.asList(raices, galerias, pozos, cupula);
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
     * [B4] Los fragmentos del Gran Farol (4 y 5), sin piezas nuevas:
     *   - El del camino (4), en (1310, 1012): en la orilla izquierda del piso derecho, a la altura
     *     de Candil, donde cae al terminar el columpio y por donde camina para empezar la chimenea.
     *     Es la mitad de la zona (a lo ancho y a lo alto) y de la ruta (tres tramos de cinco).
     *   - El escondido (5), en (2250, 285): en el aire, 270 px a la derecha de la losa del hueco y a
     *     su altura, bajo el techo del tiro derecho. Usa esa losa de otra forma: con Logi fija a su
     *     izquierda y a su altura, en (1620, 285) (sobre el vacío entre la chimenea y la repisa del
     *     hueco), su sombra sale de lado, una franja casi horizontal hacia la derecha (y 255 a 315
     *     ahí). Candil salta de la repisa del hueco a la losa, se deja caer por su orilla derecha a la
     *     franja y nada por ella hasta el fragmento. Sin sombras no se llega: a esa altura, ningún
     *     salto ni caída pasa de x = 2110, a más de 120 px de él. Aguanta a Logi corrida 15 px a
     *     cualquier lado y se llega con casi toda la vida (88 % en el peor caso). De vuelta: nada a
     *     la izquierda y arriba, sale de la sombra de un salto, cae en la losa y de ahí salta a la
     *     repisa del hueco (bajo su techo); o se deja caer al piso derecho, que se vuelve su punto
     *     firme, y sube otra vez por la chimenea. No toca el columpio ni el hueco del techo.
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

        // [B4] Los fragmentos del Gran Farol (ver el comentario de arriba)
        lvl.addFragment(new Fragment(4, 1310, 1012));   // el del camino: en la orilla del piso derecho, tras el columpio
        lvl.addFragment(new Fragment(5, 2250, 285));    // el escondido: a la derecha de la losa del hueco, a su altura
        return lvl;
    }

    /**
     * [v0.9.5] Cuántos px creció la Cúpula hacia arriba para la sala del trono. Todo lo de la v0.9
     * (la subida, el inicio y la llegada desde Los Pozos) bajó esta misma cantidad y su posición en
     * el plano subió lo mismo. Es múltiplo de 80, el tamaño de las celdas del mapa (Exploration).
     */
    public static final int CUPULA_EXTRA = 480;

    /**
     * La Cúpula: una zona de 1280 x 2640 (una pantalla de ancho y casi cuatro de alto) encima del
     * tiro derecho de Los Pozos. Se llega por abajo, por el hueco del piso (x 640 a 720), que es el
     * mismo hueco del techo de Los Pozos; por ahí también se baja de regreso. Arriba, al centro,
     * cuelga el Gran Farol: tocarlo empieza el final (es la salida de la zona, como el portal que
     * tenían Las Galerías). Sin numeral en el letrero. Sin pasos: Cave.create los pone.
     *
     * [v0.9.5] Las medidas de abajo son las de la v0.9, cuando la zona medía 2160 de alto: en el
     * código todas tienen CUPULA_EXTRA (480) px más en y, porque la zona creció hacia arriba para
     * la sala del trono (ver el final de este comentario). La subida no cambió.
     *
     * La subida por relevos (la chimenea, del piso y = 2040 a la cima y = 800, unos 1240 px): tres
     * etapas en zigzag entre las paredes, con dos descansos. Logi va abajo de cada etapa y las
     * sombras suben inclinadas hacia el otro lado. Ninguna posición fija de Logi sirve para toda la
     * chimenea: la etapa 2 sube a la derecha y pide a Logi abajo a la izquierda; la etapa 3 sube a
     * la izquierda y pide a Logi abajo a la derecha; y Logi suelta solo llega a lo que se ve. Así que
     * se sube por partes: Candil espera en un descanso mientras el jugador lleva a Logi a la etapa
     * siguiente.
     *   Un descanso es una repisa pegada a la pared con una losa debajo y otra encima (su techo,
     *   alineado con la malla de 40 px de la prueba). Parado al fondo, contra la pared, Candil
     *   queda en sombra con Logi en cualquier lugar más arriba que el piso del descanso (la losa de
     *   encima es larga, se mete en la roca y baja hasta la cabeza de Candil) y también con Logi
     *   abajo, mientras los rayos crucen la losa de debajo; así no pierde vida mientras Logi cambia
     *   de lugar. Franja que no se puede cubrir: los rayos que entran casi a la altura de Candil,
     *   por debajo del techo (a menos de unos 6 grados sobre su horizonte, del lado abierto del
     *   descanso o desde dentro de la roca del otro lado), y Logi metida dentro del propio techo;
     *   el techo no puede bajar más que la cabeza de Candil. PruebaCupula la cuenta y la avisa.
     *   La etapa 1 empieza con Candil en la luz del piso mientras Logi llega al hueco: es lo que más
     *   vida cuesta del relevo (peor caso con Logi corrida 15 px: 62 %).
     *   1. Etapa 1, del piso al descanso 1 (izquierda, y = 1640): Logi fija en el hueco del piso,
     *      en (670, 2100), abajo a la derecha de la primera losa. Candil salta del piso a la losa
     *      y sube nadando en su sombra, que va hacia arriba a la izquierda, hasta la punta del
     *      descanso; sale de un salto y camina al fondo.
     *   2. Etapa 2, del descanso 1 al descanso 2 (derecha, y = 1200): Logi fija abajo a la
     *      izquierda, debajo del descanso 1, en (309, 1756). Las sombras del techo del descanso 1 y
     *      de una losa de en medio suben a la derecha hasta la punta del descanso 2.
     *   3. Etapa 3, del descanso 2 a la cima (izquierda, y = 800): Logi fija abajo a la derecha,
     *      debajo del descanso 2, en (976, 1311). La sombra del techo del descanso 2 sube a la
     *      izquierda hasta la punta de la cima.
     * El domo: las paredes se cierran en escalones hacia arriba y de ellas salen repisas cada vez
     * más cortas (la cima 220 px, luego 160, 130 y 100), una a cada lado. Ningún salto llega de una
     * a la siguiente (150 px o más de alto); en medio hay una losa pequeña, y con Logi fija abajo
     * de ella su sombra sube inclinada hasta la punta de la repisa siguiente:
     *   4. De la cima a la repisa 1 (derecha, y = 650): Logi en (440, 915).
     *   5. De la repisa 1 a la repisa 2 (izquierda, y = 500): Logi en (760, 770).
     *   6. De la repisa 2 a la repisa 3 (derecha, y = 360): Logi en (560, 640).
     *   7. Las losas colgantes, de la repisa 3 a la repisa del Farol (y = 90): dos losas cuelgan
     *      del techo, más alto de lo que alcanza un salto desde la repisa 3 (135 y 220 px). Con
     *      Logi fija abajo a la izquierda de ellas, en (560, 300), sus sombras salen hacia arriba a
     *      la derecha: Candil salta de la repisa 3 a la sombra de la losa de la derecha, de ahí
     *      salta a la sombra de la de la izquierda (o se para en ella) y de ahí a la repisa del
     *      Farol, donde lo toca. Hacen falta las dos: desde la sombra de la de la derecha un salto
     *      no llega al Farol, y parado en ella, la repisa queda a 135 px.
     *
     * Las únicas repisas que se pisan son el inicio o el fin de algún tramo (el piso, a los dos
     * lados del hueco, los descansos, la cima, las del domo y la del Farol); las paredes suben
     * hasta el techo del mundo. La prueba (PruebaCupula) lo comprueba todo con todos los saltos y
     * caídas posibles, cada medio píxel; la chimenea, también con Logi fija en cada punto de una
     * malla de 40 px.
     *
     * [v0.9.5] La sala del trono. Ya con las medidas del código: lo que era el techo del mundo
     * (y = 0 en la v0.9) quedó en y = CUPULA_EXTRA (480), el "techo de hoy", y arriba de él está la
     * sala, de 640 x 470 (x 280 a 920, y 0 a 470), con el Gran Farol colgando al centro, encima del
     * estrado. Con la cámara arriba del todo se ve completa, con el estrado, en una pantalla.
     *   - El estrado es la repisa del Farol de siempre (x 530 a 670, y = 570): ahí termina la
     *     subida y ahí, de pie, Candil toca la salida, que es la zona a su altura bajo el Farol.
     *   - El piso de la sala son dos losas de piedra de 10 px, una a cada lado del estrado (x 280 a
     *     530 y 670 a 920, con la cara de arriba en y = 470). Su cara de abajo es justo el techo de
     *     hoy: encima de las paredes del domo y del hueco entre ellas, Candil choca donde antes
     *     chocaba con el techo del mundo, así que la subida no cambia. El techo solo se abre encima
     *     del estrado. Del estrado al piso de la sala hay 100 px: se sube con un salto simple (el
     *     salto alcanza 107) y se vuelve caminando, dejándose caer por la orilla.
     *   - Los escalones del domo terminan bajo el piso de la sala, que es más ancho que ellos: el
     *     domo se abre hacia la sala. Una pared nueva a la derecha (x 920 a 1000) deja la sala
     *     centrada en el estrado y en el Farol (x 600).
     *   - La sala no tiene losas: no agrega sombras ni tramos.
     * PruebaSala revisa la sala, y PruebaCupula sigue revisando la subida con las medidas bajadas.
     */
    public static Level createCupula() {
        final int e = CUPULA_EXTRA;   // todo lo de la v0.9 baja e px (ver el comentario)
        Level lvl = new Level("", "La C\u00fapula", 1280, 2160 + e, new Vec2(750, 2040 + e - Player.H),
                new Rectangle2D.Double(545, 50 + e, 110, 40));   // el Gran Farol: se toca de pie en el estrado
        lvl.setExitIsLantern(true);
        lvl.setWorldPosition(8960, -(2160 + e));
        lvl.setScenery(43, 47, 1.0);

        //              x     y         ancho  alto
        lvl.addPlatform(280,  2040 + e, 360,   120);        // el piso, a la izquierda del hueco
        lvl.addPlatform(720,  2040 + e, 280,   120);        // el piso, a la derecha del hueco (la llegada)
        lvl.addPlatform(0,    0,        280,   2160 + e);   // pared izquierda
        lvl.addPlatform(1000, 0,        280,   2160 + e);   // pared derecha
        lvl.addPlatform(280,  1640 + e, 220,   30);         // descanso 1: fin de la etapa 1, inicio de la 2
        lvl.addPlatform(780,  1200 + e, 220,   30);         // descanso 2: fin de la etapa 2, inicio de la 3
        lvl.addPlatform(280,  800 + e,  220,   30);         // la cima: fin de la etapa 3, inicio del domo
        lvl.addPlatform(780,  650 + e,  160,   30);         // repisa 1 del domo
        lvl.addPlatform(380,  500 + e,  130,   30);         // repisa 2 del domo
        lvl.addPlatform(760,  360 + e,  100,   30);         // repisa 3 del domo
        lvl.addPlatform(530,  90 + e,   140,   30);         // la repisa del Farol: el estrado de la sala
        lvl.addPlatform(0,    e,        340,   700);        // el domo: las paredes se cierran en escalones
        lvl.addPlatform(0,    e,        380,   540);
        lvl.addPlatform(0,    e,        420,   400);
        lvl.addPlatform(0,    e,        460,   260);
        lvl.addPlatform(940,  e,        340,   700);
        lvl.addPlatform(900,  e,        380,   540);
        lvl.addPlatform(860,  e,        420,   400);
        // [v0.9.5] La sala del trono, arriba del techo de hoy (y = e)
        lvl.addPlatform(280,  e - 10,   250,   10);         // el piso de la sala, a la izquierda del estrado
        lvl.addPlatform(670,  e - 10,   250,   10);         //                      y a la derecha
        lvl.addPlatform(920,  0,        80,    e);          // la pared derecha de la sala
        lvl.setThroneHall(new Rectangle2D.Double(280, 0, 640, e - 10));
        lvl.setCandelabraBox(new Rectangle2D.Double(360, 0, 480, e - 30));   // centrado en el estrado

        lvl.addBlock(new Block(560, 1930 + e, 80,  30));   // etapa 1: la primera losa
        lvl.addBlock(new Block(280, 1670 + e, 220, 30));   // descanso 1: la losa de debajo
        lvl.addBlock(new Block(120, 1560 + e, 345, 48));   //             y su techo (largo y bajo: hasta la cabeza de Candil)
        lvl.addBlock(new Block(780, 1230 + e, 220, 30));   // descanso 2: la losa de debajo
        lvl.addBlock(new Block(815, 1120 + e, 345, 48));   //             y su techo (largo y bajo)
        lvl.addBlock(new Block(520, 1410 + e, 50,  30));   // etapa 2: la losa de en medio
        lvl.addBlock(new Block(550, 790 + e,  40,  30));   // domo: las losas pequeñas
        lvl.addBlock(new Block(650, 640 + e,  40,  30));
        lvl.addBlock(new Block(600, 490 + e,  40,  30));
        lvl.addBlock(new Block(775, 225 + e,  30,  40));   // las losas colgantes: la de la derecha
        lvl.addBlock(new Block(690, 140 + e,  30,  25));   //                      y la de la izquierda
        return lvl;
    }
}
