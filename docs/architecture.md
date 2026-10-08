# Arquitectura de Cosmic Aces

Esta guía describe la estructura actual y los criterios para añadir clases bajo
`com.davidpe.cosmicaces`. Complementa las instrucciones de `AGENTS.md`.

## Cómo ubicar nuevas clases

| Paquete | Responsabilidad | Ejemplos y criterio de entrada |
|---|---|---|
| `boot` | Arranque y configuración de escritorio | Launcher, configuración LWJGL3 y futura lectura de configuración de arranque. |
| `application` | Coordinación global y de cada fase | Transiciones, manejo de eventos y controladores que combinan reglas del dominio. |
| `domain.game` | Estado, reglas y mensajes de la partida | Fases, puntos, vidas, resultados, límites de juego y eventos. `WorldBounds` representa las dimensiones finitas y el rango útil para cajas de naves. |
| `domain.ship` | Comportamiento compartido por las naves | Posición, velocidad, movimiento, dibujo común e intención de movimiento. |
| `domain.player` | Elementos propios del protagonista | Astra, controles de vuelo, parámetros de pilotaje y láminas de sprites. |
| `domain.enemy` | Enemigos y encuentros | Naves enemigas, trayectorias, apariciones y sus sprites. |
| `domain.weapon` | Elementos de las armas | Modelo de proyectiles, cadencia y dibujo reutilizable de ráfagas. |
| `domain.scenery` | Elementos reutilizables del escenario | Starfield para la bienvenida y WorldScenery para estrellas/isletas fijas de PhaseOne. |
| `infrastructure.gdx` | Composición de pantallas y configuración de presentación | ScreenFactory y resolución virtual. |
| `infrastructure.gdx.screen` | Pantallas concretas | Input, cámara, viewport, layout, fuentes y ciclo de vida de recursos. |
| `infrastructure.gdx.event` | Distribución de eventos | Bus, publicación y cancelación de suscripciones. |

Ubica cada nueva clase según lo que hace. Extiende un paquete existente cuando
su responsabilidad encaje ahí; crea un paquete nuevo cuando exista una necesidad
concreta. No añadas clases base, interfaces ni paquetes vacíos para anticipar
funcionalidades todavía sin definir.

LibGDX está permitido en el dominio: las naves y los elementos del escenario
pueden encapsular su dibujo. El dominio no importa clases de `application`,
`infrastructure` o `boot`. Las dimensiones y otros parámetros externos se pasan
al objeto, como hace Starfield con el tamaño del área.

Los controladores de fase en application coordinan el dominio. GameCoordinator
mantiene el estado global y decide las transiciones. CosmicAcesGame es la excepción
de composición: depende de LibGDX y de infraestructura para construir pantallas y
aplicar esas transiciones. Infraestructura puede depender de application y domain;
boot ensambla y arranca la aplicación.

