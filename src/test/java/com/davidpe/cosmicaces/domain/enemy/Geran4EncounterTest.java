package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.enemy.Geran4Encounter.ViewportFrame;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Deterministic coverage of the Geran-4 calendar and viewport-relative placement: first pulse at 10
 * active seconds, next pulses every 10 s without skipped pulses, no-dogfight spawns fully visible
 * near an edge, dogfight spawns fully outside the real rotated/zoomed viewport, world bounds and
 * Astra keep-out are respected, and retired/destroyed UAVs never come back.
 */
class Geran4EncounterTest {

  private static final WorldBounds WIDE = new WorldBounds(8192f, 12000f);
  private static final ViewportFrame VIEW = new ViewportFrame(4000f, 6000f, 0f, 1f, 1f,
      800f, 600f);
  private static final float ASTRA_X = 4000f;
  private static final float ASTRA_Y = 5400f;
  private static final float ASTRA_BODY_RADIUS = 50f;
  private static final float HALF_DIAGONAL = 36f * (float) Math.sqrt(2d);
  private static final float EPSILON = 0.01f;

  @Test
  void firstUavAppearsAtTenActiveSecondsAndTheNextPulseFollowsTenSecondsLater() {
    Geran4Encounter encounter = new Geran4Encounter(new ScriptedRandom(0f, 0.5f));
    encounter.advance(9.9f, WIDE, VIEW, ASTRA_X, ASTRA_Y, ASTRA_BODY_RADIUS, false);
    assertTrue(encounter.activeDrones().isEmpty(), "no UAV before the first 10 s pulse");
    assertEquals(0.1f, encounter.secondsUntilNextSpawn(), EPSILON);

    encounter.advance(0.2f, WIDE, VIEW, ASTRA_X, ASTRA_Y, ASTRA_BODY_RADIUS, false);
    assertEquals(1, encounter.activeDrones().size());
    assertTrue(encounter.secondsUntilNextSpawn() > 9f,
        "the next pulse is a full 10 s away from the first one");

    encounter.advance(10f, WIDE, VIEW, ASTRA_X, ASTRA_Y, ASTRA_BODY_RADIUS, false);
    assertEquals(2, encounter.activeDrones().size(), "a second UAV appears 10 s after the first");
  }

  @Test
  void largeDeltaResolvesEveryDuePulseWithoutDroppingOrBurstingOnePoint() {
    // Lower camera, side 3 (bottom edge) travelling up: after 30 s the three UAVs stay inside the
    // 12000-high world, so the count reflects the pulses at 10, 20 and 30 s.
    ViewportFrame lower = new ViewportFrame(4000f, 4000f, 0f, 1f, 1f, 800f, 600f);
    Geran4Encounter encounter = new Geran4Encounter(
        new ScriptedRandom(0.8f, 0.5f, 0.8f, 0.5f, 0.8f, 0.5f));
    encounter.advance(30f, WIDE, lower, 4000f, 3400f, ASTRA_BODY_RADIUS, false);
    List<Geran4Uav> drones = encounter.activeDrones();
    assertEquals(3, drones.size(), "every due pulse must be resolved, none skipped");
    assertEquals(10f, encounter.secondsUntilNextSpawn(), EPSILON);

    assertNotEquals(drones.get(0).id(), drones.get(1).id());
    assertNotEquals(drones.get(1).id(), drones.get(2).id());
    assertTrue(drones.get(0).centerY() > drones.get(1).centerY(),
        "each pulse advances only after its own spawn instant, keeping positions staggered");
    assertTrue(drones.get(1).centerY() > drones.get(2).centerY());
  }

  @Test
  void noDogfightSpawnsTheWholeSpriteNearTheVisibleEdgeAndCrossesIt() {
    Geran4Encounter encounter = new Geran4Encounter(new ScriptedRandom(0f, 0.5f));
    encounter.advance(10f, WIDE, VIEW, ASTRA_X, ASTRA_Y, ASTRA_BODY_RADIUS, false);
    Geran4Uav drone = encounter.activeDrones().get(0);

    assertFullyInside(drone, VIEW);
    float right = VIEW.localRight(drone.centerX(), drone.centerY());
    assertTrue(right > 0f && right < VIEW.halfWidth(),
        "no-dogfight UAV must appear near the right visible edge, got right=" + right);
    assertEquals(-1f, drone.directionX(), EPSILON);
    assertEquals(0f, drone.directionY(), EPSILON);

    encounter.advance(2f, WIDE, VIEW, ASTRA_X, ASTRA_Y, ASTRA_BODY_RADIUS, false);
    assertTrue(VIEW.localRight(drone.centerX(), drone.centerY()) < right,
        "the fixed heading must cross the visible zone in a straight line");
  }

