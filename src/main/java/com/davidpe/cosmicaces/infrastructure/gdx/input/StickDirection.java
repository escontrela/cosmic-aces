package com.davidpe.cosmicaces.infrastructure.gdx.input;

/**
 * Digital result of a stick sample. The existing flight model is digital, so the adapter preserves
 * that semantics instead of introducing proportional speed.
 */
public record StickDirection(boolean left, boolean right, boolean up, boolean down) {

  public static final StickDirection NEUTRAL =
      new StickDirection(false, false, false, false);

  /** True when no cardinal direction is active. */
  public boolean neutral() {
    return !left && !right && !up && !down;
  }

  /** True when at least one direction is active. */
  public boolean any() {
    return !neutral();
  }

  /** Horizontal sign for menus and missile aim: -1 left, 0 neutral, 1 right. */
  public int horizontal() {
    return (right ? 1 : 0) - (left ? 1 : 0);
  }

  /** Vertical sign for menus and missile aim: -1 down, 0 neutral, 1 up. */
  public int vertical() {
    return (up ? 1 : 0) - (down ? 1 : 0);
  }

  /** OR-combines two samples so keyboard and gamepad can hold the same direction. */
  public StickDirection or(StickDirection other) {
    if (other == null) {
      return this;
    }
    return new StickDirection(left || other.left, right || other.right, up || other.up,
        down || other.down);
  }
}
