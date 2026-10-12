package com.davidpe.cosmicaces.domain.enemy;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.weapon.GunBurst;
import com.davidpe.cosmicaces.domain.weapon.GunTuning;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps the single Vesper Raider of a phase alive in world coordinates. After one random wait the
 * raider appears once ahead of the player's route, then flies a fully deterministic dogfight:
 * once it has spawned, no flight, heading, phase or maneuver-side decision depends on
 * {@link UnitRandom}, which stays only for the initial wait and for spawn/respawn placement.
 *
 * <p>Its dogfight flies committed fast passes instead of a per-frame pursuit: during
 * {@code APPROACH} it acquires a firing line toward a bounded intercept ({@code APPROACH}), commits
 * to a fixed high-speed pass corridor once close and aligned ({@code ATTACK_PASS}), then opens
 * space and executes a sustained reversal arc on the side chosen once ({@code INVERT}) to re-engage
 * another pass. The pass corridor is frozen at commit (entry point, normalized direction, exit
 * distance); it is not rebuilt each frame. Collision avoidance and the inward border response
 * remain safety overrides on top of the phases.
 *
 * <p>Each phase keeps one persistent flight intention that only changes at a maneuver transition,
 * so the raider holds clean lines instead of rectifying from side to side every frame. Speeds are a
 * single per-phase target (cruise during approach, pass during the corridor, re-engagement during
 * the reversal) reached through the shared bounded acceleration ramp; there is no separate turbo
 * window, so the pass is never cut by a second speed policy.
 *
 * <p>A destroyed raider keeps its instance (and its borrowed visuals) and stops moving until the
 * controller respawns it elsewhere. Timing, speeds and avoidance values are technical choices
 * configurable for the PO visual QA; they are not product criteria.
 */
public final class RaiderEncounter {

  /** Raider cruise speed in world units per second, used while acquiring the firing line. */
  public static final float CRUISE_SPEED = 300f;
  /** Committed fast-pass speed in world units per second. */
  public static final float PASS_SPEED = 500f;
  /** Re-engagement speed in world units per second while opening space for the next pass. */
  public static final float REENGAGE_SPEED = 320f;
  /** Speed ramp-up rate in world units per second squared. */
  public static final float SPEED_ACCELERATION = 300f;
  /** Speed ramp-down rate in world units per second squared. */
  public static final float SPEED_DECELERATION = 450f;
  /** World size of the raider box used for bounds and matched by the sprite drawing. */
  public static final float RAIDER_WIDTH = 80f;
  public static final float RAIDER_HEIGHT = 80f;
  /** Random wait before the only raider appears, in seconds. */
  public static final float MIN_SPAWN_WAIT_SECONDS = 3f;
  public static final float MAX_SPAWN_WAIT_SECONDS = 7f;
  /** The raider appears this far ahead of the player's position, with bounded lateral offset. */
  public static final float SPAWN_AHEAD_DISTANCE = 1700f;
  public static final float SPAWN_LATERAL_DISTANCE = 500f;
  /** Maximum heading change per second, in degrees. */
  public static final float MAX_TURN_RATE_DEGREES = 130f;
  /** Maximum change of the heading angular velocity per second, in degrees per second squared. */
  public static final float MAX_ANGULAR_ACCELERATION_DEGREES = 360f;
  /** Distance from a world edge where the inward steering response starts fading in. */
  public static final float BORDER_ZONE = 1400f;
  /** Relative strength of the inward border response against the unit bearing to the player. */
  private static final float BORDER_WEIGHT = 1.5f;
  private static final float MAX_STEP_SECONDS = 1f / 120f;

  /** Prediction horizon used to decide whether the current path leads to a collision. */
  public static final float AVOID_HORIZON_SECONDS = 1.2f;
  /** Extra separation added to the combined body radii before a contact is predicted. */
  public static final float AVOID_CONTACT_MARGIN = 70f;
  /** Minimum seconds an avoidance side is held, preventing left/right oscillation per frame. */
  public static final float AVOID_MIN_HOLD_SECONDS = 1.2f;
  /**
   * Avoidance horizon used inside a committed pass: the pass presses in close so it crosses into a
   * useful firing window, and the break starts only shortly before the contact at pass speed. The
   * wider {@link #AVOID_HORIZON_SECONDS} still applies while acquiring the line and during the
   * reversal.
   */
  public static final float PASS_AVOID_HORIZON_SECONDS = 0.5f;
  /** Smaller contact margin used while the pass corridor is committed, matching the close break. */
  public static final float PASS_AVOID_CONTACT_MARGIN = 40f;

  /** Fallback lead used when no ballistic solution exists, in seconds. */
  public static final float INTERCEPT_HORIZON_SECONDS = 1f;
  /** Upper bound on the ballistic interception lead, in seconds. */
  public static final float MAX_INTERCEPT_LEAD_SECONDS = 2f;
  /** How often the approach line is re-planned (a new aim point), in seconds. */
  public static final float APPROACH_REPLAN_SECONDS = 0.2f;

