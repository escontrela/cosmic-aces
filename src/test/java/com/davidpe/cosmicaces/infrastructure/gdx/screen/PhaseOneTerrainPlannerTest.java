package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainCatalog.Tile;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainPlanner.Bounds;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainPlanner.Camera;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainPlanner.Plan;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainPlanner.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for the PhaseOne terrain selection policy. No native window, no {@code Texture}: the
 * camera is described by primitive values, exactly what {@code FlightCamera} publishes to LibGDX.
 */
class PhaseOneTerrainPlannerTest {

  private static final float REFERENCE_WIDTH = 1024f;
  private static final float REFERENCE_HEIGHT = 768f;
  private static final float LARGE_WIDTH = 1920f;
  private static final float LARGE_HEIGHT = 1080f;

  @Test
  void uprightCameraSelectsExactlyTheTilesUnderTheView() {
    Plan plan = planAt(4096f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);

    assertEquals(Set.of("8:4", "8:5", "9:4", "9:5"), keys(plan.visible()));
    // Nearest to the camera centre first.
    assertEquals("8:4", key(plan.visible().get(0)));
    assertNonDecreasingDistance(plan.visible(), 4096f, 6000f);
  }

  @Test
  void yawZeroAndNinetyDegreesSwapTheVisibleExtents() {
    Bounds upright = planAt(4096f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT)
        .visibleBounds();
    assertEquals(REFERENCE_WIDTH, upright.maxX() - upright.minX(), 0.001f);
    assertEquals(REFERENCE_HEIGHT, upright.maxY() - upright.minY(), 0.001f);

    // yaw 90° => up = (1, 0), right = (0, -1): the view extents swap.
    Bounds rotated = planAt(4096f, 6000f, 1f, 0f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT)
        .visibleBounds();
    assertEquals(REFERENCE_HEIGHT, rotated.maxX() - rotated.minX(), 0.001f);
    assertEquals(REFERENCE_WIDTH, rotated.maxY() - rotated.minY(), 0.001f);
  }

  @Test
  void diagonalCameraWidensTheConservativeBoundsAndKeepsEveryCornerCovered() {
    Plan upright = planAt(4096f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);
    float diagonal = (float) Math.sin(Math.toRadians(45));
    Camera diagonalCamera =
        new Camera(4096f, 6000f, diagonal, diagonal, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);
    Plan plan = PhaseOneTerrainPlanner.plan(diagonalCamera);

    float uprightWidth = upright.visibleBounds().maxX() - upright.visibleBounds().minX();
    float diagonalWidth = plan.visibleBounds().maxX() - plan.visibleBounds().minX();
    assertTrue(diagonalWidth > uprightWidth, "45° view produces a wider AABB");
    assertTrue(plan.visible().size() > upright.visible().size(),
        "the wider AABB must select more tiles");
    assertEveryVisibleCornerIsSelected(diagonalCamera);
  }

  @Test
  void combatZoomSelectsASupersetOfTheUprightView() {
    Plan zoomOne = planAt(4096f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);
    Plan combatZoom = planAt(4096f, 6000f, 0f, 1f, 1.25f, REFERENCE_WIDTH, REFERENCE_HEIGHT);

    assertTrue(keys(combatZoom.visible()).containsAll(keys(zoomOne.visible())),
        "combat zoom must keep every tile the narrower view already showed");
    assertTrue(
        (combatZoom.visibleBounds().maxX() - combatZoom.visibleBounds().minX())
            > (zoomOne.visibleBounds().maxX() - zoomOne.visibleBounds().minX()));
    assertEveryVisibleCornerIsSelected(
        new Camera(4096f, 6000f, 0f, 1f, 1.25f, REFERENCE_WIDTH, REFERENCE_HEIGHT));
  }

  @Test
  void biggerWindowMakesMoreWorldVisibleWithoutAnyFixedBudget() {
    Plan reference = planAt(3500f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);
    Plan large = planAt(3500f, 6000f, 0f, 1f, 1f, LARGE_WIDTH, LARGE_HEIGHT);

    assertTrue(keys(large.visible()).containsAll(keys(reference.visible())));
    assertTrue(large.visible().size() > reference.visible().size(),
        "a large window exposes more tiles; visible is never capped by a fixed budget");
  }

  @Test
  void everyVisibleCornerFallsInsideTheSelectedSetForManyCameraStates() {
    float[] yaws = {0f, 45f, 90f, 135f, 180f, 270f};
    float[] zooms = {1f, 1.15f, 1.25f};
    float[][] views = {{REFERENCE_WIDTH, REFERENCE_HEIGHT}, {LARGE_WIDTH, LARGE_HEIGHT}};
    float[][] positions = {
        {4096f, 6000f}, {1024f, 768f}, {300f, 300f}, {7800f, 11600f}, {4000f, 100f}};

    for (float yaw : yaws) {
      double radians = Math.toRadians(yaw);
      float upX = (float) Math.sin(radians);
      float upY = (float) Math.cos(radians);
      for (float zoom : zooms) {
        for (float[] view : views) {
          for (float[] position : positions) {
            Camera camera = new Camera(
                position[0], position[1], upX, upY, zoom, view[0], view[1]);
            assertEveryVisibleCornerIsSelected(camera);
          }
        }
      }
    }
  }

