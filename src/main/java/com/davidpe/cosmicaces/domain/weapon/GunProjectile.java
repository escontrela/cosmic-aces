package com.davidpe.cosmicaces.domain.weapon;

/**
 * A single visual gun projectile. It copies its muzzle origin and a normalized forward direction at
 * construction, so later movement or turns of the firing ship cannot change its straight path. It
 * retains no reference to a ship, camera or world.
 */
public final class GunProjectile {

  private final float originX;
  private final float originY;
  private final float forwardX;
  private final float forwardY;
  private final float speed;
  private final float maxDistance;
  private float x;
  private float y;
  private float travelled;
  private boolean consumed;

  /**
   * @param x muzzle origin X in world coordinates
   * @param y muzzle origin Y in world coordinates
   * @param forwardX non-zero direction X, copied and normalized once
   * @param forwardY non-zero direction Y, copied and normalized once
   * @param speed travel speed in world units per second
   * @param maxDistance travel distance before the projectile expires
   */
  public GunProjectile(float x, float y, float forwardX, float forwardY,
      float speed, float maxDistance) {
    if (!Float.isFinite(x) || !Float.isFinite(y)
        || !Float.isFinite(forwardX) || !Float.isFinite(forwardY)) {
      throw new IllegalArgumentException("Projectile origin and direction must be finite");
    }
    if (!Float.isFinite(speed) || speed <= 0f) {
      throw new IllegalArgumentException("Projectile speed must be positive and finite");
    }
    if (!Float.isFinite(maxDistance) || maxDistance <= 0f) {
      throw new IllegalArgumentException("Projectile range must be positive and finite");
    }
    double magnitude = Math.hypot(forwardX, forwardY);
    if (magnitude == 0d) {
      throw new IllegalArgumentException("Projectile direction must not be zero");
    }
    this.originX = x;
    this.originY = y;
    this.x = x;
    this.y = y;
    this.forwardX = (float) (forwardX / magnitude);
    this.forwardY = (float) (forwardY / magnitude);
    this.speed = speed;
    this.maxDistance = maxDistance;
    this.travelled = 0f;
  }

  /**
   * Moves the projectile along its frozen direction. A non-positive or non-finite delta is ignored.
   * The travelled distance never exceeds the range, so a projectile expires exactly at its limit.
   */
  public void advance(float deltaSeconds) {
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
      return;
    }
    float remaining = maxDistance - travelled;
    if (remaining <= 0f) {
      return;
    }
    float step = Math.min(deltaSeconds * speed, remaining);
    x += forwardX * step;
    y += forwardY * step;
    travelled += step;
  }

  /** True once the projectile has covered its full range; retirement is by distance only. */
  public boolean expired() {
    return travelled >= maxDistance;
  }

  /**
   * Distance still available before the projectile expires at its range. Useful to predict the
   * exact segment a projectile will cover during the next frame, including its final segment.
   */
  public float remainingDistance() {
    return Math.max(0f, maxDistance - travelled);
  }

  /**
   * Marks the projectile as having impacted a ship. A consumed projectile is retired by its weapon
   * and can never impact again, so each projectile contributes at most one hit.
   */
  public void consume() {
    consumed = true;
  }

  /** True once this projectile has already impacted a ship. */
  public boolean isConsumed() {
    return consumed;
  }

  public float originX() {
    return originX;
  }

  public float originY() {
    return originY;
  }

  public float x() {
    return x;
  }

  public float y() {
    return y;
  }

  public float forwardX() {
    return forwardX;
  }

  public float forwardY() {
    return forwardY;
  }

  public float speed() {
    return speed;
  }

  public float maxDistance() {
    return maxDistance;
  }

  public float travelled() {
    return travelled;
  }
}
