package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.weapon.MissileTarget;
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

  @Test void targetingForcesMapVisibleAndRestoresTheCurrentPlayerPreference() {
    FlightMinimap minimap = new FlightMinimap();
    minimap.setTargeting(true);
    assertTrue(minimap.isVisible());
    minimap.toggle();
    assertTrue(minimap.isVisible(), "M cannot hide the only targeting map");
    minimap.setTargeting(false);
    assertTrue(minimap.isVisible(), "M pressed during aiming becomes the restored preference");
    minimap.toggle();
    assertFalse(minimap.isVisible());

    minimap.toggle();
    minimap.setTargeting(true);
    minimap.setTargeting(false);
    assertTrue(minimap.isVisible(), "a map opened before aiming remains open after ESC");
  }

  @Test void aimCursorAndExactSelectedDroneHaveSeparateProjectedMarkers() {
    MinimapProjection projection = MinimapProjection.fit(
        new WorldBounds(8192f, 12000f), 1024f, 768f);
    MissileTarget target = new MissileTarget(
        new MissileTarget.Reference(MissileTarget.Kind.GHOSTWING, 17, 0),
        6200f, 8400f, 0f, 0f, 28f);

    FlightMinimap.AimMarkers markers = FlightMinimap.aimMarkers(
        projection, 5900f, 8100f, target);

    assertEquals(projection.projectMarker(5900f, 8100f, 7f), markers.cursor());
    assertEquals(projection.projectMarker(6200f, 8400f, 8f), markers.selectedTarget());
    assertNotEquals(markers.cursor(), markers.selectedTarget(),
        "a drone lock must not be drawn at the free cursor position");
    assertEquals(null, FlightMinimap.aimMarkers(projection, 5900f, 8100f, null).selectedTarget());
  }

  @Test
  void bomberMarkerAlternatesEveryThreeTenthsOfASecondAfterTakeoffClock() {
    assertTrue(FlightMinimap.bomberBlinkVisible(0f));
    assertTrue(FlightMinimap.bomberBlinkVisible(0.29f));
    assertFalse(FlightMinimap.bomberBlinkVisible(0.3f));
    assertFalse(FlightMinimap.bomberBlinkVisible(0.59f));
    assertTrue(FlightMinimap.bomberBlinkVisible(0.6f));
    assertFalse(FlightMinimap.bomberBlinkVisible(-0.01f));
    assertFalse(FlightMinimap.bomberBlinkVisible(Float.NaN));
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
