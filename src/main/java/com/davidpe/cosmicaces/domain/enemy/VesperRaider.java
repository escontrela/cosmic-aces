package com.davidpe.cosmicaces.domain.enemy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.ship.MovementIntent;
import com.davidpe.cosmicaces.domain.ship.Ship;

/**
 * An autonomous enemy that flies by its own heading and wanders the finite world. Its heading uses
 * the descent convention (0 points down, positive turns right) so the v3 sheet keeps its orientation
 * when the sprite is rotated by the heading.
 */
public final class VesperRaider extends Ship {

  private static final float TURN_EPSILON_DEGREES = 0.001f;

  private float headingDegrees;
  private int turnDirection;
  private int driftDirection;
  private Visuals visuals;

  public VesperRaider(float speed, float width, float height, float x, float y,
      float headingDegrees) {
    super(speed, x, y, width, height, VesperRaiderSheet.maxSliceWidth(),
        VesperRaiderSheet.maxSliceHeight());
    if (width <= 0f || height <= 0f || !Float.isFinite(width) || !Float.isFinite(height)) {
      throw new IllegalArgumentException("Raider box must be positive: " + width + "x" + height);
    }
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

  public float headingDegrees() {
    return headingDegrees;
  }

  /** Direction of the last bounded turn: -1 left, 0 straight, +1 right. */
  public int turnDirection() {
    return turnDirection;
  }

  /** Replaces the heading and clears any in-progress turn. */
  public void setHeadingDegrees(float headingDegrees) {
    if (!Float.isFinite(headingDegrees)) {
      throw new IllegalArgumentException("Heading must be finite");
    }
    this.headingDegrees = normalizeYaw(headingDegrees);
    turnDirection = 0;
  }

  /** Records the side of the current drift so the banked pose can reflect it. */
  public void setDriftDirection(int driftDirection) {
    this.driftDirection = Integer.signum(driftDirection);
  }

  /**
   * Rotates the heading toward {@code targetHeadingDegrees} by at most {@code maxDeltaDegrees}.
   * The applied change is clamped, so a distant target is approached over several steps without a
   * jump, and the applied direction is remembered for pose selection.
   */
  public void steerTowards(float targetHeadingDegrees, float maxDeltaDegrees) {
    if (!Float.isFinite(targetHeadingDegrees) || !Float.isFinite(maxDeltaDegrees)
        || maxDeltaDegrees < 0f) {
      throw new IllegalArgumentException(
          "Steering target and rate must be finite and non-negative");
    }
    float difference = normalizeYaw(targetHeadingDegrees - headingDegrees);
    float applied = Math.max(-maxDeltaDegrees, Math.min(maxDeltaDegrees, difference));
    headingDegrees = normalizeYaw(headingDegrees + applied);
    turnDirection = Math.abs(applied) < TURN_EPSILON_DEGREES ? 0 : (applied > 0f ? 1 : -1);
  }

  /** Last-resort safeguard that keeps the raider box inside the finite world. */
  public void clampToWorld(WorldBounds world) {
    setPosition(world.clampX(x(), drawWidth()), world.clampY(y(), drawHeight()));
  }

  public void advance(float deltaSeconds) {
    advance(MovementIntent.fromDownwardHeading(headingDegrees), deltaSeconds);
  }

  VesperRaiderSheet.Pose pose() {
    if (turnDirection != 0) {
      return VesperRaiderSheet.poseForTurn(turnDirection);
    }
    return VesperRaiderSheet.poseForBank(driftDirection);
  }

  private static float normalizeYaw(float yaw) {
    float normalized = yaw % 360f;
    if (normalized > 180f) normalized -= 360f;
    if (normalized <= -180f) normalized += 360f;
    return normalized;
  }

  @Override
  protected TextureRegion currentRegion() {
    if (visuals == null) {
      throw new IllegalStateException("Vesper Raider visuals have not been attached");
    }
    return visuals.region(pose());
  }

  /** One shared sprite sheet for the single raider of a phase. */
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

    private TextureRegion region(VesperRaiderSheet.Pose pose) {
      return regions[pose.ordinal()];
    }

    @Override
    public void dispose() {
      texture.dispose();
    }
  }
}
