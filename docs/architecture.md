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
| `domain.effect` | Efectos gráficos reutilizables del juego | Recortes de explosión y recursos compartidos; sin reglas de daño/reaparición. |
| `domain.collision` | Geometría común entre fases | Contactos y trayectorias barridas; sin daño, input, cámara o reglas de enemigos. |
| `domain.scenery` | Elementos reutilizables del escenario y modelo procedural del terreno | `Starfield`; mapa, configuración, regiones y distribución determinista de decoración. Sin carga de imágenes ni dependencia de pantallas. |
| `infrastructure.gdx` | Composición de pantallas y configuración de presentación | ScreenFactory, resolución virtual, catálogo de vídeo y preferencias persistentes locales. |
| `infrastructure.gdx.scenery` | Catálogo, propiedad de atlas y dibujo reutilizable del escenario | Repetición del suelo y dibujo de instancias; las pantallas son dueñas del renderer y lo liberan en `dispose()`. |
| `infrastructure.gdx.screen` | Pantallas concretas y catálogos de geometría de la pantalla | Input, cámara, viewport, layout, fuentes, geometría de tiles y ciclo de vida de recursos. |
| `infrastructure.gdx.event` | Distribución de eventos | Bus, publicación y cancelación de suscripciones. |
| `infrastructure.gdx.audio` | Adaptadores y recursos de audio de las fases | `PhaseOneAudio` encapsula `Gdx.audio`/`Gdx.files`; dueño único de Music/Sound de la fase. El dominio y `PhaseOneGameController` no importan clases de audio. |

Ubica cada nueva clase según lo que hace. Extiende un paquete existente cuando
su responsabilidad encaje ahí; crea un paquete nuevo cuando exista una necesidad
concreta. No añadas clases base, interfaces ni paquetes vacíos para anticipar
funcionalidades todavía sin definir.

LibGDX está permitido en el dominio: las naves y los elementos del escenario
pueden encapsular su dibujo. El dominio no importa clases de `application`,
`infrastructure` o `boot`. Las dimensiones y otros parámetros externos se pasan
al objeto, como hace Starfield con el tamaño del área.

`scripts/PackTerrainAtlas.java` empaqueta los trece recursos de terreno en una página
RGBA8888 de 2560×2200 con extrusión de dos píxeles para filtrado lineal; conserva los originales,
normaliza las variantes de alta resolución dentro de sus regiones y mezcla 32 píxeles
de los bordes opuestos de la única textura de pradera activa. El atlas incluye las bases aliada y
enemiga, ambas de 3072×2304 unidades y alineadas en X en los extremos sur y norte respectivamente;
las dos zonas se reservan para que la decoración no invada edificios ni pistas. `TerrainSceneryRenderer`
dibuja ambas desde el atlas. `FlightMinimap` señala el centro aliado con un punto cian y el enemigo
con una X roja. El renderer vive en `infrastructure.gdx.scenery`; una pantalla lo crea en el hilo
LibGDX y libera su atlas compartido al salir.

Los controladores de fase en application coordinan el dominio. GameCoordinator
mantiene el estado global y decide las transiciones. CosmicAcesGame es la excepción
de composición: depende de LibGDX y de infraestructura para construir pantallas y
aplicar esas transiciones. Infraestructura puede depender de application y domain;
boot ensambla y arranca la aplicación.

Las pantallas traducen el input en llamadas al controlador y publican eventos.
La presentación de bienvenida y PhaseOne usa `ScreenViewport` con una unidad por
píxel lógico: la ventana maximizada/redimensionable cambia el rectángulo visible
sin escalar sprites ni añadir bandas de aspecto. Las cámaras de mundo conservan
su posición y zoom al redimensionar; la cámara HUD se recentra en el tamaño lógico
efectivo. `VirtualScreenSize` (1024×768) sigue siendo la referencia para valores
de juego y tamaño restaurado de ventana, no el límite del viewport. En HiDPI, las
dimensiones del viewport provienen del callback lógico `resize`, no del backbuffer.
En PhaseOne, `FlightControls` separa ↑/↓/←/→ y la pulsación de P del
`MovementIntent` cartesiano que aún usan otras naves. `Astra` posee posición,
rumbo, velocidad instantánea, tiempo restante de Ultra y su uso único por fase.
`PhaseOneGameController.advanceFlight` aplica únicamente el delta que resta
del recorrido y fija el mundo en 8192×12000: ocho anchos virtuales de 1024
unidades. `FlightSpeeds` guarda los objetivos configurables de Astra (normal 150, turbo 650,
Ultra 750 u/s por defecto; independientes entre 50 y 1000 en pasos de 10).
`FlightTuning` reúne aceleración, frenado y giro (130°/s; el desplazamiento lateral-solo conserva 300 u/s como
`STRAFE_SPEED`, separado de la velocidad de crucero); el borde anticipa el giro
según la velocidad y el recorte de posición es la última salvaguarda.
PhaseOne conserva un `PhaseOneScenery` determinista por ejecución en el controlador: un mapa de
terreno y un campo de decoración con reserva para la base se generan una sola vez con semilla fija.
La pista ocupa el extremo sur, Astra despega desde su umbral y cada reaparición vuelve allí, orientada
al norte. `PhaseOneScreen` usa `TerrainSceneryRenderer` para repetir el césped, dibujar la base y
dibujar árboles, arbustos, charcos y rocas dentro
de la caja visible de la cámara. `PhaseOneTerrainPlanner.visibleBounds` calcula la caja conservadora
de cámara con giro, zoom y dimensiones reales del `ScreenViewport`; así se consultan solo adornos
cercanos y se recortan los sprites que cruzan el límite del mundo. El atlas compartido de una página
se carga al crear la pantalla y se libera al salir. El terreno y la decoración se dibujan antes de
naves y efectos; el HUD mantiene su cámara independiente. No se genera ni carga terreno por frame y
la pantalla no abre los 128 PNG históricos. El minimapa conserva su marco y marcadores de Astra,
Vesper, Ghostwing, las dos bases y el Bastion Bomber tras despegar. `WelcomeScreen`
mantiene el `Starfield` y redistribuye sus estrellas dentro del tamaño lógico nuevo cuando
`WelcomeScreen.resize` cambia el viewport. La composición ASCII, su panel de fondo y el prompt se centran con las
dimensiones efectivas; el arte y la tipografía mantienen su escala.
`ScreenFactory` comparte `VideoDisplaySettings` y `GamePresentationSettings` entre la bienvenida
y las fases durante la sesión de aplicación. `GameSettingsStore` usa las preferencias locales de
LibGDX (archivo de ajustes de la aplicación), se inicializa en `CosmicAcesGame.create()` cuando el
contexto está disponible y persiste el encuadre, velocidades y modelo visual de Astra, el tamaño de ventana y la selección de pantalla completa.
Una preferencia de pantalla completa se valida contra el monitor actual al arrancar; si el modo ya
no existe, la aplicación mantiene un modo utilizable. `WelcomeScreen` abre Ajustes con ESC; sus secciones
Vídeo, Encuadre, Nave y Velocidades de Astra son overlays de infraestructura y no cambian la transición de Y ni el ESC
de PhaseOne. `VideoDisplaySettings` consulta cada vez los modos que el monitor actual anuncia y
aplica cambios de ventana/pantalla completa con restauración segura si el backend rechaza el modo.
`GamePresentationSettings` aplica el encuadre de Astra (18 % inicial, 10–35 %, pasos de 1 %) y el
modelo visual elegido (`AstraVariant`, original por defecto, alternado con `cycleAstraVariant`) y los
guarda a través de `GameSettingsStore`; `PhaseOneScreen` lo inyecta en `FlightCamera`, que conserva
ese porcentaje en exploración y combate. El árbol de clases debe incluir el adaptador de almacenamiento
junto a la configuración de presentación.
`FlightCameraState` calcula posición, rumbo y zoom interpolados sin depender
del nativo LibGDX; `FlightCamera` aplica ese estado a `OrthographicCamera`.
En exploración, el centro de Astra queda en el porcentaje persistido (18 % inicial,
configurable entre 10–35 %) de la altura visible desde el borde inferior; el adelanto del foco se calcula con el alto lógico efectivo y el
zoom de vuelo para conservar esa posición en 4:3, 16:10 y 16:9. La cámara sigue
el foco con 0,10 s de retardo y suaviza el yaw durante 0,25 s; usa zoom
1,00/1,08/1,15. Con COS-38 la cámara tiene un encuadre
de combate: mientras el enemigo vivo intersecta el **encuadre de exploración de
referencia** (foco derivado de Astra, yaw suavizado y adelanto del alto visible con
zoom del modo de vuelo, nunca el adelanto/zoom de combate que se está decidiendo), el objetivo pasa
a zoom `max(modo de vuelo, 1,25)` y recalcula el adelanto según ese zoom para mantener Astra
al 18 % desde el borde inferior, interpolados con las mismas constantes (0,25 s zoom,
0,10 s posición). El cambio es un latch con umbrales
distintos: entra al cruzar el marco de exploración y sale solo tras permanecer
más de 0,75 s más allá de un margen adicional de 120 unidades, de modo que los
cruces breves del borde no alternan el modo. Sin enemigo activo (destruido, en
espera de reaparecer o fase terminada) el objetivo se libera de inmediato y los
valores vuelven suavemente; la referencia estable evita que el zoom de combate se
active a sí mismo. `PhaseOneScreen` pasa el centro/caja del raider activo antes de
dibujar; la visibilidad de armas, dibujo y minimapa sigue usando la cámara real ya
actualizada y la puntuación conserva su muestreo previo al movimiento.
`PhaseOneScreen` separa la proyección de mundo de la del HUD fijo.
`FlightHud` presenta rumbo y velocidad instantánea como texto directo sobre
el mundo, sin panel ni títulos, anclando la velocidad al margen derecho actual;
el score acumulado también se alinea a esa esquina. El minimapa y su reserva del
indicador de Vesper usan el mismo tamaño lógico actual de la cámara HUD, por lo
que ambos permanecen juntos en el borde derecho después de resize. `PhaseScore`
(domain.game) convierte el tiempo válido del recorrido en
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
lecturas. Marca el centro aliado con un punto cian y el enemigo con una X roja,
además de Astra, Vesper y Ghostwing. `GoForwardPrompt`, propiedad de `PhaseOneScreen`, crea y
libera una textura procedural con aro azul degradado y dispone “GO FORWARD” en
un arco alrededor de Astra; aparece al iniciar y en cada reaparición, y cualquier
tecla o clic lo oculta. Durante el combate, `PhaseOneScreen` oculta el minimapa mientras un
Vesper activo intersecta el viewport, usando la misma comprobación de cámara
rotada y zoom que decide dibujar al enemigo. Al salir del viewport o quedar
destruido, el mapa recupera la preferencia elegida con M; la ocultación temporal
no modifica esa preferencia. `MinimapProjection` adapta el `WorldBounds` completo al panel sin
deformar la proporción; proyecta las isletas persistentes, la flecha de Astra y
el punto rojo con estela direccional de Vesper Raider.
El estado global vive en GameState; el estado de un recorrido vive en el controlador
de su fase y en los objetos que coordina. Una pantalla nueva se incorpora a
ScreenFactory y al flujo de navegación de CosmicAcesGame y GameCoordinator.

Las pantallas crean y liberan sus recursos gráficos. Astra.Visuals y
VesperRaider.Visuals cargan sus texturas y las liberan mediante dispose(); la pantalla
es su propietaria. Ship.draw(batch) usa un SpriteBatch ya abierto por la pantalla.
`GhostwingDroneSheet` contiene solo metadatos de la lámina Ghostwing existente:
2172×724 píxeles, tres fotogramas cuadrados de 724×724 y morro hacia +Y.
`internalPath()`, `frameSlices()` y `frameSlice(int)` exponen recortes sin cargar
texturas; el futuro objeto visual de Ghostwing deberá compartir una textura por
pantalla y liberarla allí.

### Grupos de drones Ghostwing con vuelo mundial (COS-46)

