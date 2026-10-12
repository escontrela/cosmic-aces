package com.davidpe.cosmicaces.domain.enemy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.ship.MovementIntent;
import com.davidpe.cosmicaces.domain.ship.Ship;
import com.davidpe.cosmicaces.domain.weapon.GunBurst;
import java.util.ArrayList;
import java.util.List;

/**
 * An autonomous enemy that flies by its own heading and wanders the finite world. Its heading uses
 * the descent convention (0 points down, positive turns right) so the v4 sheet keeps its orientation
 * when the sprite is rotated by the heading.
 */
public final class VesperRaider extends Ship {

  private static final float TURN_EPSILON_DEGREES = 0.001f;
  /**
   * Angular-velocity dead band for the yaw pose: the turn pose engages once the physical turn
   * reaches the enter threshold and only releases below the (lower) exit threshold, so a nearly
   * straight heading does not flicker between neutral and a yaw frame.
   */
  private static final float TURN_POSE_ENTER_DEGREES_PER_SECOND = 5f;
  private static final float TURN_POSE_EXIT_DEGREES_PER_SECOND = 1f;
  /**
   * Heading error, in degrees, below which the controller snaps onto the target and damps the
   * angular velocity to zero without reversing it. This is far below any visible turn and stops the
   * bounded deceleration profile from limit-cycling around the target.
   */
  private static final float TARGET_SNAP_DEGREES = 0.05f;

  private float headingDegrees;
  private int turnDirection;
  private int driftDirection;
  private boolean muzzleFlashVisible;
  private Visuals visuals;
  /** Current effective speed, ramped toward the encounter's target by bounded acceleration. */
  private float currentSpeed;
  /** Current angular velocity of the heading, in degrees per second (sign = turn side). */
  private float angularVelocityDegrees;
  /** Whether the yaw pose is latched on, held with hysteresis around the angular dead band. */
  private boolean yawPoseActive;

  public VesperRaider(float speed, float width, float height, float x, float y,
      float headingDegrees) {
    super(speed, x, y, width, height, VesperRaiderSheet.maxSliceWidth(),
        VesperRaiderSheet.maxSliceHeight());
    if (width <= 0f || height <= 0f || !Float.isFinite(width) || !Float.isFinite(height)) {
      throw new IllegalArgumentException("Raider box must be positive: " + width + "x" + height);
    }
    currentSpeed = speed;
    setHeadingDegrees(headingDegrees);
  }

  public void setVisuals(Visuals visuals) {
    this.visuals = visuals;
  }

  /** Presentation flag driven by the cannon's own emission pulse; never independently timed. */
  public void setMuzzleFlashVisible(boolean muzzleFlashVisible) {
    this.muzzleFlashVisible = muzzleFlashVisible;
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

  /** Current effective speed in world units per second, after bounded acceleration. */
  public float currentSpeed() {
    return currentSpeed;
  }

  /** Current heading angular velocity in degrees per second (sign selects the turn side). */
  public float angularVelocityDegrees() {
    return angularVelocityDegrees;
  }

  /** Direction of the last bounded turn: -1 left, 0 straight, +1 right. */
  public int turnDirection() {
    return turnDirection;
  }

  /** Replaces the heading and clears any in-progress turn, angular velocity and speed ramp. */
  public void setHeadingDegrees(float headingDegrees) {
    if (!Float.isFinite(headingDegrees)) {
      throw new IllegalArgumentException("Heading must be finite");
    }
    this.headingDegrees = normalizeYaw(headingDegrees);
    turnDirection = 0;
    angularVelocityDegrees = 0f;
    yawPoseActive = false;
    currentSpeed = speed();
  }

  /** Places the raider centre at a world point while keeping it inside the finite world. */
  public void placeAt(float x, float y, float headingDegrees, WorldBounds world) {
    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(headingDegrees)) {
      throw new IllegalArgumentException("Raider position and heading must be finite");
    }
    setPosition(world.clampX(x, drawWidth()), world.clampY(y, drawHeight()));
    setHeadingDegrees(headingDegrees);
    setDriftDirection(0);
  }

  /** World X of the ship's stable body centre. */
  public float centerX() {
    return x() + drawWidth() / 2f;
  }