  @Test
  void prefetchAddsAtLeastOneRingAroundTheVisibleZone() {
    Plan plan = planAt(4096f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);

    // Visible cols 4-5 rows 8-9; the prefetch ring reaches cols 3-6 and rows 7-10.
    assertEquals(Set.of("8:4", "8:5", "9:4", "9:5"), keys(plan.visible()));
    assertTrue(keys(plan.prefetch()).containsAll(
        Set.of("7:3", "7:6", "8:3", "8:6", "9:3", "9:6", "10:3", "10:6")),
        "the ring must reach one tile beyond the visible zone on every side");
    assertTrue(plan.prefetchBounds().minX() <= plan.visibleBounds().minX() - 1024f);
    assertTrue(plan.prefetchBounds().maxX() >= plan.visibleBounds().maxX() + 1024f);
    assertTrue(plan.prefetchBounds().minY() <= plan.visibleBounds().minY() - 768f);
    assertTrue(plan.prefetchBounds().maxY() >= plan.visibleBounds().maxY() + 768f);

    Set<String> visible = keys(plan.visible());
    Set<String> prefetch = keys(plan.prefetch());
    assertTrue(prefetch.stream().noneMatch(visible::contains),
        "prefetch and visible must be disjoint");
  }

  @Test
  void forwardTilesArePrioritizedOverLateralAndRearOnes() {
    Plan north = planAt(4096f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT,
        0f, 1f);
    assertTrue(north.prefetch().size() > 0);
    assertForwardBeforeRear(north.prefetch(), 4096f, 6000f, 0f, 1f);
    // The first requested tile lies north of the camera.
    assertTrue(tileCenterY(north.prefetch().get(0)) > 6000f);

    Plan south = planAt(4096f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT,
        0f, -1f);
    assertForwardBeforeRear(south.prefetch(), 4096f, 6000f, 0f, -1f);
    assertTrue(tileCenterY(south.prefetch().get(0)) < 6000f);
  }

  @Test
  void reversingTravelReordersPrefetchButKeepsTheSameVisibleSet() {
    Plan forward = planAt(4096f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT, 0f, 1f);
    Plan backward = planAt(4096f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT, 0f, -1f);

    assertEquals(keys(forward.visible()), keys(backward.visible()));
    assertEquals(keys(forward.prefetch()), keys(backward.prefetch()),
        "only the order changes, not the demand");
    assertNotEqualsFirst(forward.prefetch(), backward.prefetch());
  }

  @Test
  void returningToAPreviousCameraYieldsTheSameSelection() {
    Plan before = planAt(4096f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);
    Plan moved = planAt(4096f, 8000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);
    Plan returned = planAt(4096f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);

    assertFalse(keys(moved.visible()).isEmpty());
    assertFalse(keys(before.visible()).equals(keys(moved.visible())),
        "the moved camera must select a different region");
    assertEquals(keys(before.visible()), keys(returned.visible()));
    assertEquals(keys(before.prefetch()), keys(returned.prefetch()));
    assertEquals(before.visibleBounds(), returned.visibleBounds());
  }

  @Test
  void retentionZoneIsStrictlyLargerThanThePrefetchZoneAndNeverEvictsVisibleTiles() {
    Plan plan = planAt(4096f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);

    Set<String> visible = keys(plan.visible());
    Set<String> prefetch = keys(plan.prefetch());
    Set<String> retained = keys(plan.retained());
    assertTrue(retained.containsAll(visible));
    assertTrue(retained.containsAll(prefetch));
    assertTrue(retained.size() > visible.size() + prefetch.size());

    for (Tile tile : plan.visible()) {
      assertTrue(plan.retains(tile), "a visible tile must always be retained");
    }
    // The retention zone reaches two tile rings beyond the visible zone.
    assertTrue(plan.retentionBounds().minX()
        <= plan.visibleBounds().minX() - 2f * PhaseOneTerrainCatalog.TILE_PIXEL_WIDTH);
    assertTrue(plan.retentionBounds().maxX()
        >= plan.visibleBounds().maxX() + 2f * PhaseOneTerrainCatalog.TILE_PIXEL_WIDTH);
    assertTrue(plan.retentionBounds().minY()
        <= plan.visibleBounds().minY() - 2f * PhaseOneTerrainCatalog.TILE_PIXEL_HEIGHT);
    assertTrue(plan.retentionBounds().maxY()
        >= plan.visibleBounds().maxY() + 2f * PhaseOneTerrainCatalog.TILE_PIXEL_HEIGHT);
  }