  /** Distance at or below which the raider may commit to a firing pass while closely aligned. */
  public static final float PASS_COMMIT_DISTANCE = 900f;
  /** Maximum heading error, in degrees, that still permits committing to a pass. */
  public static final float PASS_COMMIT_ANGLE_DEGREES = 15f;
  /** Shortest committed pass, so a grazing pass is not immediately abandoned. */
  public static final float PASS_MIN_SECONDS = 0.5f;
  /** Longest a committed pass lasts before the raider breaks off to line up another one. */
  public static final float PASS_MAX_SECONDS = 3f;
  /** Extra distance the corridor keeps flying past the commit point, in world units. */
  public static final float PASS_EXIT_OVERSHOOT = 400f;
  /** Shortest corridor length regardless of the commit distance, in world units. */
  public static final float MIN_PASS_LENGTH = 250f;

  /** Straight post-pass separation segment before the reversal arc starts, in seconds. */
  public static final float SEPARATE_SECONDS = 0.5f;
  /** Heading error, in degrees, at which the reversal is considered complete (firing line back). */
  public static final float INVERT_ALIGN_DEGREES = 25f;
  /** Longest the reversal/re-engagement may last before a fresh approach is forced. */
  public static final float INVERT_MAX_SECONDS = 5f;

  /** Minimum centre separation from Astra when the raider respawns. */
  public static final float RESPAWN_MIN_DISTANCE_FROM_TARGET = 500f;
  /** Minimum centre separation from the previous death spot, so the respawn is a new point. */
  public static final float RESPAWN_MIN_DISTANCE_FROM_DEATH = 800f;
  private static final int RESPAWN_ATTEMPTS = 24;

  private final UnitRandom random;
  private VesperRaider raider;
  private VesperRaider.Visuals visuals;
  private float waitSeconds;
  private boolean defeated;
  private float deathCenterX;
  private float deathCenterY;
  private int avoidSide;
  private float avoidHoldSeconds;
  private Maneuver maneuver = Maneuver.APPROACH;
  private float maneuverSeconds;
  /** Committed pass corridor: entry center, normalized direction and length, fixed at commit. */
  private int passSide;
  private float passDirX;
  private float passDirY;
  private float passEntryX;
  private float passEntryY;
  private float passLength;
  /** Reversal side chosen once at pass exit (+1 right, -1 left) and the turn already accumulated. */
  private int invertSide;
  private float invertTurnAccumulated;
  /** Persistent approach aim, re-planned at most every {@link #APPROACH_REPLAN_SECONDS}. */
  private float approachAimHeadingDegrees;
  private float approachReplanSeconds;
  /**
   * Kinematic samples recorded during the last {@link #advance} call, one per bounded substep, so
   * {@link #shotAt(double, float)} can rebuild the raider's position, heading and muzzle pose at any
   * frame-local emission instant instead of freezing every shot on the frame's final state.
   */
  private final List<TrajectorySample> shotTrajectory = new ArrayList<>();
  private int shotTrajectoryGeneration;
  /** Monotonic life counter; a spawn, death or respawn invalidates any recorded trajectory. */
  private int lifeGeneration;

  /** One recorded emission instant: its frame-local time plus the captured kinematics. */
  private record TrajectorySample(float seconds, VesperRaider.ShotSample sample) {}

  /** Coarse dogfight phases: acquire a firing line, fly a committed pass, then reverse to re-engage. */
  enum Maneuver {
    APPROACH,
    ATTACK_PASS,
    INVERT
  }

  public RaiderEncounter() {
    this(ThreadLocalRandom.current()::nextFloat);
  }

  public RaiderEncounter(UnitRandom random) {
    if (random == null) {
      throw new IllegalArgumentException("Random source must not be null");
    }
    this.random = random;
    waitSeconds = nextWaitSeconds();
  }

  /** Whether the single raider is present and alive (not waiting and not destroyed). */
  public boolean isActive() {
    return raider != null && !defeated;
  }

  /** The single raider instance, or {@code null} during the initial wait. */
  public VesperRaider raider() {
    return raider;
  }

  /** True after the raider has been destroyed and before it is respawned. */
  public boolean isDefeated() {
    return defeated;
  }

  /** Current dogfight phase, exposed for deterministic domain tests only. */
  Maneuver maneuver() {
    return maneuver;
  }

  /** Current maneuver side: +1 right / -1 left, 0 when no side maneuver is active. */
  int separationSide() {
    return switch (maneuver) {
      case ATTACK_PASS -> passSide;
      case INVERT -> invertSide;
      case APPROACH -> 0;
    };
  }

  /** Current avoidance side: 0 when not avoiding, otherwise +1 right / -1 left; for tests only. */
  int avoidanceSide() {
    return avoidSide;
  }