`domain.enemy.GhostwingDrone` es una nave sin arma que extiende `Ship`: caja estable
72×72 (celda cuadrada 724×724 sin estirar), dirección cardinal fija y reloj de
hélice de tres fases (`PROPELLER_FRAME_SECONDS = 0,08 s`). El enum `Direction`
mapea cada sentido a su rotación de render sobre la orientación base de la lámina
(morro +Y): norte 0°, este -90°, sur 180° y oeste +90°, siempre respecto al centro
de la caja. `advance(dt)` mueve en línea recta (movimiento lineal: dividir el delta
no cambia la trayectoria) y cicla la hélice; no persigue, gira ni dispara. El dron
no posee textura: `Visuals` carga una única `Texture` con sus tres regiones y es
propiedad de la pantalla que las use (la hija de presentación), mientras el modelo
se construye y prueba sin iniciar GDX. `currentRegion()` lanza si no se han adjuntado
los visuals, igual que Vesper.

`domain.enemy.GhostwingEncounter` programa y vuela los grupos independientemente de
`RaiderEncounter`. Cada grupo (`GhostwingGroup`, identificable por `id()`) contiene
2–10 drones (ambos extremos incluidos, `MIN_GROUP_SIZE`/`MAX_GROUP_SIZE`), entra
completo desde **fuera** de un borde mundial y cruza en recta a 90 u/s
(`DRONE_SPEED`, por debajo del crucero de Astra 230 u/s y del Raider 180 u/s) hasta
que cada miembro supera el borde opuesto; `GhostwingGroup.advance` retira a los que
completan el cruce y el encuentro elimina el grupo vacío. La aparición usa el mismo
patrón inyectable `UnitRandom` que el Raider: primer y siguientes intervalos en
[20, 30] s de **tiempo activo** de fase, eje (horizontal/vertical) y sentido
aleatorios, techo de 3 grupos vivos (`MAX_ACTIVE_GROUPS`) con aplazamiento de la
aparición pendiente (el contador se mantiene en 0) para que liberar un cupo genere
como máximo un grupo nuevo, nunca una ráfaga. La formación escala los centros en el
eje perpendicular con `FORMATION_SPACING = 1,4 × caja` (distancia entre centros
siempre mayor que la caja de colisión) y un desplazamiento longitudinal acotado
(`FORMATION_LONGITUDINAL_JITTER = 0,9 × caja`); no se usan `clampX/Y` al mover los
drones para que crucen y salgan del mundo. `markDestroyed(id)` retira un dron (inerte
para combate posterior, sin reciclado) y elimina el grupo sin miembros;
`representativePoint(group)` devuelve el centroide de los miembros vivos, base del
símbolo único por grupo del minimapa.

`PhaseOneGameController` posee un `GhostwingEncounter` con fuente de aleatoriedad
propia (constructor de dos `UnitRandom`, uno por encuentro). `advanceGhostwing(dt)`
alimenta solo el tramo activo de la fase (acotado por el frame abierto o, fuera de
él, por `remainingRunSeconds()`) y no toca armas, puntuación, cámara ni el Raider;
`ghostwingEncounter()` expone el encuentro para presentación y pruebas.
`PhaseOneScreen` llama a `advanceGhostwing(delta)` una vez por frame dentro del
recorrido, junto a `advanceEncounter`; dibujar sprites, explosiones y marcador de
minimapa pertenece a la hija de presentación.

### Combate Ghostwing en PhaseOne (COS-47)

El controlador integra a los drones vivos en el combate sin mezclarlos con las reglas
de Vesper. `astraProjectileStep` barre cada proyectil de Astra contra todas las formas
de casco de la fase —el círculo del Raider (activo o protector) y el círculo inscrito
de cada dron activo (`BODY_RADIUS_FACTOR`, radio 36 u para la caja 72×72)— y elige el
blanco de **menor fracción de entrada** sobre el segmento; los empates se resuelven por
`ProjectileTarget(fraction, kind, droneId)` (dron < blindaje Raider < casco Raider y
menor id de dron), de modo que el resultado no depende del orden de iteración. Un
proyectil se consume una vez y destruye como máximo un blanco: el dron elegido se
retira al instante con `markDestroyed`, así un segundo proyectil del mismo frame no lo
re-impacta, y el Raider conserva exactamente su paso de daño de diez impactos y su
bonus de 1000 puntos. `destroyGhostwingDrone(id, cause)` elimina el dron vivo, emite
`GhostwingDeath` una única vez y suma el bonus solo cuando la causa es disparo.

`resolveGhostwingContact`, invocado en `advanceCombat` después del contacto Vesper
existente y solo si `astraCombat.canBeHit()`, barre el movimiento relativo exacto de
ambos círculos traslatorios sobre el mismo frame (`CollisionDetector.sweep`): Astra
barre su `AstraMotion` recién capturada por `advanceFlight` y cada dron se reconstruye
restando su vector cardinal fijo × `DRONE_SPEED` × el mismo tramo activo que recibió
`advanceGhostwing`, por lo que ni la velocidad ni los frames largos atraviesan el casco.
En un contacto válido se destruye solo el dron implicado (causa `COLLISION`, cero
puntos) y se llama al `destroyAstra()` existente; `handleRespawns()` mantiene la
reaparición de 3 s con 2 s de inmunidad. Un Astra destruido o protegido nunca colisiona,
igual que con el Raider, y no hay daño en área ni reacción en cadena: los drones
próximos a una explosión no se ven afectados.

`PhaseScore` añade `GHOSTWING_DESTROYED_BONUS = 100` y `awardGhostwingDestroyed()`,
llamado solo en la transición activa→destruido por disparo; el bonus se drena por
`pendingCombatPoints`/`drainCombatPoints()` y se publica con `PointsEarned`, de modo
que un choque, una fuga o repetir impacto sobre un dron ya muerto nunca puntúan.
`drainGhostwingDeaths()` devuelve y limpia los `GhostwingDeath(droneId, centerX,
centerY, cause)` pendientes (causa `SHOT` o `COLLISION`), que la presentación (hija
COS-48) convertirá en explosiones ancladas exactamente una vez por dron; los drones
que completan el cruce se retiran sin evento.

### Presentación Ghostwing en PhaseOne (COS-48)

`PhaseOneScreen` posee un único `GhostwingDrone.Visuals` (textura compartida y tres regiones de
hélice) creado en su bloque de carga con limpieza parcial y liberado una sola vez en `dispose()`.
Cada frame, `drawGhostwingDrones(batch)` adjunta esos visuals a todos los drones vivos (idempotente,
así los grupos recién aparecidos siempre dibujan) y en el mismo `SpriteBatch`/proyección mundial que
Astra/Vesper solo a los que intersectan la vista rotada real (`CameraVisibility.shipVisible`), con
`Ship.draw(batch, rotationDegrees)` usando el rumbo cardinal y el frame de hélice del modelo. Un
dron destruido sale de `activeDrones()` en el frame de su muerte, por lo que no vuelve a dibujarse
aunque su explosión siga visible (CA6).

`domain.effect.ShipExplosions` es un pool puro (sin LibGDX) de explosiones simultáneas:
`spawn(centerX, centerY, peakSize)` añade una en el centro mundial congelado, `advance(dt)` avanza
todas y retira las terminadas, `active()` expone las que siguen. `PhaseOneScreen.updateExplosions`
consume exactamente una vez por frame los `GhostwingDeath` de `drainGhostwingDeaths()`: cada evento
crea una explosión anclada y añade un cue a `audio.playExplosions(...)`, sumado a los de Astra/Vesper
—un choque Astra+Ghostwing produce dos explosiones y dos cues; un dron ya muerto nunca reinicia su
explosión. Todas las explosiones de drones comparten la `ShipExplosionVisuals` existente y se dibujan
en `drawExplosions`. El pico es `1,8 ×` la caja 72×72 del dron (ajuste visual del PO).

`FlightMinimap.draw` recibe ahora una lista de puntos representativos
(`GhostwingEncounter.representativePoint(group)`, uno por grupo activo —centroide de los miembros
vivos—, no por dron) y dibuja exactamente un rombo ámbar hueco por grupo
(`GROUP_MARKER_HALF = 6` px; vértices puros en `diamondVertices`, probados sin renderer). El grupo
sin miembros vivos desaparece del encuentro y del mapa; la preferencia M y la ocultación temporal
por combate se conservan, y esa ocultación sigue siendo solo por Vesper.

El indicador fuera de cámara (`OffscreenEnemyIndicator`) y el encuadre de combate
(`cameraCombatTarget`) permanecen estrictamente en Vesper: retienen la API del raider y nunca
reciben drones, de modo que Ghostwing no genera triángulo de dogfight ni activa el encuadre del
Raider (CA8), y la cámara, HUD y señalización de Vesper quedan intactos (CA9). La comprobación
visual de tamaño/color/legibilidad/audio y ritmo pertenece al PO humano.
Ship.draw(batch) mantiene la proporción de las regiones dentro de una caja estable.

### Bastion Bomber en PhaseOne

`domain.enemy.BastionBomberSheet` describe los cinco recortes del PNG de giro del bombardero,
medidos desde la esquina superior izquierda; `BastionBomber.Visuals` convierte esa coordenada al
origen inferior de `TextureRegion`, comparte una textura por pantalla y la libera en `dispose()`.
`BastionBomber` es una `Ship` sin armas: permanece en la pista enemiga durante 10 s de tiempo activo,
vuela hacia la pista aliada a velocidad fija y, al entrar en un radio de 330 u del centro, mantiene
una órbita circular. El estado, rumbo y fotograma se pueden probar sin contexto gráfico.
`ShipCombatState.bastionBomber()` requiere tres veces los impactos de Vesper para agotar la energía;
el controlador no lo reaparece ni le asigna disparos. `PhaseOneGameController` integra su avance,
impactos de los proyectiles de Astra y transición de destrucción; `PhaseOneScreen` lo dibuja y crea
su explosión. `FlightMinimap` no lo muestra mientras espera y, tras despegar, pinta un marcador rojo
intermitente con intervalos de 0,3 s.
`BastionEscort` mantiene dos encuentros Vesper adicionales: se despliegan a izquierda y derecha
cuando el bombardero despega y conservan esos puestos mientras Astra está fuera de 1800 u. Al
detectar a Astra pasan al mismo encuentro de aproximación, pasadas y disparo que el Raider normal.
El controlador resuelve por separado su energía, impactos, cañones, puntos y muertes; la pantalla
reutiliza las texturas compartidas de Vesper y solo emite disparos cuando escolta y Astra coinciden
en cámara. La aparición inicial del Raider principal permanece independiente.
Las láminas v3 de Astra y v4 de Vesper Raider tienen cinco recortes: las tres poses de
alabeo y dos de guiñada. Las variantes de disparo ya están preparadas:
`HeroShipSheet.NORMAL_FIRING` y `ACCELERATE_FIRING` definen los recortes propios
de cada PNG; `firingSheets()` las enumera sin cambiar las referencias de tamaño
de las láminas de vuelo. `VesperRaiderSheet.firingInternalPath()`, `firingSlice()`
y `firingSlices()` proporcionan la variante del enemigo. Los recortes incluyen
fogonazos y excluyen manchas aisladas de fondo; los tres primeros dibujos de
algunas láminas se tocan en los bordes, por lo que no son celdas uniformes.
`Astra.Visuals.region(pose, accelerating, firing)` y
`VesperRaider.Visuals.region(pose, firing)` exponen las regiones listas para usar.
Los PNG de Vesper se agrupan en `assets/images/enemy/vesper_raider/`; el juego
carga `vesper_raider_roll_sheet_v4.png` y `vesper_raider_roll_sheet_v4_firing.png`
(2079 × 756). Las versiones anteriores se conservan en la misma carpeta.
La variante del Raider conserva el mismo volteo vertical que su lámina de vuelo.
Las pantallas liberan las cuatro texturas de Astra y las dos de Vesper mediante
sus `Visuals.dispose()`. La preparación no activa el disparo: el temporizador de
fogonazo, input, proyectiles y HUD pertenecen a COS-27. Al integrarlos, conservar
la escala y ancla del cuerpo entre variantes: las imágenes fuente de disparo
tienen diferencias de geometría y margen respecto a las originales; no estirar
cada pose para rellenar la caja ni aumentar la caja de vuelo por el fogonazo.

