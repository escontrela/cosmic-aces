package com.davidpe.cosmicaces.domain.enemy;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps the single Vesper Raider of a phase alive in world coordinates. After one random wait the
 * raider appears once ahead of the player's route, then seeks Astra with bounded turns and
 * occasional reproducible drift from {@link UnitRandom}. It is slower and steadier than the earlier
 * tuning and, when a collision is predicted within a short horizon and body radii are supplied, it
 * turns perpendicular to the bearing on a retained side to avoid ramming while staying near enough
 * to fire.
 *
 * <p>Its dogfight now has explicit phases: it closes in on an intercept course ({@code APPROACH}),
 * commits to a bounded curved firing pass once it crosses the attack band ({@code ATTACK_PASS}), then
 * disengages sideways for a limited time ({@code REPOSITION}) before lining up another pass. This
 * gives the player a recognizable second pass instead of an endless chase; collision avoidance and
 * the inward border response remain safety overrides on top of the phases.
 *
 * <p>A destroyed raider keeps its instance (and its borrowed visuals) and stops moving until the
 * controller respawns it elsewhere. Timing, speed, drift and avoidance values are technical choices
 * configurable for the PO visual QA; they are not product criteria.
 */
public final class RaiderEncounter {

  /** Raider speed in world units per second. */
  public static final float RAIDER_SPEED = 180f;
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
  public static final float MAX_TURN_RATE_DEGREES = 75f;
  /** Random interval between drift changes, in seconds. */
  public static final float MIN_DRIFT_INTERVAL_SECONDS = 4f;
  public static final float MAX_DRIFT_INTERVAL_SECONDS = 6f;
  /** Maximum deviation added to the bearing toward the player, in degrees. */
  public static final float MAX_DRIFT_DEGREES = 10f;
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
  /** Distance below which the raider circles instead of driving straight at the player. */
  public static final float MIN_ATTACK_DISTANCE = 300f;
  /** Bounded lead used to plot an intercept course toward the player's current motion. */
  public static final float INTERCEPT_HORIZON_SECONDS = 1f;
  /** Distance at or below which the raider commits to a firing pass instead of only closing in. */
  public static final float ATTACK_PASS_ENTER_DISTANCE = 350f;
  /** Longest a firing pass lasts before the raider breaks off to line up another one. */
  public static final float ATTACK_PASS_MAX_SECONDS = 1.8f;
  /** Shortest reposition loop, so a grazing pass is not immediately abandoned. */
  public static final float REPOSITION_MIN_SECONDS = 0.6f;
  /** Longest reposition loop before the raider forces a new approach. */
  public static final float REPOSITION_MAX_SECONDS = 2.5f;

  /** Minimum centre separation from Astra when the raider respawns. */
  public static final float RESPAWN_MIN_DISTANCE_FROM_TARGET = 500f;
  /** Minimum centre separation from the previous death spot, so the respawn is a new point. */
  public static final float RESPAWN_MIN_DISTANCE_FROM_DEATH = 800f;
  private static final int RESPAWN_ATTEMPTS = 24;

  private final UnitRandom random;
  private VesperRaider raider;
  private VesperRaider.Visuals visuals;
  private float waitSeconds;
  private float driftSeconds;
  private float driftDegrees;
  private boolean defeated;
  private float deathCenterX;
  private float deathCenterY;
  private int avoidSide;
  private float avoidHoldSeconds;
  private Maneuver maneuver = Maneuver.APPROACH;
  private float maneuverSeconds;
  private int repositionSide;

  /** Coarse dogfight phases: close in, fire a pass, then disengage to line up the next pass. */
  enum Maneuver {
    APPROACH,
    ATTACK_PASS,
    REPOSITION
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

  /** Clears the dogfight plan so a fresh life starts by closing in again. */
  private void resetManeuver() {
    maneuver = Maneuver.APPROACH;
    maneuverSeconds = 0f;
    repositionSide = 0;
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
    avoidSide = 0;
    avoidHoldSeconds = 0f;
    resetManeuver();
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
    driftDegrees = nextDriftDegrees();
    driftSeconds = nextDriftSeconds();
    avoidSide = 0;
    avoidHoldSeconds = 0f;
    resetManeuver();
  }