Las pantallas traducen el input en llamadas al controlador y publican eventos.
En PhaseOne, `FlightControls` separa ↑/↓/←/→ y la pulsación de P del
`MovementIntent` cartesiano que aún usan otras naves. `Astra` posee posición,
rumbo, velocidad instantánea, tiempo restante de Ultra y su uso único por fase.
`PhaseOneGameController.advanceFlight` aplica únicamente el delta que resta
del recorrido y fija el mundo en 8192×12000: ocho anchos virtuales de 1024
unidades. `FlightTuning` reúne las cifras
de velocidad, aceleración, frenado y giro aprobadas; el borde anticipa el giro
según la velocidad y el recorte de posición es la última salvaguarda.
PhaseOne crea `WorldScenery` una vez por recorrido con semilla propia: las
estrellas cubren todo el mapa y las isletas de bloques permanecen en las mismas
coordenadas al regresar. El dibujo filtra los elementos fuera de la región
visible. `Starfield` conserva el scroll de la pantalla de bienvenida.
`FlightCameraState` calcula posición, rumbo y zoom interpolados sin depender
del nativo LibGDX; `FlightCamera` aplica ese estado a `OrthographicCamera`.
La cámara enfoca 100 unidades por delante de Astra y sigue su posición con
0,10 s de retardo, para mantener la nave más abajo en pantalla; suaviza el yaw
durante 0,25 s y usa zoom 1,00/1,08/1,15.
`PhaseOneScreen` separa la proyección de mundo de la del HUD fijo.
`FlightHud` presenta rumbo y velocidad instantánea como texto directo sobre
el mundo, sin panel ni títulos, y el score acumulado en la esquina superior
derecha. `PhaseScore` (domain.game) convierte el tiempo válido del recorrido en
puntos solo al cruzar segundos completos: 1 por segundo de fase y 10 por cada
segundo completo de coincidencia visible de Astra y el Raider; los segundos ya
concedidos evitan doble conteo y el resultado no depende de cómo se reparta el
delta ni de los FPS. `PhaseOneGameController.advanceFlight` usa el mismo delta
acotado por el reloj para el vuelo y la puntuación y devuelve los puntos del
frame; la pantalla los publica como `PointsEarned` antes de `PhaseCompleted` y
dibuja el score desde el snapshot del coordinador. La coincidencia se evalúa con
la vista real de la cámara (rectángulo rotado por `camera.up` y `camera.zoom`),
no con un rectángulo fijo. `Ship.draw(batch, rotationDegrees)` rota el
sprite sobre el centro de su caja estable, de modo que Astra conserva su
orientación respecto al mundo mientras la cámara gira.
`FlightMinimap` vive en infraestructura y permanece oculto hasta pulsar M; se
dibuja sobre la proyección fija del HUD sin reservar espacio ni mover sus
lecturas. `MinimapProjection` adapta el `WorldBounds` completo al panel sin
deformar la proporción; proyecta las isletas persistentes, la flecha de Astra y
el punto rojo con estela direccional de Vesper Raider.
El estado global vive en GameState; el estado de un recorrido vive en el controlador
de su fase y en los objetos que coordina. Una pantalla nueva se incorpora a
ScreenFactory y al flujo de navegación de CosmicAcesGame y GameCoordinator.

Las pantallas crean y liberan sus recursos gráficos. Astra.Visuals y
VesperRaider.Visuals cargan sus texturas y las liberan mediante dispose(); la pantalla
es su propietaria. Ship.draw(batch) usa un SpriteBatch ya abierto por la pantalla.
Ship.draw(batch) mantiene la proporción de las regiones dentro de una caja estable.
Las láminas v3 de Astra y Vesper Raider tienen cinco recortes: las tres poses de
alabeo y dos de guiñada. Las variantes de disparo ya están preparadas:
`HeroShipSheet.NORMAL_FIRING` y `ACCELERATE_FIRING` definen los recortes propios
de cada PNG; `firingSheets()` las enumera sin cambiar las referencias de tamaño
de las láminas de vuelo. `VesperRaiderSheet.firingInternalPath()`, `firingSlice()`
y `firingSlices()` proporcionan la variante del enemigo. Los recortes incluyen
fogonazos y excluyen manchas aisladas de fondo; los tres primeros dibujos de
algunas láminas se tocan en los bordes, por lo que no son celdas uniformes.
`Astra.Visuals.region(pose, accelerating, firing)` y
`VesperRaider.Visuals.region(pose, firing)` exponen las regiones listas para usar.
La variante del Raider conserva el mismo volteo vertical que su lámina de vuelo.
Las pantallas liberan las cuatro texturas de Astra y las dos de Vesper mediante
sus `Visuals.dispose()`. La preparación no activa el disparo: el temporizador de
fogonazo, input, proyectiles y HUD pertenecen a COS-27. Al integrarlos, conservar
la escala y ancla del cuerpo entre variantes: las imágenes fuente de disparo
tienen diferencias de geometría y margen respecto a las originales; no estirar
cada pose para rellenar la caja ni aumentar la caja de vuelo por el fogonazo.

