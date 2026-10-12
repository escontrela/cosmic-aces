package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.FlightTuning;
import org.junit.jupiter.api.Test;

/**
 * Joint validation of the Vesper Raider dogfight (COS-61 root, COS-65 closing child).
 *
 * <p>These are deterministic, texture-free fixtures: an explicit world, explicit target
 * positions/velocities and a scripted {@link UnitRandom} used only for the initial spawn wait and
 * placement. They complement {@code RaiderEncounterTest} by asserting the <em>combined</em>
 * behaviour the root asked for: two geometric passes with a fresh firing window at both quiet and
 * cruising targets, a pass speed clearly above the historic tuning, bounded turn/acceleration, and
 * frame-rate tolerances published here and in {@code docs/vesper-dogfight-validation.md}.
 *
 * <p>Geometric pass fixtures run in seek-only mode (zero body radii), exactly like the existing
 * deterministic corridor tests, so the frozen corridor is not interrupted by avoidance and the
 * target-plane overrun is unambiguous. The firing-window and hull-separation fixture runs with the
 * real body radii used by the integrated controller.
 *
 * <p>Baseline limitation: the pre-root tuning (cruise 180 u/s, turbo 270 u/s, 75 deg/s turn) no
 * longer exists in this checkout, so it is compared as a documented constant (see the root issue and
 * {@code docs/vesper-dogfight-validation.md}) rather than by re-running commit
 * {@code 866425b390afaca642510eaacffac2805e4c0516}. No synthetic data is invented.
 */
class VesperDogfightValidationTest {

  /** The real PhaseOne world, so borders match the integrated phase. */
  private static final WorldBounds WORLD = new WorldBounds(8192f, 12000f);
  private static final float EPSILON = 0.01f;
  private static final float SEEK_ONLY = 0f;
  private static final float SELF_RADIUS = RaiderEncounter.RAIDER_WIDTH / 2f;
  private static final float TARGET_RADIUS = 20f;
  private static final float START_X = 2000f;
  private static final float START_Y = 9000f;

  /**
   * Documented pre-root Vesper tuning (root issue COS-61), replaced by this iteration. Kept as an
   * explicit baseline constant because the old turbo/seek API is not available in this checkout.
   */
  private static final float HISTORIC_CRUISE_SPEED = 180f;
  private static final float HISTORIC_TURBO_SPEED = 270f;

  /** Published frame-rate tolerances: position, heading and pass-timing agreement. */
  private static final float FPS_POSITION_TOLERANCE = 1.0f;
  private static final float FPS_HEADING_TOLERANCE_DEGREES = 0.5f;

  // ---- Central fixtures: two geometric passes and two fresh firing windows -------------------

  @Test
  void quietTargetAchievesTwoGeometricPassesAndTwoFiringWindows() {
    RaiderEncounter encounter = spawnCentred();
    Campaign campaign = run(encounter, 60f, 1f / 60f, START_X, START_Y, 0f, 0f, SEEK_ONLY);

    assertTrue(campaign.passEntries >= 2,
        "the quiet fixture must commit two passes, saw " + campaign.passEntries);
    assertTrue(campaign.firingWindowEntries >= 2,
        "each pass must open a fresh firing window, saw " + campaign.firingWindowEntries);
    assertTrue(campaign.maxAccumulatedInversionTurn >= 150f,
        "the reversal must be a sustained ~180 arc, saw "
            + campaign.maxAccumulatedInversionTurn + " deg");
    assertTrue(campaign.maxTurnPerFrame <= RaiderEncounter.MAX_TURN_RATE_DEGREES / 60f + 0.01f,
        "heading must never jump beyond the bounded turn rate, saw " + campaign.maxTurnPerFrame);
    assertTrue(campaign.maxSpeedPerFrame
            <= Math.max(RaiderEncounter.SPEED_ACCELERATION, RaiderEncounter.SPEED_DECELERATION)
                / 60f + 0.01f,
        "speed must never jump beyond the bounded ramp, saw " + campaign.maxSpeedPerFrame);
  }

  @Test
  void cruisingTargetAchievesTwoGeometricPassesAndTwoFiringWindows() {
    // Astra cruising forward at her normal 150 u/s, moving north along her initial route. The target
    // position advances analytically with the same elapsed time, never by a different delta per FPS.
    float velocityY = FlightTuning.NORMAL_SPEED;
    RaiderEncounter encounter = spawnCentred();
    Campaign campaign = run(encounter, 60f, 1f / 60f, 2000f, 2500f, 0f, velocityY, SEEK_ONLY);

    assertTrue(campaign.passEntries >= 2,
        "a cruising target must still get two geometric passes, saw " + campaign.passEntries);
    assertTrue(campaign.firingWindowEntries >= 2,
        "both passes must open a firing window against a cruising target, saw "
            + campaign.firingWindowEntries);
    assertTrue(campaign.maxAccumulatedInversionTurn >= 150f,
        "the reversal must still be a sustained ~180 arc against a moving target, saw "
            + campaign.maxAccumulatedInversionTurn + " deg");
  }