  /**
   * Advances the encounter by {@code deltaSeconds} of game time toward {@code (targetX, targetY)}.
   * Non-positive or non-finite deltas are ignored; large deltas are integrated in bounded steps so
   * turns and drift stay smooth and frame-rate independent.
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
      return;
    }
    if (defeated) {
      // The controller owns the 5 s respawn clock and repositions the raider with respawnAt.
      return;
    }
    float remaining = deltaSeconds;
    while (remaining > 0f) {
      float step = Math.min(remaining, MAX_STEP_SECONDS);
      advanceStep(step, world, targetX, targetY, targetVelocityX, targetVelocityY,
          targetRadius, selfRadius);
      remaining -= step;
      if (remaining < 0.000001f) {
        break;
      }
    }
  }

  private void advanceStep(float step, WorldBounds world, float targetX, float targetY,
      float targetVelocityX, float targetVelocityY, float targetRadius, float selfRadius) {
    driftSeconds -= step;
    if (driftSeconds <= 0f) {
      driftDegrees = nextDriftDegrees();
      driftSeconds = nextDriftSeconds();
    }
    raider.setDriftDirection(Float.compare(driftDegrees, 0f));
    updateManeuver(step, targetX, targetY);
    raider.steerTowards(desiredHeadingDegrees(world, targetX, targetY, targetVelocityX,
        targetVelocityY, targetRadius, selfRadius, step), MAX_TURN_RATE_DEGREES * step);
    raider.advance(step);
    raider.clampToWorld(world);
  }

  /**
   * Advances the coarse dogfight phase from the current centre distance to the player. Crossing the
   * attack band commits a bounded firing pass; once that pass ends the raider disengages sideways
   * for a limited time and then closes in again, so the player gets a recognizable second pass
   * instead of an endless chase. Collision avoidance stays a separate safety override.
   */
  private void updateManeuver(float step, float targetX, float targetY) {
    double dx = targetX - raider.centerX();
    double dy = targetY - raider.centerY();
    double distance = Math.hypot(dx, dy);
    maneuverSeconds += step;
    switch (maneuver) {
      case APPROACH -> {
        if (distance <= ATTACK_PASS_ENTER_DISTANCE) {
          maneuver = Maneuver.ATTACK_PASS;
          maneuverSeconds = 0f;
        }
      }
      case ATTACK_PASS -> {
        if (distance > ATTACK_PASS_ENTER_DISTANCE
            || maneuverSeconds >= ATTACK_PASS_MAX_SECONDS) {
          maneuver = Maneuver.REPOSITION;
          maneuverSeconds = 0f;
          repositionSide = chooseSeparationSide(Math.atan2(dy, dx));
        }
      }
      case REPOSITION -> {
        boolean separated = maneuverSeconds >= REPOSITION_MIN_SECONDS
            && distance >= ATTACK_PASS_ENTER_DISTANCE;
        if (separated || maneuverSeconds >= REPOSITION_MAX_SECONDS) {
          maneuver = Maneuver.APPROACH;
          maneuverSeconds = 0f;
        }
      }
    }
  }

  private void spawn(WorldBounds world, float targetX, float targetY) {
    float lateral = (random.nextUnit() * 2f - 1f) * SPAWN_LATERAL_DISTANCE;
    float x = world.clampX(targetX + lateral, RAIDER_WIDTH);
    float y = world.clampY(targetY + SPAWN_AHEAD_DISTANCE, RAIDER_HEIGHT);
    raider = new VesperRaider(RAIDER_SPEED, RAIDER_WIDTH, RAIDER_HEIGHT, x, y, 0f);
    raider.setVisuals(visuals);
    driftDegrees = nextDriftDegrees();
    driftSeconds = nextDriftSeconds();
    resetManeuver();
  }

