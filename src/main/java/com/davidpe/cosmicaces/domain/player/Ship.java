package com.davidpe.cosmicaces.domain.player;

import com.davidpe.cosmicaces.domain.game.PlayArea;

/**
 * The player's ship: a continuous position and bounded directional movement inside a play area.
 * Diagonal movement is normalized so the ship does not travel faster than on a single axis.
 */
public final class Ship {

  /** Default cruise speed in world units per second; a technical choice subject to PO visual QA. */
  public static final float DEFAULT_SPEED = 300f;

  private static final float DIAGONAL_NORMALIZER = 0.70710678f; // 1 / sqrt(2)

  private final float speed;
  private float x;
  private float y;

  public Ship(float speed) {
    this(speed, 0f, 0f);
  }

  public Ship(float speed, float x, float y) {
    if (speed <= 0f || Float.isNaN(speed)) {
      throw new IllegalArgumentException("Ship speed must be positive: " + speed);
    }
    this.speed = speed;
    this.x = x;
    this.y = y;
  }

  public float x() {
    return x;
  }

  public float y() {
    return y;
  }

  public float speed() {
    return speed;
  }

  /** Places the ship at the given position, clamped to the play area. */
  public void placeAt(float x, float y, PlayArea area) {
    this.x = area.clampX(x);
    this.y = area.clampY(y);
  }

  /**
   * Moves the ship for {@code deltaSeconds} according to the movement intent, keeping it inside
   * the play area. Non-positive or non-finite deltas and empty intents leave the ship unchanged.
   */
  public void move(MovementIntent intent, float deltaSeconds, PlayArea area) {
    float dx = intent.horizontal();
    float dy = intent.vertical();
    if ((dx == 0f && dy == 0f) || deltaSeconds <= 0f || Float.isNaN(deltaSeconds)) {
      return;
    }
    if (dx != 0f && dy != 0f) {
      dx *= DIAGONAL_NORMALIZER;
      dy *= DIAGONAL_NORMALIZER;
    }
    x = area.clampX(x + dx * speed * deltaSeconds);
    y = area.clampY(y + dy * speed * deltaSeconds);
  }
}