  @Test
  void realRadiiKeepHullSeparationWhileThePassPressesIntoAFiringWindow() {
    RaiderEncounter encounter = spawnCentred();
    Campaign campaign = run(encounter, 30f, 1f / 60f, START_X, START_Y, 0f, 0f, SELF_RADIUS);

    assertTrue(campaign.passEntries >= 1,
        "the raider must still commit a pass with real radii, saw " + campaign.passEntries);
    assertTrue(campaign.minHullSeparation > SELF_RADIUS + TARGET_RADIUS,
        "hull separation must stay above the radii sum, saw " + campaign.minHullSeparation);
    assertTrue(campaign.firingWindowEntries >= 1,
        "the committed pass must cross a useful firing window before breaking, saw "
            + campaign.firingWindowEntries);
  }

  // ---- Speed compared to the historic baseline ----------------------------------------------

  @Test
  void passSpeedClearlyExceedsTheHistoricBaseline() {
    RaiderEncounter encounter = spawnCentred();
    Campaign campaign = run(encounter, 30f, 1f / 60f, START_X, START_Y, 0f, 0f, SEEK_ONLY);

    assertTrue(campaign.maxPassSpeed > HISTORIC_TURBO_SPEED + 150f,
        "the fast pass must clearly beat the historic 270 u/s turbo, saw " + campaign.maxPassSpeed);
    assertTrue(campaign.maxPassSpeed >= RaiderEncounter.PASS_SPEED - 30f,
        "the pass must reach its committed target speed, saw " + campaign.maxPassSpeed);
    assertTrue(campaign.maxPassSpeed <= RaiderEncounter.PASS_SPEED + EPSILON,
        "the speed must never exceed its single per-phase target");
    assertTrue(RaiderEncounter.PASS_SPEED >= 2f * HISTORIC_CRUISE_SPEED,
        "the committed pass speed must be at least twice the historic 180 u/s cruise");
  }

  // ---- Frame-rate independence with an irregular 0.1 s spike --------------------------------

  @Test
  void irregularFramesWithASpikeMatchTheFineGridWithinPublishedTolerances() {
    // Same stationary seek-only geometry and the same total 8 s; the irregular run uses a pattern
    // that includes 0.1 s spikes, the fine run uniform 1/120 s frames. Both must land within the
    // published tolerances. This proves a long frame does not teleport the raider or its plan.
    RaiderEncounter fine = spawnCentred();
    RaiderEncounter irregular = spawnCentred();
    float totalSeconds = 8f;
    // Consume the identical 5 s spawn wait on both, so the same life is already flying when the
    // measured window starts and only the frame partitioning differs.
    fine.advance(5f, WORLD, START_X, START_Y, 0f, 0f, SEEK_ONLY, SEEK_ONLY);
    irregular.advance(5f, WORLD, START_X, START_Y, 0f, 0f, SEEK_ONLY, SEEK_ONLY);

    int fineSteps = Math.round(totalSeconds * 120f);
    for (int i = 0; i < fineSteps; i++) {
      fine.advance(1f / 120f, WORLD, START_X, START_Y, 0f, 0f, SEEK_ONLY, SEEK_ONLY);
    }
    float[] pattern = {0.1f, 0.05f, 0.05f}; // sums to 0.2 s; 40 cycles = 8 s
    float elapsed = 0f;
    int guard = 0;
    while (elapsed < totalSeconds - EPSILON && guard++ < 10_000) {
      for (float delta : pattern) {
        if (elapsed >= totalSeconds - EPSILON) {
          break;
        }
        irregular.advance(delta, WORLD, START_X, START_Y, 0f, 0f, SEEK_ONLY, SEEK_ONLY);
        elapsed += delta;
      }
    }

    assertEquals(fine.raider().centerX(), irregular.raider().centerX(), FPS_POSITION_TOLERANCE,
        "position X at the irregular frame pattern must agree with the fine grid");
    assertEquals(fine.raider().centerY(), irregular.raider().centerY(), FPS_POSITION_TOLERANCE,
        "position Y at the irregular frame pattern must agree with the fine grid");
    assertEquals(fine.raider().headingDegrees(), irregular.raider().headingDegrees(),
        FPS_HEADING_TOLERANCE_DEGREES,
        "heading at the irregular frame pattern must agree with the fine grid");
  }

