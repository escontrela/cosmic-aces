# Recursos de las pantallas — v1

Generados con la herramienta integrada ImageGen el 2026-11-03. `welcome-background-v1.png` y `cosmic-aces-logo-v1.png` ya se dibujan en `WelcomeScreen`; `hall-of-fame-background-v1.png` queda reservado para la pantalla de puntuaciones (COS-123).

| Archivo dentro de assets/images | Tamaño | Uso |
| --- | --- | --- |
| screens/welcome-background-v1.png | 1448 × 1086 (4:3), RGB | Fondo de inicio; logo en zona superior, menú en zona inferior. |
| screens/hall-of-fame-background-v1.png | 1448 × 1086 (4:3), RGB | Fondo de puntuaciones; dibujar título, nombres y puntos dinámicamente sobre la zona oscura central. |
| branding/cosmic-aces-logo-v1.png | 1774 × 887, RGBA | Logo independiente con transparencia para sustituir el ASCII en la futura integración. |

Los fondos pueden escalarse proporcionalmente a 800 × 600. No contienen puntuaciones incrustadas.

## Prompts finales

### Inicio

Use case: illustration-story. Asset type: finished game title-screen background for Cosmic Aces. Create a 4:3 landscape image, ideally 1600x1200, composed for an 800x600 game. Realistic airbrushed painted illustration from a premium 1990s PC/arcade flight combat game title screen, detailed aircraft, dramatic clouds, rich deep navy shadows and warm orange sunlight, subtle painted grain, not pixel art and not a modern UI mockup. Astra IS the hero aircraft, not a human character: a silver gray F-16 MLU fighter with single tail, single engine, long pointed nose, blue reflective canopy, white star insignia, underwing missiles and bright blue afterburner. Show ONE hero F-16 dynamically banking across the middle foreground in three-quarter view, pursuing ONE distant dark charcoal angular twin-engine Vesper Raider enemy fighter, with twin blue exhausts and sensor nose; distinct aircraft silhouettes. An active dogfight above sweeping storm clouds at dusk, a brief streak of cannon tracer fire and curling contrails conveys combat without hiding the aircraft. Keep the upper 25 percent dark and comparatively quiet for a separate Cosmic Aces logo overlay; keep bottom 18 percent dark subdued for future menu text. Aircraft wholly inside frame. Full bleed art with no borders, no typography, no logos, no numbers, no fake buttons, no watermark. Original artwork.

### Hall of Fame

Use case: illustration-story. Asset type: finished Hall of Fame / high-score screen background for the original flight-combat game Cosmic Aces. Create a 4:3 landscape image, ideally 1600x1200 for an 800x600 game. Realistic detailed airbrush painting in the tradition of premium 1990s PC arcade game title artwork, sophisticated navy blue, steel and amber palette, subtle painted grain. A dim military aircraft hangar at twilight celebrating returning ace pilots. Frame the view with shadowy industrial beams and warm lamps along the outer edges; a silver gray single-engine F-16 with blue canopy and white star insignia sits parked in the lower-left edge, seen three-quarter side, restrained scale, hero aircraft entirely below the center of the picture. A very restrained gold winged aviation insignia with a small central star is mounted high at the top center, without lettering. CRITICAL composition: central 60 percent of image width and vertical area from 23 to 80 percent is an uninterrupted very dark navy, low-detail softly shadowed hangar wall, practically blank, intended for actual runtime player names and numeric scores in ten rows. Keep the center free of aircraft, bright beams, objects, props, built-in panels, text or horizontal lines. Convey prestige, quiet victory and aviation nostalgia around the periphery. It is a BACKGROUND ART ASSET only: do not paint any fake scores, names, letters, title, UI, numbers, buttons, table, or watermark. Full bleed illustration, no border.

### Logo

Use case: logo-brand. Asset type: production raster title logo for original 1990s-style aviation arcade game Cosmic Aces, intended to replace an ASCII title. Create a wide horizontal logo on a truly transparent alpha background. Exact and only text, spelled correctly: "COSMIC ACES". Two tightly composed centered lines: COSMIC above, ACES below, very legible bold forward-leaning custom arcade block capitals. Premium early-to-mid 1990s PC game box title lettering: beveled brushed chrome with icy blue reflections and restrained warm gold edge accents, dark navy keyline around each letter that keeps the silhouette legible over clouds. Compact integrated aviation motif: a small sharp five-point star and a pair of swept stylized wings behind the lettering, secondary to the title. Modest depth, crisp polished edges, readable when reduced to approximately 400 pixels wide. Occupy the canvas efficiently with only a small clear transparent margin; width about twice the height. Original design, no other words, no slogan, no trademark symbols, no watermark, no rectangle, no sky, no scene, no background, no checkerboard painted into the artwork. Real alpha transparency everywhere outside the emblem and lettering; no giant glow.

