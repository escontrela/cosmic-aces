package com.davidpe.cosmicaces.domain.scenery;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/** A reusable, vertically scrolling star background. */
public final class Starfield {

  private final int width;
  private final int height;
  private final int count;
  private final float minSpeed;
  private final float maxSpeed;
  private final float minRadius;
  private final float maxRadius;
  private final float wrapMargin;
  private final float[] x;
  private final float[] y;
  private final float[] speed;
  private final float[] radius;

  public Starfield(
      int width,
      int height,
      int count,
      float minSpeed,
      float maxSpeed,
      float minRadius,
      float maxRadius,
      float wrapMargin) {
    if (width <= 0 || height <= 0 || count <= 0) {
      throw new IllegalArgumentException("Starfield dimensions and count must be positive");
    }
    if (minSpeed < 0f || maxSpeed < minSpeed) {
      throw new IllegalArgumentException("Star speed range is invalid");
    }
    if (minRadius < 0f || maxRadius < minRadius) {
      throw new IllegalArgumentException("Star radius range is invalid");
    }
    if (wrapMargin < maxRadius) {
      throw new IllegalArgumentException("Wrap margin must be at least the maximum radius");
    }
    this.width = width;
    this.height = height;
    this.count = count;
    this.minSpeed = minSpeed;
    this.maxSpeed = maxSpeed;
    this.minRadius = minRadius;
    this.maxRadius = maxRadius;
    this.wrapMargin = wrapMargin;
    x = new float[count];
    y = new float[count];
    speed = new float[count];
    radius = new float[count];
    for (int i = 0; i < count; i++) {
      randomize(i, MathUtils.random(0f, height));
    }
  }

  public void update(float delta) {
    for (int i = 0; i < count; i++) {
      y[i] -= speed[i] * delta;
      if (y[i] < -wrapMargin) {
        randomize(i, height + wrapMargin);
      }
    }
  }

  public void draw(ShapeRenderer shapes) {
    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(Color.WHITE);
    for (int i = 0; i < count; i++) {
      shapes.circle(x[i], y[i], radius[i]);
    }
    shapes.end();
  }

  private void randomize(int index, float initialY) {
    x[index] = MathUtils.random(0f, width);
    y[index] = initialY;
    speed[index] = MathUtils.random(minSpeed, maxSpeed);
    radius[index] = MathUtils.random(minRadius, maxRadius);
  }
}
