package com.davidpe.cosmicaces.domain.player;

/**
 * Player movement intent for one update, derived from the movement keys currently held.
 * Opposite directions cancel out.
 */
public record MovementIntent(boolean left, boolean right, boolean up, boolean down) {

  /** Intent with no direction pressed. */
  public static MovementIntent none() {
    return new MovementIntent(false, false, false, false);
  }

  /** Horizontal direction: -1 left, 0 none, +1 right. */
  public float horizontal() {
    return (right ? 1f : 0f) - (left ? 1f : 0f);
  }

  /** Vertical direction: -1 down, 0 none, +1 up. */
  public float vertical() {
    return (up ? 1f : 0f) - (down ? 1f : 0f);
  }
}