  // ---- Helpers -----------------------------------------------------------------------------

  private static RaiderEncounter spawnCentred() {
    // 0.5 -> wait 5 s and lateral offset 0, so the spawn is centred ahead of the target.
    return new RaiderEncounter(new ScriptedRandom(0.5f, 0.5f));
  }

  private static Campaign run(RaiderEncounter encounter, float seconds, float delta,
      float startX, float startY, float velocityX, float velocityY, float selfRadius) {
    Campaign campaign = new Campaign();
    float spawnSeconds = Math.min(5f, seconds);
    encounter.advance(spawnSeconds, WORLD, startX, startY, velocityX, velocityY, TARGET_RADIUS,
        selfRadius);
    float elapsed = spawnSeconds;
    RaiderEncounter.Maneuver previous = encounter.maneuver();
    float previousSpeed = encounter.raider().currentSpeed();
    float previousHeading = encounter.raider().headingDegrees();
    int seenPasses = 0;
    boolean inWindow = false;
    boolean planeCrossedThisPass = false;
    float firstPassSeconds = Float.NaN;
    while (elapsed < seconds - EPSILON) {
      float step = Math.min(delta, seconds - elapsed);
      float targetX = startX + velocityX * elapsed;
      float targetY = startY + velocityY * elapsed;
      encounter.advance(step, WORLD, targetX, targetY, velocityX, velocityY, TARGET_RADIUS,
          selfRadius);
      elapsed += step;

      VesperRaider raider = encounter.raider();
      float currentSpeed = raider.currentSpeed();
      campaign.maxSpeedPerFrame = Math.max(campaign.maxSpeedPerFrame,
          Math.abs(currentSpeed - previousSpeed));
      campaign.maxTurnPerFrame = Math.max(campaign.maxTurnPerFrame,
          Math.abs(normalizeYaw(raider.headingDegrees() - previousHeading)));
      previousSpeed = currentSpeed;
      previousHeading = raider.headingDegrees();

      float dx = targetX - raider.centerX();
      float dy = targetY - raider.centerY();
      float distance = (float) Math.hypot(dx, dy);
      campaign.minHullSeparation = Math.min(campaign.minHullSeparation, distance);

      RaiderEncounter.Maneuver current = encounter.maneuver();
      if (current == RaiderEncounter.Maneuver.ATTACK_PASS) {
        if (previous != RaiderEncounter.Maneuver.ATTACK_PASS) {
          seenPasses++;
          planeCrossedThisPass = false;
          if (Float.isNaN(firstPassSeconds)) {
            firstPassSeconds = elapsed;
          }
        }
        campaign.maxPassSpeed = Math.max(campaign.maxPassSpeed, currentSpeed);
        float bearingHeading = RaiderEncounter.headingFor(dx, dy);
        float headingError = Math.abs(normalizeYaw(bearingHeading - raider.headingDegrees()));
        boolean windowOpen = distance <= 600f
            && headingError <= RaiderEncounter.PASS_COMMIT_ANGLE_DEGREES;
        if (windowOpen && !inWindow) {
          campaign.firingWindowEntries++;
        }
        inWindow = windowOpen;
        double plane = (raider.centerX() - encounter.passEntryX()) * encounter.passDirectionX()
            + (raider.centerY() - encounter.passEntryY()) * encounter.passDirectionY();
        double targetPlane = (targetX - encounter.passEntryX()) * encounter.passDirectionX()
            + (targetY - encounter.passEntryY()) * encounter.passDirectionY();
        if (plane > targetPlane) {
          planeCrossedThisPass = true;
        }
      } else {
        if (previous == RaiderEncounter.Maneuver.ATTACK_PASS) {
          campaign.passEntries += planeCrossedThisPass ? 1 : 0;
          inWindow = false;
        }
      }
      if (current == RaiderEncounter.Maneuver.INVERT) {
        campaign.maxAccumulatedInversionTurn = Math.max(campaign.maxAccumulatedInversionTurn,
            encounter.invertTurnAccumulated());
      }
      previous = current;
    }
    return campaign;
  }

  private static float normalizeYaw(float yaw) {
    float normalized = yaw % 360f;
    if (normalized > 180f) normalized -= 360f;
    if (normalized <= -180f) normalized += 360f;
    return normalized;
  }

  /** Aggregated measurements for one deterministic dogfight campaign. */
  private static final class Campaign {
    int passEntries;
    int firingWindowEntries;
    float maxPassSpeed;
    float minHullSeparation = Float.MAX_VALUE;
    float maxAccumulatedInversionTurn;
    float maxTurnPerFrame;
    float maxSpeedPerFrame;
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
