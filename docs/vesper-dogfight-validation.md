# Validación del dogfight de Vesper Raider (COS-65)

Documento de cierre de la raíz **COS-61** (_Vesper Raider: IA competitiva con pasadas rápidas,
virajes fluidos y reenganche de tiro_). Recoge los fixtures reproducibles, las mediciones, las
tolerancias publicadas y el guion de QA visual que corresponde al PO humano.

> La verificación técnica de este documento (compilación, pruebas, arranque) la ejecuta OpenCode.
> La **comprobación visual** (fluidez, velocidad percibida, dificultad, oportunidades de respuesta,
> morro/pose/fogonazos) es del **PO humano** y no se declara hecha por ningún agente.

## 1. Qué se ha cerrado

Las hijas COS-62 (cinemática continua), COS-63 (pasadas comprometidas e inversión) y COS-64
(muestreo temporal de disparo) dejaron el comportamiento y sus pruebas. COS-65 comprueba el
**comportamiento conjunto** y añade los escenarios que faltaban, sin cambiar reglas del PO:

- Fixture de objetivo **en crucero** (Astra a 150 u/s) que exige dos pasadas geométricas y dos
  ventanas de tiro.
- Comparación de la velocidad de pasada frente al **baseline histórico documentado**.
- Delta **irregular con pico de 0,1 s** frente a rejilla fina 1/120 s, con tolerancias publicadas.
- Comprobación integrada de **permiso de fuego**, **identidad de la instancia** y **no interpolación
  a través del respawn** usando el controlador real de PhaseOne.

No se modifica ningún parámetro de producción: la evidencia confirma que el tuning de COS-62/63/64
ya cumple la aceptación, por lo que **no fue necesario ajustar** Vesper.

## 2. Baseline y limitación declarada

El baseline propuesto era el commit `866425b390afaca642510eaacffac2805e4c0516`, previo a esta
iteración. Ese checkout **ya no contiene** la API antigua de Vesper (seguimiento por bearing, turbo
que se cortaba en la pasada, giro 75°/s) y no puede reejecutarse sin un segundo checkout; la
aceptación permite entonces «calcularla desde posiciones públicas equivalentes y declarar la
limitación; no inventar datos».

Por eso la comparación de velocidad usa los valores **históricos documentados en la raíz COS-61**:

| Métrica | Baseline histórico documentado | Actual (COS-61) | Resultado |
|---|---|---|---|
| Crucero | 180 u/s | `CRUISE_SPEED` = 300 u/s | ×1,67 |
| Turbo/pasada | 270 u/s | `PASS_SPEED` = 500 u/s | ×1,85 (~+230 sobre el turbo) |
| Giro máximo | 75°/s | `MAX_TURN_RATE_DEGREES` = 130°/s | acotado, no copiado de Astra |
| Aceleración angular | — | `MAX_ANGULAR_ACCELERATION_DEGREES` = 360°/s² | acotada |

La afirmación medible es: la pasada alcanza ≥ 420 u/s (`> 270 + 150`), llega a 500 u/s y **nunca** lo
supera. No se presenta como ejecución binaria del commit antiguo.

## 3. Escenarios y comandos

Fixtures deterministas sin cargar texturas: mundo real (`WorldBounds(8192, 12000)`), posiciones y
velocidades explícitas, y `UnitRandom` guionizado solo para la espera/posición de aparición.
Las pasadas **geométricas** se miden en modo _seek-only_ (radios cero), como las pruebas de corredor
existentes, para que la evasión no interrumpa el corredor; la ventana de tiro y la separación de
casco se miden con **radios reales** (Astra 20 u, Vesper 40 u).

| Escenario | Fixture | Verifica | Resultado |
|---|---|---|---|
| Objetivo quieto | `quietTargetAchievesTwoGeometricPassesAndTwoFiringWindows` | Dos pasadas que sobrepasan el plano del objetivo + dos ventanas de tiro; giro/aceleración acotados | OK |
| Objetivo en crucero (150 u/s) | `cruisingTargetAchievesTwoGeometricPassesAndTwoFiringWindows` | Dos pasadas e inversión sostenida con objetivo en movimiento | OK |
| Radios reales | `realRadiiKeepHullSeparationWhileThePassPressesIntoAFiringWindow` | Separación de casco > suma de radios y ventana de tiro antes del quiebre | OK |
| Velocidad vs baseline | `passSpeedClearlyExceedsTheHistoricBaseline` | Pasada ≥ 420 u/s, ≤ 500 u/s, ≥ 2× crucero histórico | OK |
| FPS irregular 0,1 s | `irregularFramesWithASpikeMatchTheFineGridWithinPublishedTolerances` | Posición/rumbo equivalentes a 1/120 s | OK |
| Permiso/identidad integrados | `VesperDogfightIntegrationTest` | Sin fuego sin coincidencia ni estando destruido; misma instancia tras respawn; disparos desde la vida nueva | OK |

