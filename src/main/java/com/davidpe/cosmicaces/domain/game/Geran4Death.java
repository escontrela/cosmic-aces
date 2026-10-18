package com.davidpe.cosmicaces.domain.game;

/**
 * Inmutable notification that a Geran-4 UAV stopped being live, emitted exactly once per UAV by the
 * phase controller on the only transitions that destroy one: destroyed by a single Astra projectile
 * ({@link Cause#SHOT}) or by hull-to-hull contact with Astra ({@link Cause#COLLISION}). UAVs that
 * complete their straight flight and leave the world retire without any event, exactly like the
 * Ghostwing drone.
 *
 * <p>The cause lets the presentation play effects and lets tests assert scoring, but neither the
 * cause nor the event decides damage, points or respawn rules: those belong to the phase
 * controller, which applies the same approved 100-point drone bonus as the Ghostwing drone and only
 * for a shot. The centre is the world position of the UAV at the moment of death, so the screen can
 * anchor an explosion. A dead UAV leaves {@code activeDrones()}, movement, collision and the draw
 * list immediately, even while its explosion animation may still be visible.
 */
public record Geran4Death(long droneId, float centerX, float centerY, Cause cause) {

  /** Why the UAV left combat; only a shot destruction awards the 100-point drone bonus. */
  public enum Cause {
    /** The UAV was destroyed by a single Astra projectile impact. Awards the +100 bonus. */
    SHOT,
    /** The UAV collided with Astra's hull; both explode and no points are awarded. */
    COLLISION
  }

  public Geran4Death {
    if (!Float.isFinite(centerX) || !Float.isFinite(centerY)) {
      throw new IllegalArgumentException("Death centre must be finite");
    }
    if (cause == null) {
      throw new IllegalArgumentException("Cause is required");
    }
  }
}