### Audio de PhaseOne y loop de cabina (COS-42)

`infrastructure.gdx.audio.PhaseOneAudio` es el adaptador de audio de la fase, propietario de los
recursos nativos de sonido: encapsula `Gdx.audio`/`Gdx.files` para que el dominio y
`PhaseOneGameController` no importen clases de audio y centraliza los volúmenes (sin UI de mezcla
por alcance de COS-41). `PhaseOneScreen` lo construye junto con los demás recursos (con limpieza
parcial en el constructor) y lo libera exactamente una vez en `dispose()`, de modo que cambiar de
pantalla o cerrar el juego detiene y libera la música; WelcomeScreen no crea audio de combate.

El ambiente de cabina se reproduce como `Music` en bucle desde
`assets/sounds/sound-airplane-cabin-near-ground-pcm16.wav`, un WAV PCM 16-bit estéreo 44,1 kHz de
120 s derivado del original Float32 de 42 MB (que permanece intacto). La conversión es obligatoria:
el decoder `Wav` del backend LWJGL3 1.12.1 solo admite PCM (tipo 1) de 16 bits y rechaza IEEE float
y otros bits con `GdxRuntimeException`. El derivado se generó con
`afconvert -f WAVE -d LEI16@44100 -c 2` preservando canales, sample rate y duración. `Music` se
reproduce como stream sin cargar el clip entero como efecto corto.

La política de loop es un helper puro y testeable sin dispositivo de audio:
`PhaseOneAudio.cabinAmbienceAudible(astraActive, runFinished, flightSpeed)` — audible solo con
Astra activa, fase sin terminar y velocidad de avance finita > 0 (estado real de la nave, nunca una
tecla sostenida). `updateCabinAmbience(boolean)` es idempotente: solo llama `play()`/`pause()` en el
cambio de estado, de modo que un frame nunca reinicia, copia ni apila el stream (`Music.play()` de
OpenALMusic ya es no-op si está sonando y `pause()` conserva la posición). La pantalla evalúa la
política una vez por frame después de `updateExplosions(...)`, cuando `runFinished`, vida de Astra y
velocidad ya reflejan este frame: destrucción de Astra, velocidad cero, fin de fase o salida de
pantalla pausan/detienen el loop; el vuelo activo posterior reanuda la misma instancia. COS-43 y
COS-44 extienden este mismo dueño con los efectos de ráfaga y explosión.

### Audio de PhaseOne: cue M61 sincronizado (COS-43)

La ráfaga audible se sincroniza con el reloj de emisión real del arma, no con el frame, la tecla ni
el fogonazo. `domain.weapon.GunBurst` añade un contador monotónico `emissionCount()`: incrementa una
vez por cada `emit(...)` que produce un `Shot` (nunca por boca/cañón, proyectil ni frame). Sigue
determinista y sin dependencias de infraestructura; `clear()` no lo resetea para que los deltas entre
muestras sigan siendo válidos.

`PhaseOneScreen` toma una muestra del contador de `astraGun()` y `raiderGun()` antes y después de su
única llamada por frame a `advanceWeapons(...)` y convierte cada delta con el helper puro
`PhaseOneAudio.burstsSince(before, after)` (devuelve 0 ante muestras no crecientes). Como la
autorización vive en `PhaseOneGameController` (`astraEnabled`, `raiderEnabled`), un delta cero
significa «no hubo ráfaga»: SPACE sostenido sin emisión, nave destruida, Vesper sin coincidencia o
fase terminada no reproducen sonido. Astra y Vesper comparten el mismo efecto.

El asset es `assets/sounds/sound-machine-gun-burst-pcm16.wav`: un recorte de 0,08 s (a la cadencia de
Astra) dentro del WAV original de 1,95 s, tomado de la ventana de mayor energía y con fades cortos
de entrada/salida para evitar clics. El original `sound-machine-gun-2-8-bit.wav` queda intacto. El
recorte se carga como `Sound` en memoria (efecto corto, baja latencia, voces solapadas acotadas por
su propia duración) en el mismo `PhaseOneAudio` owner de COS-42, con volumen centralizado
`VULCAN_BURST_VOLUME` y liberación única en `dispose()`. `VULCAN_BURST_SECONDS ≤
GunTuning.BURST_INTERVAL_SECONDS` garantiza que una ráfaga no empiece antes de que acabe la anterior
(y la prueba lo fija); la escucha y el ajuste perceptual de duración/volumen quedan para el PO.

### Audio de PhaseOne: explosión por destrucción (COS-44)

La explosión se sincroniza con la transición «nave viva → destruida», no con el frame ni con la
animación. `PhaseOneScreen.updateExplosions(...)` ya es el único punto que detecta esa transición por
nave (banderas `astraExplosionSpawned`/`raiderExplosionSpawned`, que solo se reinician cuando la nave
vuelve a estar viva); antes de mutarlas, la pantalla consulta el helper puro
`PhaseOneAudio.explosionCues(astraDestroyed, astraSpawned, raiderDestroyed, raiderSpawned)` y envía el
total a `PhaseOneAudio.playExplosions(int)`. Así una destrucción reproduce un cue exacto, una nave que
sigue destruida en frames posteriores no añade voces, y un choque mutuo que destruye ambas produce una
transición por nave (dos cues, independientes de la causa: proyectil, energía agotada o colisión).

El asset es `assets/sounds/sound-crunchy-explosion1.wav`, un WAV **mono PCM 16-bit 44,1 kHz** de
1,731 s. Al ser PCM de 16 bits el decoder `Wav` de LWJGL3 1.12.1 lo acepta tal cual, sin derivado (a
diferencia del ambiente Float32 de COS-42); se carga como `Sound` en memoria y se libera en el
`dispose()` único de `PhaseOneAudio`, con volumen centralizado `EXPLOSION_VOLUME`. El dominio sigue
sin dependencias de audio: solo la pantalla traduce su estado de destrucción en reproducción.

### Detector de colisiones preparado para COS-32

`domain.collision.CollisionDetector` es una utilidad sin estado ni LibGDX, utilizable
por todas las fases y tipos de entidad. Sus records inmutables `Circle`, `Box`
(centro, semianchos y ángulo matemático antihorario desde X) y `Segment` reciben
coordenadas del mundo, nunca del viewport. `overlaps(shapeA, shapeB)` detecta
contacto entre círculos, círculo/caja orientada y dos cajas orientadas (SAT).
Tangencias cuentan como contacto; un círculo de radio cero representa un punto.

`firstHit(segment, shape)` devuelve `OptionalDouble` con la primera fracción del
recorrido [0,1], o vacío; una superposición inicial devuelve cero. `sweep` acepta
dos círculos móviles o un círculo y una caja móvil de orientación fija, usando
movimiento relativo. La expansión círculo/caja conserva esquinas redondeadas;
no confunde la caja expandida con la geometría exacta. Radios/tamaño/orientación
deben permanecer constantes durante cada barrido. No calcula barridos exactos de
cajas rotatorias ni polígonos arbitrarios: usar subpasos o envolventes circulares
para naves que viran. Tamaños/posiciones se validan y no se crean recursos nativos.

La fase adapta las entidades a estas formas, consulta el detector y decide daños,
inmunidad, consumo de proyectiles, destrucción y evitación de choques. El detector
no añade colisiones a elementos decorativos ni activa combate por sí solo. COS-32
debe reutilizarlo y añadir su integración/pruebas, sin otro motor geométrico para
PhaseOne. La evitación de Vesper pertenece a `RaiderEncounter`.

### Combate determinista integrado (COS-32)

`domain.ship.ShipCombatState` modela la energía y el ciclo de vida de cada nave,
sin LibGDX. Cada estado se configura con el umbral de impactos, el daño por tramo
y los tiempos de respawn/inmunidad: Astra usa 1 % cada 10 impactos, respawn 3 s e
inmunidad 2 s; Vesper usa 35 % cada 10 impactos y respawn 5 s. `receiveProjectileHit()`
acumula impactos residuales y aplica el tramo al alcanzar el umbral (el décimo
impacto daña; no agrupa ráfagas); `destroy()` es idempotente y arranca el reloj de
muerte; `advance(dt)` avanza el reloj de muerte o de inmunidad; `respawn()` reinicia
energía, contador residual y generación de vida. `canAct()` = vivo o inmune;
`canBeHit()` = solo activo. El controlador decide cuándo destruir (energía cero o
contacto de cascos) para atribuir una única vez el bonus de destrucción.

`PhaseOneGameController` posee `astraCombat()` y `raiderCombat()`. Cada frame dentro
del recorrido llama a `advanceCombat(delta)`: avanza los dos relojes, reapariciones y
contacto de cascos. `advanceWeapons(...)` resuelve ya los impactos de proyectil: ahora
acepta un `GunBurst.ProjectileStep(projectile, fromX, fromY, toX, toY)` (sobrecarga
nueva; la de 4 argumentos sigue para cadencia) que recibe el segmento de cada
proyectil —existentes, recién emitidos y el último antes del alcance—; el arma retira
consumidos/expirados al final del avance y los consumidos en emisión no se añaden.
`GunProjectile.consume()/isConsumed()`/`remainingDistance()` dan soporte a esa
retirada y predicción. Cada proyectil impacta como máximo una vez: un casco destruido
o ausente no es objetivo; un casco inmune consume el proyectil sin daño. La silueta
de cada nave es su círculo inscrito (radio = mitad de la dimensión menor de la caja),
adaptado en el controlador a `CollisionDetector.Circle`/`Segment`. El contacto de dos
naves vulnerables (ambas `canBeHit`) destruye a las dos; si Astra es inmune no
destruye a ninguna (CA15). `destroyRaider()` llama a `score.awardRaiderDestroyed()`
(+1000, una vez por vida) y el controlador expone el bonus pendiente con
`drainCombatPoints()`, que la pantalla publica como `PointsEarned`; no se emite
`LifeLost` por estas muertes. Astra destruida congela su posición (la cámara la sigue),
no vuela ni dispara y el reloj de 10:00 continúa; reaparece vía `placeAt` en la misma
posición/rumbo. `RaiderEncounter.destroyActive()` conserva la instancia y sus
Visuals; `respawnAt(world, targetCenterX, targetCenterY, targetRadius)` elige con
`UnitRandom` un punto válido dentro del mundo, a ≥ 500 del centro de Astra y ≥ 800
del punto de muerte, con fallback al mejor candidato y sin recrear texturas.

`RaiderEncounter` usa la cinemática continua de Vesper (COS-62): crucero 300 u/s,
pasada 500 u/s y reenganche 320 u/s como velocidades objetivo por fase, con
aceleración 300 u/s² y deceleración 450 u/s²; giro máximo 130°/s y aceleración
angular 360°/s². El estado físico vive en `VesperRaider` (`currentSpeed`,
`angularVelocityDegrees`): `steerTowards(target, ωmax, α, dt)` sigue un perfil de
frenado angular acotado y `advance(dt, targetSpeed, accel, decel)` rampa la
velocidad; el objetivo táctico lo fija `RaiderEncounter` (`targetSpeed()`): una
sola política de velocidad por fase (crucero en APPROACH, pasada en ATTACK_PASS,
reenganche en INVERT), sin ventana de turbo que compita o corte la pasada. Las
constantes son un punto de partida técnico medible, no una cifra fijada por el PO.
`leastTurnSide` convierte cada dirección candidata al marco de descenso con
`headingFor(vx, vy) = degrees(atan2(vx, -vy))` antes de comparar, corrigiendo la
mezcla de marcos anterior. La pose de guiñada usa histéresis sobre la velocidad
angular real (entra ≥5°/s, sale ≤1°/s) para evitar parpadeo neutral. Con CA18, la
sobrecarga
`advance(dt, world, targetX, targetY, targetVelX, targetVelY, targetRadius, selfRadius)`
prevé la aproximación más cercana dentro de un horizonte con movimiento relativo
(el controlador pasa el centro, velocidad y radio de Astra); si predice contacto
(radios + margen), gira perpendicular a la demora en un lado elegido por menor
cambio de rumbo y lo mantiene ≥ 1,2 s para evitar oscilación; el vector de bordes
interiores se mantiene. El horizonte y el margen son los generales (1,2 s y 70)
salvo dentro de la pasada comprometida, donde se usan unos cortos dedicados
(`PASS_AVOID_HORIZON_SECONDS=0,5 s`, `PASS_AVOID_CONTACT_MARGIN=40`) para que una
pasada no rompa a 600 unidades y sí presione hasta la ventana de tiro antes del
quiebre. Con COS-61/COS-63 el combate usa fases explícitas
(`Maneuver.APPROACH`, `ATTACK_PASS`, `INVERT`):