  /** Normalized pass corridor direction X at the current (or last) committed pass; tests only. */
  float passDirectionX() {
    return passDirX;
  }

  /** Normalized pass corridor direction Y at the current (or last) committed pass; tests only. */
  float passDirectionY() {
    return passDirY;
  }

  /** World centre where the current (or last) committed pass started; tests only. */
  float passEntryX() {
    return passEntryX;
  }

  /** World centre where the current (or last) committed pass started; tests only. */
  float passEntryY() {
    return passEntryY;
  }

  /** Corridor length of the current (or last) committed pass; tests only. */
  float passLength() {
    return passLength;
  }

  /** Longitudinal progress along the current (or last) pass corridor; tests only. */
  float passProgress() {
    return (raider.centerX() - passEntryX) * passDirX
        + (raider.centerY() - passEntryY) * passDirY;
  }

  /** Side of the current (or last) reversal arc: +1 right, -1 left, 0 none; tests only. */
  int inversionSide() {
    return invertSide;
  }

  /** Degrees of heading already turned inside the current (or last) reversal arc; tests only. */
  float invertTurnAccumulated() {
    return invertTurnAccumulated;
  }

  /**
   * Clears the dogfight plan so a fresh life starts by acquiring a firing line at cruise speed with
   * no retained pass corridor, reversal side or avoidance side.
   */
  private void resetPlan() {
    maneuver = Maneuver.APPROACH;
    maneuverSeconds = 0f;
    passSide = 0;
    passDirX = 0f;
    passDirY = -1f;
    passEntryX = 0f;
    passEntryY = 0f;
    passLength = 0f;
    invertSide = 0;
    invertTurnAccumulated = 0f;
    approachReplanSeconds = 0f;
    approachAimHeadingDegrees = raider != null ? raider.headingDegrees() : 0f;
    avoidSide = 0;
    avoidHoldSeconds = 0f;
  }

  /** Gives the raider the phase's sprite sheet. */
  public void setVisuals(VesperRaider.Visuals visuals) {
    this.visuals = visuals;
    if (raider != null) {
      raider.setVisuals(visuals);
    }
  }

  /**
   * Marks the current raider destroyed without discarding its instance or visuals. It freezes its
   * last centre so the next {@link #respawnAt} can choose a genuinely different point.
   */
  public void destroyActive() {
    if (raider == null || defeated) {
      return;
    }
    defeated = true;
    deathCenterX = raider.centerX();
    deathCenterY = raider.centerY();
    lifeGeneration++;
    resetPlan();
  }

  /**
   * Repositions the existing raider at a new valid world point facing the target, clearing the
   * defeated flag. It never creates textures; the borrowed visuals stay attached to the instance.
   */
  public void respawnAt(WorldBounds world, float targetCenterX, float targetCenterY,
      float targetRadius) {
    if (world == null) {
      throw new IllegalArgumentException("World is required");
    }
    if (raider == null) {
      throw new IllegalStateException("The raider has not appeared yet");
    }
    if (!Float.isFinite(targetCenterX) || !Float.isFinite(targetCenterY)
        || !Float.isFinite(targetRadius) || targetRadius < 0f) {
      throw new IllegalArgumentException("Respawn target must be finite");
    }
    float half = raider.drawWidth() / 2f;
    float avoidTarget = RESPAWN_MIN_DISTANCE_FROM_TARGET + targetRadius + half;
    float bestCenterX = raider.centerX();
    float bestCenterY = raider.centerY();
    double bestMargin = Double.NEGATIVE_INFINITY;
    for (int attempt = 0; attempt < RESPAWN_ATTEMPTS; attempt++) {
      float centerX = (float) (random.nextUnit() * world.width());
      float centerY = (float) (random.nextUnit() * world.height());
      double fromTarget = Math.hypot(centerX - targetCenterX, centerY - targetCenterY);
      double fromDeath = Math.hypot(centerX - deathCenterX, centerY - deathCenterY);
      double margin = Math.min(fromTarget - avoidTarget,
          fromDeath - RESPAWN_MIN_DISTANCE_FROM_DEATH);
      if (margin > bestMargin) {
        bestMargin = margin;
        bestCenterX = centerX;
        bestCenterY = centerY;
      }
      if (margin >= 0d) {
        break;
      }
    }
    double headingRadians = Math.atan2(targetCenterX - bestCenterX, bestCenterY - targetCenterY);
    raider.placeAt(bestCenterX - half, bestCenterY - raider.drawHeight() / 2f,
        (float) Math.toDegrees(headingRadians), world);
    defeated = false;
    lifeGeneration++;
    resetPlan();
  }

  /**
   * Advances the encounter by {@code deltaSeconds} of game time toward {@code (targetX, targetY)}.
   * Non-positive or non-finite deltas are ignored; large deltas are integrated in bounded steps so
   * turns stay smooth and frame-rate independent.
   */
  public void advance(float deltaSeconds, WorldBounds world, float targetX, float targetY) {
    advance(deltaSeconds, world, targetX, targetY, 0f, 0f, 0f, 0f);
  }

