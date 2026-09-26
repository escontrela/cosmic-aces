# Cosmic Aces

Juego arcade de disparos espaciales con scroll vertical continuo, inspirado en la jugabilidad de *1945 Air Force*. La nave podrá desplazarse y virar dentro del viewport, disparar y enfrentarse a enemigos que entran desde la parte superior.

La base usa Java 22, LibGDX 1.12.1, LWJGL3 y Maven, siguiendo el stack y la estructura sencilla de `escontrela/ghosts-game`. Por ahora solo está implementada la ventana inicial vacía.

## Dirección futura

Se contempla añadir funciones en línea mediante WebSockets, por ejemplo para partidas multijugador, salas o rankings. Si se implementan, el servidor podrá desarrollarse como un servicio independiente con Spring Boot; el cliente LibGDX seguirá gestionando el juego y la representación gráfica. Spring Boot todavía no forma parte de este proyecto.

LibGDX permite distribuir aplicaciones de escritorio para macOS y Windows, además de otras plataformas. El backend de escritorio actual es LWJGL3.

## Requisitos

- JDK 22
- Maven 3.x

## Ejecutar

```bash
mvn compile exec:exec
```

En macOS, el lanzador Maven ya aplica `-XstartOnFirstThread`.

Consulta [AGENTS.md](AGENTS.md) para la visión y las convenciones de desarrollo.

## Flujo de trabajo con agentes

Linear es la fuente de verdad de los tickets. Codex actúa como Tech Lead: analiza tickets del PO, crea subtareas ordenadas y verifica/cierra el trabajo. OpenCode actúa como Developer: implementa cada subtarea en una rama compartida, crea commits y abre una PR cuando todas han sido verificadas. Ningún agente fusiona la PR.

El workspace Linear es [`cosmic-aces`](https://linear.app/cosmic-aces), con el equipo `Cosmic-aces`. En Codex selecciona la conexión `cosmic-aces-linear`; no uses la conexión genérica `Linear` ni `LastMoveChess`. Actualmente no hay un proyecto Linear configurado, así que las issues se crean en el equipo sin proyecto hasta que el PO confirme uno.

Las instrucciones compartidas están en [AGENTS.md](AGENTS.md); las skills de ambos roles están en [`.agents/skills/`](.agents/skills/), y los perfiles y la conexión MCP de OpenCode están en [`.opencode/`](.opencode/). Los prompts para programar las ejecuciones están en [`docs/agent-workflow/`](docs/agent-workflow/). La autenticación de OpenCode y la programación local todavía deben configurarse en el entorno de desarrollo.

All agents must follow the hard prohibition in `AGENTS.md`: they may not make purchases, increase token or usage limits, change plans, or create accounts under any circumstances.
