package com.davidpe.cosmicaces.domain.effect;

/**
 * A short, purely presentational hit-flash pulse for one ship. Each counted projectile impact renews
 * the pulse to a full 0,10 s of white intensity that decays linearly back to zero, so a burst of
 * impacts never accumulates a longer flash. It knows nothing about energy, damage thresholds,
 * invulnerability, death, respawns, cameras or shaders: the phase controller decides which impacts
 * count and clears the pulse when death takes priority; the screen owns the graphical interpretation.
 *
 * <p>The class is deliberately independent of LibGDX and reusable by any phase or ship. The same
 * shared duration is used for Astra and Vesper Raider; a separate instance per ship lets both react
 * to simultaneous impacts without interference.
 */
public final class ShipHitFlash {

  /** Full pulse length in seconds (CA21 of COS-27: 100 ms, inside the analysed 80–120 ms range). */
  public static final float DURATION_SECONDS = 0.10f;

  private float remainingSeconds;

  /** Restarts the pulse at full intensity. New impacts renew it but never add extra duration. */
  public void trigger() {
    remainingSeconds = DURATION_SECONDS;
  }

  /**
   * Decays the pulse by the elapsed frame time. Non-finite or non-positive deltas are ignored and
   * the remaining time clamps at zero, so the pulse always expires and never goes negative.
   */
  public void advance(float deltaSeconds) {
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
      return;
    }
    remainingSeconds = Math.max(0f, remainingSeconds - deltaSeconds);
  }

  /**
   * Linear white intensity from 1 at the trigger down to 0 once the pulse expires, clamped to the
   * [0,1] range so callers never need to re-clamp.
   */
  public float intensity() {
    if (remainingSeconds <= 0f) {
      return 0f;
    }
    return Math.min(1f, remainingSeconds / DURATION_SECONDS);
  }

  /** Ends the pulse immediately; used when death/respawn takes priority over the impact flash. */
  public void clear() {
    remainingSeconds = 0f;
  }
}