# Umbra

Juego de plataformas 2D en Java donde **la sombra es el único refugio**. Con el ratón mueves a **Logi**, una pequeña llama; las sombras que proyecta son el camino.

## La historia

Bajo la tierra existió un mundo de cavernas iluminado por el **Gran Farol**. Una noche el Farol se apagó y su luz se rompió en mil pedazos. La oscuridad llenó todo y se llamó **Umbra**.

De la última chispa nacieron dos: **Candil**, un pequeño fantasma que puede andar pero apenas brilla, y **Logi**, una llama que alumbra lejos pero no puede andar. Solos no llegan a ningún lado. Juntos tienen una oportunidad.

Pero Candil es frágil. El vacío lo apaga poco a poco, y la luz directa lo deshace: si Logi se le acerca demasiado, se desmorona. Solo la sombra lo abriga. Cuando Logi ilumina las cosas nacen sombras, y dentro de ellas Candil flota a salvo.

Así avanzan juntos, caverna por caverna: Logi abre caminos de sombra y Candil los recorre, buscando el Gran Farol para volver a encenderlo. En las losas antiguas todavía quedan runas del Farol, y despiertan cuando Logi pasa cerca.

---

Proyecto final de **Graficación**.

- **Autores:** Maria Magdalena Rivera Ramirez y Raul Omar Jurado Camargo

---

## Cómo se juega

- **Dentro de una sombra** Candil está a salvo: flota sin gravedad (W o Espacio para subir, S para bajar) y recupera vida.
- **Fuera de la sombra** camina y salta, pero pierde vida (la barra de arriba a la derecha) y se va deshaciendo en polvo de luz.
- **Si Logi se acerca demasiado**, Candil se desmorona.
- Mueve a Logi con el ratón y **haz clic para fijarla**: así no se mueve mientras avanzas. Otro clic la suelta.
- Si sales de una sombra nadando hacia arriba, **sales de un salto**: sirve para alcanzar repisas altas.
- Si te desmoronas o caes al vacío, reapareces en la última repisa firme que pisaste.

El nivel es un recorrido de tres pantallas de ancho: la cámara avanza conforme avanzas.

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
| M | Silenciar o encender la música |
| F3 | Mostrar u ocultar los datos de depuración |

## Cómo pasar el Nivel 1

Ningún hueco se puede cruzar saltando: hay que usar las sombras. En cada tramo pon la luz donde se indica, haz clic para fijarla, métete a la sombra y avanza flotando (**D + W**). Al llegar arriba, sigue subiendo y Candil sale de un salto.

| Tramo | Obstáculo | Dónde va la luz |
|---|---|---|
| 1. El puente | Hueco de 450 px | A la izquierda del bloque, a la altura de su cara de arriba: la sombra se estira como un puente |
| 2. La rampa | Pared de 220 px | Abajo a la izquierda del bloque: la sombra sube hasta lo alto de la pared |
| 3. Hacia atrás | Hueco con el bloque del otro lado | A la derecha del bloque: la sombra viene hacia ti; salta desde la pared para entrar en ella |
| 4. La carpa | Foso de 700 px con un bloque flotando | Justo encima del bloque: la sombra baja por los dos lados como una montaña; crúzala por dentro |

## Estructura del proyecto

```
src/umbra/
├── Main.java                 Crea la ventana y arranca el juego
├── audio/
│   └── Music.java            Música de ambiente en bucle (entrada suave, tecla M)
├── core/
│   ├── Config.java           Constantes: física, vida, música, versión
│   ├── GamePanel.java        Game loop, update y render
│   └── Input.java            Estado del teclado y del ratón
├── entities/
│   ├── Player.java           Candil: física, vida, flotar en la sombra y animación
│   ├── LightSource.java      Logi: sigue al ratón con interpolación; se fija con un clic
│   └── Block.java            Objetos que proyectan sombra
├── graphics/
│   ├── Background.java       Cueva por capas (parallax), niebla, esporas y viñeta
│   ├── Camera.java           Cámara que avanza con Candil (matriz de vista)
│   ├── LevelArt.java         Roca, musgo, plantas que se mecen, hongos, losas con runas
│   ├── ParticleSystem.java   Rastro de luz, chispas, polvo y fragmentos
│   └── ShadowCaster.java     Proyección de sombras (envolvente convexa)
├── math/
│   ├── Vec2.java             Vector 2D
│   └── Mat3.java             Matriz 3x3 (traslación, rotación, escala)
├── res/
│   └── ambiente.wav          Música
└── world/
    └── Level.java            Nivel 1 (tres pantallas) y nivel de prueba
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

### v1.0 — Entrega en la materia (en desarrollo)
- [x] Mecánica de la sombra: Candil flota dentro, pierde vida fuera y se desmorona cerca de la luz
- [x] Ambiente de cueva: fondo por capas con parallax, niebla, esporas y viñeta
- [x] Decoraciones: repisas de roca con musgo, hongos que brillan, losas con runas y columnas rotas
- [x] Candil animado: flota, parpadea, se estira al saltar y se aplasta al caer
- [x] Partículas: rastro de luz, chispas, polvo al perder vida y fragmentos al desmoronarse
- [x] Plantas que se mecen al pasar Candil
- [x] Música de ambiente
- [x] 60 FPS en Windows
- [ ] Menú con la historia, título del nivel y pantalla de victoria
- [ ] Capturas de pantalla en este README

### Después (v1.1 en adelante)
La v1.0 no es la versión final: el juego sigue creciendo.
- [ ] Más niveles y cambio de nivel al llegar a la salida
- [ ] Luces que se mueven solas
- [ ] Enemigos: polillas de ceniza que persiguen la luz
- [ ] Fragmentos del Gran Farol para coleccionar
- [ ] Efectos de sonido

Tip: busca `TODO (v1.0)` en el código para ver qué falta.

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

## Música

La música está en `src/umbra/res/ambiente.wav` y se repite sin fin (**M** la silencia). Para cambiarla, reemplaza ese archivo por otro **WAV** con el mismo nombre: de preferencia mono, de 22 050 o 44 100 Hz y de 3 minutos o menos. Java no lee MP3, así que hay que convertirla antes.

La pieza actual es de prueba: fue generada con código (síntesis aditiva), así que no tiene derechos de terceros. Si se usa otra, aquí deben ir su autor y su licencia.