  /**
   * Advances the encounter with enough target information for collision avoidance. The target
   * velocity and body radii are optional: when both radii are zero the encounter keeps the original
   * seek-only behaviour, which the deterministic unit tests use.
   *
   * @param targetVelocityX target world velocity X in units per second
   * @param targetVelocityY target world velocity Y in units per second
   * @param targetRadius target body radius, or zero to disable avoidance
   * @param selfRadius raider body radius, or zero to disable avoidance
   */
  public void advance(float deltaSeconds, WorldBounds world, float targetX, float targetY,
      float targetVelocityX, float targetVelocityY, float targetRadius, float selfRadius) {
    if (world == null) {
      throw new IllegalArgumentException("World is required");
    }
    if (!Float.isFinite(targetX) || !Float.isFinite(targetY)
        || !Float.isFinite(targetVelocityX) || !Float.isFinite(targetVelocityY)
        || !Float.isFinite(targetRadius) || !Float.isFinite(selfRadius)) {
      throw new IllegalArgumentException("Target position, velocity and radii must be finite");
    }
    if (deltaSeconds <= 0f || !Float.isFinite(deltaSeconds)) {
      return;
    }
    if (raider == null) {
      waitSeconds -= deltaSeconds;
      if (waitSeconds <= 0f) {
        spawn(world, targetX, targetY);
      }
      beginTrajectory();
      if (raider != null) {
        // It appeared at the end of this frame and has not moved yet: one snapshot is exact.
        recordTrajectory(deltaSeconds);
      }
      return;
    }
    beginTrajectory();
    if (defeated) {
      // The controller owns the 5 s respawn clock and repositions the raider with respawnAt.
      return;
    }
    recordTrajectory(0f);
    float elapsed = 0f;
    float remaining = deltaSeconds;
    while (remaining > 0f) {
      float step = Math.min(remaining, MAX_STEP_SECONDS);
      advanceStep(step, world, targetX, targetY, targetVelocityX, targetVelocityY,
          targetRadius, selfRadius);
      remaining -= step;
      elapsed += step;
      recordTrajectory(elapsed);
      if (remaining < 0.000001f) {
        break;
      }
    }
  }

  /** Starts a fresh trajectory for the frame, stamped with the current life generation. */
  private void beginTrajectory() {
    shotTrajectory.clear();
    shotTrajectoryGeneration = lifeGeneration;
  }

  /** Records the raider's current kinematics at a frame-local instant. */
  private void recordTrajectory(float seconds) {
    if (raider == null) {
      return;
    }
    shotTrajectory.add(new TrajectorySample(seconds, raider.captureShotSample()));
  }

  /**
   * Emission-time snapshot for the frame-local {@code offsetSeconds} of the trajectory recorded by
   * the last {@link #advance} call. The body centre is interpolated linearly along the substep that
   * contains the instant and the heading along its shortest arc, so a fast turn produces distinct
   * origins and directions per emission instead of repeating the final frame state; the muzzle pose
   * comes from the nearest recorded sample (a historical pose, never the live mutable one).
   *
   * <p>A death or respawn since the recording invalidates it, so the live raider state is returned as
   * a single snapshot and no shot is ever interpolated across a teleport. Returns {@code null} while
   * the raider has not appeared yet; out-of-range or non-finite offsets are clamped to the recording.
   */
  public GunBurst.Shot shotAt(double offsetSeconds, float range) {
    VesperRaider.ShotSample sample = shotSampleAt(offsetSeconds);
    return sample == null ? null : raider.shotAt(sample, range);
  }

  /**
   * The interpolated emission sample for a frame-local instant of the last recorded trajectory, or
   * {@code null} while the raider has not appeared. Exposed so the controller and the deterministic
   * tests can inspect the exact centre, heading and muzzle pose an emission at that instant used.
   */
  public VesperRaider.ShotSample shotSampleAt(double offsetSeconds) {
    VesperRaider raider = this.raider;
    if (raider == null) {
      return null;
    }
    if (shotTrajectory.isEmpty() || shotTrajectoryGeneration != lifeGeneration) {
      return raider.captureShotSample();
    }
    if (shotTrajectory.size() == 1) {
      return shotTrajectory.get(0).sample();
    }
    float seconds = Double.isFinite(offsetSeconds) ? (float) offsetSeconds : 0f;
    if (seconds <= 0f) {
      return shotTrajectory.get(0).sample();
    }
    TrajectorySample previous = shotTrajectory.get(0);
    for (int index = 1; index < shotTrajectory.size(); index++) {
      TrajectorySample next = shotTrajectory.get(index);
      if (seconds <= next.seconds()) {
        return interpolate(previous, next, seconds);
      }
      previous = next;
    }
    return shotTrajectory.get(shotTrajectory.size() - 1).sample();
  }

