package com.davidpe.cosmicaces.domain.game;

/** Finite world coordinates; ships use the usable origin range for their drawing box. */
public record WorldBounds(float width, float height) {
  public WorldBounds {
    if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0f || height <= 0f) {
      throw new IllegalArgumentException("World dimensions must be positive and finite");
    }
  }

  public float maxX(float boxWidth) {
    if (!Float.isFinite(boxWidth) || boxWidth <= 0f || boxWidth >= width) {
      throw new IllegalArgumentException("Invalid box width");
    }
    return width - boxWidth;
  }

  public float maxY(float boxHeight) {
    if (!Float.isFinite(boxHeight) || boxHeight <= 0f || boxHeight >= height) {
      throw new IllegalArgumentException("Invalid box height");
    }
    return height - boxHeight;
  }

  public float clampX(float x, float boxWidth) {
    return Math.max(0f, Math.min(maxX(boxWidth), x));
  }

  public float clampY(float y, float boxHeight) {
    return Math.max(0f, Math.min(maxY(boxHeight), y));
  }
}