  @Test
  void dogfightSpawnsCompletelyOutsideTheViewportAndEntersAfterwards() {
    Geran4Encounter encounter = new Geran4Encounter(new ScriptedRandom(0f, 0.5f));
    encounter.advance(10f, WIDE, VIEW, ASTRA_X, ASTRA_Y, ASTRA_BODY_RADIUS, true);
    assertEquals(1, encounter.activeDrones().size(),
        "the calendar keeps spawning while Astra is fighting a Vesper Raider");
    Geran4Uav drone = encounter.activeDrones().get(0);

    assertFullyOutside(drone, VIEW);
    float right = VIEW.localRight(drone.centerX(), drone.centerY());
    assertTrue(right > VIEW.halfWidth() + HALF_DIAGONAL,
        "a dogfight spawn starts beyond the visible edge plus the whole sprite");

    encounter.advance(3f, WIDE, VIEW, ASTRA_X, ASTRA_Y, ASTRA_BODY_RADIUS, true);
    assertFullyInside(drone, VIEW);
  }

  @Test
  void rotatedAndZoomedCameraUsesTheRealViewportFrame() {
    float upX = 0.5f;
    float upY = (float) Math.cos(Math.toRadians(30d));
    ViewportFrame rotated = new ViewportFrame(4000f, 6000f, upX, upY, 1.25f, 800f, 600f);
    Geran4Encounter encounter = new Geran4Encounter(new ScriptedRandom(0f, 0.5f));
    encounter.advance(10f, WIDE, rotated, ASTRA_X, ASTRA_Y, ASTRA_BODY_RADIUS, false);
    Geran4Uav drone = encounter.activeDrones().get(0);

    assertFullyInside(drone, rotated);
    assertEquals(rotated.halfWidth() - HALF_DIAGONAL - Geran4Encounter.ENTRY_INSET,
        rotated.localRight(drone.centerX(), drone.centerY()), EPSILON);
    // The fixed heading points inward along the rotated camera's right axis.
    assertEquals(-upY, drone.directionX(), EPSILON);
    assertEquals(upX, drone.directionY(), EPSILON);
  }

  @Test
  void candidatesThatWouldLeaveTheWorldAreRejectedInFavorOfTheNextSide() {
    // Camera hugging the left world edge: the camera-left side has no room, so the next side wins.
    ViewportFrame edge = new ViewportFrame(100f, 6000f, 0f, 1f, 1f, 800f, 600f);
    Geran4Encounter encounter = new Geran4Encounter(new ScriptedRandom(0.3f, 0.5f, 0.5f));
    encounter.advance(10f, WIDE, edge, 100f, 5400f, ASTRA_BODY_RADIUS, false);
    assertEquals(1, encounter.activeDrones().size());
    Geran4Uav drone = encounter.activeDrones().get(0);
    assertTrue(drone.x() >= 0f, "the spawned box must stay inside the world");
    assertTrue(drone.x() + drone.drawWidth() <= WIDE.width());
    assertFullyInside(drone, edge);
  }

  @Test
  void aSpawnNeverOverlapsAstra() {
    // The plain right-side spawn would land exactly on Astra; the encounter shifts it clear.
    Geran4Encounter encounter = new Geran4Encounter(new ScriptedRandom(0f, 0.5f));
    encounter.advance(10f, WIDE, VIEW, 4339.1f, 6000f, ASTRA_BODY_RADIUS, false);
    Geran4Uav drone = encounter.activeDrones().get(0);
    float distance = (float) Math.hypot(drone.centerX() - 4339.1f, drone.centerY() - 6000f);
    assertTrue(distance >= ASTRA_BODY_RADIUS + HALF_DIAGONAL,
        "UAV must be born clear of Astra, got distance=" + distance);
    assertFullyInside(drone, VIEW);
  }

