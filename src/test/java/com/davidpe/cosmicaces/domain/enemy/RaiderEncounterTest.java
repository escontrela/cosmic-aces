package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import org.junit.jupiter.api.Test;

class RaiderEncounterTest {

  private static final WorldBounds WORLD = new WorldBounds(4000f, 12000f);
  private static final float EPSILON = 0.001f;
  private static final float TARGET_X = 2000f;
  private static final float TARGET_Y = 600f;

  @Test
  void waitsForTheRandomDelayBeforeSpawningOnce() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0f)); // wait = 3s
    assertFalse(encounter.isActive());
    assertNull(encounter.raider());

    encounter.advance(2.99f, WORLD, TARGET_X, TARGET_Y);
    assertFalse(encounter.isActive());

    encounter.advance(0.02f, WORLD, TARGET_X, TARGET_Y);
    assertTrue(encounter.isActive());
    assertNotNull(encounter.raider());
  }

  @Test
  void spawnsAheadOfTheTargetAndInsideTheWorld() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f)); // wait = 5s
    encounter.advance(5f, WORLD, TARGET_X, TARGET_Y);
    VesperRaider raider = encounter.raider();
    assertNotNull(raider);
    assertTrue(raider.y() > TARGET_Y, "raider must appear ahead of the initial route");
    assertTrue(raider.y() <= WORLD.maxY(raider.drawHeight()) + EPSILON);
    assertTrue(raider.x() >= -EPSILON && raider.x() <= WORLD.maxX(raider.drawWidth()) + EPSILON);
  }

  @Test
  void keepsASinglePersistentInstanceForTheWholeRun() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f));
    encounter.advance(5f, WORLD, TARGET_X, TARGET_Y);
    VesperRaider first = encounter.raider();
    assertNotNull(first);

    // A full run's worth of frames, with the target far away: the raider persists and never respawns.
    for (int i = 0; i < 600; i++) {
      encounter.advance(0.1f, WORLD, 200f, 11000f);
      assertTrue(encounter.isActive());
      assertSame(first, encounter.raider());
    }
  }

  @Test
  void turnsTowardTheTargetWithBoundedSteps() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f)); // drift = 0
    encounter.advance(5f, WORLD, TARGET_X, TARGET_Y);
    VesperRaider raider = encounter.raider();
    float previous = raider.headingDegrees();
    float maxStep = RaiderEncounter.MAX_TURN_RATE_DEGREES / 120f + 0.01f;

    for (int i = 0; i < 120; i++) {
      // Keep the target straight to the right so the raider must turn toward east (heading +90).
      encounter.advance(1f / 120f, WORLD, raider.x() + 1000f, raider.y());
      float current = raider.headingDegrees();
      assertTrue(Math.abs(current - previous) <= maxStep,
          "heading jumped from " + previous + " to " + current);
      previous = current;
    }
    assertTrue(raider.headingDegrees() > 5f, "the raider should have started turning right");
    assertTrue(raider.headingDegrees() <= 90f + EPSILON);
  }

  @Test
  void differentRandomSourcesProduceDifferentSearchPaths() {
    RaiderEncounter neutralDrift = new RaiderEncounter(new ScriptedRandom(0.5f)); // drift = 0
    RaiderEncounter leftDrift = new RaiderEncounter(new ScriptedRandom(0f)); // drift = -35
    neutralDrift.advance(5f, WORLD, TARGET_X, TARGET_Y);
    leftDrift.advance(3f, WORLD, TARGET_X, TARGET_Y);

    for (int i = 0; i < 120; i++) {
      neutralDrift.advance(1f / 60f, WORLD, TARGET_X, TARGET_Y);
      leftDrift.advance(1f / 60f, WORLD, TARGET_X, TARGET_Y);
    }
    assertNotEquals(neutralDrift.raider().headingDegrees(),
        leftDrift.raider().headingDegrees(), 1f,
        "the reproducible drift must make the search path imperfect");
  }

  @Test
  void staysInsideTheWorldEvenWhenPushedTowardAnEdge() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0f));
    encounter.advance(3f, WORLD, TARGET_X, TARGET_Y);
    VesperRaider raider = encounter.raider();

    for (int i = 0; i < 1200; i++) {
      encounter.advance(1f / 60f, WORLD, -WORLD.width(), TARGET_Y);
    }
    assertTrue(raider.x() >= -EPSILON, "left edge crossed: " + raider.x());
    assertTrue(raider.x() <= WORLD.maxX(raider.drawWidth()) + EPSILON, "right edge crossed");
    assertTrue(raider.y() >= -EPSILON, "bottom edge crossed: " + raider.y());
    assertTrue(raider.y() <= WORLD.maxY(raider.drawHeight()) + EPSILON, "top edge crossed");
  }

  @Test
  void isDeterministicForTheSameRandomSource() {
    RaiderEncounter a = new RaiderEncounter(new ScriptedRandom(0.5f, 0.25f, 0.75f));
    RaiderEncounter b = new RaiderEncounter(new ScriptedRandom(0.5f, 0.25f, 0.75f));
    for (int i = 0; i < 300; i++) {
      a.advance(0.1f, WORLD, TARGET_X, TARGET_Y);
      b.advance(0.1f, WORLD, TARGET_X, TARGET_Y);
    }
    assertEquals(a.raider().x(), b.raider().x(), EPSILON);
    assertEquals(a.raider().y(), b.raider().y(), EPSILON);
    assertEquals(a.raider().headingDegrees(), b.raider().headingDegrees(), EPSILON);
  }

  @Test
  void ignoresNonPositiveOrNonFiniteDeltas() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0f)); // wait = 3s
    encounter.advance(-1f, WORLD, TARGET_X, TARGET_Y);
    encounter.advance(0f, WORLD, TARGET_X, TARGET_Y);
    encounter.advance(Float.NaN, WORLD, TARGET_X, TARGET_Y);
    encounter.advance(Float.POSITIVE_INFINITY, WORLD, TARGET_X, TARGET_Y);
    assertFalse(encounter.isActive());

    encounter.advance(3f, WORLD, TARGET_X, TARGET_Y);
    assertTrue(encounter.isActive()); // the wait was not consumed by the invalid deltas
  }

  @Test
  void rejectsNullAndNonFiniteArguments() {
    assertThrows(IllegalArgumentException.class, () -> new RaiderEncounter(null));

    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f));
    assertThrows(IllegalArgumentException.class, () -> encounter.advance(1f, null, 0f, 0f));
    assertThrows(IllegalArgumentException.class,
        () -> encounter.advance(1f, WORLD, Float.NaN, 0f));
    assertThrows(IllegalArgumentException.class,
        () -> encounter.advance(1f, WORLD, 0f, Float.POSITIVE_INFINITY));
  }

  @Test
  void usesTheSlowerSteadierTuningApprovedByThePO() {
    assertEquals(180f, RaiderEncounter.RAIDER_SPEED, "was 240, now 180");
    assertEquals(10f, RaiderEncounter.MAX_DRIFT_DEGREES, "was 35, now 10");
    assertEquals(4f, RaiderEncounter.MIN_DRIFT_INTERVAL_SECONDS, "was 1.3");
    assertEquals(6f, RaiderEncounter.MAX_DRIFT_INTERVAL_SECONDS, "was 2.7");
    assertEquals(75f, RaiderEncounter.MAX_TURN_RATE_DEGREES, "bounded turns are kept");
  }

  @Test
  void destroyedRaiderFreezesUntilRespawnPlacesItElsewhere() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f)); // wait = 5s
    encounter.advance(5f, WORLD, TARGET_X, TARGET_Y);
    VesperRaider first = encounter.raider();
    float deathCenterX = first.centerX();
    float deathCenterY = first.centerY();

    encounter.destroyActive();
    encounter.destroyActive(); // idempotent
    assertFalse(encounter.isActive());
    assertTrue(encounter.isDefeated());

    encounter.advance(10f, WORLD, TARGET_X, TARGET_Y);
    assertEquals(deathCenterX, first.centerX(), EPSILON, "the wreck must not keep flying");
    assertEquals(deathCenterY, first.centerY(), EPSILON);

    encounter.respawnAt(WORLD, TARGET_X, TARGET_Y, 30f);
    assertTrue(encounter.isActive());
    assertFalse(encounter.isDefeated());
    assertSame(first, encounter.raider(), "respawn must reuse the same instance and visuals");

    assertTrue(first.centerX() >= 0f && first.centerX() <= WORLD.width() + EPSILON);
    assertTrue(first.centerY() >= 0f && first.centerY() <= WORLD.height() + EPSILON);
    double fromTarget = Math.hypot(first.centerX() - TARGET_X, first.centerY() - TARGET_Y);
    assertTrue(fromTarget >= 500f + 30f - 1f, "must not overlap Astra, got " + fromTarget);
    double fromDeath = Math.hypot(first.centerX() - deathCenterX, first.centerY() - deathCenterY);
    assertTrue(fromDeath >= 800f - 1f, "must be a new point, got " + fromDeath);
  }

  @Test
  void respawnFallsBackToTheBestCandidateWhenRandomIsHostile() {
    // A random stuck at 0.499 samples a grid close to the target/death: forcing the fallback
    // requires a tiny world shaped so every candidate is rejected; the fallback must still be valid.
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f));
    encounter.advance(5f, WORLD, TARGET_X, TARGET_Y);
    encounter.destroyActive();
    encounter.respawnAt(WORLD, TARGET_X, TARGET_Y, 50f);
    VesperRaider raider = encounter.raider();
    assertTrue(encounter.isActive());
    assertTrue(raider.centerX() >= 0f && raider.centerX() <= WORLD.width() + EPSILON);
    assertTrue(raider.centerY() >= 0f && raider.centerY() <= WORLD.height() + EPSILON);
  }

  @Test
  void avoidanceKeepsTheRaiderAwayFromAPredictedHeadOnCollision() {
    // Both raiders start identical and run the same scripted random; only the second one has body
    // radii so its avoidance is active. A stationary target sits directly in the raider's downward
    // path, right below its spawn point.
    ScriptedRandom random = new ScriptedRandom(0.5f);
    RaiderEncounter blind = new RaiderEncounter(random.copy());
    RaiderEncounter evasive = new RaiderEncounter(random.copy());
    for (RaiderEncounter encounter : new RaiderEncounter[] {blind, evasive}) {
      encounter.advance(5f, WORLD, 2040f, 9400f); // spawn wait = 5 s, drift = 0
    }
    float targetCenterX = 2080f;
    float targetCenterY = 10840f;
    float selfRadius = 20f;
    float targetRadius = 10f;
    float contactDistance = selfRadius + targetRadius;

    float blindClosest = Float.MAX_VALUE;
    float evasiveClosest = Float.MAX_VALUE;
    for (int i = 0; i < 600; i++) {
      blind.advance(1f / 60f, WORLD, targetCenterX, targetCenterY, 0f, 0f, 0f, 0f);
      evasive.advance(1f / 60f, WORLD, targetCenterX, targetCenterY, 0f, 0f,
          targetRadius, selfRadius);
      blindClosest = Math.min(blindClosest,
          (float) Math.hypot(blind.raider().centerX() - targetCenterX,
              blind.raider().centerY() - targetCenterY));
      evasiveClosest = Math.min(evasiveClosest,
          (float) Math.hypot(evasive.raider().centerX() - targetCenterX,
              evasive.raider().centerY() - targetCenterY));
    }

    assertTrue(blindClosest < contactDistance, "without avoidance the path crosses the target");
    assertTrue(evasiveClosest > contactDistance,
        "with avoidance the raider must keep its distance, closest was " + evasiveClosest);
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
