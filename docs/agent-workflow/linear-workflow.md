# Contrato de flujo de trabajo con Linear

Este documento define el intercambio entre PO, Tech Lead (Codex) y Developer (OpenCode). Las issues de Linear son el estado compartido y durable del proceso. El checkout Git contiene el trabajo técnico; comentarios de Linear enlazan ambas cosas.

## Workspace y selección de conexión

- Usa exclusivamente el workspace **`cosmic-aces`**, URL `https://linear.app/cosmic-aces`.
- En el conector/MCP de Codex selecciona la cuenta/conexión **`cosmic-aces-linear`**. Puede coexistir con otras conexiones Linear; nunca asumas que la conexión predeterminada corresponde al proyecto.
- El equipo de trabajo es **`Cosmic-aces`**. Consulta sus estados reales antes de transicionar issues.
- A fecha de esta configuración, el workspace no devuelve un proyecto Linear `cosmic-aces`. Crea las issues en el equipo `Cosmic-aces` sin proyecto hasta que exista un proyecto aprobado y disponible; no inventes ni crees un proyecto.
- Si workspace, conexión o equipo no coinciden, no escribas en Linear: informa el bloqueo y pide corregir la conexión.

## Requisitos de una issue de PO

Una issue raíz de producto debe explicar, en la medida conocida:

1. **Problema y valor:** qué necesita el jugador o qué defecto observa.
2. **Resultado esperado:** qué debe ocurrir desde el punto de vista del usuario.
3. **Criterios de aceptación:** condiciones observables, concretas y comprobables.
4. **Alcance y límites:** qué incluye el ticket y qué queda fuera.
5. **Contexto visual o técnico:** referencias, capturas, controles, assets permitidos o restricciones relevantes.

No hace falta que el PO conozca el diseño técnico. Codex traduce el resultado de producto a trabajo de ingeniería. Cuando falte una decisión de producto, Codex comenta preguntas concretas en la issue raíz y no crea subtareas que dependan de una respuesta especulativa.

## Modelo de issues

- **Issue raíz:** solicitud del PO. No tiene `parentId`; agrupa el objetivo y todas las subtareas de implementación.
- **Subtarea:** una pieza pequeña, revisable y verificable de la solución. Tiene `parentId` apuntando a la issue raíz.
- **Dependencia:** relación Linear `blocks` / `blockedBy` entre subtareas consecutivas. Para una secuencia A → B → C, A bloquea B y B bloquea C. No dependas de que el orden alfabético, la fecha de creación o la posición visual de la lista sea estable.
- **Proyecto y equipo:** todas las subtareas heredan el proyecto y equipo de su issue raíz. No muevas issues a otro equipo ni cambies el proyecto sin una razón aprobada.
- **Labels:** conserva las labels que puso el PO. No crees taxonomías nuevas como parte de una ejecución normal.

## Contrato del plan técnico por subtarea

El Tech Lead no se limita a repartir títulos: después de inspeccionar el código debe dejar una propuesta técnica ejecutable para OpenCode en cada issue hija. La propuesta orienta el trabajo y reduce la exploración repetida, pero no prevalece sobre la realidad del checkout ni autoriza cambiar el resultado de producto pedido por el PO.

Cada descripción de subtarea debe incluir, cuando aplique:

1. **Resultado y límites:** entregable concreto y exclusiones.
2. **Hallazgos del repositorio:** archivos, clases, métodos, ciclo de vida o abstracciones existentes relevantes, verificados en el código. No inventar rutas ni afirmar inspecciones que no ocurrieron.
3. **Propuesta de implementación:** pasos ordenados, componentes/archivos sugeridos, límites entre responsabilidades y consideraciones de entrada, estado, renderizado, recursos y ciclo de vida que afecten al cambio. Diferenciar hechos actuales de decisiones recomendadas.
4. **Trazabilidad de aceptación:** qué criterios de la raíz cubre la subtarea y cómo se observará su cumplimiento. No reinterpretar ni reducir criterios del PO.
5. **Dependencias y orden:** prerequisitos técnicos/producto y relaciones Linear `blocks`/`blockedBy` cuando exista una dependencia real.
6. **Verificación:** comandos exactos y comprobaciones manuales/visuales necesarias; indicar qué evidencia se espera en el comentario de revisión.
7. **Riesgos y decisiones pendientes:** riesgos concretos y distinguir elecciones técnicas de decisiones de producto.

