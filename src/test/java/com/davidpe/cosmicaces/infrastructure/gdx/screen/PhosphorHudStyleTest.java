package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Palette tests for the shared green phosphor of the console and the minimap (COS-131/COS-133). The
 * map is monochrome, so the checks forbid any colour where red or blue dominates and verify that
 * the three marker intensities remain distinguishable without hue.
 */
class PhosphorHudStyleTest {

  private static final List<Color> CONSOLE_COLORS = List.of(
      PhosphorHudStyle.PANEL,
      PhosphorHudStyle.FRAME,
      PhosphorHudStyle.FRAME_DIM,
      PhosphorHudStyle.TEXT,
      PhosphorHudStyle.TEXT_BRIGHT,
      PhosphorHudStyle.TEXT_DIM,
      PhosphorHudStyle.TEXT_FAINT,
      PhosphorHudStyle.SHADOW);

  private static final List<Color> MAP_COLORS = List.of(
      PhosphorHudStyle.MAP_PANEL,
      PhosphorHudStyle.MAP_FILL,
      PhosphorHudStyle.MAP_FRAME,
      PhosphorHudStyle.MAP_TEXT,
      PhosphorHudStyle.MAP_MARKER_BRIGHT,
      PhosphorHudStyle.MAP_MARKER,
      PhosphorHudStyle.MAP_MARKER_DIM);

  @Test
  void everyConsoleAndMapColorIsGreenDominant() {
    for (Color color : CONSOLE_COLORS) {
      assertGreenDominant("console", color);
    }
    for (Color color : MAP_COLORS) {
      assertGreenDominant("map", color);
    }
  }

  @Test
  void mapAndConsoleKeepTheirTranslucencyInsideTheReadableRange() {
    assertTranslucent(PhosphorHudStyle.MAP_PANEL);
    assertTranslucent(PhosphorHudStyle.MAP_FILL);
    assertTrue(PhosphorHudStyle.MAP_PANEL.a >= 0.4f && PhosphorHudStyle.MAP_PANEL.a <= 0.7f,
        "the panel stays dark enough to read the markers");
    assertTrue(PhosphorHudStyle.MAP_PANEL.a > PhosphorHudStyle.MAP_FILL.a,
        "the panel frame stays darker than the map interior");
    assertTrue(PhosphorHudStyle.PANEL.a > 0f && PhosphorHudStyle.PANEL.a < 1f,
        "the console keeps its translucent fill");
  }

  @Test
  void markerTiersStayDistinctByIntensityAlone() {
    float bright = luminance(PhosphorHudStyle.MAP_MARKER_BRIGHT);
    float standard = luminance(PhosphorHudStyle.MAP_MARKER);
    float dim = luminance(PhosphorHudStyle.MAP_MARKER_DIM);
    float frame = luminance(PhosphorHudStyle.MAP_FRAME);

    assertTrue(bright > standard, "bright markers outshine the standard tier");
    assertTrue(standard > dim, "the standard tier outshines the dim outlines");
    assertTrue(frame > dim && frame < bright, "the frame sits between the outer marker tiers");
  }

  private static void assertGreenDominant(String surface, Color color) {
    assertTrue(color.g > color.r, surface + " color must not be red-dominant");
    assertTrue(color.g > color.b, surface + " color must not be blue-dominant");
    assertTrue(color.a > 0f, surface + " color must stay visible");
  }

  private static void assertTranslucent(Color color) {
    assertTrue(color.a > 0f && color.a < 1f, "map fills must be translucent");
  }

  private static float luminance(Color color) {
    return 0.3f * color.r + 0.55f * color.g + 0.15f * color.b;
  }
}
