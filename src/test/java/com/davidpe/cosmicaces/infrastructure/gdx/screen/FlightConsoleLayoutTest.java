package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import org.junit.jupiter.api.Test;

/**
 * Pure geometry tests for the fixed bottom pilot console (COS-131). The layout is measured without a
 * native window so the shared band, the three columns and the reading baselines stay inside the HUD
 * viewport at every supported size.
 */
class FlightConsoleLayoutTest {

  private static final float[][] VIEWPORTS = {
      {800f, 600f}, {1024f, 768f}, {1440f, 900f}, {1920f, 1080f}};

  @Test void panelIsAnchoredToTheBottomEdgeAcrossSupportedViewports() {
    for (float[] size : VIEWPORTS) {
      FlightConsoleLayout.Layout layout = FlightConsoleLayout.fit(size[0], size[1]);
      FlightConsoleLayout.Rectangle panel = layout.panel();

      assertEquals(0f, panel.x(), 0.0001f);
      assertEquals(0f, panel.y(), 0.0001f, "the console always hugs the bottom edge");
      assertEquals(size[0], panel.width(), 0.0001f, "the panel spans the whole viewport width");
      assertEquals(FlightConsoleLayout.HEIGHT, panel.height(), 0.0001f);
      assertTrue(panel.y() + panel.height() <= size[1]);
    }
  }

  @Test void threeColumnsShareTheContentWidthWithoutOverlapping() {
    for (float[] size : VIEWPORTS) {
      FlightConsoleLayout.Layout layout = FlightConsoleLayout.fit(size[0], size[1]);
      FlightConsoleLayout.Rectangle navigation = layout.navigation();
      FlightConsoleLayout.Rectangle weapons = layout.weapons();
      FlightConsoleLayout.Rectangle threats = layout.threats();

      assertEquals(FlightConsoleLayout.EDGE_MARGIN, navigation.x(), 0.0001f);
      assertTrue(navigation.width() > 0f && weapons.width() > 0f && threats.width() > 0f);
      assertTrue(navigation.x() + navigation.width() <= weapons.x(),
          "the navigation column never reaches the weapons column");
      assertTrue(weapons.x() + weapons.width() <= threats.x(),
          "the weapons column never reaches the reserved threat column");
      assertEquals(layout.contentRight(), threats.x() + threats.width(), 0.0001f,
          "the reserved threat column ends on the right content edge");
      assertTrue(layout.firstSeparatorX() > navigation.x() + navigation.width());
      assertTrue(layout.secondSeparatorX() > weapons.x() + weapons.width());
      assertTrue(layout.secondSeparatorX() < threats.x());
    }
  }

  @Test void baselinesKeepHeadersRowsAndFooterInsideTheOrderedPanel() {
    for (float[] size : VIEWPORTS) {
      FlightConsoleLayout.Layout layout = FlightConsoleLayout.fit(size[0], size[1]);
      float top = layout.panel().height();

      assertTrue(layout.headerBaseline() < top, "the header stays below the top edge");
      assertTrue(layout.headerSeparatorY() < layout.headerBaseline());
      assertTrue(layout.rowBaseline(0) < layout.headerSeparatorY(),
          "the first reading row starts under the header rule");
      assertTrue(layout.rowBaseline(1) < layout.rowBaseline(0));
      assertTrue(layout.rowBaseline(2) < layout.rowBaseline(1));
      assertTrue(layout.footerSeparatorY() < layout.rowBaseline(2),
          "the footer rule stays below the last reading row");
      assertTrue(layout.footerBaseline() < layout.footerSeparatorY());
      assertTrue(layout.footerBaseline() > 0f, "the help line stays inside the panel");
    }
  }

  @Test void sharesItsHeightAsTheSingleBottomBandReservation() {
    assertEquals(FlightConsoleLayout.HEIGHT, MinimapProjection.BOTTOM_MARGIN, 0.0001f);
    assertEquals(FlightConsoleLayout.HEIGHT, FlightConsoleLayout.bottomBand(), 0.0001f);

    MinimapProjection.Rectangle panel = MinimapProjection.fit(
        new WorldBounds(8192f, 36000f), 800f, 600f).panel();
    assertEquals(FlightConsoleLayout.HEIGHT, panel.y(), 0.0001f,
        "the minimap starts exactly above the console");
  }

  @Test void trimsThePanelHeightInAVeryShortViewportInsteadOfFailing() {
    FlightConsoleLayout.Layout layout = FlightConsoleLayout.fit(320f, 90f);

    assertEquals(90f, layout.panel().height(), 0.0001f);
    assertEquals(90f - 18f, layout.headerBaseline(), 0.0001f);
    assertTrue(layout.navigation().width() > 0f);
  }

  @Test void rejectsOutOfRangeRowsAndUnusableViewports() {
    FlightConsoleLayout.Layout layout = FlightConsoleLayout.fit(800f, 600f);
    assertThrows(IllegalArgumentException.class, () -> layout.rowBaseline(-1));
    assertThrows(IllegalArgumentException.class, () -> layout.rowBaseline(3));

    assertThrows(IllegalArgumentException.class, () -> FlightConsoleLayout.fit(0f, 600f));
    assertThrows(IllegalArgumentException.class, () -> FlightConsoleLayout.fit(800f, 0f));
    assertThrows(IllegalArgumentException.class, () -> FlightConsoleLayout.fit(Float.NaN, 600f));
    assertThrows(IllegalArgumentException.class,
        () -> FlightConsoleLayout.fit(2f * FlightConsoleLayout.EDGE_MARGIN, 600f));
  }
}
