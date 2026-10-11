package com.davidpe.cosmicaces.domain.game;

/**
 * Inmutable notification that a Ghostwing drone stopped being live, emitted exactly once per drone
 * by the phase controller on the only transitions that destroy one: destroyed by a single Astra
 * projectile ({@link Cause#SHOT}) or by hull-to-hull contact with Astra ({@link Cause#COLLISION}).
 * Drones that complete their world crossing retire without any event.
 *
 * <p>The cause lets the presentation play effects and lets tests assert scoring, but neither the
 * cause nor the event decides damage, points or respawn rules: those belong to the phase
 * controller. The centre is the world position of the drone at the moment of death, so the screen
 * can anchor an explosion. A dead drone leaves {@code activeDrones()}, movement, collision and the
 * group marker immediately, even while its animation may still be visible.
 */
public record GhostwingDeath(long droneId, float centerX, float centerY, Cause cause) {

  /** Why the drone left combat; only a shot destruction awards the 100-point bonus. */
  public enum Cause {
    /** The drone was destroyed by a single Astra projectile impact. Awards the +100 bonus. */
    SHOT,
    /** The drone collided with Astra's hull; both explode and no points are awarded. */
    COLLISION
  }

  public GhostwingDeath {
    if (!Float.isFinite(centerX) || !Float.isFinite(centerY)) {
      throw new IllegalArgumentException("Death centre must be finite");
    }
    if (cause == null) {
      throw new IllegalArgumentException("Cause is required");
    }
  }
}