package com.davidpe.cosmicaces.domain.enemy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.ship.MovementIntent;
import com.davidpe.cosmicaces.domain.ship.Ship;

/**
 * A weak, unarmed Geran-4 UAV that keeps one fixed heading across its whole straight flight: it
 * never turns, chases or fires. Unlike the Ghostwing drone, the heading is any unit direction, so
 * the encounter can send it across the player's visible zone from any side of the viewport. The
 * shared three-frame propeller sheet animates only the propeller phase; the body stays centered in
 * a stable box that matches the square 724×724 cell without stretching it.
 *
 * <p>The render rotation derives from the sheet's base orientation (nose up, +Y) with the same
 * convention as the Ghostwing drone (north 0°, east -90°, south 180°, west +90°), generalized to
 * any heading. The drone builds its own {@link Visuals} only when the presentation layer attaches
 * them; the model itself stays constructible and testable without starting LibGDX.
 */
public final class Geran4Uav extends Ship {

  /** Seconds each propeller frame is held; the phase cycles 0, 1, 2. */
  public static final float PROPELLER_FRAME_SECONDS = 0.08f;

  /**
   * Stable square box in world units: below the 80×80 fighter box (Vesper Raider) and equal to the
   * Ghostwing drone, never the bomber scale. The square 724×724 cell draws unstretched inside it.
   */
  public static final float BOX_WIDTH = 72f;
  public static final float BOX_HEIGHT = 72f;

  private final long id;
  private final float directionX;
  private final float directionY;
  private final float rotationDegrees;
  private float propellerSeconds;
  private Visuals visuals;

  public Geran4Uav(long id, float speed, float x, float y, float directionX, float directionY) {
    super(speed, x, y, BOX_WIDTH, BOX_HEIGHT, Geran4UavSheet.FRAME_SIZE, Geran4UavSheet.FRAME_SIZE);
    if (!Float.isFinite(directionX) || !Float.isFinite(directionY)) {
      throw new IllegalArgumentException("UAV direction must be finite");
    }
    double magnitude = Math.hypot(directionX, directionY);
    if (magnitude == 0d) {
      throw new IllegalArgumentException("UAV direction must not be the zero vector");
    }
    this.id = id;
    this.directionX = (float) (directionX / magnitude);
    this.directionY = (float) (directionY / magnitude);
    this.rotationDegrees = -(float) Math.toDegrees(
        Math.atan2(this.directionX, this.directionY));
  }

  /** Stable identity of this UAV, so combat and presentation can refer to one instance. */
  public long id() {
    return id;
  }

  /** Unit horizontal component of the fixed heading; never changes during the flight. */
  public float directionX() {
    return directionX;
  }

  /** Unit vertical component of the fixed heading; never changes during the flight. */
  public float directionY() {
    return directionY;
  }

  /** World X of the UAV's stable box centre. */
  public float centerX() {
    return x() + drawWidth() / 2f;
  }

  /** World Y of the UAV's stable box centre. */
  public float centerY() {
    return y() + drawHeight() / 2f;
  }

  /**
   * Degrees the base nose-up region rotates around its center for the fixed heading; equivalent
   * modulo 360° to the Ghostwing convention for the four cardinal directions.
   */
  public float rotationDegrees() {
    return rotationDegrees;
  }

  public void setVisuals(Visuals visuals) {
    this.visuals = visuals;
  }

  /**
   * Advances the UAV one frame along its fixed heading and cycles the propeller clock. Non-positive
   * or non-finite deltas are ignored; movement is straight, so splitting a delta does not change
   * the trajectory.
   */
  public void advance(float deltaSeconds) {
    if (deltaSeconds <= 0f || !Float.isFinite(deltaSeconds)) {
      return;
    }
    advance(new MovementIntent(directionX, directionY), deltaSeconds);
    propellerSeconds += deltaSeconds;
  }

  /** Current propeller animation frame in [0, {@link Geran4UavSheet#FRAME_COUNT}). */
  public int propellerFrame() {
    return (int) (propellerSeconds / PROPELLER_FRAME_SECONDS) % Geran4UavSheet.FRAME_COUNT;
  }

  @Override
  protected TextureRegion currentRegion() {
    if (visuals == null) {
      throw new IllegalStateException("Geran-4 UAV visuals have not been attached");
    }
    return visuals.region(propellerFrame());
  }

  /**
   * Shared three-frame visual from the bundled Geran-4 sheet. One instance serves every UAV of a
   * phase; the phase screen owns it and disposes it exactly once. No UAV holds its own texture.
   */
  public static final class Visuals implements Disposable {
    private final Texture texture;
    private final TextureRegion[] frames = new TextureRegion[Geran4UavSheet.FRAME_COUNT];

    public Visuals() {
      texture = new Texture(Gdx.files.internal(Geran4UavSheet.internalPath()));
      for (int i = 0; i < Geran4UavSheet.FRAME_COUNT; i++) {
        Geran4UavSheet.Slice slice = Geran4UavSheet.frameSlice(i);
        frames[i] = new TextureRegion(texture, slice.x(), slice.y(), slice.width(), slice.height());
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