`domain.weapon.GunBurstVisual` dibuja un proyectil trazador fino inspirado en
`docs/art/rafagas-inspiration.png`: estela ámbar afilada, trazo dorado y punta
amarilla clara. `draw(shapes, tipX, tipY, forwardX, forwardY)` recibe coordenadas
del mundo y el vector de disparo guardado al emitirlo; no consulta la nave ni la
cámara. La pantalla pone la proyección del mundo y abre/cierra un único lote
`ShapeRenderer.ShapeType.Filled` para todos los proyectiles, después de cerrar
`SpriteBatch`. El objeto restaura el color del renderer y no crea recursos nativos.
Una instancia visual puede compartirse entre Astra y Vesper y todos sus disparos.
Longitud y grosor (42 y 1,2 unidades inicialmente) son ajuste de dibujo, no alcance
ni velocidad. Se dibuja una vez por proyectil de cada cañón; la colocación de los
dos cañones, emisión, movimiento, cadencia y retirada corresponden a COS-27.

`domain.weapon.GunProjectile` es un proyectil puramente visual: copia en su
constructor su origen mundial y un vector de rumbo que normaliza una sola vez,
avanza en línea recta con `advance(delta)` y no retiene referencias a nave,
cámara, mundo ni puntuación. Limita su avance al alcance recibido y `expired()`
se cumple exactamente al cubrirlo, de modo que la retirada depende solo de la
distancia. `domain.weapon.GunTuning` reúne la cadencia (0,08 s), la velocidad
(1800 u/s) y la duración del fogonazo (0,035 s); el alcance lo congela el
adaptador en cada disparo. `domain.weapon.GunBurst` posee el reloj de cadencia,
el pulso de fogonazo y la colección de proyectiles, sin depender de LibGDX ni de
infraestructura. `advance(frameDelta, emissionSeconds, enabled, ShotSource)`
avanza una vez por frame los proyectiles existentes y luego emite dentro de la
ventana `emissionSeconds` (recortada a `[0, frameDelta]`); los proyectiles
nuevos avanzan solo el tiempo posterior a su emisión. La emisión es inmediata al
activar y cada 0,08 s; deshabilitar descarta la deuda de cadencia, detiene el
pulso y no borra los proyectiles existentes, y `emissionSeconds=0` corta los
disparos nuevos al terminar la fase sin alterar la trayectoria de los ya
emitidos. `ShotSource.shotAt(offsetSeconds)` entrega una instantánea `Shot` con
sus orígenes `Muzzle`, la dirección y el alcance; cada origen emite un proyectil,
de modo que los dos cañones de Astra producen dos trayectorias. La cadencia se
mide por tiempo acumulado, por lo que no depende de los FPS, y no hay cupo ni
munición: la caducidad por distancia acota la colección. `GunBurst` expone
`projectiles()` como vista de solo lectura, `isFiring()`, `flashVisible()` y
`clear()`; no colisiona, no causa daño y no toca vidas ni puntos.

Astra integra el cañón visual con PhaseOne. `PhaseOneGameController` posee un
`astraGun` (`GunBurst`) y lo expone con `astraGun()`; `advanceWeapons(frameDelta,
emissionSeconds, astraTrigger, shotRange)` avanza una vez por frame los
proyectiles existentes y emite nuevos solo dentro de `emissionSeconds`. Antes de
avanzar el reloj, `advanceFlight` guarda la posición y el rumbo de Astra
anteriores y posteriores a `fly()`, y la fuente de disparo interpola por arco
corto la instantánea de cada emisión; cada `GunProjectile` congela origen y
rumbo y no retiene referencias a la nave. `PhaseOneScreen` traduce
`isKeyPressed(SPACE)` como gatillo y llama a `advanceWeapons` exactamente una vez
tras el vuelo, el encuentro y la cámara del frame, también al terminar la fase
con `emissionSeconds=0`; conserva el `SPACE` de abandono y ESC/M. El alcance es
`2 × altura visible × camera.zoom`, congelado por disparo (unos 1200–1380
unidades). La geometría de los dos cañones vive en
`HeroShipSheet.cannonMouths(pose)`, como desplazamientos lateral/avanzado
respecto al centro de la caja estable, medidos sobre los fogonazos de
`NORMAL_FIRING`; `Astra.shotAt(centerX, centerY, yawDegrees, range)` los
transforma con `forward=(sin yaw, cos yaw)` y `right=(cos yaw, -sin yaw)`.
`shotAt` no activa láminas ni dibuja: el fogonazo anclado, el trazador y el HUD
M61 Vulcan corresponden a la hija final.

