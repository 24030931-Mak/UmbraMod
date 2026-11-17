# Umbra

Juego de plataformas 2D en Java donde **la sombra es el único refugio**. Con el ratón mueves a **Logi**, una pequeña llama; las sombras que proyecta son el camino.

## La historia

Bajo la tierra existió un mundo de cavernas iluminado por el **Gran Farol**. Una noche el Farol se apagó y su luz se rompió en mil pedazos. La oscuridad llenó todo y se llamó **Umbra**.

De la última chispa nacieron dos: **Candil**, un pequeño fantasma que puede andar pero apenas brilla, y **la Llama(Logi)**, una luz que alumbra lejos pero no puede andar. Solos no llegan a ningún lado. Juntos tienen una oportunidad.

Pero Candil es frágil. El vacío lo apaga poco a poco, y la luz directa lo deshace: si la Llama(Logi) se le acerca demasiado, se desmorona. Solo la sombra lo abriga. Cuando la Llama(Logi) ilumina las cosas nacen sombras, y dentro de ellas Candil flota a salvo.

Así avanzan juntos, caverna por caverna: la Llama(Logi) abre caminos de sombra y Candil los recorre, buscando el Gran Farol para volver a encenderlo. En las losas antiguas todavía quedan runas del Farol, y despiertan cuando la Llama(Logi) pasa cerca.

---

Proyecto final de **Graficación**.

- **Autores:** Maria Magdalena Rivera Ramirez y Raul Omar Jurado Camargo

---

## Cómo se juega

- **Dentro de una sombra** Candil está a salvo: flota sin gravedad (W o Espacio para subir, S para bajar) y recupera vida.
- **Fuera de la sombra** camina y salta, pero pierde vida (la barra de arriba a la derecha, debajo del mapa circular) y se va deshaciendo en polvo de luz.
- **Si la Llama(Logi) se acerca demasiado**, Candil se desmorona.
- Mueve la Llama(Logi) con el ratón y **haz clic para fijarla**: así no se mueve mientras avanzas. Otro clic la suelta.
- Si sales de una sombra nadando hacia arriba, **sales de un salto**: sirve para alcanzar repisas altas.
- Si te desmoronas o caes al vacío, reapareces en la última repisa firme que pisaste.

El juego es un mundo de niveles conectados, uno junto al otro. Al llegar a la orilla de un nivel pasas al siguiente, y también puedes regresar al anterior. Cada nivel mide tres pantallas de ancho: la cámara avanza conforme avanzas. El nivel 2, **Las Galerías**, termina en un portal que lleva a la pantalla final.

**El mapa.** Arriba a la derecha hay un mapa circular que siempre muestra dónde estás. Con **Tab** se abre el mapa grande, con todos los niveles juntos. Lo que no has recorrido está cubierto de neblina, y lo que ya recorriste se ilumina con la luz de Logi.

**Candil y Logi cambian de forma.** Candil es un fantasma encapuchado y tiene una forma para cada acción: descansa, corre, salta, cae y flota. Logi también cambia: se estira como cometa cuando la mueves rápido, se enrosca en espiral al subir y se calma cuando está quieta.

## Cómo ejecutarlo

Requisitos: **Java 8 o superior** (recomendado 17 o 21). No usa librerías externas, solo Java2D (Swing) y `javax.sound` para la música.

