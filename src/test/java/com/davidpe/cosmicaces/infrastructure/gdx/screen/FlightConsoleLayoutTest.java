package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import org.junit.jupiter.api.Test;

/** Geometry checks for the single-line bottom pilot strip and its shared map reservation. */
class FlightConsoleLayoutTest {

  private static final float[][] VIEWPORTS = {
      {800f, 600f}, {1024f, 768f}, {1440f, 900f}, {1920f, 1080f}};

  @Test void oneLineStripFitsEverySupportedViewport() {
    for (float[] size : VIEWPORTS) {
      FlightConsoleLayout.Layout layout = FlightConsoleLayout.fit(size[0], size[1]);
      FlightConsoleLayout.Rectangle panel = layout.panel();
      assertEquals(0f, panel.x());
      assertEquals(0f, panel.y());
      assertEquals(size[0], panel.width());
      assertEquals(FlightConsoleLayout.HEIGHT, panel.height());
      assertTrue(layout.contentLeft() > 0f);
      assertTrue(layout.contentRight() > layout.contentLeft());
      assertTrue(layout.contentRight() < panel.width());
      assertTrue(layout.baseline() > 0f && layout.baseline() < panel.height());
    }
  }

  @Test void minimapStartsAboveTheStrip() {
    assertEquals(FlightConsoleLayout.HEIGHT, MinimapProjection.BOTTOM_MARGIN);
    assertEquals(FlightConsoleLayout.HEIGHT, FlightConsoleLayout.bottomBand());
    MinimapProjection.Rectangle mapPanel = MinimapProjection.fit(
        new WorldBounds(8192f, 36000f), 800f, 600f).panel();
    assertEquals(FlightConsoleLayout.HEIGHT, mapPanel.y());
  }

  @Test void shortViewportTrimsStripAndInvalidDimensionsFail() {
    FlightConsoleLayout.Layout shortLayout = FlightConsoleLayout.fit(320f, 30f);
    assertEquals(30f, shortLayout.panel().height());
    assertTrue(shortLayout.baseline() < 30f);
    assertThrows(IllegalArgumentException.class, () -> FlightConsoleLayout.fit(0f, 600f));
    assertThrows(IllegalArgumentException.class, () -> FlightConsoleLayout.fit(800f, 0f));
    assertThrows(IllegalArgumentException.class, () -> FlightConsoleLayout.fit(Float.NaN, 600f));
    assertThrows(IllegalArgumentException.class, () -> FlightConsoleLayout.fit(90f, 600f));
  }
}