- **APPROACH** adquiere una línea de tiro balística: resuelve la raíz positiva
  acotada de `|r + v·t| = projectileSpeed·t` con la velocidad real del proyectil
  (`GunTuning.PROJECTILE_SPEED`), con fallback a un adelanto directo de 1 s; la
  línea se replanifica cada `APPROACH_REPLAN_SECONDS=0,2 s` y el rumbo se mantiene
  entre replanes (objetivo persistente, sin rehacer la línea por frame).
- **ATTACK_PASS** compromete un corredor fijo: al estar a ≤900 unidades y con
  error de rumbo ≤15° congela punto de entrada, dirección normalizada y longitud
  (`max(250, distancia de compromiso + 400)`); al cruzar el plano de salida
  (progreso longitudinal ≥ longitud) o el límite de 3 s termina la pasada. El
  corredor no se reconstruye por frame; solo la seguridad lo puede interrumpir y
  la pasada termina entonces por progreso/timeout para replanificar.
- **INVERT** abre espacio (0,5 s rectos siguiendo el corredor de salida a
  velocidad de reenganche) y ejecuta un arco de inversión sobre el lado elegido
  una sola vez en la entrada (menor giro hacia el objetivo, desempate a la derecha
  y guarda de espacio mundial por tangente hacia un borde próximo), manteniéndolo
  hasta recuperar alineación ≤25° con el objetivo o el plazo de 5 s; después vuelve
  a APPROACH y encara otra pasada.

Con COS-40 la trayectoria deja de depender de `UnitRandom` después de la
aparición: se retiró la deriva aleatoria y cada fase conserva una intención de
vuelo persistente; `UnitRandom` queda solo para la espera inicial y para las
posiciones de aparición/reaparición. La predicción de contacto usa la velocidad
efectiva actual (`VesperRaider.currentSpeed()`). La evitación de choque y la
respuesta de bordes siguen siendo la prioridad de seguridad sobre las tres fases.
El plan (fase, corredor, lado de inversión y acumulador) se reinicia en `spawn`,
`destroyActive` y `respawnAt`, y los accesos de paquete `maneuver()`,
`separationSide()`, `avoidanceSide()`, `passDirectionX/Y()`, `passEntryX/Y()`,
`passLength()`, `passProgress()`, `inversionSide()` e `invertTurnAccumulated()`
solo existen para las pruebas deterministas de dominio. La sobrecarga de 4
argumentos conserva el comportamiento de búsqueda pura para las pruebas
deterministas. `PhaseOneScreen`
llama a `advanceCombat` una vez por frame dentro del recorrido y suma `drainCombatPoints()`
con los puntos de vuelo antes de publicar `PointsEarned`; no dibuja a Astra cuando
`isAstraActive()` es falso y la coincidencia visible (`shipsCoincidentVisible`)
también exige a Astra activa. La explosión gráfica, el parpadeo y el HUD de energía
quedan descritos en la sección «Explosiones, parpadeo y energía integrados (COS-33)».

### Bajas de Vesper conectadas a la misión (COS-68)

`PhaseOneGameController.destroyRaider()` es el único punto de transición «vida → destruido» del
Raider, se invoca por proyectiles de Astra. El contacto entre cascos solo destruye a Astra y no
daña, cuenta ni puntúa a Vesper. En una baja válida, tras validar
ACTIVE→DESTROYED, guardar el centro de muerte, destruir el encuentro y limpiar el destello, la baja
pasa por `PhaseOneMission.recordVesperKill()`: cada destrucción real cuenta una sola vez hasta la
meta de 3, y el bonus de 1000 de `PhaseScore.awardRaiderDestroyed()` se concede **solo si la baja
fue aceptada**, de modo que una destrucción posterior a un desenlace terminal ni cuenta ni puntúa.
El contador no se infiere del bonus 1000, del frame de explosión ni de `raiderVisible`.

`handleRespawns()` conserva la única instancia de `RaiderEncounter` y el `ShipCombatState.vesper()`
para las vidas 2 y 3 (espera de 5 s, energía al 100 % y un punto válido del mundo), pero
`isRaiderRespawnClosed()` interrumpe esa reaparición en cuanto `vesperKills >= 3` o la misión quedó
terminal por cualquier causa (vidas agotadas o reloj expirado): tras la tercera baja Vesper no
reaparece, no se crea una cuarta vida ni un segundo enemigo, y no se cargan texturas nuevas. Los
proyectiles ya emitidos no re-impactan sobre restos del mismo frame: `activeRaider()` es null al
morir, `destroyRaider()` es idempotente y cada proyectil impacta como máximo una vez. El desenlace
por tiempo/vidas y su gating en pantalla pertenecen a las hijas COS-69/COS-70.

### Vidas reales de Astra (COS-69)

`CosmicAcesGame` concede 3 vidas iniciales a cada partida (el valor provisional anterior era 1). El
`GameCoordinator` sigue siendo la única fuente de vidas persistentes: `PhaseOneScreen` lee el recuento
autoritativo del snapshot al empezar cada frame y lo pasa a
`PhaseOneGameController.advanceCombat(delta, astraLivesRemaining)`, de modo que `handleRespawns()` solo
devuelve a Astra al mundo mientras queden vidas; con 0 no reaparece aunque el reloj de 10:00 siga
corriendo y el estado de combate no aplica más daño a la nave destruida.

`destroyAstra()` es el único punto de transición ACTIVE→DESTROYED (proyectil de Vesper, contacto de
cascos con el Raider o con un dron Ghostwing) y ya es idempotente: encola exactamente una pérdida por
transición, que la pantalla drena con `drainAstraLifeLosses()`. `PhaseOneScreen` publica un `LifeLost`
por entrada drenada, después de `advanceCombat` y de `advanceWeapons` y antes de leer el snapshot, de
modo que el coordinador y `PhaseCompleted` siempre ven la vida ya descontada y varios impactos sobre el
mismo cuerpo no restan vidas extra. Inmediatamente después la pantalla resuelve el desenlace con las
vidas autoritativas (`resolveMissionOutcome`); la prioridad (0 vidas ⇒ LOSE antes de la 3ª baja, luego
la meta, luego el reloj) vive en `PhaseOneMission.resolve`. El panel WIN/LOSE visible corresponde al
hijo de flujo de pantalla.

### Ciclo de misión en pantalla (COS-70)

`infrastructure.gdx.screen.PhaseOneScreenFlow` es la regla pura (sin LibGDX) del ciclo de la
pantalla: `Stage` = `INTRO` (panel de objetivo), `PLAYING` (misión activa) y `RESULT` (panel
congelado). También posee el latch de ESPACIO: una pulsación solo actúa después de que la tecla se
haya visto soltada, de modo que la pulsación que confirma el objetivo no dispara y la que dispara el
golpe final no confirma el panel. Sustituye al antiguo `boolean runFinished`. `ScreenFactory` ya no
llama a `controller.start()` al construir la pantalla; el inicio ocurre en `PhaseOneScreen` al
consumir la primera pulsación de ESPACIO, y ese mismo frame simula 0 segundos y no emite disparos.

El orden del frame es: `activeSeconds = min(delta, remainingRunSeconds())` una vez; vuelo,
encuentro, drones y combate con esa porción; publicación de `LifeLost`/`PointsEarned`; una sola
llamada a `advanceWeapons(activeSeconds, activeSeconds, flow.fireTrigger(...), ...)` en la que
proyectiles existentes y emisiones comparten el corte del reloj (así un impacto posterior a 00:00 no
se resuelve y no puede otorgar victoria tardía); nuevas publicaciones de `LifeLost`/`PointsEarned`;
y por último `resolveMissionOutcome` con las vidas autoritativas. Si el desenlace es terminal,
`completeRun()` publica una única `PhaseCompleted` con el snapshot ya actualizado y congela el
estado. `advanceHitFeedback` y las animaciones de explosión siguen avanzando por presentación. La
prioridad del resultado (0 vidas ⇒ LOSE antes de la 3ª baja, meta ⇒ WIN, reloj expirado ⇒ LOSE) vive
en `PhaseOneMission`.

`PhaseOneScreen` dibuja ambos paneles bajo la proyección HUD existente con `GlyphLayout` medido
contra `hudViewport` (soporta resize) y fondo translúcido, sin ocultar del todo los indicadores: el
de objetivo resume la meta, las vidas y el límite; el de resultado lee `controller.missionOutcome()`
y muestra `WIN`/`LOSE`. ESPACIO (pulsación nueva) publica `GameAbandoned` y vuelve a Welcome; ESC
abandona desde cualquier estado. Al volver a Welcome, `CosmicAcesGame` libera PhaseOne y una partida
nueva recibe un controlador y estado nuevos (3 vidas, 0/3 bajas, 10:00).

### HUD de misión en pantalla (COS-71)

`infrastructure.gdx.screen.MissionHud` es el HUD fijo de misión de PhaseOne, dibujado bajo la misma
proyección HUD de `hudViewport`: icono pequeño de Vesper con bajas `0/3 … 3/3`, icono pequeño de
Astra con vidas `3 … 0` y reloj `10:00 … 00:00`. No posee recursos nativos: los iconos son las
regiones neutras sin fuego prestadas de `Astra.Visuals` y `VesperRaider.Visuals` (propiedad de la
pantalla, que sigue creando y liberando las texturas); el icono de Vesper se rota 180° para que la
región orientada a descenso apunte arriba sin mutar la región compartida. Nada aquí carga ni libera
texturas y tampoco se duplican contadores: el `record MissionHud.Readout` captura una única lectura
por frame (`vesperKills`/`vesperKillGoal` del controlador, `lives` del snapshot del coordinador,
`remainingRunSeconds` del reloj de dominio) y la pantalla la construye una vez al final del frame con
el mismo `PhaseResult` que alimenta `drawScore`. `PhaseOneScreen` no vuelve a sumar ni restar vidas,
bajas o tiempo.

El reloj formatea segundos pendientes con `ceil` a `MM:SS` de dos dígitos (`10:00`, `09:59`, `01:00`,
`00:00`; sin `09:60`, sin negativos; valores no finitos o ≤ 0 → `00:00`) usando la misma fuente de
tiempo del dominio. Las lecturas no se avanzan fuera de `PLAYING` (el `PhaseOneScreenFlow` corta la
simulación), por lo que INTRO muestra 0/3 + 3 vidas + 10:00 y RESULT muestra los valores congelados
del desenlace. El layout es una función pura del ancho/altura del `hudViewport` y de los anchos
medidos con `GlyphLayout`: contadores en la banda superior izquierda, reloj centrado en la línea
superior (con separación mínima de los contadores y margen derecho) y score existente arriba a la
derecha; la banda inferior y la zona del minimapa no se tocan, incluido al redimensionar (800×600,
1024×768, 1920×1080). El dibujo respeta `begin/end` del `SpriteBatch` y restaura color de fuente y
batch. `MissionHudTest` cubre formato del reloj, clamps del `Readout`, layout puro y la lectura
inicial autoritativa; la legibilidad visual es del PO.

### HUD de enemigos visibles en PhaseOne (COS-72)

