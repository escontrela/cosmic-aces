package com.davidpe.cosmicaces.domain.enemy;

import com.davidpe.cosmicaces.domain.game.PlayArea;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Schedules and advances autonomous Vesper Raider encounters during the playable run. The cycle is
 * a random wait, a single active raider descending across the play area, and retirement once the
 * whole raider box has left the area, after which a new random wait starts. At most one raider is
 * active at any time and the raider never reads the ship position.
 *
 * <p>Timing and trajectory values are initial technical choices, configurable for the PO visual QA;
 * they are not product criteria.
 */
public final class RaiderEncounter {

  /** Descent speed of the raider in world units per second. */
  public static final float DESCENT_SPEED = 140f;
  /** World size of the raider box used for spawn/retirement and matched by the sprite drawing. */
  public static final float RAIDER_WIDTH = 80f;
  public static final float RAIDER_HEIGHT = 80f;
  /** Random wait between a retired raider and the next spawn, in seconds. */
  public static final float MIN_WAIT_SECONDS = 3f;
  public static final float MAX_WAIT_SECONDS = 7f;
  /** Random interval between bounded heading changes, in seconds. */
  public static final float MIN_TURN_INTERVAL_SECONDS = 0.8f;
  public static final float MAX_TURN_INTERVAL_SECONDS = 1.4f;
  /** Maximum heading deviation from straight descent, in degrees. */
  public static final float MAX_HEADING_DEGREES = 20f;

  private enum Phase {
    WAITING,
    ACTIVE
  }

  private final UnitRandom random;
  private Phase phase = Phase.WAITING;
  private float waitSeconds;
  private float turnSeconds;
  private VesperRaider raider;

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

  public boolean isActive() {
    return phase == Phase.ACTIVE;
  }

  /** The active raider, or {@code null} while the encounter is waiting. */
  public VesperRaider raider() {
    return raider;
  }

  /** Horizontal position of the active raider, or 0 while waiting. */
  public float raiderX() {
    return raider == null ? 0f : raider.x();
  }

  /** Vertical position of the active raider, or 0 while waiting. */
  public float raiderY() {
    return raider == null ? 0f : raider.y();
  }

  /** Bank of the active raider (-1/0/+1), or 0 while waiting. */
  public int raiderBank() {
    return raider == null ? 0 : raider.bank();
  }

  /**
   * Advances the encounter by {@code deltaSeconds} in game time. Non-positive or non-finite deltas
   * are ignored; large deltas consume waits and heading intervals in bounded steps.
   */
  public void advance(float deltaSeconds, PlayArea area) {
    if (deltaSeconds <= 0f || !Float.isFinite(deltaSeconds)) {
      return;
    }
    if (phase == Phase.WAITING) {
      waitSeconds -= deltaSeconds;
      if (waitSeconds <= 0f) {
        spawn(area);
      }
      return;
    }
    raider.advance(deltaSeconds);
    turnSeconds -= deltaSeconds;
    while (turnSeconds <= 0f) {
      changeHeading();
      turnSeconds += nextTurnSeconds();
    }
    if (fullyOutside(area)) {
      phase = Phase.WAITING;
      raider = null;
      waitSeconds = nextWaitSeconds();
    }
  }

  private void spawn(PlayArea area) {
    float spawnX = random.nextUnit() * Math.max(0f, area.width() - RAIDER_WIDTH);
    raider = new VesperRaider(
        DESCENT_SPEED, RAIDER_WIDTH, RAIDER_HEIGHT, spawnX, spawnX, area.height(), 0f);
    phase = Phase.ACTIVE;
    turnSeconds = nextTurnSeconds();
  }

  private void changeHeading() {
    float randomHeading = (random.nextUnit() * 2f - 1f) * MAX_HEADING_DEGREES;
    raider.setHeadingDegrees(clamp(randomHeading, -MAX_HEADING_DEGREES, MAX_HEADING_DEGREES));
  }

  private boolean fullyOutside(PlayArea area) {
    return raider.y() + raider.height() < 0f
        || raider.y() > area.height()
        || raider.x() + raider.width() < 0f
        || raider.x() > area.width();
  }

  private float nextWaitSeconds() {
    return MIN_WAIT_SECONDS + random.nextUnit() * (MAX_WAIT_SECONDS - MIN_WAIT_SECONDS);
  }

  private float nextTurnSeconds() {
    return MIN_TURN_INTERVAL_SECONDS
        + random.nextUnit() * (MAX_TURN_INTERVAL_SECONDS - MIN_TURN_INTERVAL_SECONDS);
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }
}