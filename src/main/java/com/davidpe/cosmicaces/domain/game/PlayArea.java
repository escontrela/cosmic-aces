package com.davidpe.cosmicaces.domain.game;

/** Rectangular area where gameplay entities can move; dimensions are supplied by the caller. */
public record PlayArea(float width, float height) {

  public PlayArea {
    if (width <= 0f || height <= 0f) {
      throw new IllegalArgumentException(
          "Play area dimensions must be positive: " + width + "x" + height);
    }
  }

  /** Clamps the given x coordinate to the horizontal bounds of the area. */
  public float clampX(float x) {
    return Math.max(0f, Math.min(width, x));
  }

  /** Clamps the given y coordinate to the vertical bounds of the area. */
  public float clampY(float y) {
    return Math.max(0f, Math.min(height, y));
  }
}