`infrastructure.gdx.screen.EnemyStatusHud` dibuja nombre y energía de los enemigos bajo la misma
proyección HUD de `hudViewport`, con `Readout(title, energyPercent)` y porcentaje entero con clamp
0–100. Vesper conserva su regla actual: Astra y el Raider vivo deben coincidir en la vista rotada
real de cámara durante PLAYING. Bastion se muestra durante PLAYING cuando su nave activa intersecta
esa cámara, incluso si Astra no coincide con él. Si ambos son visibles, los dos bloques ocupan
slots verticales separados; el bloque de Bastion dice `BASTION B-52`. La pantalla calcula la
visibilidad para el fotograma dibujado y lee la energía después de resolver impactos, de modo que el
HUD coincide con el estado del avión. No posee recursos nativos y es independiente del porcentaje
de Astra (`FlightHud`). El layout puro mide cada nombre con `GlyphLayout`, queda bajo los contadores,
reloj y score, y encima de la banda inferior y el minimapa; `EnemyStatusHudTest` cubre las reglas de
visibilidad, títulos, energía y slots.

### Tramo activo autorizado del frame (COS-73)

`PhaseOneGameController.beginActiveFrame(activeSeconds)` / `endActiveFrame()` abren y cierran el
frame de simulación. `PhaseOneScreen.render()` calcula el tramo activo una sola vez
(`activeRunSeconds(delta)`, acotado por el tiempo restante), lo abre al empezar a simular y lo cierra
tras resolver el desenlace; mientras el frame está abierto, `advanceFlight`, `advanceEncounter`,
`advanceGhostwing` y `advanceCombat` (y `resolveGhostwingContact`) consumen exactamente ese tramo
mediante `boundedActiveSeconds`, sin volver a derivarlo de `PlayableRun.remainingRunSeconds()` ni
salir por `isRunFinished()`. Así, un delta que alcanza 00:00 durante el vuelo no recorta el
movimiento, el encuentro, los drones, los temporizadores, las colisiones ni las armas del mismo
frame: todos simulan el tramo autorizado una sola vez y después se resuelve el desenlace
(`PhaseOneMission` conserva la prioridad 0 vidas → LOSE, 3 bajas → WIN, reloj 0 → LOSE). Fuera de un
frame (llamadas directas, p. ej. pruebas deterministas) no hay tramo autorizado: cada método acota el
delta con el reloj y un recorrido ya terminado no avanza nada, preservando el contrato previo. En los
frames siguientes el tramo es cero y ningún subsistema avanza naves, combate, disparos ni puntos.
`PhaseOneActiveSliceTest` cubre el cruce del límite (mismo tramo para todos, tope del tramo,
congelación con tramo cero) y el tercer derribo dentro del tramo hacia WIN o LOSE.

### Catálogo PNG anterior de PhaseOne (COS-82; conservado, no usado por la pantalla)

`infrastructure.gdx.screen.PhaseOneTerrainCatalog` conserva el catálogo puro de los 128 tiles del mapa
(8 columnas × 16 filas), sin `Texture`, sin E/S y sin estado mutable; se construye y consulta sin
arrancar LibGDX. Fija la correspondencia de COS-81: `row-01-col-01` en el suroeste (inicio del
recorrido, y=0) y `row-16-col-08` en el noreste (final). Cada `Tile` describe su ruta
(`assets/images/map-phase-one/row-%02d-col-%02d.png`), el rectángulo destino en unidades de mundo
(1024 × 768 por celda) y el sub-rectángulo fuente del PNG que le corresponde 1 píxel a 1 unidad de
mundo, de modo que dibujar la fuente en el destino nunca estira la imagen. La última fila rebasa el
mundo (y=12288) en 288 unidades: su rectángulo visible y su recorte miden 480 (desde `sourceY=288`)
para cubrir exactamente `[0,8192]×[0,12000]`. `tiles()`, `tile(row,col)` y `pathFor(...)` validan
los índices 1..16/1..8; `tileAt(x,y)` resuelve un punto con fronteras interiores semiabiertas y
devuelve vacío fuera del mundo (sin solicitar recursos); `tilesIn(minX,minY,maxX,maxY)` devuelve en
orden fila-mayor los tiles que intersecan el rectángulo recortado al mundo, contando los bordes
compartidos para no perder vecinos en la precarga. La orientación es explícita:
`requiresVerticalFlip()` devuelve `false` porque el borde superior del PNG es el norte y LibGDX ya
dibuja el `TextureRegion` por defecto con su fila superior arriba (mayor Y del mundo). El catálogo no
consulta la nave ni altera el mundo; la pantalla y su caché histórica lo consumían. La pantalla actual
usa el atlas procedural descrito arriba. La comprobación visual histórica de orientación y continuidad
corresponde al PO humano.

### Planificador PNG anterior (COS-83; API conservada para compatibilidad)

`infrastructure.gdx.screen.PhaseOneTerrainPlanner` conserva la política pura de selección del mapa: sin
`Texture`, sin LibGDX y sin estado mutable, se prueba sin arrancar la ventana y consume solo el
catálogo de COS-82. Su `Camera(worldX, worldY, upX, upY, zoom, viewWidth, viewHeight)` replica lo
que `FlightCamera` publica a LibGDX y `viewWidth/viewHeight` es el tamaño lógico real del
`ScreenViewport` (ventana grande muestra más mundo). `visibleCorners` reconstruye el rectángulo
rotado de la vista con `right=(upY,-upX)`, `up=(upX,upY)` y semiejes `viewWidth/2*zoom` y
`viewHeight/2*zoom`, igual que `CameraVisibility`; `Bounds` calcula su AABB conservador y lo recorta
a `[0,8192]×[0,12000]`, de modo que ninguna esquina visible queda fuera del conjunto y nunca se
piden índices fuera de 1..16/1..8.

La pantalla actual solo usa `visibleBounds(camera)` para obtener la caja AABB del escenario procedural.
`plan(camera)` y `plan(camera, travelX, travelY)` (por defecto la dirección de avance de la cámara)
devuelven un `Plan` con tres zonas concéntricas que son la histéresis de la caché futura:
`visible` (tiles que intersecan el AABB recortado, ordenados del más cercano a la cámara al más
lejano), `prefetch` (un anillo de tiles alrededor de lo visible, ordenado delantero > lateral/trasero
según la dirección de desplazamiento reciente) y `retained` (dos anillos alrededor de lo visible;
`retains(tile)` es la condición de conservación, de modo que una oscilación sobre una frontera no
recarga la textura). Visibles ⊂ precarga ⊂ retención; lo visible nunca se filtra por presupuesto
fijo. La ruta de planificación completa se conserva para sus pruebas; la pantalla actual solo pide
la caja con `visibleBounds` después de actualizar `FlightCamera`. `PhaseOneTerrainPlannerTest` cubre yaw 0/45/90/135/180/270°,
zoom 1/1,15/1,25, viewport 1024×768 y 1920×1080, fronteras del mundo, avance, inversión de rumbo y
vuelta a tiles previos, y exige que toda esquina visible quede cubierta.

### Caché PNG anterior (COS-84; conservada, no usada por la pantalla)

`infrastructure.gdx.screen.PhaseOneTerrainCache<T>` es el dueño único de los recursos del mapa: no
dibuja ni conoce `Texture`, es genérico sobre el recurso y recibe un `ResourceFactory<T>`
(crear/liberar/estimar bytes), de modo que toda la política se prueba con un cargador falso sin
contexto LibGDX. Consume el `Plan` de COS-83 tal cual: primero crea **todos** los `visible` (sin
presupuesto, para que un salto de cámara o un resize nunca muestren hueco), después recorre
`prefetch` —ya ordenado delantero primero— mientras el tiempo transcurrido desde el inicio de la
llamada no supere el presupuesto `budgetNanos` del frame, y expone los tiles que quedan pendientes.
`primeVisible(plan)` es la carga bloqueante previa al primer dibujo: sirve solo lo visible y deja el
anillo como pendiente, sin decodificar la matriz entera al entrar. Cada `Tile` tiene como máximo un
recurso residente (`Map<Tile,T>`), así que nunca hay dos copias GL del mismo PNG.

La memoria se acota por la zona de **retención** del plan, no por un número fijo: tras cargar, solo
se liberan los residentes para los que `plan.retains(tile)` es falso (visible + dos anillos). Como
lo visible siempre está dentro de su propia retención, una textura dibujable no se expulsa y una
oscilación sobre una junta no recarga (histéresis). El límite se adapta al viewport: una ventana
grande mueve más tiles a `visible` y amplía la retención. Contadores internos
(`residentCount`, `pendingCount`, `totalLoaded`, `totalEvictions`, `estimatedBytes`) y el
`FrameResult` de cada llamada sirven a pruebas y diagnóstico, sin HUD de depuración.
`dispose()` libera todos los residentes y limpia los pendientes de forma idempotente; entrar y
salir repetidamente no deja recursos ni solicitudes. `PhaseOneTerrainTextureFactory` es el adaptador
GL concreto (`Texture` desde `path`, `dispose`, bytes RGBA8888), propiedad de `PhaseOneScreen` a
través de la caché.
Medición del banco con cargador falso sobre el vuelo completo: 1024×768 retiene como máximo 24
texturas (~72 MiB) y 1920×1080 un máximo de 30 (~90 MiB), muy por debajo de las 128 (~384 MiB).
`PhaseOneTerrainCacheTest` cubre carga inicial, salto/resize/giro/bordes/regreso sin hueco,
presupuesto y pendientes, prioridad delantera, oscilación sin recarga, expulsión fuera de retención,
bytes y cierre. La QA visual de orientación, continuidad y capas queda para el PO.

### Explosiones, parpadeo y energía integrados (COS-33)

`domain.effect.ShipExplosionSheet` describe el PNG original de 1774×887 con ocho
recortes medidos (alfa > 8, margen de dos píxeles), ordenados de izquierda a derecha
en la primera fila y luego en la segunda. `internalPath()`, `frameSlices()` y
`frameSlice(index)` no necesitan contexto gráfico. Cada `Slice` expone anclas
geométricas centradas; `REFERENCE_SIZE=417` permite usar una sola escala en todos
los frames y conservar expansión/disipación sin estirar cada recorte.

`ShipExplosionVisuals` carga una textura compartida y prepara sus ocho regiones
una vez, con filtrado Nearest. `region(index)` entrega una región prestada que no
se debe modificar. `draw(batch, index, centerX, centerY, peakSize)` dibuja sobre
un batch mundial ya abierto; peakSize es la dimensión máxima de la animación en
unidades del mundo. No cambia proyección/color ni abre/cierra lotes. La pantalla
crea una instancia para las explosiones de ambas naves y la libera en `dispose()`;
el cargador limpia fallos parciales y su liberación es idempotente.

Esta preparación ya está conectada a PhaseOne por COS-33. `domain.effect.ShipExplosion`
es una animación no cíclica: congela el centro mundial de cada destrucción y expone
`advance(dt)`, `frameIndex()` (ocho frames a 0,1 s, 0,8 s en total) e `isFinished()`;
no conoce timers de respawn/daño ni posee recursos. `PhaseOneGameController` expone el
centro de muerte de ambas naves con `astraDeathCenterX()/Y()` (derivados de la posición
congelada de Astra) y `raiderDeathCenterX()/Y()` (capturados en `destroyRaider()` antes
de `destroyActive()`), de solo lectura. `PhaseOneScreen` posee una única
`ShipExplosionVisuals` compartida por ambas naves y dos animaciones independientes:
detecta la transición de `state.destroyed` una sola vez por vida (bandera propia que se
resetea al reaparecer) para iniciar la explosión exactamente en el centro congelado,
avanza las animaciones cada frame, las elimina al terminar y oculta cada nave hasta su
respawn aunque la animación ya haya acabado; la explosión se dibuja en proyección de
mundo, por lo que girar/mover la cámara no la arrastra. Un choque simultáneo produce
dos animaciones independientes. El pico de cada explosión es `1,8 ×` la dimensión
mayor de la caja de la nave (ajuste visual, revisable en la QA del PO); la escala
común `peakSize/417` se aplica a los ocho frames sin estirarlos.

