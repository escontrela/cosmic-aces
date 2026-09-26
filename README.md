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
