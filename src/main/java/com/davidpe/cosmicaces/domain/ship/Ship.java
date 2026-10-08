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

  /** Rotates around the stable box center, keeping every sprite slice aligned. */
  public final void draw(SpriteBatch batch, float rotationDegrees) {
    TextureRegion region = currentRegion();
    float scale = Math.min(drawWidth / widestRegion, drawHeight / tallestRegion);
    float width = region.getRegionWidth() * scale;
    float height = region.getRegionHeight() * scale;
    batch.draw(region, x + (drawWidth - width) / 2f, y + (drawHeight - height) / 2f,
        width / 2f, height / 2f, width, height, 1f, 1f, rotationDegrees);
  }

  protected abstract TextureRegion currentRegion();
}