El parpadeo de Astra es presentación pura: mientras `invulnerabilityRemaining() > 0`
alterna cuerpo/fogonazo cada 0,1 s (regla `PhaseOneScreen.astraVisible(remaining)`,
sin pruebas de color/alpha residual en el batch); la inmunidad que decide el dominio
(`ShipCombatState`) no depende de la visibilidad del sprite y Astra puede pilotar y
disparar mientras parpadea. `FlightHud.draw(...)` recibe ahora además la energía
0–100 de `astraCombat().energyPercent()` y `energyLabel(int)` muestra «ENERGIA NN%»
(clamp a 0–100) en la fila inferior izquierda, siempre visible durante la partida,
incluidos el 0 % de la espera y el 100 % del respawn, sin invadir rumbo, velocidad,
arma, score ni minimapa. El cuerpo del Vesper muerto no se dibuja ni cuenta como
coincidente ni en el minimapa (`isRaiderActive()` ya es falso mientras está destruido);
el score mostrado sigue siendo el snapshot del coordinador, incluido el bonus 1000 de
COS-32, y ni la animación ni el fin de explosión publican puntos.

### Destello de impacto integrado (COS-34)

`domain.effect.ShipHitFlash` es un pulso temporal puro, sin LibGDX y reutilizable por cualquier
nave/fase: `trigger()` renueva a 0,10 s de intensidad 1, `advance(dt)` decae linealmente hacia 0
(ignora deltas no finitos/no positivos y clampa al terminar) y `intensity()` devuelve 0–1. Cada
impacto de proyectil válido renueva el pulso sin acumular duración; `clear()` lo corta al instante
cuando la muerte toma prioridad. No contiene energía, umbral de daño, inmunidad, input, cámara ni
shader: el controlador decide qué impactos cuentan y si el pulso se limpia.

`PhaseOneGameController` posee `astraHitFlash` y `raiderHitFlash` independientes. Cada frame la
pantalla llama a `advanceHitFeedback(delta)` una vez, **antes** de resolver impactos y también tras
terminar la fase (un proyectil en vuelo puede aún impactar y el último pulso debe extinguirse);
avanzar antes de los `trigger()` nuevos permite dibujar la primera intensidad completa del impacto
aceptado en el mismo frame. Los triggers se activan en `astraProjectileStep` (impacto sobre Vesper →
`raiderHitFlash.trigger()`) y en `raiderProjectileStep` (impacto sobre Astra → `astraHitFlash
.trigger()`), **sin** depender del boolean de `receiveProjectileHit()` que solo señala el tramo de
daño cada 10 impactos. Las ramas de inmunidad consumen el proyectil sin activar el destello (CA22);
`destroyAstra`/`destroyRaider` y el respawn limpian el pulso de cada nave. `astraHitFlashIntensity()`
y `raiderHitFlashIntensity()` exponen 0–1 para la presentación.

`infrastructure.gdx.screen.ShipHitFlashRenderer` es el adaptador gráfico compartido, propiedad de la
pantalla (creado en su estructura de carga con limpieza parcial y liberado una vez en `dispose()`).
Contiene un único `ShaderProgram` para ambas naves cuyo vertex shader replica el default de
`SpriteBatch` (`u_projTrans`, `a_color`, `a_texCoord0` y la corrección `v_color.a * 255/254`) y cuyo
fragmento conserva alfa: `rgb = mix(sample.rgb, vec3(1.0), clamp(u_hitFlash, 0, 1))` con
`gl_FragColor = vec4(rgb, sample.a) * v_color`. `isCompiled()` se comprueba y un fallo lanza error
explícito con `dispose()`. `draw(batch, ship, rotationDegrees, intensity)` dibuja la nave en su pose
actual con `Ship.draw` (misma escala/ancla/alpha) y hace `flush()` del lote antes de cambiar el
uniforme compartido y después de dibujar, restaurando el shader previo al volver; intensidad ≤ 0
mantiene exactamente el dibujo histórico sin tocar el shader. La pantalla reemplaza únicamente el
dibujo de Astra y Vesper por este wrapper con la intensidad del controlador; explosiones,
proyectiles, minimapa, HUD y fondo conservan su shader/color habituales. No hay sprites nuevos ni
cambio de daño, cadencia, puntuación o controles.

`infrastructure.gdx.screen.AstraLightEffect` es un POC de iluminación de motor propiedad de
`PhaseOneScreen`: genera una textura radial pequeña en memoria, aplica filtrado lineal y la libera
en `dispose()`. Antes de dibujar el sprite coloca un halo cian y una estela azul detrás de Astra con
blending aditivo; la intensidad y longitud suben con la velocidad y alcanzan un pulso azul intenso
durante Ultra. La pasada respeta el yaw mundial y restaura color/blending del `SpriteBatch` antes de
continuar con la nave, efectos y explosiones. En guiñadas sostenidas, acumula el ángulo realmente
girado y dibuja halos suaves en ambas alas con radio proporcional, que se desvanecen al soltar el
giro, morir Astra o terminar la fase. No modifica el sprite, su tamaño, movimiento ni reglas.

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
distancia. COS-32 añadió `consume()/isConsumed()` (retirada por impacto, un único
impacto por proyectil) y `remainingDistance()` para predecir el segmento exacto
del próximo frame. `domain.weapon.GunTuning` reúne la cadencia (Astra: 0,08 s;
Vesper: 0,4 s), la velocidad
(1800 u/s) y la duración del fogonazo (0,035 s); el alcance lo congela el
adaptador en cada disparo. `domain.weapon.GunBurst` posee el reloj de cadencia,
el pulso de fogonazo y la colección de proyectiles, sin depender de LibGDX ni de
infraestructura. `advance(frameDelta, emissionSeconds, enabled, ShotSource)`
avanza una vez por frame los proyectiles existentes y luego emite dentro de la
ventana `emissionSeconds` (recortada a `[0, frameDelta]`); los proyectiles
nuevos avanzan solo el tiempo posterior a su emisión. La emisión es inmediata al
activar y después respeta el intervalo propio del arma, recibido en
`GunBurst(float burstIntervalSeconds)`; el constructor sin argumentos conserva
los 0,08 s de Astra. Deshabilitar descarta la deuda de cadencia, detiene el
pulso y no borra los proyectiles existentes, y `emissionSeconds=0` corta los
disparos nuevos al terminar la fase sin alterar la trayectoria de los ya
emitidos. `ShotSource.shotAt(offsetSeconds)` entrega una instantánea `Shot` con
sus orígenes `Muzzle`, la dirección y el alcance; cada origen emite un proyectil,
de modo que los dos cañones de Astra producen dos trayectorias. La cadencia se
mide por tiempo acumulado, por lo que no depende de los FPS, y no hay cupo ni
munición: la caducidad por distancia acota la colección. `GunBurst` expone
`projectiles()` como vista de solo lectura, `isFiring()`, `flashVisible()` y
`clear()`; no decide daños ni toca vidas/puntos, pero su sobrecarga nueva de
`advance` acepta un `ProjectileStep` que observa el segmento de cada proyectil
(existente, recién emitido o antes de expirar) y retira consumidos/expirados al
final del mismo avance, de modo que el controlador resuelve ahí los impactos
(véase «Combate determinista integrado (COS-32)»).

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
M61 Vulcan pertenecen a la hija final. La entrega visible de COS-27 quedó
integrada así: `PhaseOneScreen` posee un `GunBurstVisual` compartido (sin
recursos nativos) y, tras cerrar el `SpriteBatch` de naves, mantiene la
proyección de mundo y abre una sola sesión `ShapeRenderer.Filled` para dibujar
los proyectiles de ambas armas con `draw(shapes, x, y, forwardX, forwardY)`
usando siempre la posición y el vector congelados de cada `GunProjectile`;
después cambia a `hudCamera` para minimap/HUD. `Ship.draw` rota sobre el centro
de la caja estable mediante el hook protegido `spritePlacement(TextureRegion)`
que devuelve `Ship.SpritePlacement(scale, offsetX, offsetY)`: el default centra
la región idéntico al dibujo histórico y un desplazamiento solo mueve la imagen,
nunca el pivote. `HeroShipSheet.firingPlacement(pose, accelerating)` y
`VesperRaiderSheet.firingPlacement(pose)` guardan, medido sobre los PNG reales
con la regla de color cálido para Astra y el centro de la lente azul de cabina
para Vesper v4, el desplazamiento que deja el
cuerpo de la variante de disparo anclado sobre el de vuelo de la misma familia y
pose (escala uniforme; no se estira la pose ni cambia la caja). El pulso del
fogonazo lo da el propio `GunBurst.flashVisible()` (0,035 s del mismo reloj de
emisión): la pantalla lo propaga con `Astra.setMuzzleFlashVisible` y
`VesperRaider.setMuzzleFlashVisible` antes del dibujo, y `currentRegion` elige
`region(pose, accelerating, flash)` / `region(pose, flash)`. `FlightHud.draw`
recibe `isFiring` y `weaponLabel(boolean)` devuelve «M61 VULCAN» o vacío,
dibujado centrado en una fila inferior libre (y=58) sin mover cámara ni
reorganizar las lecturas existentes.

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
(dos por pose, tomadas de las bases de los fogonazos de la nariz en la lámina
v4 de disparo, incluidas las dos bocas visibles en las poses de guiñada). Cada
`Shot` congela centro, bocas, rumbo y alcance, sin retener referencia a la nave.
La coincidencia de las armas se calcula **después** del vuelo, el encuentro y la
cámara del frame, con el helper puro `CameraVisibility.shipVisible(centerX,
centerY, halfBox, cameraX, cameraY, upX, upY, viewWidth, viewHeight, zoom)`; la
puntuación sigue usando la coincidencia muestreada antes del movimiento, de modo
que su regla y valores no cambian y nunca se reutiliza una coincidencia obsoleta
para disparar. El dibujo del HUD/arma visible sigue perteneciendo a la hija final.

Con COS-64 el cañón del raider deja de congelar todas las emisiones en el estado
final del frame. `RaiderEncounter.advance(...)` graba una `VesperRaider.ShotSample`
(centro, rumbo y pose) por subpaso acotado (≤1/120 s), con tiempo local al frame, y
`shotAt(offsetSeconds, range)` interpola el centro linealmente y el rumbo por arco
corto entre las muestras que rodean ese instante, tomando la pose de la muestra más
cercana; `shotSampleAt(...)` expone la muestra resultante al controlador y a las
pruebas deterministas. Así un giro o una pasada rápida produce orígenes y `forward`
distintos por evento sin añadir filtros por fase, cono de tiro, cadencia ni daño.
`VesperRaider.captureShotSample()` y `shotAt(ShotSample, range)` construyen la
instantánea desde la muestra histórica (la firma de 4 argumentos se conserva y usa la
pose viva); `ShotSample` no retiene texturas ni referencias a Visuals. Un contador de
vida (`lifeGeneration`, incrementado en `spawn`, `respawnAt` y `destroyActive`)
invalida la grabación: tras una muerte o reaparición `shotAt` devuelve una única
instantánea en la posición viva, de modo que nunca interpola a través del
teletransporte, y sin raider devuelve `null`. `PhaseOneGameController.raiderShotSource(
shotRange)` delega en el encuentro y conserva la autorización existente
(`emissionSeconds`, coincidencia visible y `raiderCombat.canAct()`); `GunBurst` sigue
siendo el único reloj de cadencia/pulso y `emissionCount` la única señal de audio.

### Validación conjunta del dogfight (COS-65)

Cierre de la raíz COS-61: no añade clases de producción ni cambia el tuning; consolida la
verificación del comportamiento conjunto y publica tolerancias y guion de QA. El detalle (baseline,
escenarios, tolerancias, parámetros finales y guion humano) vive en
[docs/vesper-dogfight-validation.md](vesper-dogfight-validation.md).

`VesperDogfightValidationTest` (domain.enemy) añade fixtures deterministas sin texturas: objetivo
quieto y **en crucero** (Astra a 150 u/s) con dos pasadas geométricas que sobrepasan el plano del
objetivo y dos ventanas de tiro, comparación de `PASS_SPEED` frente al baseline histórico
documentado (180 u/s crucero, 270 u/s turbo, ya inexistente en este checkout), separación de casco
con radios reales y un delta **irregular con pico de 0,1 s** frente a la rejilla 1/120 s con
tolerancias publicadas (posición 1,0 u, rumbo 0,5°). `VesperDogfightIntegrationTest`
(application) recorre el orden real de frame del controlador y exige que Vesper conserve una única
instancia tras muerte/reaparición (`assertSame`), que su cañón no emita estando destruido ni sin
coincidencia visible, y que los disparos de la vida nueva nazcan en la posición nueva. Las pruebas de
las hijas COS-62/63/64 siguen cubriendo cinemática, pasadas/inversión, bordes y muestreo temporal.
`PASS_SPEED`/`CRUISE_SPEED`/`REENGAGE_SPEED`, las rampas y el giro quedan como parámetros técnicos
finales, ajustables por el PO. La QA visual sigue siendo del PO humano.