  /** Interpolates a sample between two neighbouring recorded substeps at a frame-local instant. */
  private static VesperRaider.ShotSample interpolate(TrajectorySample previous,
      TrajectorySample next, float seconds) {
    VesperRaider.ShotSample a = previous.sample();
    VesperRaider.ShotSample b = next.sample();
    float span = next.seconds() - previous.seconds();
    float fraction = span <= 0f ? 1f
        : Math.max(0f, Math.min(1f, (seconds - previous.seconds()) / span));
    float centerX = a.centerX() + (b.centerX() - a.centerX()) * fraction;
    float centerY = a.centerY() + (b.centerY() - a.centerY()) * fraction;
    float heading = normalizeYaw(a.headingDegrees()
        + normalizeYaw(b.headingDegrees() - a.headingDegrees()) * fraction);
    VesperRaiderSheet.Pose pose = fraction < 0.5f ? a.pose() : b.pose();
    return new VesperRaider.ShotSample(centerX, centerY, heading, pose);
  }

  private void advanceStep(float step, WorldBounds world, float targetX, float targetY,
      float targetVelocityX, float targetVelocityY, float targetRadius, float selfRadius) {
    updateManeuver(step, world, targetX, targetY, targetVelocityX, targetVelocityY);

    double selfX = raider.centerX();
    double selfY = raider.centerY();
    double dx = targetX - selfX;
    double dy = targetY - selfY;
    double distance = Math.hypot(dx, dy);
    double bearing = Math.atan2(dy, dx);
    boolean avoidanceAvailable = selfRadius + targetRadius > 0f && distance > 0.0001d;
    float horizon = maneuver == Maneuver.ATTACK_PASS
        ? PASS_AVOID_HORIZON_SECONDS : AVOID_HORIZON_SECONDS;
    float margin = maneuver == Maneuver.ATTACK_PASS
        ? PASS_AVOID_CONTACT_MARGIN : AVOID_CONTACT_MARGIN;
    boolean contactRisk = avoidanceAvailable
        && predictsContact(targetX, targetY, targetVelocityX, targetVelocityY,
            targetRadius, selfRadius, selfX, selfY, raider.currentSpeed(), horizon, margin);
    updateAvoidanceSide(step, contactRisk, bearing);

    float previousHeading = raider.headingDegrees();
    raider.steerTowards(desiredHeadingDegrees(world, targetX, targetY, targetVelocityX,
        targetVelocityY, targetRadius, selfRadius), MAX_TURN_RATE_DEGREES,
        MAX_ANGULAR_ACCELERATION_DEGREES, step);
    if (maneuver == Maneuver.INVERT) {
      invertTurnAccumulated += Math.abs(normalizeYaw(raider.headingDegrees() - previousHeading));
    }
    raider.advance(step, targetSpeed(), SPEED_ACCELERATION, SPEED_DECELERATION);
    raider.clampToWorld(world);
  }

  /**
   * Advances the dogfight phase from the current geometry: acquire a firing line while closing,
   * commit to a fixed high-speed corridor once close and aligned, then open space and reverse on a
   * fixed side to recover a useful line for another pass. The pass corridor is frozen at commit and
   * only the safety override (or its time budget) can invalidate it; the reversal is held on the
   * side chosen at pass exit until a useful alignment returns or its deadline expires.
   */
  private void updateManeuver(float step, WorldBounds world, float targetX, float targetY,
      float targetVelocityX, float targetVelocityY) {
    double dx = targetX - raider.centerX();
    double dy = targetY - raider.centerY();
    double distance = Math.hypot(dx, dy);
    double bearing = Math.atan2(dy, dx);
    maneuverSeconds += step;
    switch (maneuver) {
      case APPROACH -> {
        approachReplanSeconds -= step;
        if (approachReplanSeconds <= 0f) {
          approachReplanSeconds = APPROACH_REPLAN_SECONDS;
          double[] aim = interceptOffset(targetX, targetY, targetVelocityX, targetVelocityY);
          approachAimHeadingDegrees = headingFor(aim[0], aim[1]);
        }
        double error = Math.abs(
            normalizeYaw(approachAimHeadingDegrees - raider.headingDegrees()));
        if (distance <= PASS_COMMIT_DISTANCE && error <= PASS_COMMIT_ANGLE_DEGREES) {
          commitPass(targetX, targetY, targetVelocityX, targetVelocityY);
        }
      }
      case ATTACK_PASS -> {
        double progress = (raider.centerX() - passEntryX) * passDirX
            + (raider.centerY() - passEntryY) * passDirY;
        boolean crossedExit = progress >= passLength;
        if ((crossedExit && maneuverSeconds >= PASS_MIN_SECONDS)
            || maneuverSeconds >= PASS_MAX_SECONDS) {
          beginInvert(world, bearing);
        }
      }
      case INVERT -> {
        boolean aligned = maneuverSeconds >= SEPARATE_SECONDS
            && Math.abs(normalizeYaw(headingFor(Math.cos(bearing), Math.sin(bearing))
                - raider.headingDegrees())) <= INVERT_ALIGN_DEGREES;
        if (aligned || maneuverSeconds >= INVERT_MAX_SECONDS) {
          maneuver = Maneuver.APPROACH;
          maneuverSeconds = 0f;
          approachReplanSeconds = 0f; // a fresh life (or a new pass) re-acquires immediately
        }
      }
    }
  }

