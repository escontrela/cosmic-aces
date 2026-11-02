package com.davidpe.cosmicaces.domain.weapon;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Free world-space aiming cursor; owns no keyboard, camera or native resources. */
public final class MissileAim {
  private final WorldBounds world;
  private final float speed;
  private final float captureRadius;
  private boolean active;
  private float x;
  private float y;

  public MissileAim(WorldBounds world, float speed, float captureRadius) {
    if (world == null || !Float.isFinite(speed) || speed <= 0f
        || !Float.isFinite(captureRadius) || captureRadius <= 0f) {
      throw new IllegalArgumentException("World, positive aim speed and capture radius are required");
    }
    this.world = world;
    this.speed = speed;
    this.captureRadius = captureRadius;
  }

  public void begin(float worldX, float worldY) {
    if (!Float.isFinite(worldX) || !Float.isFinite(worldY)) {
      throw new IllegalArgumentException("Aim coordinates must be finite");
    }
    x = clamp(worldX, world.width());
    y = clamp(worldY, world.height());
    active = true;
  }

  public void cancel() {
    active = false;
  }

  public boolean isActive() { return active; }
  public float x() { return x; }
  public float y() { return y; }

  /** Diagonal movement has the same speed as cardinal movement; invalid deltas do nothing. */
  public void move(float horizontal, float vertical, float deltaSeconds) {
    if (!active || !Float.isFinite(horizontal) || !Float.isFinite(vertical)
        || !Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) return;
    double magnitude = Math.hypot(horizontal, vertical);
    if (magnitude == 0d) return;
    double distance = (double) speed * deltaSeconds;
    x = (float) Math.max(0d, Math.min(world.width(), x + distance * horizontal / magnitude));
    y = (float) Math.max(0d, Math.min(world.height(), y + distance * vertical / magnitude));
  }

  /** Nearest live candidate in capture radius, with stable ordering when distances tie. */
  public Optional<MissileTarget> select(List<MissileTarget> liveTargets) {
    if (liveTargets == null) throw new IllegalArgumentException("Targets are required");
    if (!active) return Optional.empty();
    return liveTargets.stream()
        .filter(target -> distanceSquared(target) <= (double) captureRadius * captureRadius)
        .min(Comparator.comparingDouble(this::distanceSquared)
            .thenComparing(target -> target.reference().kind())
            .thenComparingLong(target -> target.reference().entityId())
            .thenComparingInt(target -> target.reference().lifeGeneration()));
  }

  private double distanceSquared(MissileTarget target) {
    double dx = (double) target.x() - x;
    double dy = (double) target.y() - y;
    return dx * dx + dy * dy;
  }

  private static float clamp(float value, float max) {
    return Math.max(0f, Math.min(max, value));
  }
}