Las láminas de vuelo mantienen sus cinco recortes: las tres poses de
alabeo existentes y dos poses de guiñada (derecha, izquierda). Astra selecciona
alabeo con laterales solos y guiñada con diagonales; la pantalla aplica la
rotación de rumbo en coordenadas de mundo. `RaiderEncounter` conserva una única
instancia de Vesper Raider por fase: espera una vez, aparece por delante de la
ruta inicial dentro de `WorldBounds` y persiste en coordenadas de mundo aunque
salga de cámara. Con COS-62 la velocidad es continua (crucero 300 u/s, pasada
500 u/s, reenganche 320 u/s) y rampa con aceleración/deceleración acotadas;
`destroyActive()/respawnAt(...)` conservan esa misma instancia y sus Visuals
tras la destrucción y reinician plan, velocidad, ω y pose; con COS-61/COS-63
alterna fases de adquisición de línea, pasada comprometida y arco de inversión
(véase «Combate determinista integrado (COS-32)»), y con COS-40 esas
fases usan rumbos deterministas sin deriva aleatoria ni random posterior al
spawn; el turbo táctico anterior se retiró en COS-63 a favor de una única
velocidad objetivo por fase.
`VesperRaider.steerTowards` gira el rumbo con velocidad angular y aceleración
angular acotadas (`MAX_TURN_RATE_DEGREES=130`, `MAX_ANGULAR_ACCELERATION_DEGREES=360`)
y `setDriftDirection`/`pose()` eligen la pose de guiñada al
girar o la de alabeo al derivar; el empuje acotado combina la aproximación al
jugador con una respuesta suave en los
bordes. La posición se recorta al mundo solo como salvaguarda. `PhaseOneScreen`
dibuja al raider cuando su círculo envolvente intersecta la vista real de la
cámara, sin destruirlo al salir de cámara, y no pasa ninguna `PlayArea` al
controlador. `Starfield.draw(shapes)` abre y cierra el dibujo con el ShapeRenderer recibido, que
pertenece a la pantalla de bienvenida. El coordinador cambia de pantalla después del render del
frame y libera la anterior.