  private void spawn(WorldBounds world, float targetX, float targetY) {
    float lateral = (random.nextUnit() * 2f - 1f) * SPAWN_LATERAL_DISTANCE;
    float x = world.clampX(targetX + lateral, RAIDER_WIDTH);
    float y = world.clampY(targetY + SPAWN_AHEAD_DISTANCE, RAIDER_HEIGHT);
    raider = new VesperRaider(CRUISE_SPEED, RAIDER_WIDTH, RAIDER_HEIGHT, x, y, 0f);
    raider.setVisuals(visuals);
    lifeGeneration++;
    resetPlan();
  }

  /** Freezes the pass corridor at the commit instant: entry point, normalized direction, length. */
  private void commitPass(float targetX, float targetY, float targetVelocityX,
      float targetVelocityY) {
    maneuver = Maneuver.ATTACK_PASS;
    maneuverSeconds = 0f;
    passEntryX = raider.centerX();
    passEntryY = raider.centerY();
    double[] offset = interceptOffset(targetX, targetY, targetVelocityX, targetVelocityY);
    double magnitude = Math.hypot(offset[0], offset[1]);
    if (magnitude < 1e-6d) {
      passDirX = 0f;
      passDirY = -1f;
    } else {
      passDirX = (float) (offset[0] / magnitude);
      passDirY = (float) (offset[1] / magnitude);
    }
    double distance = Math.hypot(targetX - passEntryX, targetY - passEntryY);
    passLength = Math.max(MIN_PASS_LENGTH, (float) distance + PASS_EXIT_OVERSHOOT);
    passSide = chooseSeparationSide(Math.atan2(targetY - passEntryY, targetX - passEntryX));
  }

  /** Opens the reversal phase: the separation segment runs first; the side is fixed once. */
  private void beginInvert(WorldBounds world, double bearing) {
    maneuver = Maneuver.INVERT;
    maneuverSeconds = 0f;
    invertTurnAccumulated = 0f;
    invertSide = chooseInversionSide(world, bearing);
  }

  /**
   * Combines the unit direction of the current phase intention, the retained avoidance side and any
   * inward border response, then converts the resulting world vector to the raider's
   * descent-relative heading. The per-phase intention is deterministic: APPROACH steers the
   * persistent aim heading, ATTACK_PASS holds the frozen corridor, INVERT first continues the exit
   * corridor to open space and then forces a sustained arc on the inversion side until the bearing
   * is recovered.
   */
  private float desiredHeadingDegrees(WorldBounds world, float targetX, float targetY,
      float targetVelocityX, float targetVelocityY, float targetRadius, float selfRadius) {
    double selfX = raider.centerX();
    double selfY = raider.centerY();
    double dx = targetX - selfX;
    double dy = targetY - selfY;
    double bearing = Math.atan2(dy, dx);

    double desiredX = 0d;
    double desiredY = 0d;
    if (avoidSide != 0) {
      double escape = bearing + avoidSide * Math.PI / 2d;
      desiredX = Math.cos(escape);
      desiredY = Math.sin(escape);
    } else {
      switch (maneuver) {
        case ATTACK_PASS -> {
          desiredX = passDirX;
          desiredY = passDirY;
        }
        case INVERT -> {
          if (maneuverSeconds < SEPARATE_SECONDS) {
            // Keep flying past the exit point before starting the reversal: opens real space.
            desiredX = passDirX;
            desiredY = passDirY;
          } else {
            float heading = raider.headingDegrees();
            float bearingHeading = headingFor(Math.cos(bearing), Math.sin(bearing));
            float error = normalizeYaw(bearingHeading - heading);
            if (invertSide != 0 && Math.abs(error) >= 175f
                && ((error > 0f) != (invertSide > 0f))) {
              // Near the 180-degree ambiguity: force the arc through the previously chosen side.
              double forced = Math.toRadians(normalizeYaw(heading + invertSide * 90f));
              desiredX = Math.sin(forced);
              desiredY = -Math.cos(forced);
            } else {
              desiredX = Math.cos(bearing);
              desiredY = Math.sin(bearing);
            }
          }
        }
        case APPROACH -> {
          double radians = Math.toRadians(approachAimHeadingDegrees);
          desiredX = Math.sin(radians);
          desiredY = -Math.cos(radians);
        }
      }
    }
    double magnitude = Math.hypot(desiredX, desiredY);
    if (magnitude < 0.0001d) {
      return raider.headingDegrees();
    }
    desiredX = desiredX / magnitude + BORDER_WEIGHT * inwardX(world);
    desiredY = desiredY / magnitude + BORDER_WEIGHT * inwardY(world);
    if (desiredX == 0d && desiredY == 0d) {
      return raider.headingDegrees();
    }
    return (float) Math.toDegrees(Math.atan2(desiredX, -desiredY));
  }