  @Test
  void worldBoundariesClipTheSelectionWithoutRequestingInvalidIndices() {
    Plan corner = planAt(300f, 300f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);
    assertTrue(corner.visibleBounds().minX() >= 0f);
    assertTrue(corner.visibleBounds().minY() >= 0f);
    assertEquals(Set.of("1:1"), keys(corner.visible()));
    assertValidIndices(corner);

    Plan farCorner = planAt(
        PhaseOneTerrainCatalog.WORLD_WIDTH, PhaseOneTerrainCatalog.WORLD_HEIGHT,
        0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);
    assertTrue(farCorner.visibleBounds().maxX() <= PhaseOneTerrainCatalog.WORLD_WIDTH);
    assertTrue(farCorner.visibleBounds().maxY() <= PhaseOneTerrainCatalog.WORLD_HEIGHT);
    assertEquals(Set.of("16:8"), keys(farCorner.visible()));
    assertValidIndices(farCorner);
  }

  @Test
  void cameraFullyOutsideTheWorldSelectsNothing() {
    Plan plan = planAt(-4000f, -4000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);
    assertTrue(plan.visible().isEmpty());
    assertTrue(plan.prefetch().isEmpty());
    assertTrue(plan.retained().isEmpty());
  }

  @Test
  void invalidCameraParametersAreRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> new Camera(0f, 0f, 0f, 0f, 1f, 1024f, 768f));
    assertThrows(IllegalArgumentException.class,
        () -> new Camera(0f, 0f, 0f, 1f, 0f, 1024f, 768f));
    assertThrows(IllegalArgumentException.class,
        () -> new Camera(0f, 0f, 0f, 1f, 1f, 0f, 768f));
    assertThrows(IllegalArgumentException.class,
        () -> new Camera(Float.NaN, 0f, 0f, 1f, 1f, 1024f, 768f));
  }

  private static void assertEveryVisibleCornerIsSelected(Camera camera) {
    Plan plan = PhaseOneTerrainPlanner.plan(camera);
    for (Point corner : plan.visibleCorners()) {
      if (corner.x() < 0f || corner.x() >= PhaseOneTerrainCatalog.WORLD_WIDTH
          || corner.y() < 0f || corner.y() >= PhaseOneTerrainCatalog.WORLD_HEIGHT) {
        continue;
      }
      Tile tile = PhaseOneTerrainCatalog.tileAt(corner.x(), corner.y()).orElseThrow();
      assertTrue(plan.visible().contains(tile),
          "visible corner " + corner + " must be covered by " + tile);
    }
    assertValidIndices(plan);
  }

  private static void assertValidIndices(Plan plan) {
    for (Tile tile : allTiles(plan)) {
      assertTrue(tile.row() >= 1 && tile.row() <= PhaseOneTerrainCatalog.ROWS,
          "row out of range: " + tile);
      assertTrue(tile.column() >= 1 && tile.column() <= PhaseOneTerrainCatalog.COLUMNS,
          "column out of range: " + tile);
    }
  }

  private static List<Tile> allTiles(Plan plan) {
    List<Tile> all = new ArrayList<>();
    all.addAll(plan.visible());
    all.addAll(plan.prefetch());
    all.addAll(plan.retained());
    return all;
  }

  private static void assertForwardBeforeRear(
      List<Tile> prefetch, float cameraX, float cameraY, float travelX, float travelY) {
    boolean seenRear = false;
    for (Tile tile : prefetch) {
      float centerX = tile.worldX() + tile.worldWidth() / 2f;
      float centerY = tile.worldY() + tile.worldHeight() / 2f;
      boolean ahead = (centerX - cameraX) * travelX + (centerY - cameraY) * travelY > 0f;
      if (ahead) {
        assertFalse(seenRear, "a forward tile appeared after a lateral/rear one");
      } else {
        seenRear = true;
      }
    }
  }

  private static void assertNonDecreasingDistance(
      List<Tile> tiles, float cameraX, float cameraY) {
    double previous = -1d;
    for (Tile tile : tiles) {
      float dx = tile.worldX() + tile.worldWidth() / 2f - cameraX;
      float dy = tile.worldY() + tile.worldHeight() / 2f - cameraY;
      double distance = dx * dx + dy * dy;
      assertTrue(distance >= previous, "visible tiles must be nearest-first: " + tiles);
      previous = distance;
    }
  }

  private static void assertNotEqualsFirst(List<Tile> first, List<Tile> second) {
    assertFalse(first.isEmpty() || second.isEmpty());
    assertFalse(key(first.get(0)).equals(key(second.get(0))),
        "the travel direction must change which tile is requested first");
  }

  private static Plan planAt(float x, float y, float upX, float upY, float zoom,
      float viewWidth, float viewHeight) {
    return PhaseOneTerrainPlanner.plan(new Camera(x, y, upX, upY, zoom, viewWidth, viewHeight));
  }

  private static Plan planAt(float x, float y, float upX, float upY, float zoom,
      float viewWidth, float viewHeight, float travelX, float travelY) {
    return PhaseOneTerrainPlanner.plan(
        new Camera(x, y, upX, upY, zoom, viewWidth, viewHeight), travelX, travelY);
  }

  private static float tileCenterY(Tile tile) {
    return tile.worldY() + tile.worldHeight() / 2f;
  }

  private static Set<String> keys(List<Tile> tiles) {
    return tiles.stream().map(PhaseOneTerrainPlannerTest::key).collect(Collectors.toSet());
  }

  private static String key(Tile tile) {
    return tile.row() + ":" + tile.column();
  }
}
