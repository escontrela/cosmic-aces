package com.davidpe.cosmicaces.domain.enemy;

/**
 * An autonomous descending enemy. The position is the bottom-left corner of its box; {@code x}
 * grows to the right and {@code y} grows upward, matching the presentation axis so the screen needs
 * no coordinate conversion. The heading is measured in degrees from straight-down descent, positive
 * toward the right; the encounter keeps it within a bounded range and the raider never steers toward
 * the ship.
 */
public final class VesperRaider {

  private final float speed;
  private final float width;
  private final float height;
  private final float spawnX;
  private float x;
  private float y;
  private float headingDegrees;

  public VesperRaider(
      float speed, float width, float height, float spawnX, float x, float y, float headingDegrees) {
    if (speed <= 0f || Float.isNaN(speed)) {
      throw new IllegalArgumentException("Raider speed must be positive: " + speed);
    }
    if (width <= 0f || height <= 0f || Float.isNaN(width) || Float.isNaN(height)) {
      throw new IllegalArgumentException("Raider box must be positive: " + width + "x" + height);
    }
    if (Float.isNaN(spawnX) || Float.isNaN(x) || Float.isNaN(y)) {
      throw new IllegalArgumentException("Raider position must be finite");
    }
    this.speed = speed;
    this.width = width;
    this.height = height;
    this.spawnX = spawnX;
    this.x = x;
    this.y = y;
    this.headingDegrees = headingDegrees;
  }

  public float x() {
    return x;
  }

  public float y() {
    return y;
  }

  public float width() {
    return width;
  }

  public float height() {
    return height;
  }

  /** Horizontal lane the raider spawned in. */
  public float spawnX() {
    return spawnX;
  }

  public float speed() {
    return speed;
  }

  public float headingDegrees() {
    return headingDegrees;
  }

  /** Heading sign: +1 turning right, -1 turning left, 0 descending straight. */
  public int bank() {
    return Float.compare(headingDegrees, 0f);
  }

  /** Replaces the heading; the encounter owns the bounds on its value. */
  public void setHeadingDegrees(float headingDegrees) {
    this.headingDegrees = headingDegrees;
  }

  /**
   * Moves the raider for {@code deltaSeconds} along its heading, descending predominantly.
   * Non-positive or non-finite deltas leave the raider unchanged.
   */
  public void advance(float deltaSeconds) {
    if (deltaSeconds <= 0f || !Float.isFinite(deltaSeconds)) {
      return;
    }
    double radians = Math.toRadians(headingDegrees);
    x += (float) Math.sin(radians) * speed * deltaSeconds;
    y -= (float) Math.cos(radians) * speed * deltaSeconds;
  }
}