El comentario de análisis en la raíz debe resumir la interpretación, arquitectura, enfoque, criterios cubiertos, riesgos y secuencia de subtareas; cada hija contiene el plan específico de su entregable. Evita copiar un diseño genérico o prescribir detalles sin haber inspeccionado el código.

OpenCode debe comprobar el plan contra el checkout real. Puede ajustar la solución técnica si el plan está obsoleto, no encaja con la arquitectura existente o introduce riesgo evitable. Antes de implementar, debe explicar en un comentario de la hija qué cambia, la evidencia del repositorio y cómo conserva los criterios de aceptación. Si el ajuste cambia comportamiento visible, alcance o criterios del PO, debe detenerse y pedir una decisión al Tech Lead/PO. Codex revisará también la justificación cuando verifique la subtarea.

Antes de crear subtareas, enumera las hijas existentes. Si ya existe la descomposición, actualízala con cuidado en vez de duplicarla. No modifiques relaciones existentes de forma destructiva salvo que el plan aprobado lo requiera; deja la razón como comentario.

## Máquina de estados recomendada

Los nombres exactos dependen del workflow del equipo Linear. Al empezar, consulta los estados de ese equipo y usa sus IDs/nombres reales. La tabla expresa estados semánticos, no autoriza crear o reconfigurar estados del equipo.

| Entidad | Flujo semántico | Quién hace la transición |
|---|---|---|
| Issue raíz | Backlog → Todo/Ready → In Progress → In Review → Done | PO crea; Codex analiza; OpenCode inicia implementación; OpenCode publica PR; Codex verifica y cierra |
| Subtarea | Todo/Ready → In Progress → In Review → Done | Codex prepara; OpenCode implementa y solicita revisión; Codex verifica y cierra |
| Cualquier issue bloqueada | Estado semántico actual + comentario de bloqueo; usar estado `Blocked` solo si el equipo ya lo tiene | Agente que detecta el bloqueo |
| Trabajo descartado | Canceled (o equivalente configurado), solo con decisión del PO | Codex actualiza después de registrar el motivo |

### Correspondencia práctica

- `Backlog`: raíz nueva del PO, pendiente de análisis.
- `Todo`/`Ready`: análisis terminado; subtareas creadas; espera que OpenCode empiece.
- `In Progress`: OpenCode está trabajando en esa subtarea y la raíz refleja que hay implementación activa.
- `In Review`: cambio implementado y comprometido localmente, listo para que Codex lo verifique. Para la raíz, significa PR abierta lista para revisión final.
- `Done`: Codex comprobó los criterios y evidencia correspondientes. Una raíz solo pasa a `Done` después de revisar la PR y confirmar que todas las subtareas están `Done`.
- `Blocked`: si está configurado, explica en comentario el impedimento, responsable de la próxima acción y qué lo desbloquea. No crees este estado automáticamente.

Si el equipo no tiene un estado semánticamente equivalente, no cambies su configuración. Deja la issue en su estado actual y registra el impedimento para el usuario.

## Ciclo de vida detallado

### 1. Análisis y planificación — Codex

1. Consulta estados del equipo, proyecto Cosmic Aces y issues candidatas.
2. Selecciona únicamente issues raíz del backlog PO: sin padre y sin subtareas equivalentes ya planificadas.
3. Lee descripción, comentarios, labels, proyecto, enlaces y criterios de aceptación. Inspecciona el código actual para comprobar límites y dependencias reales.
4. Si una decisión de producto falta, pregunta en un comentario de Linear. No conviertas una suposición en criterio de aceptación.
5. Añade al comentario de análisis: interpretación del objetivo, solución técnica propuesta, riesgos reales, criterios técnicos de salida y secuencia de subtareas.
6. Crea subtareas hijas concretas. Cada una debe incluir su resultado, límites, criterios de aceptación, comando(s) o método(s) de verificación, y dependencia previa cuando exista.
7. Crea las relaciones `blocks`/`blockedBy` para fijar una secuencia inequívoca.
8. Mueve la raíz al estado semántico Todo/Ready. Nunca la cierres durante la planificación.

Un plan puede tener una sola subtarea si el cambio es pequeño. No fuerces cuatro tickets si no hay cuatro unidades reales. Separa por entregables revisables, no por capas artificiales.

### 2. Implementación — OpenCode

