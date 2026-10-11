package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlightMinimapTest {
  @Test void startsHiddenAndTogglesVisibilityOncePerCall() {
    FlightMinimap minimap = new FlightMinimap();

    assertFalse(minimap.isVisible());
    minimap.toggle();
    assertTrue(minimap.isVisible());
    minimap.toggle();
    assertFalse(minimap.isVisible());
  }

  @Test
  void ghostwingGroupMarkerIsACloserDiamondAroundTheRepresentativePoint() {
    MinimapProjection.Point center = new MinimapProjection.Point(40f, 50f);

    float[] vertices = FlightMinimap.diamondVertices(center, FlightMinimap.GROUP_MARKER_HALF);

    assertEquals(8, vertices.length, "four two-dimensional vertices");
    assertEquals(40f, vertices[0], 0.0001f);
    assertEquals(50f + FlightMinimap.GROUP_MARKER_HALF, vertices[1], 0.0001f, "top vertex");
    assertEquals(40f + FlightMinimap.GROUP_MARKER_HALF, vertices[2], 0.0001f, "right vertex");
    assertEquals(50f, vertices[3], 0.0001f);
    assertEquals(40f, vertices[4], 0.0001f);
    assertEquals(50f - FlightMinimap.GROUP_MARKER_HALF, vertices[5], 0.0001f, "bottom vertex");
    assertEquals(40f - FlightMinimap.GROUP_MARKER_HALF, vertices[6], 0.0001f, "left vertex");
    assertEquals(50f, vertices[7], 0.0001f);
    // The diamond is a single closed marker: no two vertices coincide.
    assertTrue(vertices[0] != vertices[2] && vertices[1] != vertices[3], "top != right");
    assertTrue(vertices[2] != vertices[4] && vertices[3] != vertices[5], "right != bottom");
    assertTrue(vertices[4] != vertices[6] && vertices[5] != vertices[7], "bottom != left");
    assertTrue(vertices[6] != vertices[0] && vertices[7] != vertices[1], "left != top");
  }

  @Test
  void ghostwingGroupMarkerRejectsMissingOrInvalidCentres() {
    assertThrows(IllegalArgumentException.class,
        () -> FlightMinimap.diamondVertices(null, FlightMinimap.GROUP_MARKER_HALF));
    assertThrows(IllegalArgumentException.class,
        () -> FlightMinimap.diamondVertices(new MinimapProjection.Point(Float.NaN, 0f),
            FlightMinimap.GROUP_MARKER_HALF));
    assertThrows(IllegalArgumentException.class,
        () -> FlightMinimap.diamondVertices(new MinimapProjection.Point(0f, 0f), 0f));
    assertThrows(IllegalArgumentException.class,
        () -> FlightMinimap.diamondVertices(new MinimapProjection.Point(0f, 0f), -1f));
  }
}
