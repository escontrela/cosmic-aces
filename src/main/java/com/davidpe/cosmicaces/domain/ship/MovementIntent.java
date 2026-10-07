package com.davidpe.cosmicaces.domain.ship;

/** A direction requested for one movement update, independent of who controls the ship. */
public record MovementIntent(float horizontal, float vertical) {

  public MovementIntent {
    if (!Float.isFinite(horizontal) || !Float.isFinite(vertical)) {
      throw new IllegalArgumentException("Movement direction must be finite");
    }
  }

  public static MovementIntent none() {
    return new MovementIntent(0f, 0f);
  }

  /** Converts the currently held direction keys to an intent; opposite keys cancel out. */
  public static MovementIntent fromDirections(boolean left, boolean right, boolean up, boolean down) {
    return new MovementIntent((right ? 1f : 0f) - (left ? 1f : 0f),
        (up ? 1f : 0f) - (down ? 1f : 0f));
  }

  /** Converts a heading measured from straight-down descent into a movement intent. */
  public static MovementIntent fromDownwardHeading(float headingDegrees) {
    if (!Float.isFinite(headingDegrees)) {
      throw new IllegalArgumentException("Heading must be finite");
    }
    double radians = Math.toRadians(headingDegrees);
    return new MovementIntent((float) Math.sin(radians), (float) -Math.cos(radians));
  }
}