1. Busca raíces preparadas del proyecto con hijas pendientes. No tomes una issue raíz como si fuera una subtarea.
2. Elige la primera hija desbloqueada en Todo/Ready de la raíz activa. Comprueba que sus blockers estén en Done.
3. Confirma que no hay otra ejecución de OpenCode trabajando sobre esa raíz o rama. Solo se permite un escritor a la vez por raíz.
4. Crea/reutiliza una rama por issue raíz y cambia la subtarea a In Progress. Cuando comienza el conjunto de trabajo, cambia también la raíz a In Progress.
5. Implementa solo esa subtarea y mantén intactos los cambios que no pertenecen al ticket.
6. Ejecuta la compilación y las verificaciones definidas. Corrige fallos atribuibles al cambio antes de solicitar revisión.
7. Crea un commit lógico, con mensaje estilo Conventional Commits e ID de Linear cuando esté disponible.
8. Comenta en la subtarea qué cambió, commit SHA, archivos relevantes, comandos ejecutados y resultados. Cambia la subtarea a In Review.
9. Espera la verificación de Codex antes de empezar la siguiente subtarea dependiente. Si Codex la devuelve a In Progress, corrige los hallazgos en la misma rama, vuelve a validar y solicita revisión de nuevo.
10. Tras la última subtarea, cuando todas las hijas estén en Done, ejecuta una verificación final, sube la rama y crea una PR. Enlaza la PR a la raíz y mueve la raíz a In Review.

### 3. Verificación de subtareas — Codex

1. Procesa las hijas en In Review de Cosmic Aces que tengan commit y evidencia.
2. Revisa el diff o commit real, no solo el resumen del agente. Comprueba criterios de la hija, criterios padre relacionados, convenciones del repo, y posibles regresiones.
3. Ejecuta o inspecciona las verificaciones pertinentes si tienes acceso al checkout. Si no puedes verificar un punto, no lo declares aprobado: explica la limitación.
4. Si cumple, deja comentario conciso con evidencia y marca la hija Done. Eso desbloquea la siguiente hija.
5. Si no cumple, devuelve la hija a In Progress, detalla cada hallazgo con referencia a archivos/líneas cuando sea posible y señala una forma de reproducir o comprobarlo. No edites la rama ni crees commits.

### 4. PR y cierre de raíz — OpenCode y Codex

**OpenCode:** verifica que todas las hijas están Done, ejecuta las comprobaciones finales y confirma que no quedan cambios fuera del conjunto. Sube la rama y abre una PR; no la fusiones. Incluye en el cuerpo objetivo, resumen, subtareas/IDs, verificaciones y limitaciones. Comenta/enlaza la URL de PR en la raíz y pasa la raíz a In Review.

**Codex:** inspecciona la PR y confirma que cubre los criterios del PO, las subtareas Done y las verificaciones. Revisa checks disponibles y cambios fuera de alcance. Si la PR necesita cambios, devuelve la raíz a In Progress y documenta los hallazgos; reabre solo las hijas afectadas o añade una hija correctiva bajo la raíz cuando sea el diseño más claro. Si pasa, comenta la verificación y marca la raíz Done. No fusiona la PR.

El estado Done significa que la implementación fue verificada y la PR está lista para revisión/merge humano. No significa que se haya fusionado.

## Idempotencia y concurrencia

- Linear es el coordinador: cada agente vuelve a leer el estado justo antes de reclamar o transicionar una issue.
- Codex no planifica una raíz que ya tiene un plan/subtareas equivalentes. Revisa antes de escribir.
- OpenCode no toma una hija ya In Progress por otra ejecución ni trabaja con dos procesos sobre la misma raíz. La futura tarea programada debe impedir ejecuciones solapadas.
- Si hay estado Git inesperado o actividad externa desde la última lectura, detente antes de hacer reset, checkout destructivo, rebase, push forzado o alterar archivos ajenos.
- Una llamada MCP ambigua no debe repetirse ciegamente: lee de nuevo la issue para comprobar si se aplicó antes de reintentar.
- No marques Done, abras PR ni repitas commits por un estado anterior ambiguo. Reconciliar primero Linear, Git y GitHub.

## Criterios mínimos de comentarios

Los comentarios de progreso sirven como registro operativo. Deben ser breves, legibles y específicos. Para análisis: resumen del alcance, diseño y subtareas. Para revisión de hijo: aceptación/rechazo y evidencia. Para Developer: commit, cambios, pruebas y resultado. Para PR: URL, alcance, verificaciones y limitaciones.

No incluyas secretos, tokens, rutas locales sensibles ni el contenido completo de logs. En los prompts de automatización usa instrucciones de silencio cuando no haya cambios accionables; el usuario no necesita una notificación horaria vacía.
