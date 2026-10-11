package com.davidpe.cosmicaces.infrastructure.gdx;

/** Session-only presentation preferences shared by the welcome and playable screens. */
public final class GamePresentationSettings {

  public static final float DEFAULT_ASTRA_BOTTOM_FRACTION = 0.18f;
  public static final float MIN_ASTRA_BOTTOM_FRACTION = 0.10f;
  public static final float MAX_ASTRA_BOTTOM_FRACTION = 0.35f;
  public static final float ASTRA_BOTTOM_STEP = 0.01f;

  private float astraBottomFraction = DEFAULT_ASTRA_BOTTOM_FRACTION;

  public float astraBottomFraction() {
    return astraBottomFraction;
  }

  /** Sets Astra's viewport position, clamping the user-facing 10–35 percent range. */
  public void setAstraBottomFraction(float fraction) {
    if (!Float.isFinite(fraction)) {
      return;
    }
    astraBottomFraction = Math.max(MIN_ASTRA_BOTTOM_FRACTION,
        Math.min(MAX_ASTRA_BOTTOM_FRACTION, fraction));
  }

  public void adjustAstraBottomFraction(float delta) {
    setAstraBottomFraction(astraBottomFraction + delta);
  }
}
