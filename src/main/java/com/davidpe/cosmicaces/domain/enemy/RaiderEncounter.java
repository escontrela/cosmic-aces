package com.davidpe.cosmicaces.domain.enemy;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps the single Vesper Raider of a phase alive in world coordinates. After one random wait the
 * raider appears once ahead of the player's route, then seeks Astra with bounded turns and
 * occasional reproducible drift from {@link UnitRandom}; it never respawns, is never retired for
 * leaving the camera, and stays inside the finite world with a soft border response.
 *
 * <p>Timing, speed and drift values are initial technical choices configurable for the PO visual QA;
 * they are not product criteria.
 */
public final class RaiderEncounter {

  /** Raider speed in world units per second. */
  public static final float RAIDER_SPEED = 240f;
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
  public static final float MIN_DRIFT_INTERVAL_SECONDS = 1.3f;
  public static final float MAX_DRIFT_INTERVAL_SECONDS = 2.7f;
  /** Maximum deviation added to the bearing toward the player, in degrees. */
  public static final float MAX_DRIFT_DEGREES = 35f;
  /** Distance from a world edge where the inward steering response starts fading in. */
  public static final float BORDER_ZONE = 1400f;
  /** Relative strength of the inward border response against the unit bearing to the player. */
  private static final float BORDER_WEIGHT = 1.5f;
  private static final float MAX_STEP_SECONDS = 1f / 120f;

  private final UnitRandom random;
  private VesperRaider raider;
  private VesperRaider.Visuals visuals;
  private float waitSeconds;
  private float driftSeconds;
  private float driftDegrees;

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

  /** Whether the single raider has already appeared. */
  public boolean isActive() {
    return raider != null;
  }

  /** The single raider, or {@code null} during the initial wait. */
  public VesperRaider raider() {
    return raider;
  }

  /** Gives the raider the phase's sprite sheet. */
  public void setVisuals(VesperRaider.Visuals visuals) {
    this.visuals = visuals;
    if (raider != null) {
      raider.setVisuals(visuals);
    }
  }

  /**
   * Advances the encounter by {@code deltaSeconds} of game time toward {@code (targetX, targetY)}.
   * Non-positive or non-finite deltas are ignored; large deltas are integrated in bounded steps so
   * turns and drift stay smooth and frame-rate independent.
   */
  public void advance(float deltaSeconds, WorldBounds world, float targetX, float targetY) {
    if (world == null) {
      throw new IllegalArgumentException("World is required");
    }
    if (!Float.isFinite(targetX) || !Float.isFinite(targetY)) {
      throw new IllegalArgumentException("Target position must be finite");
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
    float remaining = deltaSeconds;
    while (remaining > 0f) {
      float step = Math.min(remaining, MAX_STEP_SECONDS);
      advanceStep(step, world, targetX, targetY);
      remaining -= step;
      if (remaining < 0.000001f) {
        break;
      }
    }
  }

  private void advanceStep(float step, WorldBounds world, float targetX, float targetY) {
    driftSeconds -= step;
    if (driftSeconds <= 0f) {
      driftDegrees = nextDriftDegrees();
      driftSeconds = nextDriftSeconds();
    }
    raider.setDriftDirection(Float.compare(driftDegrees, 0f));
    raider.steerTowards(desiredHeadingDegrees(world, targetX, targetY),
        MAX_TURN_RATE_DEGREES * step);
    raider.advance(step);
    raider.clampToWorld(world);
  }

  private void spawn(WorldBounds world, float targetX, float targetY) {
    float lateral = (random.nextUnit() * 2f - 1f) * SPAWN_LATERAL_DISTANCE;
    float x = world.clampX(targetX + lateral, RAIDER_WIDTH);
    float y = world.clampY(targetY + SPAWN_AHEAD_DISTANCE, RAIDER_HEIGHT);
    raider = new VesperRaider(RAIDER_SPEED, RAIDER_WIDTH, RAIDER_HEIGHT, x, y, 0f);
    raider.setVisuals(visuals);
    driftDegrees = nextDriftDegrees();
    driftSeconds = nextDriftSeconds();
  }

  /**
   * Combines the unit bearing toward the player, the current drift and any inward border response,
   * then converts the resulting world vector to the raider's descent-relative heading.
   */
  private float desiredHeadingDegrees(WorldBounds world, float targetX, float targetY) {
    float dx = targetX - raider.x();
    float dy = targetY - raider.y();
    float vx = 0f;
    float vy = 0f;
    double magnitude = Math.hypot(dx, dy);
    if (magnitude > 0.0001d) {
      double angle = Math.atan2(dy, dx) + Math.toRadians(driftDegrees);
      vx = (float) Math.cos(angle);
      vy = (float) Math.sin(angle);
    }
    vx += BORDER_WEIGHT * inwardX(world);
    vy += BORDER_WEIGHT * inwardY(world);
    if (vx == 0f && vy == 0f) {
      return raider.headingDegrees();
    }
    return (float) Math.toDegrees(Math.atan2(vx, -vy));
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
