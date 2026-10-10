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
de velocidad, aceleración, frenado y giro aprobadas (crucero 230 u/s, turbo 550,
Ultra 750, giro 130°/s; el desplazamiento lateral-solo conserva 300 u/s como
`STRAFE_SPEED`, separado de la velocidad de crucero); el borde anticipa el giro
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
lecturas. Durante el combate, `PhaseOneScreen` oculta el minimapa mientras un
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
Ship.draw(batch) mantiene la proporción de las regiones dentro de una caja estable.
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
no vuela ni dispara y el reloj de 60 s continúa; reaparece vía `placeAt` en la misma
posición/rumbo. `RaiderEncounter.destroyActive()` conserva la instancia y sus
Visuals; `respawnAt(world, targetCenterX, targetCenterY, targetRadius)` elige con
`UnitRandom` un punto válido dentro del mundo, a ≥ 500 del centro de Astra y ≥ 800
del punto de muerte, con fallback al mejor candidato y sin recrear texturas.

`RaiderEncounter` queda con la afinación aprobada del PO (velocidad 180, deriva
±10° cada 4–6 s, giro 75°/s) y CA18: la sobrecarga
`advance(dt, world, targetX, targetY, targetVelX, targetVelY, targetRadius, selfRadius)`
prevé la aproximación más cercana dentro de un horizonte de 1,2 s con movimiento
relativo (el controlador pasa el centro, velocidad y radio de Astra); si predice
contacto (radios + margen 70), gira perpendicular a la demora en un lado elegido por
menor cambio de rumbo y lo mantiene ≥ 1,2 s para evitar oscilación; el vector de
bordes interiores se mantiene. La sobrecarga de 4 argumentos conserva el
comportamiento de búsqueda pura para las pruebas deterministas. `PhaseOneScreen`
llama a `advanceCombat` una vez por frame dentro del recorrido y suma `drainCombatPoints()`
con los puntos de vuelo antes de publicar `PointsEarned`; no dibuja a Astra cuando
`isAstraActive()` es falso y la coincidencia visible (`shipsCoincidentVisible`)
también exige a Astra activa. La explosión gráfica, el parpadeo y el HUD de energía
quedan descritos en la sección «Explosiones, parpadeo y energía integrados (COS-33)».

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

Las láminas de vuelo mantienen sus cinco recortes: las tres poses de
alabeo existentes y dos poses de guiñada (derecha, izquierda). Astra selecciona
alabeo con laterales solos y guiñada con diagonales; la pantalla aplica la
rotación de rumbo en coordenadas de mundo. `RaiderEncounter` conserva una única
instancia de Vesper Raider por fase: espera una vez, aparece por delante de la
ruta inicial dentro de `WorldBounds` y persiste en coordenadas de mundo aunque
salga de cámara. Con COS-32 la velocidad es 180 u/s, la deriva ±10° cada 4–6 s
y `destroyActive()/respawnAt(...)` conservan esa misma instancia y sus Visuals
tras la destrucción (véase «Combate determinista integrado (COS-32)»).
`VesperRaider.steerTowards` gira el rumbo con un límite de
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
|   +-- PhaseOneGameController extends GameController: encuentros, armas y combate de PhaseOne.
|       1. placeAstra()  2. advanceFlight()  3. advanceEncounter()
|       4. advanceCombat()  5. advanceWeapons()  6. drainCombatPoints()
|       7. isRaiderActive()  8. activeRaider()  9. setRaiderVisuals()
|       10. astraCombat()  11. raiderCombat()  12. isAstraActive()
|       13. scorePoints()  14. astraGun()  15. raiderGun()
|       16. advanceHitFeedback()  17. astraHitFlashIntensity()  18. raiderHitFlashIntensity()
|
+-- domain
|   +-- game
|   |   +-- GameState: identidad, fase, puntos y vidas de la partida.
|   |   |   1. changePhase()  2. addPoints()  3. loseLife()
|   |   |   4. completePhase()  5. snapshot()
|   |   +-- GamePhase [enum]: WELCOME, PLAYING_PHASE_ONE, GAME_OVER.
|   |   +-- PlayableRun: recorrido actual de 60 segundos.
|   |   |   1. start()  2. advance()  3. isFinished()  4. remainingSeconds()
|   |   +-- PhaseScore: puntos por segundos completos, coincidencia visible y bonus.
|   |   |   1. advance()  2. totalPoints()  3. awardRaiderDestroyed()
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
|   |   |   1. placeAt()  2. fly()  3. yawDegrees()  4. flightSpeed()
|   |   |   5. ultraRemainingSeconds()  6. setVisuals()  7. setMuzzleFlashVisible()
|   |   |   8. currentRegion()  9. shotAt(): instantánea de los dos cañones para una emisión.
|   |   |   +-- Visuals: carga texturas; dispose() las libera.
|   |   +-- FlightControls: teclas de pilotaje y activación puntual de Ultra.
|   |   |   1. neutral()  2. lateral()  3. turning()
|   |   +-- FlightTuning: parámetros de vuelo aprobados de PhaseOne.
|   |   +-- HeroShipSheet: laminas v3 y cinco recortes de sprites de Astra.
|   |   |   1. sheets()  2. firingSheets()  3. cannonMouths()  4. firingPlacement()
|   |   |   +-- CannonMouth(lateral, forward): boca de cañon medida por pose.
|   |   |   +-- FiringPlacement(offsetXPx, offsetYPx): ancla del fogonazo por pose/familia.
|   +-- enemy
|   |   +-- VesperRaider extends Ship: enemigo autónomo con rumbo en el mundo.
|   |   |   1. advance()  2. steerTowards()  3. setHeadingDegrees()
|   |   |   4. placeAt()  5. centerX()  6. centerY()  7. setDriftDirection()
|   |   |   8. clampToWorld()  9. pose()  10. setVisuals()
|   |   |   11. setMuzzleFlashVisible()  12. shotAt(): instantánea de los dos cañones.
|   |   |   +-- Visuals: carga textura; dispose() la libera.
|   |   +-- RaiderEncounter: aparición, muerte y reaparición del enemigo en el mundo.
|   |   |   1. advance() [2 sobrecargas]  2. destroyActive()  3. respawnAt()
|   |   |   4. isActive()  5. isDefeated()  6. raider()  7. setVisuals()
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
|   |   |   4. flashVisible()  5. clear()
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
            +-- PhaseOneScreen: input, dibujo de mundo/HUD/explosiones, recursos.
            |   1. render()  2. resize()  3. dispose()
            +-- ShipHitFlashRenderer: shader blanco compartido para el destello de impacto.
            |   1. draw()  2. dispose()
            +-- FlightCameraState: seguimiento y zoom interpolados.
            |   1. update()  2. x()  3. y()  4. yawDegrees()  5. zoom()
            +-- CameraVisibility: regla pura de visibilidad de una nave en la vista real.
            |   1. shipVisible()
            +-- FlightCamera: aplica el estado a OrthographicCamera.
            |   1. update()
            +-- FlightHud: brújula, velocidad, arma activa, energía y score en pantalla.
                1. headingLabel()  2. headingDegrees()  3. weaponLabel()  4. energyLabel()
                5. draw()  6. drawScore()
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
