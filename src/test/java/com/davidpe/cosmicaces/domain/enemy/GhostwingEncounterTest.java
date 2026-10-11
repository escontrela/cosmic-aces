package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import java.util.List;
import org.junit.jupiter.api.Test;

class GhostwingEncounterTest {

  private static final WorldBounds WIDE = new WorldBounds(8192f, 12000f);
  private static final WorldBounds SMALL = new WorldBounds(2000f, 2000f);
  private static final float EPSILON = 0.001f;

  @Test
  void firstGroupSpawnsAfterTheMinimumIntervalWithTheChosenSize() {
    GhostwingEncounter encounter = new GhostwingEncounter(
        new ScriptedRandom(0f, 0f, 0f, 0f, 0.5f, 0f, 0f)); // interval 20 s, EAST pair
    assertTrue(encounter.groups().isEmpty());
    encounter.advance(19.5f, WIDE);
    assertTrue(encounter.groups().isEmpty(), "no group before the 20 s minimum");
    encounter.advance(0.5f, WIDE);
    assertEquals(1, encounter.groups().size());
    GhostwingEncounter.GhostwingGroup group = encounter.groups().get(0);
    assertEquals(2, group.memberCount());
    assertEquals(GhostwingDrone.Direction.EAST, group.direction());
  }

  @Test
  void spawnIntervalUsesTheTwentyToThirtySecondRange() {
    GhostwingEncounter encounter = new GhostwingEncounter(
        new ScriptedRandom(0.999f, 0f, 0f, 0f, 0.5f, 0f, 0f)); // interval 29.99 s
    encounter.advance(29.98f, WIDE);
    assertTrue(encounter.groups().isEmpty(), "still waiting inside the interval");
    encounter.advance(0.02f, WIDE);
    assertEquals(1, encounter.groups().size());
  }

  @Test
  void groupSizeIncludesTheTenDroneMaximum() {
    GhostwingEncounter encounter = new GhostwingEncounter(new ScriptedRandom(
        0f, 0f, 0f, 0.99f, 0.5f,
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)); // ten zero jitters
    encounter.advance(20f, WIDE);
    assertEquals(1, encounter.groups().size());
    assertEquals(10, encounter.groups().get(0).memberCount());
  }

  @Test
  void allFourCardinalDirectionsCrossInAStraightLine() {
    assertStraightCrossing(new ScriptedRandom(0f, 0f, 0f, 0f, 0.5f, 0f, 0f),
        GhostwingDrone.Direction.EAST, 900f, 0f);
    assertStraightCrossing(new ScriptedRandom(0f, 0f, 0.6f, 0f, 0.5f, 0f, 0f),
        GhostwingDrone.Direction.WEST, -900f, 0f);
    assertStraightCrossing(new ScriptedRandom(0f, 0.6f, 0f, 0f, 0.5f, 0f, 0f),
        GhostwingDrone.Direction.NORTH, 0f, 900f);
    assertStraightCrossing(new ScriptedRandom(0f, 0.6f, 0.6f, 0f, 0.5f, 0f, 0f),
        GhostwingDrone.Direction.SOUTH, 0f, -900f);
  }

  @Test
  void groupEntersFullyOutsideItsWorldEdge() {
    GhostwingEncounter encounter = new GhostwingEncounter(
        new ScriptedRandom(0f, 0f, 0f, 0f, 0.5f, 0f, 0f)); // EAST pair, interval 20 s
    while (encounter.groups().isEmpty()) {
      encounter.advance(0.01f, WIDE);
    }
    for (GhostwingDrone drone : encounter.activeDrones()) {
      assertTrue(drone.x() < 0f && drone.x() > -80f,
          "EAST drones must emerge from outside the left edge, got x=" + drone.x());
      assertTrue(drone.centerY() > 0f && drone.centerY() < WIDE.height());
    }
  }

