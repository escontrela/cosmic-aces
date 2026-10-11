package com.davidpe.cosmicaces.domain.enemy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.ship.MovementIntent;
import com.davidpe.cosmicaces.domain.ship.Ship;

/**
 * A slow unarmed Ghostwing scout drone that keeps one fixed cardinal direction across its whole
 * crossing of the phase world: it never turns, chases or fires. The shared three-frame propeller
 * sheet animates only the propeller phase; the body stays centered in a stable box that matches the
 * square 724×724 cell without stretching it.
 *
 * <p>Direction maps to the render rotation measured from the sheet's base orientation (nose up,
 * +Y): 0° north, -90° east, 180° south, +90° west. The drone builds its own {@link Visuals} only
 * when the presentation layer attaches them; the model itself stays constructible and testable
 * without starting LibGDX.
 */
public final class GhostwingDrone extends Ship {

  /** Seconds each propeller frame is held; the phase cycles 0, 1, 2. */
  public static final float PROPELLER_FRAME_SECONDS = 0.08f;

  /** Cardinal travel directions with their movement vector and sheet-rotation in degrees. */
  public enum Direction {
    NORTH(0f, 1f, 0f),
    EAST(1f, 0f, -90f),
    SOUTH(0f, -1f, 180f),
    WEST(-1f, 0f, 90f);

    private final float horizontal;
    private final float vertical;
    private final float rotationDegrees;

    Direction(float horizontal, float vertical, float rotationDegrees) {
      this.horizontal = horizontal;
      this.vertical = vertical;
      this.rotationDegrees = rotationDegrees;
    }

    /** Unit horizontal movement for {@link MovementIntent}. */
    public float horizontal() {
      return horizontal;
    }

    /** Unit vertical movement for {@link MovementIntent}. */
    public float vertical() {
      return vertical;
    }

    /** Degrees the base nose-up region must rotate around its center for this travel direction. */
    public float rotationDegrees() {
      return rotationDegrees;
    }
  }

  private final long id;
  private final Direction direction;
  private float propellerSeconds;
  private Visuals visuals;

  public GhostwingDrone(long id, float speed, float x, float y, float width, float height,
      Direction direction) {
    super(speed, x, y, width, height, GhostwingDroneSheet.FRAME_SIZE,
        GhostwingDroneSheet.FRAME_SIZE);
    if (direction == null) {
      throw new IllegalArgumentException("Direction is required");
    }
    this.id = id;
    this.direction = direction;
  }

  /** Stable identity of this drone, so combat and presentation can refer to one instance. */
  public long id() {
    return id;
  }

  /** The fixed cardinal direction this drone travels in for its whole crossing. */
  public Direction direction() {
    return direction;
  }

  /** World X of the drone's stable box centre. */
  public float centerX() {
    return x() + drawWidth() / 2f;
  }

  /** World Y of the drone's stable box centre. */
  public float centerY() {
    return y() + drawHeight() / 2f;
  }

  /** Rotates the drone's sprite for its fixed direction; never changes during the crossing. */
  public float rotationDegrees() {
    return direction.rotationDegrees();
  }

  public void setVisuals(Visuals visuals) {
    this.visuals = visuals;
  }

  /**
   * Advances the drone one frame along its fixed cardinal direction and cycles the propeller clock.
   * Non-positive or non-finite deltas are ignored; movement is straight, so splitting a delta does
   * not change the trajectory.
   */
  public void advance(float deltaSeconds) {
    if (deltaSeconds <= 0f || !Float.isFinite(deltaSeconds)) {
      return;
    }
    advance(new MovementIntent(direction.horizontal(), direction.vertical()), deltaSeconds);
    propellerSeconds += deltaSeconds;
  }

  /** Current propeller animation frame in [0, {@link GhostwingDroneSheet#FRAME_COUNT}). */
  public int propellerFrame() {
    return (int) (propellerSeconds / PROPELLER_FRAME_SECONDS) % GhostwingDroneSheet.FRAME_COUNT;
  }

  @Override
  protected TextureRegion currentRegion() {
    if (visuals == null) {
      throw new IllegalStateException("Ghostwing drone visuals have not been attached");
    }
    return visuals.region(propellerFrame());
  }

  /**
   * Shared three-frame visual from the bundled Ghostwing sheet. One instance serves every drone of a
   * phase; the phase screen owns it and disposes it exactly once. No drone holds its own texture.
   */
  public static final class Visuals implements Disposable {
    private final Texture texture;
    private final TextureRegion[] frames =
        new TextureRegion[GhostwingDroneSheet.FRAME_COUNT];

    public Visuals() {
      texture = new Texture(Gdx.files.internal(GhostwingDroneSheet.internalPath()));
      for (int i = 0; i < GhostwingDroneSheet.FRAME_COUNT; i++) {
        GhostwingDroneSheet.Slice slice = GhostwingDroneSheet.frameSlice(i);
        frames[i] =
            new TextureRegion(texture, slice.x(), slice.y(), slice.width(), slice.height());
      }
    }

    /** Borrowed region for one propeller frame; callers must not modify it. */
    public TextureRegion region(int frameIndex) {
      return frames[frameIndex];
    }

    @Override
    public void dispose() {
      texture.dispose();
    }
  }
}