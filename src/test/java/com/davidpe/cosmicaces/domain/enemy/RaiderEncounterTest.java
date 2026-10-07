package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.PlayArea;
import org.junit.jupiter.api.Test;

class RaiderEncounterTest {

  private static final PlayArea AREA = new PlayArea(1024f, 768f);
  private static final float EPSILON = 0.001f;

  @Test
  void waitsForTheRandomDelayBeforeSpawning() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0f)); // wait = 3s
    assertFalse(encounter.isActive());

    encounter.advance(2.99f, AREA);
    assertFalse(encounter.isActive());

    encounter.advance(0.02f, AREA);
    assertTrue(encounter.isActive());
    assertNotNull(encounter.raider());
    assertEquals(AREA.height(), encounter.raider().y(), EPSILON);
  }

  @Test
  void waitDurationVariesWithTheRandomSource() {
    RaiderEncounter shortWait = new RaiderEncounter(new ScriptedRandom(0f)); // wait = 3s
    shortWait.advance(2.99f, AREA);
    assertFalse(shortWait.isActive());
    shortWait.advance(0.01f, AREA);
    assertTrue(shortWait.isActive());

    RaiderEncounter longWait = new RaiderEncounter(new ScriptedRandom(0.999f)); // wait ~ 6.996s
    longWait.advance(6.99f, AREA);
    assertFalse(longWait.isActive());
    longWait.advance(0.01f, AREA);
    assertTrue(longWait.isActive());
  }

  @Test
  void keepsASingleInstanceWhileActive() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f)); // wait = 5s
    encounter.advance(5f, AREA);
    assertTrue(encounter.isActive());
    VesperRaider first = encounter.raider();

    encounter.advance(3f, AREA);
    assertTrue(encounter.isActive());
    assertSame(first, encounter.raider());
  }

  @Test
  void retiresWhenTheWholeBoxLeavesTheBottomAndRespawnsAfterANewWait() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f)); // wait = 5s, straight down
    encounter.advance(5f, AREA);
    assertTrue(encounter.isActive());
    assertEquals(768f, encounter.raider().y(), EPSILON);

    // Six full seconds of descent leave the top of the box inside the area...
    encounter.advance(1f, AREA);
    encounter.advance(1f, AREA);
    encounter.advance(1f, AREA);
    encounter.advance(1f, AREA);
    encounter.advance(1f, AREA);
    encounter.advance(1f, AREA);
    assertTrue(encounter.isActive());

    // ...and 0.2s more put the whole box below the bottom edge: retired.
    encounter.advance(0.2f, AREA);
    assertFalse(encounter.isActive());

    // A new random wait is scheduled: 5s with the scripted 0.5 source.
    encounter.advance(4.99f, AREA);
    assertFalse(encounter.isActive());
    encounter.advance(0.02f, AREA);
    assertTrue(encounter.isActive());
  }

  @Test
  void headingChangesStayWithinTheBoundedDeviation() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0f)); // wait = 3s
    encounter.advance(3f, AREA); // spawn; heading 0 until the first turn
    assertEquals(0f, encounter.raider().headingDegrees(), EPSILON);

    for (int i = 0; i < 40; i++) {
      encounter.advance(0.5f, AREA);
      if (encounter.isActive()) {
        float heading = encounter.raider().headingDegrees();
        assertTrue(
            heading >= -RaiderEncounter.MAX_HEADING_DEGREES - EPSILON
                && heading <= RaiderEncounter.MAX_HEADING_DEGREES + EPSILON,
            "heading out of bounds: " + heading);
      }
    }
  }

  @Test
  void bankReflectsTheCurrentHeadingThroughTheEncounter() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0f)); // wait = 3s
    encounter.advance(3f, AREA);
    assertEquals(0, encounter.raider().bank());

    encounter.advance(1f, AREA); // first turn: heading = -20 with the 0f source
    assertEquals(-1, encounter.raider().bank());
  }

  @Test
  void retiresWhenTheWholeBoxLeavesASide() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0f)); // wait = 3s, heading -> -20
    encounter.advance(3f, AREA); // spawn at x = 0
    assertTrue(encounter.isActive());

    encounter.advance(1f, AREA); // straight down, then turn to -20
    encounter.advance(1f, AREA); // drifting left
    encounter.advance(0.5f, AREA);
    encounter.advance(0.3f, AREA); // box fully left of x = 0
    assertFalse(encounter.isActive());
  }

  @Test
  void nonPositiveOrNonFiniteDeltaIsIgnored() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0f)); // wait = 3s
    encounter.advance(-1f, AREA);
    encounter.advance(0f, AREA);
    encounter.advance(Float.NaN, AREA);
    encounter.advance(Float.POSITIVE_INFINITY, AREA);
    assertFalse(encounter.isActive());

    encounter.advance(3f, AREA);
    assertTrue(encounter.isActive()); // the wait was not consumed by the invalid deltas
  }

  @Test
  void largeDeltaSettlesInAValidStateWithoutError() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f));
    encounter.advance(60f, AREA); // wait consumed: the raider spawns parked at the top edge
    assertTrue(encounter.isActive());
    assertEquals(768f, encounter.raider().y(), EPSILON);

    encounter.advance(60f, AREA); // huge active delta: the box is far below the area
    assertFalse(encounter.isActive());
  }

  @Test
  void rejectsANullRandomSource() {
    assertThrows(IllegalArgumentException.class, () -> new RaiderEncounter(null));
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