**Rápido:** descarga el `.jar` de la versión más reciente en [Releases](https://github.com/TecnoLector/Umbra/releases) y ábrelo con doble clic, o con `java -jar Umbra-vX.jar`.

**Desde un IDE**
- **IntelliJ IDEA:** `File > Open` y elige la carpeta del proyecto. Si no detecta el código, clic derecho en `src` > `Mark Directory as > Sources Root`. Ejecuta `umbra.Main`.
- **NetBeans:** `File > New Project > Java with Ant > Java Project with Existing Sources`, agrega la carpeta `src`. Ejecuta `umbra.Main`.
- **VS Code** (con *Extension Pack for Java*): abre la carpeta del proyecto y ejecuta `Main.java`.

**Desde la terminal**
- Windows: doble clic en `run.bat`
- Linux / macOS: `sh run.sh`

## Controles

| Tecla | Acción |
|---|---|
| A / D o flechas | Moverse |
| W, Espacio o flecha arriba | Saltar; dentro de la sombra, subir |
| S o flecha abajo | Bajar dentro de la sombra |
| Ratón | Mover a Logi |
| Clic | Fijar o soltar a Logi |
| R | Reiniciar el nivel |
| Tab | Abrir o cerrar el mapa |
| M | Silenciar o encender la música |
| F3 | Mostrar u ocultar los datos de depuración |
| Enter, Esc, Espacio o clic | Saltar la intro |
| I (en el menú) | Ver la intro otra vez |

## Cómo pasar el Nivel 1

Ningún hueco se puede cruzar saltando: hay que usar las sombras. En cada tramo pon la luz donde se indica, haz clic para fijarla, métete a la sombra y avanza flotando con **D**. Al llegar arriba, sigue subiendo y Candil sale de un salto.

Ojo con **W**: en los tramos 1 y 2 la sombra queda lejos de Logi y puedes mantener **D + W**. En los tramos 3 y 4 la sombra pasa cerca de Logi: si subes con W, Candil se acerca a ella y se desmorona. Ahí avanza solo con D.

| Tramo | Obstáculo | Dónde va la luz | Cómo avanzar |
|---|---|---|---|
| 1. El puente | Hueco de 450 px | A la izquierda del bloque, a la altura de su cara de arriba: la sombra se estira como un puente | D + W |
| 2. La rampa | Pared de 220 px | Abajo a la izquierda del bloque: la sombra sube hasta lo alto de la pared | D + W |
| 3. Hacia atrás | Hueco con el bloque del otro lado | A la derecha del bloque: la sombra viene hacia ti; salta desde la pared para entrar en ella | D; solo un instante D + W para entrar |
| 4. La carpa | Foso de 700 px con un bloque flotando | Justo encima del bloque: la sombra baja por los dos lados como una montaña; crúzala por dentro | Solo D |

## Cómo pasar el Nivel 2: Las Galerías

Al cruzar de nivel, Logi aparece arriba de Candil y espera a que muevas el ratón.

| Tramo | Obstáculo | Dónde va la luz | Cómo avanzar |
|---|---|---|---|
| 1. El refugio | Ninguno: es donde descansas | Arriba de la losa de la entrada: su sombra cae como una carpa y cubre a Candil, que no pierde vida | Quédate abajo y acomoda a Logi para el tramo siguiente |
| 2. Dos sombras, un puente | Hueco de 1000 px: una sola sombra no alcanza | A la izquierda de la primera losa, a la altura de su cara de arriba: las dos sombras salen hacia la derecha y se enciman | D + W hasta pasar la segunda losa; después solo D |
| 3. La chimenea | Subida de 300 px por un pozo | Arriba a la izquierda de las dos losas: sus sombras caen una sobre otra dentro del pozo | D + W: sube nadando y, al salir por arriba, sale de un salto |
| 4. El pasillo bajo | Túnel muy bajo con un foso de 200 px | Lejos, atrás del túnel y un poco más arriba que su entrada: la sombra entra casi horizontal y lo llena. Dentro del túnel Logi no sirve: Candil se desmoronaría | Solo D, flotando por el túnel |
| 5. El portal | Nada: ya casi terminas | Déjala donde quedó | Camina con D hasta el portal |

## Estructura del proyecto

```
src/umbra/
├── Main.java                 Crea la ventana y arranca el juego
├── audio/
│   └── Music.java            Música de ambiente en bucle (entrada suave, tecla M)
├── core/
│   ├── Config.java           Constantes: física, vida, música, versión
│   ├── GamePanel.java        Game loop, update y render
│   ├── Input.java            Estado del teclado y del ratón
│   └── Resources.java        Abre la música y los textos (dentro del .jar o en src)
├── entities/
│   ├── Player.java           Candil: física, vida, flotar en la sombra y animación
│   ├── LightSource.java      Logi: sigue al ratón con interpolación; se fija con un clic
│   └── Block.java            Objetos que proyectan sombra
├── graphics/
│   ├── Background.java       Cueva por capas (parallax), niebla, esporas y viñeta
│   ├── Camera.java           Cámara que avanza con Candil (matriz de vista)
│   ├── LevelArt.java         Roca, musgo, plantas que se mecen, hongos, losas con runas
│   ├── ParticleSystem.java   Rastro de luz, chispas, polvo, niebla y ceniza
│   └── ShadowCaster.java     Proyección de sombras (envolvente convexa)
├── math/
│   ├── Vec2.java             Vector 2D
│   └── Mat3.java             Matriz 3x3 (traslación, rotación, escala)
├── res/
│   ├── ambiente.wav          Música
│   ├── intro.txt             Narración de la intro (una línea por escena; se puede editar)
│   └── final.txt             Texto de la pantalla final
├── ui/
│   ├── Intro.java            La intro: línea de tiempo de las seis escenas, fundidos y narración
│   ├── TextSprite.java       Texto ya dibujado en una imagen, para que la intro vaya a 60 FPS
│   ├── intro/                Escenas de la intro: el Gran Farol, la caverna dorada, las grietas,
│   │                         los pedazos de luz, la neblina, el nacimiento de Candil y la llegada de Logi
│   ├── Menu.java             Menú de inicio: el título y las opciones
│   ├── LevelTitle.java       Título del nivel al empezar o al cruzar
│   ├── Victory.java          Pantalla final
│   ├── Text.java             Ayudantes para dibujar texto
│   ├── MapView.java          Mapa grande (Tab): todos los niveles y lo recorrido
│   ├── MiniMap.java          Mapa circular de la esquina, centrado en Candil
│   └── MapFog.java           Neblina que cubre lo no recorrido (la usan los dos mapas)
└── world/
    ├── Level.java            Niveles 1 y 2 (tres pantallas cada uno) y nivel de prueba
    ├── World.java            Los niveles en fila, conectados de ida y vuelta
    └── Exploration.java      Qué zonas de cada nivel ya recorrió Candil
```

## Avances

### v0.3 — Avance 30%: base funcional
- [x] Ventana y game loop con paso de tiempo fijo (60 actualizaciones/s)
- [x] Jugador con gravedad, salto y colisiones AABB
- [x] Fuente de luz que sigue al ratón con interpolación suave
- [x] Halo de luz con degradado radial
- [x] Salida animada con transformaciones de matrices 3x3 (traslación, rotación, escala)
- [x] Nivel de prueba con puente temporal

### v0.7 — Avance 70%: la mecánica central
- [x] Implementar `ShadowCaster.computeShadows` (proyección de sombras)
- [x] Sombras sólidas en `Player.update` (paso 5)
- [x] Quitar el puente temporal (`Config.TEMP_BRIDGE = false`)
- [x] Un nivel completo que solo se pueda cruzar usando las sombras
- [x] Cámara con desplazamiento horizontal: el nivel mide tres pantallas
- [x] Luz que se fija con un clic, escalón para subir bordes bajos y reaparición en la última repisa

### v0.8.5 — Mundo conectado, mapa y personajes
- [x] Mecánica de la sombra: Candil flota dentro, pierde vida fuera y se desmorona cerca de la luz
- [x] Ambiente de cueva: fondo por capas con parallax, niebla, esporas y viñeta
- [x] Decoraciones: repisas de roca con musgo, hongos que brillan, losas con runas y columnas rotas
- [x] Candil encapuchado, con una forma para cada acción (descansa, corre, salta, cae, flota)
- [x] Logi cambia de forma según se mueve: cometa, espiral al subir, calmada
- [x] Partículas: rastro de luz, chispas, polvo al perder vida, niebla y ceniza al desmoronarse
- [x] Plantas que se mecen al pasar Candil
- [x] Música de ambiente
- [x] 60 FPS en Windows
- [x] Menú con la historia, título del nivel y pantalla final
- [x] Mundo conectado: se puede pasar de un nivel al siguiente y regresar
- [x] Nivel 2, "Las Galerías": cuatro tramos con ideas nuevas y un portal al final
- [x] Mapa grande con Tab, con neblina sobre lo no recorrido
- [x] Mapa circular arriba a la derecha, siempre a la vista

### v0.8.6 — La intro y las sombras negras
- [x] Intro de unos 45 s dibujada por el propio juego: el Gran Farol, cómo se quebró, la oscuridad, el nacimiento de Candil y la llegada de Logi
- [x] Candil y Logi de la intro son los mismos del juego; cámara con curvas suaves, grietas por desplazamiento del punto medio y pedazos de luz con tiro parabólico
- [x] El menú ya no cuenta la historia letra por letra: la cuenta la intro
- [x] Sombras negras con una orilla tenue
- [x] Logi ya no cae sobre Candil al reaparecer

### Después
Las versiones siguen: el juego todavía puede crecer.
- [ ] v0.9 — El mundo-cueva: zonas conectadas hacia los lados, arriba y abajo; mapa grande de la cueva; nivel 3 y la Cúpula; fragmentos del Gran Farol; guardar la partida
- [ ] v0.9.5 — La persecución: la llama negra, luces que se mueven solas y obstáculos nuevos
- [ ] El final: reparar el Gran Farol
- [ ] Capturas de pantalla en este README

## Técnicas de graficación

| Técnica | Dónde |
|---|---|
| Game loop con paso de tiempo fijo | `GamePanel.tick()` |
| Interpolación lineal y suavizado exponencial | `LightSource.update()`, `Camera.follow()` |
| Degradado radial (iluminación) | `LightSource.drawGlow()` |
| Transformaciones 2D con matrices homogéneas | `Mat3`, `Level.drawPortal()` |
| Escala y rotación alrededor de un punto: `T · R · S · T⁻¹` | `Player.draw()`, `LevelArt.drawMushroom()` |
| Cámara: matriz de vista y su inversa (mundo ↔ pantalla) | `Camera` |
| Parallax: cada capa con `T(−camX · profundidad, 0)` | `Background.drawLayer()` |
| Terreno fractal por desplazamiento del punto medio | `Background.midpointDisplacement()` |
| Perspectiva atmosférica y viñeta | `Background` |
| Composición alfa (`DST_IN`) y pre-renderizado en imágenes | `Background`, `LevelArt` |
| Curvas de Bézier (la silueta de Candil) | `Player.ghostShape()` |
| Detección y respuesta de colisiones AABB | `Player.update()` |
| Proyección de sombras desde una luz puntual | `ShadowCaster.computeShadows()` |
| Envolvente convexa (monotone chain de Andrew) | `ShadowCaster.convexHull()` |
| Punto dentro de polígono: ¿Candil está en la sombra? | `Player.inShadow()` |
| Resorte amortiguado (plantas que se mecen) | `LevelArt.sway()` |
| Sistema de partículas | `ParticleSystem` |
| Mapa: escala y traslación fijas, y su recorte circular | `MapView`, `MiniMap` |
| Neblina con máscara bilineal (orillas suaves) | `MapFog` |

## Música

La música está en `src/umbra/res/ambiente.wav` y se repite sin fin (**M** la silencia). Para cambiarla, reemplaza ese archivo por otro **WAV** con el mismo nombre: de preferencia mono, de 22 050 o 44 100 Hz y de 3 minutos o menos. Java no lee MP3, así que hay que convertirla antes.

La pieza actual es de prueba: fue generada con código (síntesis aditiva), así que no tiene derechos de terceros. Si se usa otra, aquí deben ir su autor y su licencia.
