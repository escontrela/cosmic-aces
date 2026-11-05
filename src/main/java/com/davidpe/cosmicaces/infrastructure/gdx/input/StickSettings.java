package com.davidpe.cosmicaces.infrastructure.gdx.input;

/**
 * Pure stick orientation and dead-zone rules.
 *
 * <p>The raw axes are optionally swapped and inverted, then converted to the four digital
 * directions used by flight, aim and menu navigation. Values inside the dead zone are neutral; at
 * the boundary the result is neutral too, so a noisy resting stick never produces a command.
 */
public record StickSettings(boolean swapAxes, boolean invertX, boolean invertY, float deadZone) {

  public static final float MIN_DEAD_ZONE = 0f;
  public static final float MAX_DEAD_ZONE = 0.9f;
  public static final float DEFAULT_DEAD_ZONE = 0.25f;

  public static final StickSettings DEFAULTS =
      new StickSettings(false, false, false, DEFAULT_DEAD_ZONE);

  public StickSettings {
    if (!Float.isFinite(deadZone) || deadZone < MIN_DEAD_ZONE || deadZone > MAX_DEAD_ZONE) {
      throw new IllegalArgumentException(
          "La zona muerta debe estar entre " + MIN_DEAD_ZONE + " y " + MAX_DEAD_ZONE);
    }
  }

  /**
   * Converts raw axes to digital directions.
   *
   * @param rawX raw horizontal axis, normally -1..1
   * @param rawY raw vertical axis, normally -1..1
   * @return neutral for non-finite values or values inside the dead zone
   */
  public StickDirection translate(float rawX, float rawY) {
    if (!Float.isFinite(rawX) || !Float.isFinite(rawY)) {
      return StickDirection.NEUTRAL;
    }
    float x = swapAxes ? rawY : rawX;
    float y = swapAxes ? rawX : rawY;
    if (invertX) {
      x = -x;
    }
    if (invertY) {
      y = -y;
    }
    if (Math.hypot(x, y) <= deadZone) {
      return StickDirection.NEUTRAL;
    }
    return new StickDirection(x < 0f, x > 0f, y > 0f, y < 0f);
  }
}
