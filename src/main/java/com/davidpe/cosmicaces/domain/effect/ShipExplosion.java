package com.davidpe.cosmicaces.domain.effect;

/**
 * One non-looping explosion animation. It freezes the world centre of a single destruction and
 * advances a local clock to pick the sheet frame, so camera movement or later ship movement cannot
 * drag the effect. It owns no LibGDX resource and knows nothing about respawn or damage timers: the
 * screen spawns one instance per destruction and discards it when {@link #isFinished()}.
 */
public final class ShipExplosion {

  /** Seconds each of the eight sheet frames stays on screen. */
  public static final float SECONDS_PER_FRAME = 0.1f;
  /** Total animation length; the animation never loops. */
  public static final float DURATION_SECONDS =
      ShipExplosionSheet.FRAME_COUNT * SECONDS_PER_FRAME;

  /** Small tolerance so sequential float additions land exactly on the last frame/limit. */
  private static final double TIME_EPSILON = 1e-4;

  private final float centerX;
  private final float centerY;
  private double elapsedSeconds;

  public ShipExplosion(float centerX, float centerY) {
    if (!Float.isFinite(centerX) || !Float.isFinite(centerY)) {
      throw new IllegalArgumentException("Explosion centre must be finite");
    }
    this.centerX = centerX;
    this.centerY = centerY;
  }

  /** Advances the local clock, clamped at {@link #DURATION_SECONDS}; invalid deltas are ignored. */
  public void advance(float deltaSeconds) {
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
      return;
    }
    elapsedSeconds = Math.min(DURATION_SECONDS, elapsedSeconds + deltaSeconds);
  }

  /** True once the whole animation has elapsed; it stays true because the effect never loops. */
  public boolean isFinished() {
    return elapsedSeconds + TIME_EPSILON >= DURATION_SECONDS;
  }

  /** Sheet frame for the current elapsed time, clamped to the last defined frame. */
  public int frameIndex() {
    int frame = (int) ((elapsedSeconds + TIME_EPSILON) / SECONDS_PER_FRAME);
    return Math.min(ShipExplosionSheet.FRAME_COUNT - 1, Math.max(0, frame));
  }

  public float elapsedSeconds() {
    return (float) elapsedSeconds;
  }

  public float centerX() {
    return centerX;
  }

  public float centerY() {
    return centerY;
  }
}
