package com.davidpe.cosmicaces.domain.weapon;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Thin warm tracer inspired by docs/art/rafagas-inspiration.png.
 * One draw represents one cannon projectile, with a bright tip and a tapered tail.
 * Owns no native resource; does not generate or advance projectiles.
 */
public final class GunBurstVisual {
  /** Visual tuning in world units, unrelated to cadence, speed or travel distance. */
  public static final float DEFAULT_LENGTH = 42f;
  public static final float DEFAULT_WIDTH = 1.2f;
  private static final Color OUTER = new Color(0.38f, 0.12f, 0.015f, 1f);
  private static final Color GOLD = new Color(1f, 0.62f, 0.08f, 1f);
  private static final Color CORE = new Color(1f, 0.96f, 0.65f, 1f);
  private final float length;
  private final float width;

  public GunBurstVisual() {
    this(DEFAULT_LENGTH, DEFAULT_WIDTH);
  }

  public GunBurstVisual(float length, float width) {
    if (!Float.isFinite(length) || !Float.isFinite(width)
        || length <= 0f || width <= 0f || width >= length) {
      throw new IllegalArgumentException("Tracer length and width must be finite, positive and thin");
    }
    this.length = length;
    this.width = width;
  }

  /**
   * Draws at the current world-space tip along the projectile's frozen shot-time forward vector.
   * Caller ends SpriteBatch, sets the world projection and opens one Filled ShapeRenderer batch
   * for all projectiles; this method neither begins nor ends it.
   * Astra's forward=(sin(yaw), cos(yaw)); Vesper's forward=(sin(heading), -cos(heading)).
   * Call once per cannon's projectile; cannon offsets belong to the weapon integration.
   */
  public void draw(ShapeRenderer shapes, float tipX, float tipY, float forwardX, float forwardY) {
    if (shapes == null || !Float.isFinite(tipX) || !Float.isFinite(tipY)
        || !Float.isFinite(forwardX) || !Float.isFinite(forwardY)) {
      throw new IllegalArgumentException("Renderer, position and forward vector are required");
    }
    double magnitude = Math.hypot(forwardX, forwardY);
    if (magnitude == 0d) throw new IllegalArgumentException("Forward vector must not be zero");
    float dx = (float) (forwardX / magnitude);
    float dy = (float) (forwardY / magnitude);
    float tailX = tipX - dx * length;
    float tailY = tipY - dy * length;
    float shoulderX = tipX - dx * length * 0.15f;
    float shoulderY = tipY - dy * length * 0.15f;
    float sideX = dy * width;
    float sideY = -dx * width;
    Color previous = shapes.getColor();
    float r = previous.r, g = previous.g, b = previous.b, a = previous.a;
    try {
      // Opaque layers remain readable on black without requiring a blend-state change.
      shapes.setColor(OUTER);
      shapes.triangle(tailX, tailY,
          shoulderX + sideX * 1.8f, shoulderY + sideY * 1.8f,
          shoulderX - sideX * 1.8f, shoulderY - sideY * 1.8f);
      shapes.setColor(GOLD);
      shapes.rectLine(tailX, tailY, tipX, tipY, width);
      shapes.setColor(CORE);
      shapes.rectLine(shoulderX, shoulderY, tipX, tipY, width * 0.7f);
      shapes.circle(tipX, tipY, width * 0.6f, 6);
    } finally {
      shapes.setColor(r, g, b, a);
    }
  }
}
