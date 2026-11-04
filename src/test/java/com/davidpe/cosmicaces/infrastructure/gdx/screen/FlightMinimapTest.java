package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.scenery.SceneryPlacement;
import com.davidpe.cosmicaces.domain.scenery.WorldRect;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissile;
import com.davidpe.cosmicaces.domain.weapon.MissileConfig;
import com.davidpe.cosmicaces.domain.weapon.MissileTarget;
import java.util.List;
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

  @Test void activeMissileTrackingShowsMapAndRestoresThePlayersVisibilityPreference() {
    FlightMinimap minimap = new FlightMinimap();

    minimap.setMissileTracking(true);
    assertTrue(minimap.isVisible());
    minimap.toggle();
    minimap.setMissileTracking(false);

    assertTrue(minimap.isVisible(), "M pressed during flight becomes the restored preference");
    minimap.toggle();
    assertFalse(minimap.isVisible());
  }

  @Test void missileMarkerFollowsItsCurrentPositionAndOmitsRetiredMissiles() {
    MinimapProjection projection = MinimapProjection.fit(
        new WorldBounds(8192f, 12000f), 1024f, 768f);
    MissileTarget.Reference targetReference =
        new MissileTarget.Reference(MissileTarget.Kind.VESPER, 0, 0);
    MissileTarget target = new MissileTarget(targetReference, 4000f, 8000f, 0f, 0f, 20f);
    GuidedMissile missile = new GuidedMissile(GuidedMissile.Owner.ASTRA, targetReference,
        MissileConfig.DEFAULTS, 2000f, 3000f, 0f, 0f, 300f);

    FlightMinimap.MissileMarker before =
        FlightMinimap.missileMarkers(projection, List.of(missile)).get(0);
    missile.advance(0.25f, target);
    FlightMinimap.MissileMarker after =
        FlightMinimap.missileMarkers(projection, List.of(missile)).get(0);

    assertEquals(GuidedMissile.Owner.ASTRA, after.owner());
    assertNotEquals(before.position(), after.position(), "marker follows the moving missile");
    assertEquals(projection.projectMarker(missile.x(), missile.y(),
        FlightMinimap.markerMargin(projection, 5f)), after.position());

    missile.retireAfterMission();
    assertTrue(FlightMinimap.missileMarkers(projection, List.of(missile)).isEmpty());
  }

  @Test void aimCursorAndExactSelectedDroneHaveSeparateProjectedMarkers() {
    MinimapProjection projection = MinimapProjection.fit(
        new WorldBounds(8192f, 12000f), 1024f, 768f);
    MissileTarget target = new MissileTarget(
        new MissileTarget.Reference(MissileTarget.Kind.GHOSTWING, 17, 0),
        6200f, 8400f, 0f, 0f, 28f);

    FlightMinimap.AimMarkers markers = FlightMinimap.aimMarkers(
        projection, 5900f, 8100f, target);

    assertEquals(projection.projectMarker(5900f, 8100f,
        FlightMinimap.markerMargin(projection, 7f)), markers.cursor());
    assertEquals(projection.projectMarker(6200f, 8400f,
        FlightMinimap.markerMargin(projection, 10f)), markers.selectedTarget());
    assertNotEquals(markers.cursor(), markers.selectedTarget(),
        "a drone lock must not be drawn at the free cursor position");
    assertEquals(null, FlightMinimap.aimMarkers(projection, 5900f, 8100f, null).selectedTarget());
  }

  @Test void translucentPanelAndMapStillLeaveTheScenarioVisible() {
    assertTrue(FlightMinimap.PANEL_ALPHA > 0f && FlightMinimap.PANEL_ALPHA < 1f,
        "the panel background must be translucent");
    assertTrue(FlightMinimap.MAP_ALPHA > 0f && FlightMinimap.MAP_ALPHA < 1f,
        "the map fill must be translucent");
    assertTrue(FlightMinimap.PANEL_ALPHA > FlightMinimap.MAP_ALPHA,
        "the panel frame stays darker than the map interior");
    assertTrue(FlightMinimap.PANEL_ALPHA >= 0.4f && FlightMinimap.PANEL_ALPHA <= 0.7f,
        "the panel remains dark enough to read the markers");
  }

  @Test void markerMarginsGrowWithTheMapAndAlwaysStayInsideIt() {
    MinimapProjection projection = MinimapProjection.fit(
        new WorldBounds(8192f, 36000f), 800f, 600f);
    MinimapProjection.Rectangle map = projection.map();

    float margin = FlightMinimap.markerMargin(projection, 5f);

    assertTrue(margin > 5f, "the larger map enlarges marker clamps");
    assertEquals(5f * projection.visualScale(), margin, 0.0001f);
    assertTrue(margin * 2f < Math.min(map.width(), map.height()),
        "a marker clamped by this margin still fits inside the map");
  }

  @Test void markerMarginsRejectInvalidProjections() {
    assertThrows(IllegalArgumentException.class,
        () -> FlightMinimap.markerMargin(null, 5f));
    MinimapProjection projection = MinimapProjection.fit(
        new WorldBounds(8192f, 36000f), 800f, 600f);
    assertThrows(IllegalArgumentException.class,
        () -> FlightMinimap.markerMargin(projection, -1f));
  }

  @Test
  void villageMarkersProjectEachApprovedCellAndKeepTheirSouthToNorthOrder() {
    WorldBounds extended = new WorldBounds(8192f, 36000f);
    MinimapProjection projection = MinimapProjection.fit(extended, 1024f, 768f);
    MinimapProjection.Rectangle map = projection.map();
    List<SceneryPlacement> villages = List.of(
        village("a", 0, 2304), village("b", 1024, 2304),
        village("c", 0, 4608), village("d", 1024, 4608),
        village("e", 1024, 34560));

    List<MinimapProjection.Point> markers = FlightMinimap.villageMarkers(projection, villages);

    assertEquals(5, markers.size());
    for (MinimapProjection.Point marker : markers) {
      assertTrue(marker.x() >= map.x() && marker.x() <= map.x() + map.width());
      assertTrue(marker.y() >= map.y() && marker.y() <= map.y() + map.height());
    }
    assertTrue(markers.get(0).y() < markers.get(2).y());
    assertTrue(markers.get(4).y() > markers.get(3).y());
  }

  private static SceneryPlacement village(String id, double x, double y) {
    WorldRect bounds = new WorldRect(x, y, 1024, 768);
    return new SceneryPlacement(id, "map-" + id, SceneryPlacement.Anchor.BOTTOM_LEFT,
        bounds.x(), bounds.y(), bounds, 50, 48);
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