Vesper integra su cañón autónomo en el mismo controlador. `PhaseOneGameController`
posee un segundo `GunBurst`, `raiderGun()`, y `advanceWeapons(frameDelta,
emissionSeconds, astraTrigger, shipsCoincidentVisible, shotRange)` autoriza la
emisión del raider solo si la fase está activa (`emissionSeconds>0`), el Raider
existe y `shipsCoincidentVisible` es verdadero para el frame dibujado. Al cesar la
coincidencia, `GunBurst` deshabilita la emisión, reinicia deuda/pulso y conserva
los proyectiles ya emitidos; el Raider y el arma no se recrean al salir de cámara.
`VesperRaider.shotAt(centerX, centerY, headingDegrees, range)` usa
`forward=(sin heading, -cos heading)` y `right=(cos heading, sin heading)` y
transforma las bocas laterales medidas en `VesperRaiderSheet.cannonMouths(pose)`
(dos por pose, tomadas de los fogonazos simétricos que solo añade la lámina de
disparo; la pose de guiñada derecha completa el par oculto por simetría). Cada
`Shot` congela centro, bocas, rumbo y alcance, sin retener referencia a la nave.
La coincidencia de las armas se calcula **después** del vuelo, el encuentro y la
cámara del frame, con el helper puro `CameraVisibility.shipVisible(centerX,
centerY, halfBox, cameraX, cameraY, upX, upY, viewWidth, viewHeight, zoom)`; la
puntuación sigue usando la coincidencia muestreada antes del movimiento, de modo
que su regla y valores no cambian y nunca se reutiliza una coincidencia obsoleta
para disparar. El dibujo del HUD/arma visible sigue perteneciendo a la hija final.

Las láminas de vuelo mantienen sus cinco recortes: las tres poses de
alabeo existentes y dos poses de guiñada (derecha, izquierda). Astra selecciona
alabeo con laterales solos y guiñada con diagonales; la pantalla aplica la
rotación de rumbo en coordenadas de mundo. `RaiderEncounter` conserva una única
instancia de Vesper Raider por fase: espera una vez, aparece por delante de la
ruta inicial dentro de `WorldBounds` y persiste en coordenadas de mundo aunque
salga de cámara. `VesperRaider.steerTowards` gira el rumbo con un límite de
velocidad angular y `setDriftDirection`/`pose()` eligen la pose de guiñada al
girar o la de alabeo al derivar; el empuje acotado combina la aproximación al
jugador con una deriva reproducible de `UnitRandom` y una respuesta suave en los
bordes. La posición se recorta al mundo solo como salvaguarda. `PhaseOneScreen`
dibuja al raider cuando su círculo envolvente intersecta la vista real de la
cámara, sin destruirlo al salir de cámara, y no pasa ninguna `PlayArea` al
controlador. `Starfield.draw(shapes)` y `WorldScenery.draw(shapes,...)`
abren y cierran el dibujo con el ShapeRenderer recibido, que pertenece a la
pantalla. El coordinador cambia de pantalla después del render del
frame y libera la anterior.

Consulta posición y pose en la nave: `controller.astra().x()`,
`controller.activeRaider().y()`, `encounter.raider().headingDegrees()` y
`encounter.raider().turnDirection()`. No añadas métodos intermedios que solo
reenvíen estos getters. El enemigo puede ser null mientras espera su aparición;
consulta su presencia antes de usarlo.

## Clases actuales y métodos principales

Los métodos están enumerados por clase. Se omiten constructores y getters
secundarios. La indentación indica paquetes; la herencia se indica explícitamente.

