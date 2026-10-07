package com.davidpe.cosmicaces.domain.enemy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.ship.MovementIntent;
import com.davidpe.cosmicaces.domain.ship.Ship;

/** An autonomous enemy that descends along a heading and may leave the play area. */
public final class VesperRaider extends Ship {

  private final float spawnX;
  private float headingDegrees;
  private Visuals visuals;

  public VesperRaider(float speed, float width, float height, float spawnX, float x, float y,
      float headingDegrees) {
    super(speed, x, y, width, height, VesperRaiderSheet.maxSliceWidth(),
        VesperRaiderSheet.maxSliceHeight());
    if (width <= 0f || height <= 0f || !Float.isFinite(width) || !Float.isFinite(height)) {
      throw new IllegalArgumentException("Raider box must be positive: " + width + "x" + height);
    }
    if (!Float.isFinite(spawnX)) {
      throw new IllegalArgumentException("Raider spawn position must be finite");
    }
    this.spawnX = spawnX;
    setHeadingDegrees(headingDegrees);
  }

  public void setVisuals(Visuals visuals) {
    this.visuals = visuals;
  }

  public float width() {
    return drawWidth();
  }

  public float height() {
    return drawHeight();
  }

  public float spawnX() {
    return spawnX;
  }

  public float headingDegrees() {
    return headingDegrees;
  }

  /** Heading sign: +1 turning right, -1 turning left, 0 descending straight. */
  public int bank() {
    return Float.compare(headingDegrees, 0f);
  }

  public void setHeadingDegrees(float headingDegrees) {
    if (!Float.isFinite(headingDegrees)) {
      throw new IllegalArgumentException("Heading must be finite");
    }
    this.headingDegrees = headingDegrees;
  }

  public void advance(float deltaSeconds) {
    advance(MovementIntent.fromDownwardHeading(headingDegrees), deltaSeconds);
  }

  @Override
  protected TextureRegion currentRegion() {
    if (visuals == null) {
      throw new IllegalStateException("Vesper Raider visuals have not been attached");
    }
    return visuals.region(bank());
  }

  /** One shared sprite sheet for all Raiders spawned in a phase. */
  public static final class Visuals implements Disposable {
    private final Texture texture;
    private final TextureRegion[] regions;

    public Visuals() {
      texture = new Texture(Gdx.files.internal(VesperRaiderSheet.internalPath()));
      regions = new TextureRegion[VesperRaiderSheet.Pose.values().length];
      for (VesperRaiderSheet.Pose pose : VesperRaiderSheet.Pose.values()) {
        VesperRaiderSheet.Slice slice = VesperRaiderSheet.slice(pose);
        regions[pose.ordinal()] = VesperRaiderSheet.orientedForDescent(
            new TextureRegion(texture, slice.x(), slice.y(), slice.width(), slice.height()));
      }
    }

    private TextureRegion region(int bank) {
      return regions[VesperRaiderSheet.poseForBank(bank).ordinal()];
    }

    @Override
    public void dispose() {
      texture.dispose();
    }
  }
}
