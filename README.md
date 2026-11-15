# Umbra

Juego de plataformas 2D en Java donde **las sombras son el suelo**.

Controlas una pequeña criatura de luz atrapada en un mundo oscuro. No puedes caminar sobre el vacío, pero sí sobre las sombras que proyectan los objetos. Mueves una fuente de luz con el ratón y, según dónde la pongas, las sombras cambian de forma y se convierten en los caminos que te llevan a la salida.

Proyecto final de **Graficación**.

- **Autor:** Raul Omar Jurado Camargo
- **Coautor:** Maria Magdalena Rivera Ramirez

---

## Cómo ejecutarlo

Requisitos: **JDK 8 o superior** (recomendado 17 o 21). No usa librerías externas, solo Java2D (Swing).

**Desde un IDE**
- **IntelliJ IDEA:** `File > Open` y elige la carpeta `umbra`. Si no detecta el código, clic derecho en `src` > `Mark Directory as > Sources Root`. Ejecuta `umbra.Main`.
- **NetBeans:** `File > New Project > Java with Ant > Java Project with Existing Sources`, agrega la carpeta `src`. Ejecuta `umbra.Main`.
- **VS Code** (con *Extension Pack for Java*): abre la carpeta `umbra` y ejecuta `Main.java`.

**Desde la terminal**
- Windows: doble clic en `run.bat`
- Linux / macOS: `sh run.sh`

## Controles

| Tecla | Acción |
|---|---|
| A / D o flechas | Moverse |
| Espacio, W o flecha arriba | Saltar |
| Ratón | Mover la fuente de luz |
| R | Reiniciar el nivel |
| F3 | Mostrar/ocultar datos de depuración |

## Estructura del proyecto

```
src/umbra/
├── Main.java                 Crea la ventana y arranca el juego
├── core/
│   ├── Config.java           Constantes (tamaño, gravedad, velocidad, versión)
│   ├── GamePanel.java        Game loop, update y render
│   └── Input.java            Estado del teclado y del ratón
├── entities/
│   ├── Player.java           Criatura de luz: física y colisiones
│   ├── LightSource.java      Luz que sigue al ratón con interpolación
│   └── Block.java            Objetos que proyectan sombra
├── graphics/
│   ├── ShadowCaster.java     [70%] Cálculo de sombras
│   └── ParticleSystem.java   [Final] Partículas
├── math/
│   ├── Vec2.java             Vector 2D
│   └── Mat3.java             Matriz 3x3 (traslación, rotación, escala)
└── world/
    └── Level.java            Repisas, bloques, inicio y salida
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
- [ ] Implementar `ShadowCaster.computeShadows` (proyección de sombras)
- [ ] Sombras sólidas en `Player.update` (paso 5)
- [ ] Quitar el puente temporal (`Config.TEMP_BRIDGE = false`)
- [ ] Un nivel completo que solo se pueda cruzar usando las sombras

### v1.0 — Entrega final
- [ ] Sistema de partículas (chispas al aterrizar, rastro de luz)
- [ ] 3 niveles y cambio de nivel al llegar a la salida
- [ ] Menú principal y pantalla de victoria
- [ ] (Opcional) Zonas de luz directa que "disuelven" al jugador
- [ ] Capturas de pantalla en este README

Tip: busca `TODO (v0.7)` y `TODO (v1.0)` en el código para ver exactamente qué falta en cada fase.

## Técnicas de graficación

| Técnica | Dónde |
|---|---|
| Game loop con paso de tiempo fijo | `GamePanel.tick()` |
| Interpolación lineal / suavizado exponencial | `LightSource.update()` |
| Degradado radial (iluminación) | `LightSource.drawGlow()` |
| Transformaciones 2D con matrices homogéneas | `Mat3`, `Level.drawPortal()` |
| Detección y respuesta de colisiones AABB | `Player.update()` |
| Proyección de sombras desde una luz puntual | `ShadowCaster` (v0.7) |
| Sistema de partículas | `ParticleSystem` (v1.0) |