  /** World Y of the ship's stable body centre. */
  public float centerY() {
    return y() + drawHeight() / 2f;
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

  /**
   * Turns the heading toward {@code targetHeadingDegrees} with bounded angular velocity and angular
   * acceleration, integrating one bounded step. The requested angular velocity follows a time
   * optimal deceleration profile ({@code min(omegaMax, sqrt(2 * angularAcceleration * |error|))}) so
   * the turn bleeds off as it approaches the target instead of overshooting it, and the applied
   * change per call never exceeds {@code angularAcceleration * deltaSeconds}. The applied angular
   * velocity is the single source of the yaw pose, latched with a small hysteresis band.
   *
   * <p>A non-positive or non-finite {@code deltaSeconds} leaves the heading, angular velocity and
   * pose untouched, matching the encounter's delta contract.
   *
   * @param maxAngularAccelerationDegreesPerSecondSquared angular acceleration limit, in deg/s²
   */
  public void steerTowards(float targetHeadingDegrees, float maxTurnRateDegrees,
      float maxAngularAccelerationDegreesPerSecondSquared, float deltaSeconds) {
    if (!Float.isFinite(targetHeadingDegrees) || !Float.isFinite(maxTurnRateDegrees)
        || maxTurnRateDegrees < 0f
        || !Float.isFinite(maxAngularAccelerationDegreesPerSecondSquared)
        || maxAngularAccelerationDegreesPerSecondSquared < 0f) {
      throw new IllegalArgumentException(
          "Steering target, rate and acceleration must be finite and non-negative");
    }
    if (deltaSeconds <= 0f || !Float.isFinite(deltaSeconds)) {
      return;
    }
    float maxChange = maxAngularAccelerationDegreesPerSecondSquared * deltaSeconds;
    float difference = normalizeYaw(targetHeadingDegrees - headingDegrees);
    if (Math.abs(difference) <= TARGET_SNAP_DEGREES) {
      // Settle exactly on the target and bleed the last angular velocity off toward zero without
      // reversing it, so the turn never flickers left/right around the final heading.
      headingDegrees = normalizeYaw(targetHeadingDegrees);
      angularVelocityDegrees = dampTowardZero(angularVelocityDegrees, maxChange);
      updateTurnPose();
      return;
    }
    float brakingSpeed = maxAngularAccelerationDegreesPerSecondSquared <= 0f
        ? maxTurnRateDegrees
        : (float) Math.sqrt(2d * maxAngularAccelerationDegreesPerSecondSquared
            * Math.abs(difference));
    float desiredAngularVelocity = Math.min(maxTurnRateDegrees, brakingSpeed);
    if (difference < 0f) {
      desiredAngularVelocity = -desiredAngularVelocity;
    }
    angularVelocityDegrees += Math.max(-maxChange,
        Math.min(maxChange, desiredAngularVelocity - angularVelocityDegrees));
    angularVelocityDegrees = Math.max(-maxTurnRateDegrees,
        Math.min(maxTurnRateDegrees, angularVelocityDegrees));
    headingDegrees = normalizeYaw(headingDegrees + angularVelocityDegrees * deltaSeconds);
    updateTurnPose();
  }

  /** Reduces a signed value toward zero by at most {@code maxChange}, never crossing it. */
  private static float dampTowardZero(float value, float maxChange) {
    if (Math.abs(value) <= maxChange) {
      return 0f;
    }
    return value > 0f ? value - maxChange : value + maxChange;
  }

  /**
   * Integrates one bounded step along the current heading while ramping the effective speed toward
   * {@code targetSpeed} with separate acceleration and deceleration limits, so turbo or phase
   * changes can never teleport the speed. A non-positive or non-finite {@code deltaSeconds} leaves
   * the speed and position untouched.
   */
  public void advance(float deltaSeconds, float targetSpeed, float acceleration,
      float deceleration) {
    if (!Float.isFinite(targetSpeed) || targetSpeed <= 0f
        || !Float.isFinite(acceleration) || acceleration < 0f
        || !Float.isFinite(deceleration) || deceleration < 0f) {
      throw new IllegalArgumentException(
          "Speed target and rates must be finite and positive");
    }
    if (deltaSeconds <= 0f || !Float.isFinite(deltaSeconds)) {
      return;
    }
    float rate = targetSpeed >= currentSpeed ? acceleration : deceleration;
    float change = rate * deltaSeconds;
    currentSpeed = currentSpeed < targetSpeed ? Math.min(targetSpeed, currentSpeed + change)
        : Math.max(targetSpeed, currentSpeed - change);
    advance(MovementIntent.fromDownwardHeading(headingDegrees), deltaSeconds, currentSpeed);
  }

  /** Latches the yaw pose from the physical turn with hysteresis to avoid neutral flicker. */
  private void updateTurnPose() {
    if (!yawPoseActive
        && Math.abs(angularVelocityDegrees) >= TURN_POSE_ENTER_DEGREES_PER_SECOND) {
      yawPoseActive = true;
    } else if (yawPoseActive
        && Math.abs(angularVelocityDegrees) <= TURN_POSE_EXIT_DEGREES_PER_SECOND) {
      yawPoseActive = false;
    }
    turnDirection = !yawPoseActive ? 0
        : angularVelocityDegrees > 0f ? 1 : angularVelocityDegrees < 0f ? -1 : 0;
  }

  /** Last-resort safeguard that keeps the raider box inside the finite world. */
  public void clampToWorld(WorldBounds world) {
    setPosition(world.clampX(x(), drawWidth()), world.clampY(y(), drawHeight()));
  }

  public void advance(float deltaSeconds) {
    advance(MovementIntent.fromDownwardHeading(headingDegrees), deltaSeconds);
  }

  /**
   * Advances along the current heading at an explicit speed. The encounter uses it during its
   * bounded tactical turbo window; turns stay limited by {@link #steerTowards(float, float)}.
   */
  public void advance(float deltaSeconds, float speed) {
    advance(MovementIntent.fromDownwardHeading(headingDegrees), deltaSeconds, speed);
  }

  VesperRaiderSheet.Pose pose() {
    if (turnDirection != 0) {
      return VesperRaiderSheet.poseForTurn(turnDirection);
    }
    return VesperRaiderSheet.poseForBank(driftDirection);
  }

  /**
   * Builds a two-cannon emission snapshot from a box center and heading, freezing the direction and
   * both measured {@link VesperRaiderSheet.CannonMouth} origins so later turns or movement cannot
   * bend the shots. The descent heading uses {@code forward=(sin heading, -cos heading)} and
   * {@code right=(cos heading, sin heading)}; the mouths are transformed with the current pose's
   * lateral/forward offsets.
   *
   * @param centerX world X of the stable box center
   * @param centerY world Y of the stable box center
   * @param headingDegrees descent heading at the emission instant; zero points down
   * @param range travel distance frozen into each projectile
   */
  public GunBurst.Shot shotAt(float centerX, float centerY, float headingDegrees, float range) {
    if (!Float.isFinite(centerX) || !Float.isFinite(centerY) || !Float.isFinite(headingDegrees)) {
      throw new IllegalArgumentException("Shot center and heading must be finite");
    }
    double radians = Math.toRadians(headingDegrees);
    float forwardX = (float) Math.sin(radians);
    float forwardY = (float) -Math.cos(radians);
    float rightX = (float) Math.cos(radians);
    float rightY = (float) Math.sin(radians);
    List<VesperRaiderSheet.CannonMouth> mouths = VesperRaiderSheet.cannonMouths(pose());
    List<GunBurst.Muzzle> muzzles = new ArrayList<>(mouths.size());
    for (VesperRaiderSheet.CannonMouth mouth : mouths) {
      float lateral = mouth.lateral() * drawWidth();
      float forward = mouth.forward() * drawHeight();
      muzzles.add(new GunBurst.Muzzle(
          centerX + rightX * lateral + forwardX * forward,
          centerY + rightY * lateral + forwardY * forward));
    }
    return new GunBurst.Shot(muzzles, forwardX, forwardY, range);
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
    return visuals.region(pose(), muzzleFlashVisible);
  }

  /**
   * Keeps the body anchored between the flight and firing variants: while the muzzle flash is
   * visible the firing region is shifted by the per-pose correction measured on the sheets, with
   * the same uniform scale and the rotation pivot unchanged (the draw origin stays on the box
   * center). {@code orientedForDescent} is already applied once by {@link Visuals} and is never
   * repeated per frame.
   */
  @Override
  protected Ship.SpritePlacement spritePlacement(TextureRegion region) {
    Ship.SpritePlacement centered = super.spritePlacement(region);
    if (!muzzleFlashVisible) {
      return centered;
    }
    VesperRaiderSheet.FiringPlacement firing = VesperRaiderSheet.firingPlacement(pose());
    return new Ship.SpritePlacement(centered.scale(),
        firing.offsetXPx() * centered.scale(), firing.offsetYPx() * centered.scale());
  }

  /** Flight and firing sheets shared by the single raider; owned by the phase screen. */
  public static final class Visuals implements Disposable {
    private final Texture texture;
    private final Texture firingTexture;
    private final TextureRegion[] regions;
    private final TextureRegion[] firingRegions;

    public Visuals() {
      texture = new Texture(Gdx.files.internal(VesperRaiderSheet.internalPath()));
      try {
        firingTexture = new Texture(Gdx.files.internal(VesperRaiderSheet.firingInternalPath()));
      } catch (RuntimeException | Error failure) {
        texture.dispose();
        throw failure;
      }
      regions = new TextureRegion[VesperRaiderSheet.Pose.values().length];
      firingRegions = new TextureRegion[VesperRaiderSheet.Pose.values().length];
      for (VesperRaiderSheet.Pose pose : VesperRaiderSheet.Pose.values()) {
        VesperRaiderSheet.Slice slice = VesperRaiderSheet.slice(pose);
        regions[pose.ordinal()] = VesperRaiderSheet.orientedForDescent(
            new TextureRegion(texture, slice.x(), slice.y(), slice.width(), slice.height()));
        VesperRaiderSheet.Slice firingSlice = VesperRaiderSheet.firingSlice(pose);
        firingRegions[pose.ordinal()] = VesperRaiderSheet.orientedForDescent(
            new TextureRegion(firingTexture, firingSlice.x(), firingSlice.y(),
                firingSlice.width(), firingSlice.height()));
      }
    }

    private TextureRegion region(VesperRaiderSheet.Pose pose) {
      return region(pose, false);
    }

    /** Prepared firing pose; caller supplies the muzzle-flash timing. */
    public TextureRegion region(VesperRaiderSheet.Pose pose, boolean firing) {
      return (firing ? firingRegions : regions)[pose.ordinal()];
    }

    @Override
    public void dispose() {
      texture.dispose();
      firingTexture.dispose();
    }
  }
}
