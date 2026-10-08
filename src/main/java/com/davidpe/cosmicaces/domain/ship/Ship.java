package com.davidpe.cosmicaces.domain.ship;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Shared position, movement and sprite drawing for player and enemy ships. */
public abstract class Ship {

  private final float speed;
  private final float drawWidth;
  private final float drawHeight;
  private final float widestRegion;
  private final float tallestRegion;
  private float x;
  private float y;

  protected Ship(float speed, float x, float y, float drawWidth, float drawHeight,
      float widestRegion, float tallestRegion) {
    if (!Float.isFinite(speed) || speed <= 0f) {
      throw new IllegalArgumentException("Ship speed must be positive and finite: " + speed);
    }
    if (!Float.isFinite(x) || !Float.isFinite(y)) {
      throw new IllegalArgumentException("Ship position must be finite");
    }
    if (!Float.isFinite(drawWidth) || !Float.isFinite(drawHeight)
        || drawWidth <= 0f || drawHeight <= 0f || widestRegion <= 0f || tallestRegion <= 0f) {
      throw new IllegalArgumentException("Ship drawing dimensions must be positive");
    }
    this.speed = speed;
    this.x = x;
    this.y = y;
    this.drawWidth = drawWidth;
    this.drawHeight = drawHeight;
    this.widestRegion = widestRegion;
    this.tallestRegion = tallestRegion;
  }

  public final float x() {
    return x;
  }

  public final float y() {
    return y;
  }

  public final float speed() {
    return speed;
  }

  public final float drawWidth() {
    return drawWidth;
  }

  public final float drawHeight() {
    return drawHeight;
  }

  protected final void setPosition(float x, float y) {
    this.x = x;
    this.y = y;
  }

  /** Advances at the same speed in every direction, including diagonals. */
  protected final void advance(MovementIntent intent, float deltaSeconds) {
    if (deltaSeconds <= 0f || !Float.isFinite(deltaSeconds)) {
      return;
    }
    float dx = intent.horizontal();
    float dy = intent.vertical();
    double magnitude = Math.hypot(dx, dy);
    if (magnitude == 0d) {
      return;
    }
    setPosition(x + (float) (dx / magnitude * speed * deltaSeconds),
        y + (float) (dy / magnitude * speed * deltaSeconds));
  }

  /** Draws the current pose in the ship's stable box without stretching the sprite. */
  public final void draw(SpriteBatch batch) {
    draw(batch, 0f);
  }

  /**
   * Where and at what scale the current pose's region is drawn inside the stable box. The default
   * centers the region without stretching it, exactly like the pre-registration drawing; subclasses
   * override it to keep the body anchor stable between variants (for example flight vs firing) by
   * shifting the region without moving the rotation pivot.
   */
  public record SpritePlacement(float scale, float offsetX, float offsetY) {
    public SpritePlacement {
      if (!Float.isFinite(scale) || scale <= 0f || !Float.isFinite(offsetX)
          || !Float.isFinite(offsetY)) {
        throw new IllegalArgumentException("Sprite placement must have a finite positive scale");
      }
    }

    static SpritePlacement centered(float scale) {
      return new SpritePlacement(scale, 0f, 0f);
    }
  }

  /** Placement of the current region; the default centers it like the historical drawing. */
  protected SpritePlacement spritePlacement(TextureRegion region) {
    return SpritePlacement.centered(drawScale());
  }

  private float drawScale() {
    return Math.min(drawWidth / widestRegion, drawHeight / tallestRegion);
  }

  /**
   * Rotates around the stable box center, keeping every sprite slice aligned. The region is placed
   * by {@link #spritePlacement}; offsets shift the image content but never the pivot, because the
   * draw origin is always the box center.
   */
  public final void draw(SpriteBatch batch, float rotationDegrees) {
    TextureRegion region = currentRegion();
    SpritePlacement placement = spritePlacement(region);
    float width = region.getRegionWidth() * placement.scale();
    float height = region.getRegionHeight() * placement.scale();
    float centerX = x + drawWidth / 2f;
    float centerY = y + drawHeight / 2f;
    float originX = width / 2f + placement.offsetX();
    float originY = height / 2f + placement.offsetY();
    batch.draw(region, centerX - originX, centerY - originY,
        originX, originY, width, height, 1f, 1f, rotationDegrees);
  }

  protected abstract TextureRegion currentRegion();
}