  /**
   * Combines the unit bearing toward the player, the current drift, the retained avoidance side and
   * any inward border response, then converts the resulting world vector to the raider's
   * descent-relative heading.
   */
  private float desiredHeadingDegrees(WorldBounds world, float targetX, float targetY,
      float targetVelocityX, float targetVelocityY, float targetRadius, float selfRadius,
      float step) {
    double selfX = raider.centerX();
    double selfY = raider.centerY();
    double dx = targetX - selfX;
    double dy = targetY - selfY;
    double distance = Math.hypot(dx, dy);
    double bearing = Math.atan2(dy, dx);

    boolean avoidanceAvailable = selfRadius + targetRadius > 0f && distance > 0.0001d;
    boolean contactRisk = avoidanceAvailable
        && predictsContact(targetX, targetY, targetVelocityX, targetVelocityY,
            targetRadius, selfRadius, selfX, selfY);
    updateAvoidanceSide(step, contactRisk, bearing, targetX, targetY, targetVelocityX,
        targetVelocityY, selfX, selfY);

    double desiredX;
    double desiredY;
    if (avoidSide != 0) {
      double escape = bearing + avoidSide * Math.PI / 2d;
      desiredX = Math.cos(escape);
      desiredY = Math.sin(escape);
    } else if (maneuver == Maneuver.REPOSITION) {
      // Disengage sideways along the retained least-turn side to open up for the next pass.
      double separation = bearing + repositionSide * Math.PI / 2d
          + Math.toRadians(driftDegrees);
      desiredX = Math.cos(separation);
      desiredY = Math.sin(separation);
    } else if (maneuver == Maneuver.ATTACK_PASS) {
      double angle = bearing + Math.toRadians(driftDegrees);
      desiredX = Math.cos(angle);
      desiredY = Math.sin(angle);
      if (avoidanceAvailable && distance < MIN_ATTACK_DISTANCE) {
        // Circle to keep a firing standoff instead of driving straight into the player.
        double side = driftDegrees >= 0f ? 1d : -1d;
        double orbit = bearing + side * Math.PI / 2d;
        desiredX += Math.cos(orbit);
        desiredY += Math.sin(orbit);
      }
    } else {
      // APPROACH: lead the intercept by at most one second so a crossing player is met, not chased.
      double leadX = targetX + targetVelocityX * INTERCEPT_HORIZON_SECONDS;
      double leadY = targetY + targetVelocityY * INTERCEPT_HORIZON_SECONDS;
      double interceptX = leadX - selfX;
      double interceptY = leadY - selfY;
      if (interceptX * interceptX + interceptY * interceptY < 1e-6d) {
        interceptX = dx;
        interceptY = dy;
      }
      double angle = Math.atan2(interceptY, interceptX) + Math.toRadians(driftDegrees);
      desiredX = Math.cos(angle);
      desiredY = Math.sin(angle);
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

  /** Prediction of the closest approach within the avoidance horizon using relative motion. */
  private boolean predictsContact(float targetX, float targetY, float targetVelocityX,
      float targetVelocityY, float targetRadius, float selfRadius, double selfX, double selfY) {
    double heading = Math.toRadians(raider.headingDegrees());
    double selfVelocityX = Math.sin(heading) * RAIDER_SPEED;
    double selfVelocityY = -Math.cos(heading) * RAIDER_SPEED;
    double relativeX = selfX - targetX;
    double relativeY = selfY - targetY;
    double relativeVelocityX = selfVelocityX - targetVelocityX;
    double relativeVelocityY = selfVelocityY - targetVelocityY;
    double speedSquared = relativeVelocityX * relativeVelocityX
        + relativeVelocityY * relativeVelocityY;
    double time = 0d;
    if (speedSquared > 1e-6d) {
      time = -(relativeX * relativeVelocityX + relativeY * relativeVelocityY) / speedSquared;
      time = Math.max(0d, Math.min(AVOID_HORIZON_SECONDS, time));
    }
    double closestX = relativeX + relativeVelocityX * time;
    double closestY = relativeY + relativeVelocityY * time;
    double contactDistance = selfRadius + targetRadius + AVOID_CONTACT_MARGIN;
    return Math.hypot(closestX, closestY) <= contactDistance;
  }

  /** Chooses and retains the avoidance side, picking the turn that costs the least heading change. */
  private void updateAvoidanceSide(float step, boolean contactRisk, double bearing,
      float targetX, float targetY, float targetVelocityX, float targetVelocityY,
      double selfX, double selfY) {
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

  /** Least-turn perpendicular side to a bearing: +1 left, -1 right, 0 when both cost the same. */
  private int leastTurnSide(double bearing) {
    float heading = raider.headingDegrees();
    float left = normalizeYaw((float) Math.toDegrees(bearing + Math.PI / 2d));
    float right = normalizeYaw((float) Math.toDegrees(bearing - Math.PI / 2d));
    float leftCost = Math.abs(normalizeYaw(left - heading));
    float rightCost = Math.abs(normalizeYaw(right - heading));
    if (Math.abs(leftCost - rightCost) < 0.001f) {
      return 0;
    }
    return leftCost < rightCost ? 1 : -1;
  }

  /** Least-turn perpendicular side; a tie falls back to the drift side, then to the right. */
  private int chooseAvoidSide(double bearing) {
    int side = leastTurnSide(bearing);
    if (side != 0) {
      return side;
    }
    if (driftDegrees > 0f) return 1;
    if (driftDegrees < 0f) return -1;
    return 1;
  }

  /** Least-turn perpendicular side used to disengage; a tie falls back to the right. */
  private int chooseSeparationSide(double bearing) {
    int side = leastTurnSide(bearing);
    return side != 0 ? side : 1;
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

  private float nextDriftSeconds() {
    return MIN_DRIFT_INTERVAL_SECONDS
        + random.nextUnit() * (MAX_DRIFT_INTERVAL_SECONDS - MIN_DRIFT_INTERVAL_SECONDS);
  }

  private float nextDriftDegrees() {
    return (random.nextUnit() * 2f - 1f) * MAX_DRIFT_DEGREES;
  }
}