```text
com.davidpe.cosmicaces
|
+-- boot
|   +-- DesktopLauncher: arranca la aplicación LWJGL3.
|   |   1. main(args)
|   +-- DesktopConfiguration: singleton de configuración de escritorio.
|       1. getInstance()  2. createConfiguration()
|
+-- application
|   +-- CosmicAcesGame extends Game: conecta bus, coordinador y pantallas.
|   |   1. create(): muestra WelcomeScreen y registra listeners.
|   |   2. render(): dibuja la pantalla y aplica la transición pendiente.
|   |   3. dispose(): libera pantalla y suscripciones.
|   +-- GameCoordinator: mantiene la partida global y decide transiciones.
|   |   1. onEvent()  2. state()  3. snapshot()
|   |   4. consumePendingTransition()
|   +-- GameController [abstracta]: reloj del recorrido y control de Astra.
|   |   1. start()  2. advanceRun()  3. isRunFinished()
|   |   4. astra()
|   +-- PhaseOneGameController extends GameController: encuentros y armas de PhaseOne.
|       1. placeAstra()  2. advanceFlight()  3. advanceEncounter()
|       4. isRaiderActive()  5. activeRaider()  6. setRaiderVisuals()
|       7. scorePoints()  8. astraGun()  9. raiderGun()  10. advanceWeapons()
|
+-- domain
|   +-- game
|   |   +-- GameState: identidad, fase, puntos y vidas de la partida.
|   |   |   1. changePhase()  2. addPoints()  3. loseLife()
|   |   |   4. completePhase()  5. snapshot()
|   |   +-- GamePhase [enum]: WELCOME, PLAYING_PHASE_ONE, GAME_OVER.
|   |   +-- PlayableRun: recorrido actual de 60 segundos.
|   |   |   1. start()  2. advance()  3. isFinished()  4. remainingSeconds()
|   |   +-- PhaseScore: puntos por segundos completos y coincidencia visible.
|   |   |   1. advance()  2. totalPoints()
|   |   +-- WorldBounds: dimensiones finitas y límites para una caja.
|   |   |   1. maxX()  2. maxY()  3. clampX()  4. clampY()
|   |   +-- GameId: identidad de partida para descartar eventos antiguos.
|   |   +-- PhaseResult: resultado inmutable con fase, puntos y vidas.
|   |   +-- GameEvent: contrato de mensajes del juego.
|   |       +-- StartRequested: solicita empezar.
|   |       +-- PointsEarned: comunica puntos obtenidos.
|   |       +-- LifeLost: comunica una vida perdida.
|   |       +-- PhaseCompleted: comunica el resultado de la fase.
|   |       +-- GameAbandoned: solicita abandonar.
|   +-- ship
|   |   +-- Ship [abstracta]: posicion, velocidad, dimensiones y dibujo.
|   |   |   1. advance() [protegido]  2. setPosition() [protegido]
|   |   |   3. draw()  4. draw(batch, rotationDegrees)
|   |   |   5. currentRegion() [abstracto protegido]
|   |   +-- MovementIntent: direccion deseada para cualquier nave.
|   |       1. none()  2. fromDirections()  3. fromDownwardHeading()
|   +-- player
|   |   +-- Astra extends Ship: protagonista y estado de vuelo por rumbo.
|   |   |   1. placeAt()  2. fly()  3. yawDegrees()  4. flightSpeed()
|   |   |   5. ultraRemainingSeconds()  6. setVisuals()  7. currentRegion()
|   |   |   8. shotAt(): instantánea de los dos cañones para una emisión.
|   |   |   +-- Visuals: carga texturas; dispose() las libera.
|   |   +-- FlightControls: teclas de pilotaje y activación puntual de Ultra.
|   |   |   1. neutral()  2. lateral()  3. turning()
|   |   +-- FlightTuning: parámetros de vuelo aprobados de PhaseOne.
|   |   +-- HeroShipSheet: laminas v3 y cinco recortes de sprites de Astra.
|   |       1. sheets()  2. firingSheets()  3. cannonMouths()
|   |       +-- CannonMouth(lateral, forward): boca de cañon medida por pose.
|   +-- enemy
|   |   +-- VesperRaider extends Ship: enemigo autónomo con rumbo en el mundo.
|   |   |   1. advance()  2. steerTowards()  3. setHeadingDegrees()
|   |   |   4. setDriftDirection()  5. clampToWorld()  6. pose()  7. setVisuals()
|   |   |   8. shotAt(): instantánea de los dos cañones laterales para una emisión.
|   |   |   +-- Visuals: carga textura; dispose() la libera.
|   |   +-- RaiderEncounter: única aparición persistente del enemigo en el mundo.
|   |   |   1. advance()  2. isActive()  3. raider()  4. setVisuals()
|   |   +-- VesperRaiderSheet: lamina v3 y cinco recortes del enemigo.
|   |   |   1. slice()  2. poseForBank()  3. poseForTurn()
|   |   |   4. orientedForDescent()  5. maxSliceWidth()  6. maxSliceHeight()
|   |   |   7. cannonMouths()
|   |   |   +-- CannonMouth(lateral, forward): boca de cañón medida por pose.
|   |   +-- UnitRandom: fuente sustituible de aleatoriedad.
|   |       1. nextUnit()
|   +-- weapon
|   |   +-- GunTuning: cadencia, velocidad y duración del fogonazo del M61 Vulcan.
|   |   +-- GunProjectile: proyectil rectilíneo con origen y rumbo congelados.
|   |   |   1. advance()  2. expired()  3. x()  4. y()  5. forwardX()  6. forwardY()
|   |   |   7. travelled()
|   |   +-- GunBurst: emisor visual con cadencia, fogonazo y colección de proyectiles.
|   |   |   1. advance()  2. projectiles()  3. isFiring()  4. flashVisible()  5. clear()
|   |   |   +-- Muzzle(x,y): origen de un cañón congelado al disparar.
|   |   |   +-- Shot(muzzles,forwardX,forwardY,range): instantánea de una emisión.
|   |   |   +-- ShotSource: shotAt(offsetSeconds) entrega la instantánea.
|   |   +-- GunBurstVisual: aspecto reutilizable de un trazador, sin recursos propios.
|   |       1. draw(): punta y estela según posición y vector del proyectil.
|   +-- scenery
|       +-- Starfield: estrellas con scroll de la bienvenida.
|       |   1. update(): mueve y recicla estrellas.
|       |   2. draw(): dibuja con ShapeRenderer.
|       +-- WorldScenery: estrellas e isletas persistentes de PhaseOne.
|           1. stars()  2. islands()  3. visibleStarCount()  4. draw()
|
+-- infrastructure
    +-- gdx
        +-- ScreenFactory: construye pantallas y conecta dependencias.
        |   1. createWelcomeScreen()  2. createPhaseOneScreen()
        +-- VirtualScreenSize: resolucion virtual compartida, 800 x 600.
        +-- event
        |   +-- GameEventBus: bus sincrono por tipo concreto de evento.
        |   |   1. subscribe()  2. publish()
        |   +-- GameEventPublisher: contrato de publicacion.
        |   |   1. publish()
        |   +-- Subscription: cancelacion de un listener.
        |       1. cancel()
        +-- screen
            +-- WelcomeScreen: inicio, estrellas e input de la tecla Y.
            |   1. render()  2. resize()  3. dispose()
            +-- PhaseOneScreen: input, dibujo de mundo y HUD, recursos.
            |   1. render()  2. resize()  3. dispose()
            +-- FlightCameraState: seguimiento y zoom interpolados.
            |   1. update()  2. x()  3. y()  4. yawDegrees()  5. zoom()
            +-- CameraVisibility: regla pura de visibilidad de una nave en la vista real.
            |   1. shipVisible()
            +-- FlightCamera: aplica el estado a OrthographicCamera.
            |   1. update()
            +-- FlightHud: brújula, velocidad y score en coordenadas de pantalla.
                1. headingLabel()  2. headingDegrees()  3. draw()  4. drawScore()
            +-- FlightMinimap: overlay opcional del mundo, Astra y Vesper Raider.
                1. toggle()  2. isVisible()  3. draw()
            +-- MinimapProjection: adaptación y proyección del mundo al panel.
                1. fit()  2. project()  3. astraDirection()  4. raiderDirection()
```

## Comportamiento provisional actual

PlayableRun fija la duración en 60 segundos. Al terminar la fase, GameCoordinator
cambia el estado a GAME_OVER y PhaseOneScreen permanece visible con el mensaje
final hasta abandonar. Todavía no existe una pantalla de game over ni hall of fame.
El bus actual es propio y síncrono, sin Guava. ScreenFactory se instancia con sus
dependencias; no es un singleton.

Actualiza este árbol y las reglas de ubicación cuando se añadan, muevan o retiren
clases o cambien sus responsabilidades.