  /** Speed target for the current step: a single bounded target per phase, no turbo policy. */
  private float targetSpeed() {
    if (maneuver == Maneuver.ATTACK_PASS) {
      return PASS_SPEED;
    }
    if (maneuver == Maneuver.INVERT) {
      return REENGAGE_SPEED;
    }
    return CRUISE_SPEED;
  }

  /**
   * World vector from the raider centre toward the bounded ballistic intercept of the target: the
   * positive root of {@code |r + v·t| = projectileSpeed·t} using the real projectile speed, so a
   * crossing player is met where the bullets will be. When no positive bounded solution exists (or
   * the target barely moves) it falls back to the direct bearing lead capped at
   * {@link #INTERCEPT_HORIZON_SECONDS}. The returned offset is an unnormalized world direction.
   */
  private double[] interceptOffset(float targetX, float targetY, float targetVelocityX,
      float targetVelocityY) {
    double rx = targetX - raider.centerX();
    double ry = targetY - raider.centerY();
    double projectileSpeed = GunTuning.PROJECTILE_SPEED;
    double a = targetVelocityX * targetVelocityX + targetVelocityY * targetVelocityY
        - projectileSpeed * projectileSpeed;
    double b = 2d * (rx * targetVelocityX + ry * targetVelocityY);
    double c = rx * rx + ry * ry;
    double time = -1d;
    if (Math.abs(a) < 1e-6d) {
      if (Math.abs(b) > 1e-6d) {
        double candidate = -c / b;
        if (candidate > 0d) {
          time = candidate;
        }
      }
    } else {
      double discriminant = b * b - 4d * a * c;
      if (discriminant >= 0d) {
        double root = Math.sqrt(discriminant);
        double first = (-b - root) / (2d * a);
        double second = (-b + root) / (2d * a);
        double best = Double.POSITIVE_INFINITY;
        if (first > 0d && first < best) {
          best = first;
        }
        if (second > 0d && second < best) {
          best = second;
        }
        if (best < Double.POSITIVE_INFINITY) {
          time = best;
        }
      }
    }
    if (time < 0d || !Double.isFinite(time)) {
      double distance = Math.hypot(rx, ry);
      time = Math.min(INTERCEPT_HORIZON_SECONDS, distance <= 0d ? INTERCEPT_HORIZON_SECONDS
          : distance / projectileSpeed);
    }
    time = Math.min(time, MAX_INTERCEPT_LEAD_SECONDS);
    return new double[] {targetX + targetVelocityX * time - raider.centerX(),
        targetY + targetVelocityY * time - raider.centerY()};
  }

  /** Prediction of the closest approach within the avoidance horizon using relative motion. */
  private boolean predictsContact(float targetX, float targetY, float targetVelocityX,
      float targetVelocityY, float targetRadius, float selfRadius, double selfX, double selfY,
      float selfSpeed, float horizon, float margin) {
    double heading = Math.toRadians(raider.headingDegrees());
    double selfVelocityX = Math.sin(heading) * selfSpeed;
    double selfVelocityY = -Math.cos(heading) * selfSpeed;
    double relativeX = selfX - targetX;
    double relativeY = selfY - targetY;
    double relativeVelocityX = selfVelocityX - targetVelocityX;
    double relativeVelocityY = selfVelocityY - targetVelocityY;
    double speedSquared = relativeVelocityX * relativeVelocityX
        + relativeVelocityY * relativeVelocityY;
    double time = 0d;
    if (speedSquared > 1e-6d) {
      time = -(relativeX * relativeVelocityX + relativeY * relativeVelocityY) / speedSquared;
      time = Math.max(0d, Math.min(horizon, time));
    }
    double closestX = relativeX + relativeVelocityX * time;
    double closestY = relativeY + relativeVelocityY * time;
    double contactDistance = selfRadius + targetRadius + margin;
    return Math.hypot(closestX, closestY) <= contactDistance;
  }

  /** Chooses and retains the avoidance side, picking the turn that costs the least heading change. */
  private void updateAvoidanceSide(float step, boolean contactRisk, double bearing) {
    if (contactRisk) {
      if (avoidSide == 0) {
        avoidSide = chooseAvoidSide(bearing);
      }
      avoidHoldSeconds = AVOID_MIN_HOLD_SECONDS;
      return;
    }
    if (avoidSide != 0) {
      avoidHoldSeconds -= step;
      if (avoidHoldSeconds <= 0f) {
        avoidSide = 0;
        avoidHoldSeconds = 0f;
      }
    }
  }