  @Test
  void activeGroupCapPostponesDueSpawnsWithoutBursting() {
    GhostwingEncounter encounter = new GhostwingEncounter(new ScriptedRandom(
        0f,
        0f, 0f, 0f, 0.5f, 0f, 0f,
        0f,
        0f, 0f, 0f, 0.5f, 0f, 0f,
        0f,
        0f, 0f, 0f, 0.5f, 0f, 0f));
    encounter.advance(20f, WIDE);
    assertEquals(1, encounter.groups().size());
    encounter.advance(20f, WIDE);
    assertEquals(2, encounter.groups().size());
    encounter.advance(20f, WIDE);
    assertEquals(3, encounter.groups().size());
    // A fourth spawn is due while the cap is full: postponed, never queued.
    encounter.advance(20f, WIDE);
    assertEquals(3, encounter.groups().size());

    GhostwingEncounter.GhostwingGroup first = encounter.groups().get(0);
    List<GhostwingDrone> doomed = List.copyOf(first.members()); // snapshot: live list shrinks
    for (GhostwingDrone drone : doomed) {
      encounter.markDestroyed(drone.id());
    }
    assertEquals(2, encounter.groups().size());
    encounter.advance(0.1f, WIDE);
    assertEquals(3, encounter.groups().size(),
        "freeing one slot spawns at most one group, never a burst");
  }

  @Test
  void markDestroyedRemovesOnlyThatDroneAndRetiresEmptyGroups() {
    GhostwingEncounter encounter = new GhostwingEncounter(
        new ScriptedRandom(0f, 0f, 0f, 0f, 0.5f, 0f, 0f));
    encounter.advance(20f, WIDE);
    assertEquals(2, encounter.activeDrones().size());
    long firstId = encounter.activeDrones().get(0).id();
    long secondId = encounter.activeDrones().get(1).id();

    encounter.markDestroyed(firstId);
    assertEquals(1, encounter.groups().size());
    assertEquals(1, encounter.activeDrones().size());
    assertEquals(secondId, encounter.activeDrones().get(0).id());

    // The surviving drone keeps flying; the destroyed one is inert and never resurfaces.
    float survivorX = encounter.activeDrones().get(0).x();
    encounter.advance(1f, WIDE);
    assertEquals(survivorX + GhostwingEncounter.DRONE_SPEED,
        encounter.activeDrones().get(0).x(), EPSILON);
    assertEquals(1, encounter.activeDrones().size());

    encounter.markDestroyed(firstId); // idempotent for an already-destroyed drone
    encounter.markDestroyed(999L); // unknown id: no-op

    encounter.markDestroyed(secondId);
    assertTrue(encounter.groups().isEmpty(), "a group with no members must disappear");
    assertTrue(encounter.activeDrones().isEmpty());
  }

  @Test
  void groupRetiresOnceEveryMemberPassesTheOppositeEdge() {
    // Min interval 20 s; the EAST pair needs (2000 + 72) / 90 = 23.02 s to cross the small world.
    GhostwingEncounter encounter = new GhostwingEncounter(
        new ScriptedRandom(0f, 0f, 0f, 0f, 0.5f, 0f, 0f));
    encounter.advance(22.9f, SMALL); // x = -72 + 2061 = 1989 < 2000: still crossing
    assertEquals(1, encounter.groups().size());
    assertEquals(2, encounter.activeDrones().size());
    encounter.advance(0.2f, SMALL); // x = 2007 >= 2000: whole box past the edge
    assertTrue(encounter.groups().isEmpty(), "group must retire once every member left the world");
  }

  @Test
  void formationIsStaggeredNonOverlappingAndInsideTheWorld() {
    GhostwingEncounter encounter = new GhostwingEncounter(new ScriptedRandom(
        0f, 0f, 0f, 0.99f, 0.5f,
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f));
    encounter.advance(20f, WIDE);
    List<GhostwingDrone> drones = encounter.activeDrones();
    assertEquals(10, drones.size());
    for (int i = 0; i < drones.size(); i++) {
      for (int j = i + 1; j < drones.size(); j++) {
        assertTrue(Math.hypot(drones.get(i).centerX() - drones.get(j).centerX(),
            drones.get(i).centerY() - drones.get(j).centerY()) > GhostwingEncounter.DRONE_WIDTH,
            "member boxes must never overlap");
      }
      assertTrue(drones.get(i).centerY() > 0f && drones.get(i).centerY() < WIDE.height(),
          "formation must stay inside the world on the perpendicular axis");
    }
  }

