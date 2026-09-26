# Cosmic Aces — Guía para agentes

Lee estas instrucciones antes de implementar o modificar funcionalidades. El repositorio está en fase inicial: debe conservar una base pequeña y ejecutable mientras se construye el juego por entregas.

## Visión del juego

Juego arcade de disparos con desplazamiento vertical continuo, inspirado en la sensación de *1945 Air Force* y ambientado en el espacio. La nave del jugador podrá moverse dentro del viewport, avanzar y retroceder, virar a izquierda y derecha y disparar. Los enemigos aparecerán desde la parte superior. El objetivo es capturar una presentación arcade clara y legible, con movimiento y combate como prioridades.

No implementar todavía características de juego sin una solicitud o tarea concreta. La pantalla actual es intencionalmente vacía.

## Stack

| Área | Tecnología |
|---|---|
| Lenguaje | Java 22 (igual que el proyecto `lastmove`) |
| Motor 2D | LibGDX 1.12.1 |
| Escritorio | LWJGL3 (`gdx-backend-lwjgl3`) |
| Build | Maven 3.x |
| Ventana/viewport virtual | 800 × 600; `FitViewport` y `OrthographicCamera` |
| macOS | `-XstartOnFirstThread`, configurado en `exec-maven-plugin` |

Las tres dependencias LibGDX del `pom.xml` siguen a `ghosts-game`: `gdx`, `gdx-backend-lwjgl3` y `gdx-platform` con classifier `natives-desktop`. Los plugins Maven también mantienen las mismas versiones y propósito que la referencia.

## Estructura inicial

```text
com.davidpe.cosmicaces
├── DesktopLauncher                 # Configura e inicia la ventana LWJGL3
└── application/
    └── CosmicAcesGame              # Ciclo LibGDX y shell inicial
```

La estructura sigue el patrón simple de `com.davidpe.ghosts` del repositorio de referencia. Añade subpaquetes por responsabilidad a medida que exista código que los necesite; evita capas vacías o abstracciones prematuras.

## Responsabilidades y convenciones

- `DesktopLauncher` se limita a configurar título, tamaño, FPS y backend de escritorio.
- `CosmicAcesGame` gestiona el ciclo LibGDX y los recursos compartidos de escena. El estado del juego y entidades deben vivir en sus propias clases cuando se incorporen.
- Mantén la resolución virtual en 800 × 600 y actualiza el `FitViewport` al cambiar el tamaño de ventana.
- Libera con `dispose()` todo recurso nativo que el juego cree (`Texture`, `SpriteBatch`, audio, etc.).
- No guardes un `SpriteBatch` compartido dentro de entidades; pásalo al método de dibujo.
- Usa nombres y comentarios claros en inglés para el código y conserva este documento en español.
- Prefiere pixel art legible y una composición arcade, con contraste suficiente entre nave, proyectiles, enemigos y fondo espacial. Las referencias de género orientan el tono; no copies assets protegidos.
- Mantén las dependencias en Maven y evita añadir librerías sin una necesidad concreta.

## Build y ejecución

```bash
mvn compile
mvn compile exec:exec
```

Para crear el JAR ejecutable con dependencias:

```bash
mvn package
```