  /**
   * Least-turn perpendicular side to a bearing: +1 for the candidate reached by adding a quarter
   * turn to the mathematical bearing, -1 for subtracting it, 0 when both cost the same.
   *
   * <p>The bearing is a mathematical angle ({@code atan2(dy, dx)}, x-east / y-north, CCW) while the
   * raider heading uses the descent convention (0 points down, positive turns right / clockwise).
   * Each candidate world direction is therefore converted with
   * {@code headingFor(vx, vy) = degrees(atan2(vx, -vy))} before the cost is compared; the previous
   * direct comparison of {@code degrees(bearing)} against the descent heading mixed both frames.
   */
  private int leastTurnSide(double bearing) {
    float heading = raider.headingDegrees();
    float counterClockwise = headingFor(Math.cos(bearing + Math.PI / 2d),
        Math.sin(bearing + Math.PI / 2d));
    float clockwise = headingFor(Math.cos(bearing - Math.PI / 2d), Math.sin(bearing - Math.PI / 2d));
    float counterClockwiseCost = Math.abs(normalizeYaw(counterClockwise - heading));
    float clockwiseCost = Math.abs(normalizeYaw(clockwise - heading));
    if (Math.abs(counterClockwiseCost - clockwiseCost) < 0.001f) {
      return 0;
    }
    return counterClockwiseCost < clockwiseCost ? 1 : -1;
  }

  /** Converts a world direction into the raider's descent heading: 0 points down, + turns right. */
  static float headingFor(double directionX, double directionY) {
    return (float) Math.toDegrees(Math.atan2(directionX, -directionY));
  }

  /** Least-turn perpendicular side for avoidance; a tie always breaks to the right. */
  private int chooseAvoidSide(double bearing) {
    int side = leastTurnSide(bearing);
    return side != 0 ? side : 1;
  }

  /** Least-turn perpendicular side used to displace the committed pass; a tie falls back to right. */
  private int chooseSeparationSide(double bearing) {
    int side = leastTurnSide(bearing);
    return side != 0 ? side : 1;
  }

  /**
   * Side of the reversal arc, chosen once at pass exit: the shortest turn that faces the target
   * again (a straight-back 180-degree tie defaults to the right), then flipped when the tangent of
   * the chosen side points into a world border that is close, so the raider never starts its
   * reversal into a wall.
   */
  private int chooseInversionSide(WorldBounds world, double bearing) {
    float heading = raider.headingDegrees();
    float bearingHeading = headingFor(Math.cos(bearing), Math.sin(bearing));
    float error = normalizeYaw(bearingHeading - heading);
    int side = error >= 0f ? 1 : -1;
    double radians = Math.toRadians(heading);
    double tangentX = Math.cos(radians);
    double tangentY = Math.sin(radians);
    if (side < 0) {
      tangentX = -tangentX;
      tangentY = -tangentY;
    }
    float rightGap = world.maxX(raider.drawWidth()) - raider.x();
    float topGap = world.maxY(raider.drawHeight()) - raider.y();
    if ((raider.x() < BORDER_ZONE && tangentX < 0d) || (rightGap < BORDER_ZONE && tangentX > 0d)) {
      side = -side;
    }
    if ((raider.y() < BORDER_ZONE && tangentY < 0d) || (topGap < BORDER_ZONE && tangentY > 0d)) {
      side = -side;
    }
    return side;
  }

  private float inwardX(WorldBounds world) {
    float inward = 0f;
    if (raider.x() < BORDER_ZONE) {
      inward += (BORDER_ZONE - raider.x()) / BORDER_ZONE;
    }
    float right = world.maxX(raider.drawWidth()) - raider.x();
    if (right < BORDER_ZONE) {
      inward -= (BORDER_ZONE - right) / BORDER_ZONE;
    }
    return inward;
  }

  private float inwardY(WorldBounds world) {
    float inward = 0f;
    if (raider.y() < BORDER_ZONE) {
      inward += (BORDER_ZONE - raider.y()) / BORDER_ZONE;
    }
    float top = world.maxY(raider.drawHeight()) - raider.y();
    if (top < BORDER_ZONE) {
      inward -= (BORDER_ZONE - top) / BORDER_ZONE;
    }
    return inward;
  }

  private static float normalizeYaw(float yaw) {
    float normalized = yaw % 360f;
    if (normalized > 180f) normalized -= 360f;
    if (normalized <= -180f) normalized += 360f;
    return normalized;
  }

  private float nextWaitSeconds() {
    return MIN_SPAWN_WAIT_SECONDS
        + random.nextUnit() * (MAX_SPAWN_WAIT_SECONDS - MIN_SPAWN_WAIT_SECONDS);
  }
}