Con COS-39 `PhaseOneScreen` dibuja además un indicador de enemigo fuera de
pantalla (`OffscreenEnemyIndicator`) en la pasada HUD, después de fijar
`hudCamera`, entre sesiones de `SpriteBatch`/`ShapeRenderer` y sin anidar
`begin`. La colocación es geometría pura: reutiliza
`CameraVisibility.localFrame(...)` (el mismo marco de cámara con rotación y zoom
que `shipVisible`) para orientar una flecha ámbar hacia el Vesper activo, se
oculta con `CameraVisibility.shipVisible(...)` —una única regla de visibilidad,
sin un segundo test discrepante— cuando el enemigo entra en la vista, y no
escala con el zoom mundial. Solo se muestra si la fase sigue y
`controller.isRaiderActive()`: muerto, esperando reaparecer, sin aparecer aún o
con la fase terminada no hay indicador. El borde inferior del rectángulo
interior se ancla en `MinimapProjection.BOTTOM_MARGIN` para no tapar las
lecturas del HUD; mientras el minimapa está visible se le pasa su panel real
como reserva y la flecha se desliza por el mismo borde conservando la dirección.
No crea recursos nativos ni modifica el minimapa, las armas, el daño, la
puntuación ni las cadencias.

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
|   +-- PhaseOneGameController extends GameController: encuentros, armas, combate y misión de PhaseOne.
|       0. beginActiveFrame() / endActiveFrame(): tramo activo autorizado del frame.
|       1. placeAstra()  2. placeAstraAtOperationsBase()  3. advanceFlight()  4. advanceEncounter()
|       5. advanceGhostwing()
|       5. advanceCombat()  6. advanceWeapons()  7. drainCombatPoints()
|       8. drainGhostwingDeaths()  9. isRaiderActive()  10. activeRaider()
|       11. ghostwingEncounter()  12. setRaiderVisuals()  13. astraCombat()
|       14. raiderCombat()  15. isAstraActive()  16. scorePoints()  17. astraGun()
|       18. raiderGun()  19. advanceHitFeedback()
|       20. astraHitFlashIntensity()  21. raiderHitFlashIntensity()
|       22. vesperKills()  23. vesperKillGoal()  24. missionTimeLimitSeconds()
|       25. missionOutcome()  26. resolveMissionOutcome()
|       27. phaseOneScenery(): paisaje procedural determinista compartido durante la ejecución.
|   +-- PhaseOneScenery: mapa, decoración y reserva de base preparados una vez por ejecución.
|       1. config()  2. terrainMap()  3. sceneryField()  4. operationsBasePlacement()
|       5. operationsBaseSpawnCenterX()  6. operationsBaseSpawnCenterY()
|
+-- domain
|   +-- game
|   |   +-- GameState: identidad, fase, puntos y vidas de la partida.
|   |   |   1. changePhase()  2. addPoints()  3. loseLife()
|   |   |   4. completePhase()  5. snapshot()
|   |   +-- GamePhase [enum]: WELCOME, PLAYING_PHASE_ONE, GAME_OVER.
|   |   +-- PlayableRun: reloj del recorrido, límite de 10 minutos (600 s).
|   |   |   1. start()  2. advance()  3. isFinished()  4. remainingSeconds()
|   |   +-- PhaseOneMission: objetivo de la misión (3 bajas en 600 s) y desenlace único.
|   |   |   1. recordVesperKill()  2. resolve()  3. vesperKills()  4. outcome()
|   |   +-- PhaseOutcome [enum]: IN_PROGRESS | WIN | LOSE.
|   |   +-- PhaseScore: puntos por segundos completos, coincidencia visible y bonus.
|   |   |   1. advance()  2. totalPoints()  3. awardRaiderDestroyed()
|   |   |   4. awardGhostwingDestroyed()
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
|   |   +-- GhostwingDeath(droneId, centerX, centerY, cause): destrucción de un dron,
|   |       una sola vez y drenable para la presentación; Cause = SHOT | COLLISION.
|   +-- ship
|   |   +-- Ship [abstracta]: posicion, velocidad, dimensiones y dibujo.
|   |   |   1. advance() [protegido]  2. setPosition() [protegido]
|   |   |   3. draw()  4. draw(batch, rotationDegrees)
|   |   |   5. spritePlacement() [protegido]: colocación de la región en la caja.
|   |   |   6. currentRegion() [abstracto protegido]
|   |   |   +-- SpritePlacement(scale, offsetX, offsetY): registro de la pose.
|   |   +-- ShipCombatState: energía, impactos, vidas e inmunidad de una nave.
|   |   |   1. receiveProjectileHit()  2. destroy()  3. advance()
|   |   |   4. readyToRespawn()  5. respawn()  6. energyPercent()
|   |   |   7. canAct()  8. canBeHit()  9. secondsSinceDeath()
|   |   |   10. invulnerabilityRemaining()  11. lifeGeneration()
|   |   +-- MovementIntent: direccion deseada para cualquier nave.
|   |       1. none()  2. fromDirections()  3. fromDownwardHeading()
|   +-- player
|   |   +-- Astra extends Ship: protagonista y estado de vuelo por rumbo.
|   |   |   1. placeAt()  2. respawnAt()  3. fly()  4. yawDegrees()  5. flightSpeed()  6. speeds()
|   |   |   7. ultraRemainingSeconds()  8. setVisuals()  9. setMuzzleFlashVisible()
|   |   |   10. currentRegion()  11. shotAt(): instantánea de los dos cañones para una emisión.
|   |   |   +-- Visuals: carga texturas; dispose() las libera.
|   |   +-- FlightControls: teclas de pilotaje y activación puntual de Ultra.
|   |   |   1. neutral()  2. lateral()  3. turning()
|   |   +-- FlightTuning: parámetros de aceleración, frenado, giro y desplazamiento lateral.
|   |   +-- FlightSpeeds: velocidades objetivo configurables independientes, límite 50–1000 u/s.
|   |   +-- HeroShipSheet: laminas v3 y cinco recortes de sprites de Astra.
|   |   |   1. sheets()  2. firingSheets()  3. cannonMouths()  4. firingPlacement()
|   |   |   +-- CannonMouth(lateral, forward): boca de cañon medida por pose.
|   |   |   +-- FiringPlacement(offsetXPx, offsetYPx): ancla del fogonazo por pose/familia.
|   +-- enemy
|   |   +-- BastionBomberSheet: ruta, tamaño y cinco recortes del PNG de giro.
|   |   |   1. internalPath()  2. frameSlices()  3. frameSlice()
|   |   +-- BastionBomber extends Ship: bombardero sin armas con espera, vuelo y órbita.
|   |   |   1. advance()  2. flightState()  3. hasTakenOff()  4. frameIndex()
|   |   |   5. headingDegrees()  6. centerX()  7. centerY()  8. setVisuals()
|   |   |   +-- Visuals: textura compartida y cinco regiones; dispose() la libera.
|   |   +-- BastionEscort: Vesper adicional que conserva formación y activa su encuentro al detectar Astra.
|   |   |   1. deploy()  2. advance()  3. isAttacking()  4. combat()  5. gun()  6. destroy()
|   |   +-- GhostwingDroneSheet: ruta y tres recortes de hélice del PNG Ghostwing.
|   |   |   1. internalPath()  2. frameSlices()  3. frameSlice()
|   |   +-- GhostwingDrone extends Ship: dron sin arma con dirección cardinal fija y hélice.
|   |   |   1. id()  2. direction()  3. rotationDegrees()  4. centerX()  5. centerY()
|   |   |   6. advance()  7. propellerFrame()  8. setVisuals()
|   |   |   +-- Direction [enum]: NORTH/EAST/SOUTH/WEST con vector y rotación de render.
|   |   |   +-- Visuals: textura compartida y tres regiones; dispose() la libera.
|   |   +-- GhostwingEncounter: calendario y vuelo mundial de grupos de drones.
|   |   |   1. advance()  2. groups()  3. activeDrones()  4. markDestroyed()
|   |   |   5. representativePoint() [estático]  6. nextIntervalSeconds()
|   |   |   +-- Point(x, y): punto mundial por valor.
|   |   |   +-- GhostwingGroup: id, dirección, miembros y retirada en el borde opuesto.
|   |   +-- VesperRaider extends Ship: enemigo autónomo con rumbo en el mundo.
|   |   |   1. advance()  2. steerTowards()  3. setHeadingDegrees()
|   |   |   4. placeAt()  5. centerX()  6. centerY()  7. setDriftDirection()
|   |   |   8. clampToWorld()  9. pose()  10. setVisuals()
|   |   |   11. setMuzzleFlashVisible()  12. shotAt() [instantánea de los dos cañones]
|   |   |   13. captureShotSample()  14. shotAt(ShotSample)
|   |   |   +-- ShotSample(centerX, centerY, headingDegrees, pose): instantánea inmutable de emisión.
|   |   |   +-- Visuals: carga textura; dispose() la libera.
|   |   +-- RaiderEncounter: aparición, muerte y reaparición del enemigo en el mundo.
|   |   |   1. advance() [2 sobrecargas]  2. destroyActive()  3. respawnAt()
|   |   |   4. isActive()  5. isDefeated()  6. raider()  7. setVisuals()
|   |   |   8. shotAt()  9. shotSampleAt(): muestreo por offset del frame grabado.
|   |   +-- VesperRaiderSheet: laminas v4 de vuelo/disparo y cinco recortes del enemigo.
|   |   |   1. slice()  2. poseForBank()  3. poseForTurn()
|   |   |   4. orientedForDescent()  5. maxSliceWidth()  6. maxSliceHeight()
|   |   |   7. cannonMouths()  8. firingPlacement()
|   |   |   +-- CannonMouth(lateral, forward): boca de cañón medida por pose.
|   |   |   +-- FiringPlacement(offsetXPx, offsetYPx): ancla del fogonazo por pose.
|   |   +-- UnitRandom: fuente sustituible de aleatoriedad.
|   |       1. nextUnit()
|   +-- collision
|   |   +-- CollisionDetector: contactos y barridos geométricos compartidos entre fases.
|   |       1. overlaps()  2. firstHit()  3. sweep()
|   |       +-- Circle, Box, Segment: geometría mundial inmutable, sin entidades/recursos.
|   +-- effect
|   |   +-- ShipExplosionSheet: ocho recortes medidos y escala de referencia.
|   |   |   1. internalPath()  2. frameSlices()  3. frameSlice()
|   |   +-- ShipExplosion: animación mundial no cíclica de una destrucción.
|   |   |   1. advance()  2. frameIndex()  3. isFinished()
|   |   +-- ShipExplosions: pool puro de explosiones simultáneas (varias por frame).
|   |   |   1. spawn()  2. advance()  3. active()  4. size()
|   |   |   +-- Entry(animation, peakSize): explosión en curso con su pico capturado.
|   |   +-- ShipExplosionVisuals: textura compartida screen-owned, regiones y dibujo.
|   |       1. region()  2. draw()  3. dispose()
|   |   +-- ShipHitFlash: pulso temporal puro de destello por impacto (0,10 s).
|   |       1. trigger()  2. advance()  3. intensity()  4. clear()
|   +-- weapon
|   |   +-- GunTuning: cadencia, velocidad y duración del fogonazo del M61 Vulcan.
|   |   +-- GunProjectile: proyectil rectilíneo con origen y rumbo congelados.
|   |   |   1. advance()  2. expired()  3. consume()  4. isConsumed()
|   |   |   5. remainingDistance()  6. x()  7. y()  8. forwardX()  9. forwardY()
|   |   |   10. travelled()
|   |   +-- GunBurst: emisor visual con cadencia, fogonazo y colección de proyectiles.
|   |   |   1. advance() [2 sobrecargas]  2. projectiles()  3. isFiring()
|   |   |   4. flashVisible()  5. emissionCount() [contador monotónico por ráfaga real]  6. clear()
|   |   |   +-- Muzzle(x,y): origen de un cañón congelado al disparar.
|   |   |   +-- Shot(muzzles,forwardX,forwardY,range): instantánea de una emisión.
|   |   |   +-- ShotSource: shotAt(offsetSeconds) entrega la instantánea.
|   |   |   +-- ProjectileStep: observador del segmento que cubre cada proyectil.
|   |   +-- GunBurstVisual: aspecto reutilizable de un trazador, sin recursos propios.
|   |       1. draw(): punta y estela según posición y vector del proyectil.
|   +-- scenery
|       +-- Starfield: estrellas con scroll de la bienvenida.
|       |   1. update(): mueve y recicla estrellas.
|       |   2. draw(): dibuja con ShapeRenderer.
|       +-- TerrainConfig: seed/version, dimensiones, tamaño de celda, escalas y umbrales del terreno.
|       +-- TerrainType: clasificación de celda MEADOW, FOREST o ROCKY.
|       +-- TerrainMap: rejilla inmutable y consultas por celda/coordenada mundial.
|       |   1. terrainAtCell()  2. forestDensityAtCell()  3. terrainAtWorld()
|       |   4. forestDensityAtWorld()  5. columns()  6. rows()
|       +-- TerrainGenerator: muestreo OpenSimplex2S determinista fuera del render.
|       |   1. generate(TerrainConfig)
|       +-- OpenSimplex2S: implementación Java pura 2D adaptada del código CC0 oficial.
|       +-- TreePatternGenerator: lattice irregular, variantes, orientación, escala, separación y reservas.
|       |   1. generate(TerrainMap)  2. generate(TerrainMap, List<SceneryPlacement>)
|       +-- SceneryField: colección inmutable de decoración con consulta por rectángulo.
|       |   1. instances()  2. query(WorldRect)
|       +-- SceneryInstance: id, recurso lógico, tipo, posición, tamaño, giro, escala y huella.
|       +-- SceneryType: árbol, arbusto, charco o grupo de rocas y catálogo lógico de variantes.
|       +-- SceneryPlacement: asset, anclaje, rectángulo, orden de dibujo y margen reservado.
|       +-- WorldRect: rectángulo mundial inmutable, expansión de reserva e intersección.
|
+-- infrastructure
    +-- gdx
        +-- scenery
        |   +-- TerrainAtlasCatalog: ids lógicos, dimensiones y ruta del atlas de una página.
        |   +-- TerrainAtlasOwner: carga diferida y liberación idempotente del recurso compartido.
        |   +-- TerrainGroundPlanner: selección de tiles visibles y recorte de bordes mundiales.
        |   |   1. plan(WorldRect)
        |   +-- TerrainSceneryRenderer: repetición del césped, base y sprites visibles con SpriteBatch.
        |       1. drawGround()  2. drawOperationsBase()  3. drawScenery()  4. pageCount()  5. estimatedBytes()  6. dispose()
        +-- ScreenFactory: construye pantallas, carga ajustes persistentes y comparte configuración.
        |   1. loadSettings()  2. createWelcomeScreen()  3. createPhaseOneScreen()
        +-- GameSettingsStore: lee/escribe preferencias locales de presentación, vuelo y vídeo.
        |   1. initialize()  2. astraBottomFraction()  3. saveAstraBottomFraction()
        |   4. flightSpeeds()  5. saveFlightSpeeds()  6. savedVideoPreference()  7. saveVideoPreference()
        +-- VirtualScreenSize: referencia de juego/ventana restaurada, 1024 x 768; no limita la vista.
        +-- GamePresentationSettings: encuadre de Astra persistido; ajustable entre 10–35 %.
        |   1. loadFrom()  2. astraBottomFraction()  3. setAstraBottomFraction()
        |   4. adjustAstraBottomFraction()
        +-- VideoDisplaySettings: catálogo y cambio seguro de modos del monitor actual; mantiene
        |   DisplayMode dentro del adaptador y expone VideoMode, DisplayCatalog, VideoState y ApplyResult.
        |   1. catalog()  2. currentState()  3. applyFullscreen()  4. applyWindowed()
        |   5. distinctModes() [lógica pura: deduplicación por resolución y Hz mayor]
        |   6. supportedMode()  7. restoredWindowSize()
        +-- event
        |   +-- GameEventBus: bus sincrono por tipo concreto de evento.
        |   |   1. subscribe()  2. publish()
        |   +-- GameEventPublisher: contrato de publicacion.
        |   |   1. publish()
        |   +-- Subscription: cancelacion de un listener.
        |       1. cancel()
        +-- audio
        |   +-- PhaseOneAudio: dueño de los recursos de audio de PhaseOne (cabina, cue M61, explosión).
        |       1. updateCabinAmbience()  2. cabinAmbienceAudible()  3. playVulcanBursts()
        |       4. burstsSince() [helper puro del delta de emisiones]  5. playExplosions()
        |       6. explosionCues() [helper puro de destrucciones frescas]  7. dispose()
        +-- screen
            +-- WelcomeScreen: inicio, estrellas, ajustes de vídeo/encuadre/velocidades e input de bienvenida.
            |   1. render()  2. resize()  3. handleSettingsInput()  4. dispose()
            +-- PhaseOneScreen: input, dibujo de mundo/HUD/explosiones, recursos.
            |   1. render()  2. resize()  3. dispose()
            |   4. drawGhostwingDrones()  5. spawnGhostwingExplosions()
            |   6. ghostwingExplosionPeak()  7. ghostwingGroupPoints()
            |   8. drawOffscreenIndicator()  9. updateExplosions()  10. drawExplosions()
            |   11. completeRun()  12. drawObjectivePanel()  13. drawResultPanel()
            |   14. drawOverlayPanel()
            +-- PhaseOneScreenFlow: ciclo INTRO/PLAYING/RESULT y latch de ESPACIO, sin LibGDX.
            |   1. observeSpace()  2. consumeStartPress()  3. fireTrigger()
            |   4. showResult()  5. consumeResultConfirm()  6. stage() [Stage = INTRO | PLAYING | RESULT]
            +-- ShipHitFlashRenderer: shader blanco compartido para el destello de impacto.
            |   1. draw()  2. dispose()
            +-- AstraLightEffect: halo de motor y halos alares proporcionales a la guiñada.
                1. update()  2. draw()  3. dispose()
            +-- FlightCameraState: seguimiento, zoom y encuadre de combate interpolados según el viewport efectivo.
            |   1. update() [3 sobrecargas]  2. x()  3. y()  4. yawDegrees()  5. zoom()
            |   6. combatActive()  7. aheadDistance()
            |   +-- CombatTarget(active, centerX, centerY, halfBox): enemigo a encuadrar o none().
            +-- CameraVisibility: regla pura de visibilidad de una nave en la vista real.
            |   1. shipVisible()  2. localFrame() [marco de cámara compartido por el indicador]
            +-- FlightCamera: aplica el estado a OrthographicCamera.
            |   1. update()
            +-- FlightHud: brújula, velocidad, arma activa, energía y score en pantalla.
            |   1. headingLabel()  2. headingDegrees()  3. weaponLabel()  4. energyLabel()
            |   5. draw()  6. drawScore()
            +-- MissionHud: bajas 0/3–3/3, vidas 3–0 y reloj 10:00–00:00 (COS-71), sin recursos nativos.
            |   1. Readout.from()  2. clockLabel()  3. layout()  4. draw()
            +-- EnemyStatusHud: nombre + energía % de enemigos visibles (Vesper/Bastion, COS-72).
            |   1. show()  2. Readout.titleLabel()/energyLabel()  3. layout()  4. draw()
            +-- PhaseOneTerrainCatalog: catálogo histórico de 128 PNG (COS-82; sin uso activo).
            |   1. tiles()  2. tile()  3. pathFor()  4. tileAt()  5. tilesIn()
            |   6. requiresVerticalFlip()
            +-- PhaseOneTerrainPlanner: caja visible activa y planificación histórica de PNG (COS-83/91).
            |   1. visibleBounds()  2. visibleCorners()  3. plan() [ruta histórica]
            +-- PhaseOneTerrainCache: caché histórica de texturas (COS-84–86; sin uso activo).
            |   1. primeVisible()  2. update()  3. resource()  4. dispose()
            +-- PhaseOneTerrainTextureFactory: adaptador histórico de PNG (COS-84–86; sin uso activo).
                1. create()  2. dispose()  3. estimatedBytes()
            +-- FlightMinimap: overlay opcional del mundo, bases, Astra, Vesper, Bastion y grupos Ghostwing.
                1. toggle()  2. isVisible()  3. draw()
                4. bomberBlinkVisible()  5. diamondVertices() [geom. pura del rombo]
                6. GROUP_MARKER_HALF
            +-- GoForwardPrompt: aro degradado y texto curvo alrededor de Astra al despegar/reaparecer.
                1. draw()  2. dispose()
            +-- MinimapProjection: adaptación y proyección del mundo al panel.
            |   1. fit()  2. project()  3. astraDirection()  4. raiderDirection()
            |   5. BOTTOM_MARGIN [banda HUD inferior compartida con el indicador]
            +-- OffscreenEnemyIndicator: flecha HUD hacia el Vesper fuera de cámara.
                1. compute()  2. avoidReservations()  3. draw()
```

## Comportamiento provisional actual

PlayableRun fija la duración en 600 segundos (el límite de 10 minutos de la misión de PhaseOne).
PhaseOne arranca en el panel de objetivo y empieza al pulsar ESPACIO; al terminar, GameCoordinator
cambia el estado a GAME_OVER y PhaseOneScreen permanece visible con el panel WIN/LOSE hasta que una
pulsación nueva de ESPACIO (o ESC) vuelve a Welcome. Todavía no existe una pantalla de game over ni
hall of fame.
El bus actual es propio y síncrono, sin Guava. ScreenFactory se instancia con sus
dependencias; no es un singleton.

Actualiza este árbol y las reglas de ubicación cuando se añadan, muevan o retiren
clases o cambien sus responsabilidades.