Otras garantías ya cubiertas por las hijas previas y que se mantienen verdes sin cambiar sus reglas:
`RaiderEncounterTest` (30/60/120 Hz, bordes/esquinas, lados sin parpadeo, muestreo temporal y no
interpolación tras respawn), `VesperRaiderTest`, `PhaseOneWeaponTest`, `PhaseOneCombatTest`,
`PhaseOneScoringTest`, `PhaseOneGhostwingCombatTest`, `FlightCameraStateTest`, `CameraVisibilityTest`,
`OffscreenEnemyIndicatorTest` y `AstraTest`.

Comandos de verificación:

```sh
# Obligatorio de la raíz + esta hija
mvn -Dtest=RaiderEncounterTest,VesperRaiderTest,VesperDogfightValidationTest,VesperDogfightIntegrationTest,PhaseOneWeaponTest,PhaseOneCombatTest,PhaseOneScoringTest,PhaseOneGhostwingCombatTest,FlightCameraStateTest,CameraVisibilityTest,OffscreenEnemyIndicatorTest,AstraTest test

# Regresión completa
mvn test
```

## 4. Tolerancias FPS publicadas

El encuentro integra internamente subpasos acotados a `MAX_STEP_SECONDS = 1/120 s`, por lo que la
misma trayectoria analítica debe coincidir aunque la rejilla de llamada cambie (30/60/120 Hz o un
frame irregular con pico de 0,1 s). Tolerancias publicadas y usadas en las pruebas:

| Magnitud | Tolerancia | Justificación |
|---|---|---|
| Posición X/Y | 1,0 u | Acumulación en coma flotante sobre 8 s de subpasos y fronteras de maniobra detectadas en la rejilla de llamada. |
| Rumbo | 0,5° | Idem; muy por debajo de cualquier giro visible (130°/s). |
| Timing de pasada | ±2 frames del delta mayor + 0,02 s | Cada frontera (commit/salida) se detecta en el frame que llama, no en el subpaso. |

Un frame largo **no** teletransporta ni omite la trayectoria: el pico de 0,1 s se reparte en subpasos
de 1/120 s. La tolerancia con radios reales puede crecer si una frontera discreta de maniobra cae
justo entre dos subpasos distintos; por eso las tolerancias de posición/rumbo se fijan en los
fixtures _seek-only_ (mismos que la prueba histórica de FPS) y no se usan para acreditar la ventana
de tiro.

## 5. Parámetros finales de Vesper (sin cambios en COS-65)

Centralizados en `domain/enemy/RaiderEncounter` y `domain/enemy/VesperRaider`; son punto de partida
técnico ajustable por el PO, no cifras de producto:

| Parámetro | Valor |
|---|---|
| `CRUISE_SPEED` / `PASS_SPEED` / `REENGAGE_SPEED` | 300 / 500 / 320 u/s |
| `SPEED_ACCELERATION` / `SPEED_DECELERATION` | 300 / 450 u/s² |
| `MAX_TURN_RATE_DEGREES` / `MAX_ANGULAR_ACCELERATION_DEGREES` | 130°/s / 360°/s² |
| `PASS_COMMIT_DISTANCE` / `PASS_COMMIT_ANGLE_DEGREES` | 900 u / 15° |
| `PASS_MAX_SECONDS` / `PASS_AVOID_HORIZON_SECONDS` / `PASS_AVOID_CONTACT_MARGIN` | 3 s / 0,5 s / 40 u |
| `INVERT_ALIGN_DEGREES` / `INVERT_MAX_SECONDS` / `SEPARATE_SECONDS` | 25° / 5 s / 0,5 s |
| Cadencia / velocidad de proyectil (sin cambios) | `GunTuning.RAIDER_BURST_INTERVAL_SECONDS` 0,4 s / 1800 u/s |

## 6. Guion de QA visual para el PO

No sustituye a la QA del PO; es una guía para comparar la sensación antes/después.

1. Arrancar PhaseOne con los **ajustes normales anotados** (encuadre y velocidades de Astra
   visibles en Ajustes) y anotar la versión/commit.
2. Observar de frente: Vesper debe **aproximar**, pasar a **alta velocidad**, **prolongar la salida**
   y ejecutar un **arco de inversión** visible antes de una **segunda entrada**; no debe quedar
   orbitando ni buscar el contacto de casco.
3. Comprobar que morro, trayectoria y cañones giran juntos (sin alabeo fingiendo el giro) y que los
   proyectiles nacen de los cañones reales.
4. Probar: giro contrario, frenado y turbo/Ultra; confirmar que la nave responde y que Vesper no
   embiste de forma inevitable.
5. Llevar a un borde/esquina y comprobar que replanifica sin rebotes ni bloqueos.
6. Confirmar que Vesper sigue disparando dentro del permiso de coincidencia visible y **no** dispara
   fuera de pantalla.
7. Anotar fluidez, velocidad percibida, dificultad y oportunidades de respuesta; aportar vídeo
   comparable si es posible.

## 7. Evidencia y límites

- La evidencia técnica (comandos, número de tests, SHA, push y arranque acotado) se registra en el
  comentario de cierre de COS-65 en Linear.
- El baseline histórico es documental (sección 2); no se acredita como ejecución binaria previa.
- **QA visual pendiente del PO**: ningún agente ha inspeccionado la pantalla.