  @Test
  void uavRetiresWithoutEventOnceItsWholeBoxLeavesTheWorld() {
    WorldBounds small = new WorldBounds(2000f, 2000f);
    ViewportFrame view = new ViewportFrame(1000f, 1000f, 0f, 1f, 1f, 800f, 600f);
    Geran4Encounter encounter = new Geran4Encounter(new ScriptedRandom(0f, 0.5f));
    encounter.advance(10f, small, view, 1000f, 900f, ASTRA_BODY_RADIUS, false);
    assertEquals(1, encounter.activeDrones().size());
    long firstId = encounter.activeDrones().get(0).id();

    encounter.advance(20f, small, view, 1000f, 900f, ASTRA_BODY_RADIUS, false);
    assertTrue(encounter.activeDrones().stream().noneMatch(drone -> drone.id() == firstId),
        "the first UAV must be retired after crossing the world");
  }

  @Test
  void markDestroyedIsIdempotentAndNeverRespawnsTheUav() {
    Geran4Encounter encounter = new Geran4Encounter(new ScriptedRandom(0f, 0.5f));
    encounter.advance(10f, WIDE, VIEW, ASTRA_X, ASTRA_Y, ASTRA_BODY_RADIUS, false);
    long firstId = encounter.activeDrones().get(0).id();
    encounter.markDestroyed(firstId);
    encounter.markDestroyed(firstId);
    encounter.markDestroyed(999L);
    assertTrue(encounter.activeDrones().isEmpty());

    encounter.advance(10f, WIDE, VIEW, ASTRA_X, ASTRA_Y, ASTRA_BODY_RADIUS, false);
    assertEquals(1, encounter.activeDrones().size());
    assertNotEquals(firstId, encounter.activeDrones().get(0).id());
  }

  @Test
  void nonPositiveOrInvalidDeltasNeverAdvanceTheCalendar() {
    Geran4Encounter encounter = new Geran4Encounter(new ScriptedRandom(0f, 0.5f));
    encounter.advance(0f, WIDE, VIEW, ASTRA_X, ASTRA_Y, ASTRA_BODY_RADIUS, false);
    encounter.advance(-5f, WIDE, VIEW, ASTRA_X, ASTRA_Y, ASTRA_BODY_RADIUS, false);
    encounter.advance(Float.NaN, WIDE, VIEW, ASTRA_X, ASTRA_Y, ASTRA_BODY_RADIUS, false);
    assertTrue(encounter.activeDrones().isEmpty());
    assertEquals(10f, encounter.secondsUntilNextSpawn(), EPSILON);
  }

  @Test
  void viewportFrameNormalizesTheCameraUpVectorAndRoundTripsLocalCoordinates() {
    ViewportFrame frame = new ViewportFrame(10f, 20f, 0f, 2f, 1f, 800f, 600f);
    assertEquals(10f, frame.localUp(10f, 30f), EPSILON);
    assertEquals(5f, frame.localRight(15f, 20f), EPSILON);
    assertEquals(15f, frame.worldX(5f, 0f), EPSILON);
    assertEquals(30f, frame.worldY(0f, 10f), EPSILON);
  }

  private static void assertFullyInside(Geran4Uav drone, ViewportFrame viewport) {
    float right = Math.abs(viewport.localRight(drone.centerX(), drone.centerY()));
    float up = Math.abs(viewport.localUp(drone.centerX(), drone.centerY()));
    assertTrue(right + HALF_DIAGONAL <= viewport.halfWidth(),
        "whole UAV sprite must be inside the visible width, right=" + right);
    assertTrue(up + HALF_DIAGONAL <= viewport.halfHeight(),
        "whole UAV sprite must be inside the visible height, up=" + up);
  }

  private static void assertFullyOutside(Geran4Uav drone, ViewportFrame viewport) {
    float right = Math.abs(viewport.localRight(drone.centerX(), drone.centerY()));
    float up = Math.abs(viewport.localUp(drone.centerX(), drone.centerY()));
    assertTrue(right > viewport.halfWidth() + HALF_DIAGONAL
            || up > viewport.halfHeight() + HALF_DIAGONAL,
        "whole UAV sprite must be outside the visible polygon, right=" + right + " up=" + up);
  }

  /** Deterministic unit-random source feeding a scripted queue; the last value repeats. */
  private static final class ScriptedRandom implements UnitRandom {

    private final float[] values;
    private int index;

    ScriptedRandom(float... values) {
      this.values = values;
    }

    @Override
    public float nextUnit() {
      if (values.length == 0) {
        return 0f;
      }
      float value = values[Math.min(index, values.length - 1)];
      index++;
      return value;
    }
  }
}