  @Test
  void representativePointIsTheCentroidOfLiveMembers() {
    GhostwingEncounter encounter = new GhostwingEncounter(
        new ScriptedRandom(0f, 0f, 0f, 0f, 0.5f, 0f, 0f)); // EAST pair, interval 20 s
    encounter.advance(20f, WIDE);
    GhostwingEncounter.GhostwingGroup group = encounter.groups().get(0);
    GhostwingEncounter.Point point = GhostwingEncounter.representativePoint(group);
    float expectedCenterX = (group.members().get(0).centerX() + group.members().get(1).centerX())
        / 2f;
    assertEquals(expectedCenterX, point.x(), EPSILON);
    // With one member destroyed the marker stays the centroid of the remaining drone.
    encounter.markDestroyed(group.members().get(0).id());
    GhostwingEncounter.Point after = GhostwingEncounter.representativePoint(
        encounter.groups().get(0));
    assertEquals(encounter.groups().get(0).members().get(0).centerX(), after.x(), EPSILON);
    assertEquals(encounter.groups().get(0).members().get(0).centerY(), after.y(), EPSILON);
  }

  @Test
  void splittingTheAdvanceDeltaDoesNotChangeTrajectoryOrCalendar() {
    // Both partitionings reach the 20 s spawn boundary on a frame edge (0.2 and 0.1 both divide
    // 20), so the spawn instant and the consumed random values are identical; afterwards positions
    // may differ by at most the travel of a single coarse frame (the calendar granularity).
    GhostwingEncounter coarse = new GhostwingEncounter(new ScriptedRandom(
        0f, 0f, 0f, 0f, 0.5f, 0f, 0f, 0.999f)); // 20 s interval then ~30 s
    GhostwingEncounter fine = new GhostwingEncounter(new ScriptedRandom(
        0f, 0f, 0f, 0f, 0.5f, 0f, 0f, 0.999f));

    for (int i = 0; i < 100; i++) {
      coarse.advance(0.2f, WIDE);
    }
    for (int i = 0; i < 200; i++) {
      fine.advance(0.1f, WIDE);
    }
    assertEquals(1, coarse.groups().size(), "coarse calendar spawns the group at 20 s");
    assertEquals(1, fine.groups().size(), "fine calendar spawns the group at 20 s");
    assertEquals(2, coarse.activeDrones().size());
    assertEquals(2, fine.activeDrones().size());

    for (int i = 0; i < 100; i++) {
      coarse.advance(0.2f, WIDE); // t = 40 s
    }
    for (int i = 0; i < 200; i++) {
      fine.advance(0.1f, WIDE); // t = 40 s
    }
    assertEquals(coarse.groups().size(), fine.groups().size(),
        "the calendar must not depend on the frame size");
    assertEquals(coarse.activeDrones().size(), fine.activeDrones().size());
    float maxOneStepTravel = 0.2f * GhostwingEncounter.DRONE_SPEED;
    for (int i = 0; i < coarse.activeDrones().size(); i++) {
      assertEquals(coarse.activeDrones().get(i).x(), fine.activeDrones().get(i).x(),
          maxOneStepTravel, "trajectory must diverge at most by one coarse frame of travel");
      assertEquals(coarse.activeDrones().get(i).y(), fine.activeDrones().get(i).y(),
          maxOneStepTravel);
    }
  }

  /**
   * Asserts a two-drone EAST/WEST/NORTH/SOUTH group moves exactly the expected axis deltas.
   * Spawn coordinates are snapshotted as values before the second advance, since the drones are the
   * same objects afterwards and a live read would always report their post-movement position.
   */
  private static void assertStraightCrossing(ScriptedRandom random,
      GhostwingDrone.Direction expected, float travelDelta, float perpendicularDelta) {
    GhostwingEncounter encounter = new GhostwingEncounter(random);
    encounter.advance(20f, WIDE); // spawn and start the crossing
    List<GhostwingDrone> spawned = encounter.activeDrones();
    assertEquals(expected, encounter.groups().get(0).direction());
    assertEquals(2, spawned.size());
    float[] startX = {spawned.get(0).x(), spawned.get(1).x()};
    float[] startY = {spawned.get(0).y(), spawned.get(1).y()};

    encounter.advance(10f, WIDE);
    List<GhostwingDrone> after = encounter.activeDrones();
    for (int i = 0; i < after.size(); i++) {
      assertEquals(startX[i] + travelDelta, after.get(i).x(), EPSILON,
          "travel axis must move at 90 u/s in a straight line");
      assertEquals(startY[i] + perpendicularDelta, after.get(i).y(), EPSILON,
          "perpendicular axis must stay locked");
    }
  }

  /** Deterministic unit-random source feeding a scripted queue; the last value repeats. */
  private static final class ScriptedRandom implements UnitRandom {

    private final float[] values;
    private int index;

    ScriptedRandom(float... values) {
      this.values = values;
    }

    ScriptedRandom copy() {
      return new ScriptedRandom(values.clone());
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