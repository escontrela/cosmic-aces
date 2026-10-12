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
import com.davidpe.cosmicaces.domain.weapon.GunBurst;
import java.util.EnumSet;
import java.util.Set;
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
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f)); // no drift involved
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
  void identicalSpawnWithDifferentLaterRandomKeepsTheSameTrajectory() {
    // Same wait and spawn placement, then divergent random values that must no longer steer the
    // flight: the deterministic plan replaces the removed random drift.
    float targetX = 2000f;
    float targetY = 9000f;
    RaiderEncounter a = new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f, 0.05f, 0.95f));
    RaiderEncounter b = new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f, 0.95f, 0.05f));
    a.advance(5f, WORLD, targetX, targetY);
    b.advance(5f, WORLD, targetX, targetY);
    assertEquals(a.raider().x(), b.raider().x(), EPSILON, "the spawn must be identical");

    int passes = 0;
    RaiderEncounter.Maneuver previous = a.maneuver();
    for (int i = 0; i < 1800; i++) { // 30 s of deterministic 1/60 s frames
      a.advance(1f / 60f, WORLD, targetX, targetY, 0f, 0f, 20f, 40f);
      b.advance(1f / 60f, WORLD, targetX, targetY, 0f, 0f, 20f, 40f);
      assertEquals(a.raider().x(), b.raider().x(), EPSILON);
      assertEquals(a.raider().y(), b.raider().y(), EPSILON);
      assertEquals(a.raider().headingDegrees(), b.raider().headingDegrees(), EPSILON);
      assertEquals(a.maneuver(), b.maneuver());
      assertEquals(a.inversionSide(), b.inversionSide(),
          "the reversal side must not depend on post-spawn randomness");
      if (a.maneuver() == RaiderEncounter.Maneuver.ATTACK_PASS
          && previous != RaiderEncounter.Maneuver.ATTACK_PASS) {
        passes++;
      }
      previous = a.maneuver();
    }
    assertTrue(passes >= 2,
        "the deterministic run must line up at least two passes, saw " + passes);
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
  void largeDeltaMatchesManySmallSteps() {
    float targetX = 2000f;
    float targetY = 9000f;
    RaiderEncounter coarse = new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f));
    RaiderEncounter fine = new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f));
    coarse.advance(5f, WORLD, targetX, targetY);
    fine.advance(5f, WORLD, targetX, targetY);

    coarse.advance(0.5f, WORLD, targetX, targetY, 0f, 0f, 20f, 40f);
    for (int i = 0; i < 60; i++) { // the same 0.5 s as 60 bounded substeps
      fine.advance(1f / 120f, WORLD, targetX, targetY, 0f, 0f, 20f, 40f);
    }
    assertEquals(coarse.raider().x(), fine.raider().x(), 0.01f,
        "a large delta must match many small steps");
    assertEquals(coarse.raider().y(), fine.raider().y(), 0.01f);
    assertEquals(coarse.raider().headingDegrees(), fine.raider().headingDegrees(), 0.01f);
  }

  @Test
  void meetsALaterallyMovingTargetWithABoundedIntercept() {
    float targetX = 2000f;
    float targetY = 9000f;
    float velocityX = 100f;
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f));
    encounter.advance(5f, WORLD, targetX, targetY);
    float initial = distance(encounter, targetX, targetY);

    for (int i = 0; i < 600; i++) { // 10 s of a target crossing to the right
      encounter.advance(1f / 60f, WORLD, targetX, targetY, velocityX, 0f, 20f, 40f);
      targetX += velocityX / 60f;
    }
    float finalDistance = distance(encounter, targetX, targetY);
    assertTrue(Float.isFinite(finalDistance));
    assertTrue(finalDistance < initial, "the raider must close on a crossing target");
  }

  @Test
  void keepsOneSidePerManeuverWithoutPerFrameFlips() {
    float targetX = 2000f;
    float targetY = 9000f;
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f));
    encounter.advance(5f, WORLD, targetX, targetY);
    boolean sawPass = false;
    boolean sawInvert = false;
    int passSide = 0;
    int invertSide = 0;
    int passSideFlips = 0;
    int invertSideFlips = 0;
    for (int i = 0; i < 1800; i++) {
      encounter.advance(1f / 60f, WORLD, targetX, targetY, 0f, 0f, 0f, 0f);
      if (encounter.maneuver() == RaiderEncounter.Maneuver.ATTACK_PASS) {
        sawPass = true;
        if (passSide == 0) {
          passSide = encounter.separationSide();
          assertTrue(passSide != 0, "a committed pass must retain a deterministic side");
        } else if (encounter.separationSide() != passSide) {
          passSideFlips++;
        }
      } else {
        passSide = 0;
      }
      if (encounter.maneuver() == RaiderEncounter.Maneuver.INVERT) {
        sawInvert = true;
        if (invertSide == 0) {
          invertSide = encounter.inversionSide();
          assertTrue(invertSide != 0, "the reversal must retain a deterministic side");
        } else if (encounter.inversionSide() != invertSide) {
          invertSideFlips++;
        }
      } else {
        invertSide = 0;
      }
    }
    assertTrue(sawPass, "the deterministic run must reach a committed pass");
    assertTrue(sawInvert, "the deterministic run must reverse to re-engage");
    assertEquals(0, passSideFlips, "the pass side must not flip per frame");
    assertEquals(0, invertSideFlips, "the reversal side must not flip per frame");
  }

  @Test
  void speedRampsContinuouslyAcrossPhaseTransitions() {
    float targetX = 2000f;
    float targetY = 9000f;
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f));
    encounter.advance(5f, WORLD, targetX, targetY);
    assertEquals(RaiderEncounter.CRUISE_SPEED, encounter.raider().currentSpeed(), EPSILON,
        "a fresh life starts at cruise speed");
    assertEquals(RaiderEncounter.Maneuver.APPROACH, encounter.maneuver());

    float maxUp = RaiderEncounter.SPEED_ACCELERATION / 60f + 0.01f;
    float maxDown = RaiderEncounter.SPEED_DECELERATION / 60f + 0.01f;
    float previous = RaiderEncounter.CRUISE_SPEED;
    float maxSpeed = 0f;
    boolean sawPass = false;
    for (int i = 0; i < 1800; i++) { // 30 s at 1/60 s frames
      encounter.advance(1f / 60f, WORLD, targetX, targetY, 0f, 0f, 0f, 0f);
      float current = encounter.raider().currentSpeed();
      float delta = current - previous;
      assertTrue(delta <= maxUp, "speed jumped up by " + delta);
      assertTrue(delta >= -maxDown, "speed jumped down by " + delta);
      assertTrue(Float.isFinite(current) && current > 0f, "speed must stay finite and positive");
      maxSpeed = Math.max(maxSpeed, current);
      previous = current;
      sawPass |= encounter.maneuver() == RaiderEncounter.Maneuver.ATTACK_PASS;
    }
    assertTrue(sawPass, "the plan must reach the committed fast pass");
    assertTrue(maxSpeed >= RaiderEncounter.PASS_SPEED - 30f,
        "the pass must actually reach (nearly) the pass speed, saw " + maxSpeed);
    assertTrue(maxSpeed <= RaiderEncounter.PASS_SPEED + 0.5f,
        "the speed must never exceed its single per-phase target");
  }

  @Test
  void speedChangesStayWithinTheAccelerationLimitsAcrossManeuvers() {
    float targetX = 2000f;
    float targetY = 9000f;
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f));
    encounter.advance(5f, WORLD, targetX, targetY);
    float maxUp = RaiderEncounter.SPEED_ACCELERATION / 120f + 0.01f;
    float maxDown = RaiderEncounter.SPEED_DECELERATION / 120f + 0.01f;
    float previous = encounter.raider().currentSpeed();

    for (int i = 0; i < 3600; i++) { // 30 s at one 1/120 s integration substep per call
      encounter.advance(1f / 120f, WORLD, targetX, targetY, 0f, 0f, 20f, 40f);
      float current = encounter.raider().currentSpeed();
      float delta = current - previous;
      assertTrue(delta <= maxUp, "speed jumped up by " + delta);
      assertTrue(delta >= -maxDown, "speed jumped down by " + delta);
      assertTrue(Float.isFinite(current) && current > 0f, "speed must stay finite and positive");
      previous = current;
    }
  }

  @Test
  void convertsWorldDirectionsToTheRaiderDescentHeading() {
    // 0 points down, + turns right (clockwise); the math bearing must never be compared directly.
    assertEquals(0f, RaiderEncounter.headingFor(0d, -1d), 0.001f, "south/down");
    assertEquals(90f, RaiderEncounter.headingFor(1d, 0d), 0.001f, "east/right");
    assertEquals(-90f, RaiderEncounter.headingFor(-1d, 0d), 0.001f, "west/left");
    assertEquals(180f, normalizeYaw(RaiderEncounter.headingFor(0d, 1d)), 0.001f, "north/up");
    assertEquals(135f, RaiderEncounter.headingFor(1d, 1d), 0.001f, "north-east diagonal");
    assertEquals(45f, RaiderEncounter.headingFor(1d, -1d), 0.001f, "south-east diagonal");
    assertEquals(-135f, RaiderEncounter.headingFor(-1d, 1d), 0.001f, "north-west diagonal");
    assertEquals(-45f, RaiderEncounter.headingFor(-1d, -1d), 0.001f, "south-west diagonal");
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
  void keepsTheContinuousRaiderTuning() {
    // This root replaces the previous Vesper tuning (180 u/s cruise, 75 deg/s turn and a turbo that
    // cut out during the pass) with committed fast passes and a single per-phase speed policy.
    assertEquals(300f, RaiderEncounter.CRUISE_SPEED, "cruise speed");
    assertEquals(500f, RaiderEncounter.PASS_SPEED, "committed pass speed, clearly above the old 180");
    assertEquals(320f, RaiderEncounter.REENGAGE_SPEED, "re-engagement speed");
    assertEquals(300f, RaiderEncounter.SPEED_ACCELERATION, "bounded speed ramp-up");
    assertEquals(450f, RaiderEncounter.SPEED_DECELERATION, "bounded speed ramp-down");
    assertEquals(130f, RaiderEncounter.MAX_TURN_RATE_DEGREES, "bounded angular velocity");
    assertEquals(360f, RaiderEncounter.MAX_ANGULAR_ACCELERATION_DEGREES,
        "bounded angular acceleration");
    assertEquals(900f, RaiderEncounter.PASS_COMMIT_DISTANCE, "pass commit distance");
    assertEquals(15f, RaiderEncounter.PASS_COMMIT_ANGLE_DEGREES, "pass commit alignment");
    assertEquals(3f, RaiderEncounter.PASS_MAX_SECONDS, "pass deadline");
    assertEquals(0.5f, RaiderEncounter.PASS_AVOID_HORIZON_SECONDS,
        "the committed pass presses in with its own short avoidance horizon");
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
      encounter.advance(5f, WORLD, 2040f, 9400f); // spawn wait = 5 s
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

  @Test
  void committedPassPressesIntoAFiringWindowWhileKeepingHullSeparation() {
    // Real radii: the pass must break only shortly before the contact so it crosses a useful firing
    // window (aligned, inside range) and still keeps its hull distance above the radii sum.
    ScriptedRandom random = new ScriptedRandom(0.5f);
    RaiderEncounter evasive = new RaiderEncounter(random.copy());
    evasive.advance(5f, WORLD, 2040f, 9400f);
    float targetCenterX = 2080f;
    float targetCenterY = 10840f;
    float selfRadius = 20f;
    float targetRadius = 10f;
    float hullSum = selfRadius + targetRadius;

    float minDistance = Float.MAX_VALUE;
    float firingWindowDistance = Float.MAX_VALUE;
    boolean sawPass = false;
    for (int i = 0; i < 900; i++) { // 15 s at 1/60 s
      evasive.advance(1f / 60f, WORLD, targetCenterX, targetCenterY, 0f, 0f,
          targetRadius, selfRadius);
      float d = (float) Math.hypot(evasive.raider().centerX() - targetCenterX,
          evasive.raider().centerY() - targetCenterY);
      minDistance = Math.min(minDistance, d);
      if (evasive.maneuver() == RaiderEncounter.Maneuver.ATTACK_PASS) {
        sawPass = true;
        float bearing = (float) Math.toDegrees(Math.atan2(
            targetCenterX - evasive.raider().centerX(),
            evasive.raider().centerY() - targetCenterY));
        float error = Math.abs(normalizeYaw(
            bearing - evasive.raider().headingDegrees()));
        if (d <= 600f && error <= RaiderEncounter.PASS_COMMIT_ANGLE_DEGREES) {
          firingWindowDistance = Math.min(firingWindowDistance, d);
        }
      }
    }
    assertTrue(sawPass, "the raider must commit to a pass against the prepared target");
    assertTrue(minDistance > hullSum,
        "the hull separation must stay above the radii sum, closest was " + minDistance);
    assertTrue(firingWindowDistance <= 600f,
        "the pass must cross a useful firing window inside range before breaking");
  }

  @Test
  void cyclesThroughApproachPassAndInversionWithAStationaryTarget() {
    // A stationary target directly ahead exercises the whole dogfight loop: acquire, commit a fast
    // pass, reverse, then line up another pass. Body radii keep the real collision avoidance active.
    float targetX = 2000f;
    float targetY = 9000f;
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f));
    encounter.advance(5f, WORLD, targetX, targetY); // spawns ~1700 units ahead of the target

    Set<RaiderEncounter.Maneuver> seen = EnumSet.noneOf(RaiderEncounter.Maneuver.class);
    RaiderEncounter.Maneuver previous = encounter.maneuver();
    seen.add(previous);
    int passEntries = 0;
    for (int i = 0; i < 1800; i++) { // 30 s of deterministic 1/60 s frames
      encounter.advance(1f / 60f, WORLD, targetX, targetY, 0f, 0f, 20f, 40f);
      RaiderEncounter.Maneuver current = encounter.maneuver();
      if (current == RaiderEncounter.Maneuver.ATTACK_PASS
          && previous != RaiderEncounter.Maneuver.ATTACK_PASS) {
        passEntries++;
      }
      seen.add(current);
      previous = current;
    }

    assertEquals(EnumSet.allOf(RaiderEncounter.Maneuver.class), seen,
        "the raider must pass through approach, committed pass and inversion");
    assertTrue(passEntries >= 2,
        "the raider must line up a recognizable second pass, saw " + passEntries);
  }

  @Test
  void commitsTwoDistinctGeometricPassesThatOverrunTheTargetPlane() {
    // Seek-only mode (no body radii): the corridor is frozen at commit and the raider overruns the
    // target's plane by its own exit length, then inverts and comes back for a second geometric
    // pass. Counting phases is not enough: the projection along the corridor must cross the target
    // plane and return.
    float targetX = 2000f;
    float targetY = 9000f;
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f));
    encounter.advance(5f, WORLD, targetX, targetY); // spawns ~1700 units ahead of the target

    int passes = 0;
    int crossings = 0;
    int previousCrossingPlaneFrame = -1;
    RaiderEncounter.Maneuver previous = encounter.maneuver();
    boolean inPass = false;
    boolean planeCrossed = false;
    for (int i = 0; i < 3600; i++) { // 60 s of deterministic 1/60 s frames
      encounter.advance(1f / 60f, WORLD, targetX, targetY, 0f, 0f, 0f, 0f);
      RaiderEncounter.Maneuver current = encounter.maneuver();
      if (current == RaiderEncounter.Maneuver.ATTACK_PASS
          && previous != RaiderEncounter.Maneuver.ATTACK_PASS) {
        passes++;
        inPass = true;
        planeCrossed = false;
      } else if (current != RaiderEncounter.Maneuver.ATTACK_PASS
          && previous == RaiderEncounter.Maneuver.ATTACK_PASS) {
        if (planeCrossed) {
          crossings++;
          previousCrossingPlaneFrame = i;
        }
        inPass = false;
      }
      if (inPass) {
        double plane = (encounter.raider().centerX() - encounter.passEntryX())
            * encounter.passDirectionX()
            + (encounter.raider().centerY() - encounter.passEntryY())
            * encounter.passDirectionY();
        double targetPlane = (targetX - encounter.passEntryX()) * encounter.passDirectionX()
            + (targetY - encounter.passEntryY()) * encounter.passDirectionY();
        if (plane > targetPlane) {
          planeCrossed = true;
        }
      }
      previous = current;
    }
    assertTrue(passes >= 2, "two distinct pass commitments are required, saw " + passes);
    assertTrue(crossings >= 2,
        "each pass must overrun the target plane and come back, crossings = " + crossings);
    assertTrue(previousCrossingPlaneFrame > 0, "the last pass overran the target plane");
  }

  @Test
  void reversesNearlyOneHundredEightyDegreesOnOneSideWhenTheTargetIsBehind() {
    // Seek-only mode: the committed pass flies straight past the stationary target, leaving it
    // behind; the reversal then turns through a sustained ~180-degree arc on the side chosen once,
    // recovers a frontal line and lines up the second pass well inside the required window.
    float targetX = 2000f;
    float targetY = 9000f;
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f));
    encounter.advance(5f, WORLD, targetX, targetY);

    RaiderEncounter.Maneuver previous = encounter.maneuver();
    int firstPassFrame = -1;
    int secondPassFrame = -1;
    boolean sawInversionArc = false;
    float maxAccumulatedTurn = 0f;
    int inversionSide = 0;
    int sideFlips = 0;
    for (int i = 0; i < 3600; i++) {
      encounter.advance(1f / 60f, WORLD, targetX, targetY, 0f, 0f, 0f, 0f);
      RaiderEncounter.Maneuver current = encounter.maneuver();
      if (current == RaiderEncounter.Maneuver.ATTACK_PASS
          && previous != RaiderEncounter.Maneuver.ATTACK_PASS) {
        if (firstPassFrame < 0) {
          firstPassFrame = i;
        } else if (secondPassFrame < 0) {
          secondPassFrame = i;
        }
      }
      if (current == RaiderEncounter.Maneuver.INVERT) {
        sawInversionArc = true;
        maxAccumulatedTurn = Math.max(maxAccumulatedTurn,
            encounter.invertTurnAccumulated());
        if (inversionSide == 0) {
          inversionSide = encounter.inversionSide();
          assertTrue(inversionSide != 0, "the reversal must choose a concrete side");
        } else if (encounter.inversionSide() != inversionSide) {
          sideFlips++;
        }
        // The recovered frontal line: once the arc has turned far enough and stopped, the raider
        // must face (or be turning to face) the target instead of orbiting it laterally.
        if (encounter.invertTurnAccumulated() >= 120f) {
          float bearing = bearingTo(encounter, targetX, targetY);
          assertTrue(Math.abs(normalizeYaw(bearing - encounter.raider().headingDegrees())) < 120f,
              "the arc must keep recovering the frontal line, not orbit forever");
        }
      }
      previous = current;
    }
    assertTrue(firstPassFrame >= 0 && secondPassFrame >= 0, "two passes must be committed");
    assertTrue(sawInversionArc, "the reversal arc must actually run");
    assertTrue(maxAccumulatedTurn >= 150f,
        "the inversion must accumulate a sustained ~180-degree turn, saw "
            + maxAccumulatedTurn + " deg");
    assertEquals(0, sideFlips, "the inversion must hold one side for the whole arc");
    assertTrue((secondPassFrame - firstPassFrame) / 60f <= 15f,
        "the second pass must come before 15 s, saw "
            + (secondPassFrame - firstPassFrame) / 60f + " s");
  }

  @Test
  void staysFiniteAndInsideAcrossBordersAndCornersAtMaxSpeed() {
    // Drive the target to all four corners/edges at once and run at pass speed: the raider must
    // stay finite and inside the world, keep bounded turns per substep and keep moving (no clamp as
    // the main flight mode).
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f));
    encounter.advance(5f, WORLD, TARGET_X, TARGET_Y);
    float maxStep = RaiderEncounter.MAX_TURN_RATE_DEGREES / 120f + 0.01f;
    float previousHeading = encounter.raider().headingDegrees();
    float previousX = encounter.raider().x();
    float previousY = encounter.raider().y();
    int movingFrames = 0;
    for (int i = 0; i < 2400; i++) { // 20 s at one 1/120 s integration substep per call
      float corner = (i / 600) % 4;
      float targetX = corner == 0 || corner == 3 ? -3000f : 7000f;
      float targetY = corner == 0 || corner == 1 ? -3000f : 15000f;
      encounter.advance(1f / 120f, WORLD, targetX, targetY, 0f, 0f, 0f, 0f);
      VesperRaider raider = encounter.raider();
      assertTrue(Float.isFinite(raider.x()) && Float.isFinite(raider.y())
          && Float.isFinite(raider.headingDegrees()), "must stay finite");
      assertTrue(raider.x() >= -EPSILON && raider.x() <= WORLD.maxX(raider.drawWidth()) + EPSILON,
          "x out of bounds: " + raider.x());
      assertTrue(raider.y() >= -EPSILON && raider.y() <= WORLD.maxY(raider.drawHeight()) + EPSILON,
          "y out of bounds: " + raider.y());
      float turn = Math.abs(normalizeYaw(raider.headingDegrees() - previousHeading));
      assertTrue(turn <= maxStep, "heading jumped by " + turn);
      previousHeading = raider.headingDegrees();
      if (Math.abs(raider.x() - previousX) > 0.01f || Math.abs(raider.y() - previousY) > 0.01f) {
        movingFrames++;
      }
      previousX = raider.x();
      previousY = raider.y();
    }
    assertTrue(movingFrames > 2000,
        "a wall-pinned clamp must not be the flight mode, moving frames = " + movingFrames);
  }

  @Test
  void trajectoryAndPassTimingAgreeAcrossFrameRates() {
    // The encounter integrates bounded 1/120 s substeps internally, so the same seek-only scenario
    // must land at the same place and commit the same passes whether the controller feeds 30, 60,
    // 120 Hz or a single 0.1 s frame. Float accumulation stays within declared tolerances.
    float targetX = 2000f;
    float targetY = 9000f;
    double totalSeconds = 8d;
    float[][] deltas = {{1f / 30f}, {1f / 60f}, {1f / 120f}, {0.1f}};
    float previousX = Float.NaN;
    float previousY = Float.NaN;
    float previousHeading = Float.NaN;
    float previousSecondPassFrame = Float.NaN;
    float previousDelta = Float.NaN;
    for (float[] delta : deltas) {
      RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f));
      encounter.advance(5f, WORLD, targetX, targetY);
      int steps = (int) Math.round(totalSeconds / delta[0]);
      int firstPassFrame = -1;
      int secondPassFrame = -1;
      RaiderEncounter.Maneuver previousManeuver = encounter.maneuver();
      for (int i = 0; i < steps; i++) {
        encounter.advance(delta[0], WORLD, targetX, targetY, 0f, 0f, 0f, 0f);
        if (encounter.maneuver() == RaiderEncounter.Maneuver.ATTACK_PASS
            && previousManeuver != RaiderEncounter.Maneuver.ATTACK_PASS) {
          if (firstPassFrame < 0) {
            firstPassFrame = i;
          } else if (secondPassFrame < 0) {
            secondPassFrame = i;
          }
        }
        previousManeuver = encounter.maneuver();
      }
      float passTiming = secondPassFrame < 0 ? Float.NaN
          : (secondPassFrame - firstPassFrame) * delta[0];
      if (!Float.isNaN(previousX)) {
        assertEquals(previousX, encounter.raider().x(), 1f,
            "position X at " + (1f / delta[0]) + " Hz must agree");
        assertEquals(previousY, encounter.raider().y(), 1f,
            "position Y at " + (1f / delta[0]) + " Hz must agree");
        assertEquals(previousHeading, encounter.raider().headingDegrees(), 0.5f,
            "heading at " + (1f / delta[0]) + " Hz must agree");
        // Both pass boundaries are detected on the calling frame grid, so the timing may differ by
        // up to one whole frame of each compared rate; positions already prove substep equality.
        float timingTolerance = 2f * Math.max(previousDelta, delta[0]) + 0.02f;
        assertEquals(previousSecondPassFrame, passTiming, timingTolerance,
            "pass timing at " + (1f / delta[0]) + " Hz must agree within its frame grid");
      }
      assertTrue(firstPassFrame >= 0 && secondPassFrame >= 0,
          "two passes must be committed at " + (1f / delta[0]) + " Hz");
      previousX = encounter.raider().x();
      previousY = encounter.raider().y();
      previousHeading = encounter.raider().headingDegrees();
      previousSecondPassFrame = passTiming;
      previousDelta = delta[0];
    }
  }

  @Test
  void respawnStartsAFreshApproachInsteadOfInheritingThePass() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f)); // wait = 5 s
    encounter.advance(5f, WORLD, TARGET_X, TARGET_Y);
    VesperRaider raider = encounter.raider();

    // A target a hundred units below commits the current life to a firing pass.
    encounter.advance(1f / 60f, WORLD, raider.centerX(), raider.centerY() - 100f);
    assertEquals(RaiderEncounter.Maneuver.ATTACK_PASS, encounter.maneuver());

    encounter.destroyActive();
    encounter.respawnAt(WORLD, TARGET_X, TARGET_Y, 30f);
    assertEquals(RaiderEncounter.Maneuver.APPROACH, encounter.maneuver(),
        "a new life must not inherit the previous pass");
    assertEquals(0f, encounter.invertTurnAccumulated(), EPSILON,
        "respawn clears the reversal accumulator");
    assertEquals(0, encounter.inversionSide(), "respawn clears the reversal side");
    assertEquals(0f, encounter.passLength(), EPSILON, "respawn clears the pass corridor");
    assertEquals(RaiderEncounter.CRUISE_SPEED, encounter.raider().currentSpeed(), EPSILON,
        "respawn restores cruise speed");
  }

  @Test
  void headingTurnsStayBoundedAcrossEveryManeuver() {
    float targetX = 2000f;
    float targetY = 9000f;
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f));
    encounter.advance(5f, WORLD, targetX, targetY); // spawns ~1700 units ahead of the target
    float maxStep = RaiderEncounter.MAX_TURN_RATE_DEGREES / 120f + 0.01f;
    float previous = encounter.raider().headingDegrees();

    for (int i = 0; i < 3600; i++) { // 30 s at one integration substep per call
      encounter.advance(1f / 120f, WORLD, targetX, targetY, 0f, 0f, 20f, 40f);
      float current = encounter.raider().headingDegrees();
      float turn = normalizeYaw(current - previous);
      assertTrue(Math.abs(turn) <= maxStep,
          "heading jumped from " + previous + " to " + current + " (" + turn + " deg)");
      previous = current;
    }
  }

  @Test
  void shotSamplingInterpolatesTheMovementAndHeadingOfOneFrame() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0f)); // wait = 3 s
    encounter.advance(3f, WORLD, TARGET_X, TARGET_Y);
    VesperRaider raider = encounter.raider();
    float beforeCenterX = raider.centerX();
    float beforeCenterY = raider.centerY();
    float beforeHeading = raider.headingDegrees();

    // A far target to the right forces a hard turn and a long displacement in this frame.
    encounter.advance(0.85f, WORLD, raider.centerX() + 4000f, raider.centerY(),
        0f, 0f, 0f, 0f);
    float afterCenterX = raider.centerX();
    float afterCenterY = raider.centerY();
    float afterHeading = raider.headingDegrees();
    assertNotEquals(beforeHeading, afterHeading, 1f, "the raider must turn this frame");
    assertTrue(Math.abs(afterCenterX - beforeCenterX) > 5f, "the raider must move this frame");

    VesperRaider.ShotSample start = encounter.shotSampleAt(0d);
    VesperRaider.ShotSample middle = encounter.shotSampleAt(0.425d);
    VesperRaider.ShotSample end = encounter.shotSampleAt(0.85d);
    assertNotNull(start);
    assertNotNull(middle);
    assertNotNull(end);

    // The endpoints match the frame's pre/post states; the middle instant lies strictly between.
    assertEquals(beforeCenterX, start.centerX(), 0.01f);
    assertEquals(beforeCenterY, start.centerY(), 0.01f);
    assertEquals(beforeHeading, start.headingDegrees(), 0.01f);
    assertEquals(afterCenterX, end.centerX(), 0.5f);
    assertEquals(afterCenterY, end.centerY(), 0.5f);
    assertEquals(afterHeading, end.headingDegrees(), 0.01f);
    assertTrue(middle.centerX() > Math.min(beforeCenterX, afterCenterX) + 0.5f,
        "the middle emission must be inside the flown segment");
    assertTrue(middle.centerX() < Math.max(beforeCenterX, afterCenterX) - 0.5f);
    assertTrue(Math.abs(normalizeYaw(middle.headingDegrees() - beforeHeading)) > 0.05f,
        "the middle heading must be interpolated, not the final one");
  }

  @Test
  void shotSamplingCrossesTheHeadingWrapWithoutFoldingThroughSouth() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0f)); // wait = 3 s
    encounter.advance(3f, WORLD, TARGET_X, TARGET_Y);
    VesperRaider raider = encounter.raider();
    // Start just short of the +180/-180 seam and pull the nose to the far side of north.
    raider.setHeadingDegrees(179f);
    float targetX = raider.centerX() - 100f;
    float targetY = raider.centerY() + 2000f;
    encounter.advance(1f, WORLD, targetX, targetY, 0f, 0f, 0f, 0f);

    int samples = 60;
    float previousForwardX = Float.NaN;
    float previousForwardY = Float.NaN;
    for (int index = 0; index <= samples; index++) {
      double offset = 1d * index / samples;
      GunBurst.Shot shot = encounter.shotAt(offset, 1000f);
      assertNotNull(shot);
      assertTrue(shot.forwardY() > 0f,
          "the arc must stay north of the seam, sample " + index + " pointed south");
      if (!Float.isNaN(previousForwardX)) {
        float dot = shot.forwardX() * previousForwardX + shot.forwardY() * previousForwardY;
        assertTrue(dot > 0.9f,
            "the sampled heading must not flip across the seam at sample " + index);
      }
      previousForwardX = shot.forwardX();
      previousForwardY = shot.forwardY();
    }
  }

  @Test
  void shotSamplingDoesNotInterpolateAcrossARespawn() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f)); // wait = 5 s
    encounter.advance(5f, WORLD, TARGET_X, TARGET_Y);
    encounter.advance(0.5f, WORLD, TARGET_X, TARGET_Y, 0f, 0f, 0f, 0f); // records a moving path

    encounter.destroyActive();
    encounter.respawnAt(WORLD, TARGET_X, TARGET_Y, 30f);
    VesperRaider raider = encounter.raider();

    VesperRaider.ShotSample start = encounter.shotSampleAt(0d);
    VesperRaider.ShotSample end = encounter.shotSampleAt(0.5d);
    assertNotNull(start);
    assertNotNull(end);
    assertEquals(raider.centerX(), start.centerX(), EPSILON);
    assertEquals(raider.centerY(), start.centerY(), EPSILON);
    assertEquals(start.centerX(), end.centerX(), EPSILON,
        "a respawned life must not interpolate from the previous path");
    assertEquals(start.centerY(), end.centerY(), EPSILON);
    assertEquals(raider.headingDegrees(), start.headingDegrees(), EPSILON);
  }

  @Test
  void shotSamplingIsNullBeforeTheRaiderAppears() {
    RaiderEncounter encounter = new RaiderEncounter(new ScriptedRandom(0.5f));
    assertNull(encounter.shotAt(0d, 1000f));
    assertNull(encounter.shotSampleAt(0d));
  }

  private static float distance(RaiderEncounter encounter, float targetX, float targetY) {
    return (float) Math.hypot(encounter.raider().centerX() - targetX,
        encounter.raider().centerY() - targetY);
  }

  /** Bearing from the raider to the target in the raider's descent heading frame. */
  private static float bearingTo(RaiderEncounter encounter, float targetX, float targetY) {
    return RaiderEncounter.headingFor(targetX - encounter.raider().centerX(),
        targetY - encounter.raider().centerY());
  }

  private static float normalizeYaw(float yaw) {
    float normalized = yaw % 360f;
    if (normalized > 180f) normalized -= 360f;
    if (normalized <= -180f) normalized += 360f;
    return normalized;
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