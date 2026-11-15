package com.davidpe.cosmicaces.domain.effect;

/**
 * Pure mapping from a ship's real, integrated speed to the extra visibility of the smoke trail it
 * leaves while turning with displacement.
 *
 * <p>It is presentation tuning only: no LibGDX, no controls, no mode. The caller passes the speed
 * the ship actually travelled at in the last integrated step, so two ships at the same real speed
 * always get the same factor even if one reached it in Turbo and the other is still coasting in
 * Normal, and a selected speed mode alone can never jump the result. The mapping is continuous,
 * non-decreasing and bounded: it stays at {@link #SUBTLE_FACTOR} up to the cruise reference speed,
 * rises linearly up to {@link #PEAK_FACTOR} at the high-speed reference and clamps beyond it.
 *
 * <p>{@link AstraYawSmokeTrail} stores the resulting alpha in each sample when it is emitted, so a
 * later acceleration never repaints the puffs already left behind.
 */
public final class TurnSmokeIntensity {

  /**
   * Real speed at or below which the trail keeps its subtle cruise look, in world units per second.
   * Independent of the player's configured Normal speed so the effect follows real motion.
   */
  public static final float SUBTLE_SPEED = 150f;

  /**
   * Real speed at or above which the trail reaches its maximum visibility, in world units per
   * second. It matches the reference Ultra speed of the factory settings, so the whole Turbo-to-Ultra
   * band keeps adding smoke instead of saturating at the old 750 reference.
   */
  public static final float PEAK_SPEED = 1000f;

  /** Alpha multiplier applied at {@link #SUBTLE_SPEED} and below. */
  public static final float SUBTLE_FACTOR = 1f;

  /**
   * Alpha multiplier applied at {@link #PEAK_SPEED} and above. It extends the original slope over
   * the wider reference instead of raising the curve earlier: the factor at the factory Turbo speed
   * stays as approved in COS-153, while the new 750-1000 band gains visible steps.
   */
  public static final float PEAK_FACTOR = 2.98f;

  private TurnSmokeIntensity() {}

  /**
   * Visibility factor for a real speed: {@link #SUBTLE_FACTOR} at cruise or below, a linear ramp in
   * between and {@link #PEAK_FACTOR} at high speed or above. A non-finite or negative speed falls
   * back to the subtle factor, so a bad reading can never make the smoke vanish or blow up.
   */
  public static float factorForSpeed(float speed) {
    if (!Float.isFinite(speed) || speed <= SUBTLE_SPEED) {
      return SUBTLE_FACTOR;
    }
    if (speed >= PEAK_SPEED) {
      return PEAK_FACTOR;
    }
    float progress = (speed - SUBTLE_SPEED) / (PEAK_SPEED - SUBTLE_SPEED);
    return SUBTLE_FACTOR + (PEAK_FACTOR - SUBTLE_FACTOR) * progress;
  }
}
