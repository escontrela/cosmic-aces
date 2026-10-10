package com.davidpe.cosmicaces.domain.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.ship.Ship;
import com.davidpe.cosmicaces.domain.weapon.GunBurst;
import java.util.ArrayList;
import java.util.List;

/** The player's ship and its continuous, world-space flight state. */
public final class Astra extends Ship {

  public static final float DEFAULT_SPEED = FlightTuning.NORMAL_SPEED;
  // The tighter v3 slices need a larger pixel scale to retain the existing on-screen ship size.
  private static final float SPRITE_SCALE = 0.145f;
  private static final int MAX_REGION_WIDTH = maxRegionWidth();
  private static final int MAX_REGION_HEIGHT = maxRegionHeight();

  private Visuals visuals;
  private HeroShipSheet.Pose pose = HeroShipSheet.Pose.NEUTRAL;
  private boolean accelerating;
  private boolean muzzleFlashVisible;
  private float yawDegrees;
  private float flightSpeed = FlightTuning.NORMAL_SPEED;
  private float ultraRemainingSeconds;
  private boolean ultraUsed;

  public Astra() {
    this(0f, 0f);
  }

  public Astra(float x, float y) {
    super(DEFAULT_SPEED, x, y, MAX_REGION_WIDTH * SPRITE_SCALE, MAX_REGION_HEIGHT * SPRITE_SCALE,
        MAX_REGION_WIDTH, MAX_REGION_HEIGHT);
  }

  public void setVisuals(Visuals visuals) {
    this.visuals = visuals;
  }

  /** Presentation flag driven by the cannon's own emission pulse; never independently timed. */
  public void setMuzzleFlashVisible(boolean muzzleFlashVisible) {
    this.muzzleFlashVisible = muzzleFlashVisible;
  }

  public float yawDegrees() {
    return yawDegrees;
  }

  public float flightSpeed() {
    return flightSpeed;
  }

  public float height() {
    return FlightTuning.FIXED_HEIGHT;
  }

  public float pitchDegrees() {
    return FlightTuning.FIXED_PITCH_DEGREES;
  }

  public float ultraRemainingSeconds() {
    return ultraRemainingSeconds;
  }

  public boolean ultraUsed() {
    return ultraUsed;
  }

  /** Initializes a ship within the usable world rectangle; zero yaw points north. */
  public void placeAt(float x, float y, float yawDegrees, WorldBounds world) {
    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(yawDegrees)) {
      throw new IllegalArgumentException("Flight position and yaw must be finite");
    }
    setPosition(world.clampX(x, drawWidth()), world.clampY(y, drawHeight()));
    this.yawDegrees = normalizeYaw(yawDegrees);
  }

  /** Advances movement, speed modes and border steering in bounded integration steps. */
  public void fly(FlightControls controls, float deltaSeconds, WorldBounds world) {
    if (controls == null || world == null) {
      throw new IllegalArgumentException("Flight controls and world are required");
    }
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
      return;
    }
    if (controls.activateUltra() && !ultraUsed) {
      ultraUsed = true;
      ultraRemainingSeconds = FlightTuning.ULTRA_SECONDS;
    }
    pose = controls.turning()
        ? (controls.lateral() < 0 ? HeroShipSheet.Pose.YAW_LEFT : HeroShipSheet.Pose.YAW_RIGHT)
        : controls.lateral() < 0 ? HeroShipSheet.Pose.LEFT
        : controls.lateral() > 0 ? HeroShipSheet.Pose.RIGHT : HeroShipSheet.Pose.NEUTRAL;
    float remaining = deltaSeconds;
    while (remaining > 0f) {
      float step = Math.min(remaining, FlightTuning.MAX_STEP_SECONDS);
      advanceStep(controls, step, world);
      remaining -= step;
      if (remaining < 0.000001f) {
        break;
      }
    }
  }

  private void advanceStep(FlightControls controls, float step, WorldBounds world) {
    boolean ultra = ultraRemainingSeconds > 0f;
    float target = controls.down() ? 0f
        : ultra ? FlightTuning.ULTRA_SPEED
        : controls.up() ? FlightTuning.TURBO_SPEED : FlightTuning.NORMAL_SPEED;
    float rate = controls.down() ? FlightTuning.BRAKE_RATE : FlightTuning.RECOVERY_RATE;
    float change = rate * step;
    flightSpeed = flightSpeed < target ? Math.min(target, flightSpeed + change)
        : Math.max(target, flightSpeed - change);
    accelerating = !controls.down() && (controls.up() || ultra);

    int lateral = controls.lateral();
    boolean avoidingBorder = steerFromBorder(step, world);
    if (controls.turning() && !avoidingBorder) {
      yawDegrees = normalizeYaw(yawDegrees + lateral * FlightTuning.YAW_RATE_DEGREES * step);
    }
    double radians = Math.toRadians(yawDegrees);
    float forwardX = (float) Math.sin(radians);
    float forwardY = (float) Math.cos(radians);
    float strafe = controls.turning() ? 0f : lateral * FlightTuning.STRAFE_SPEED;
    float nextX = x() + (forwardX * flightSpeed + forwardY * strafe) * step;
    float nextY = y() + (forwardY * flightSpeed - forwardX * strafe) * step;
    setPosition(world.clampX(nextX, drawWidth()), world.clampY(nextY, drawHeight()));
    ultraRemainingSeconds = Math.max(0f, ultraRemainingSeconds - step);
  }

  private boolean steerFromBorder(float step, WorldBounds world) {
    float zone = Math.max(FlightTuning.NORMAL_SPEED,
        flightSpeed * 1.5f);
    double radians = Math.toRadians(yawDegrees);
    float east = (float) Math.sin(radians);
    float north = (float) Math.cos(radians);
    float inwardX = 0f;
    float inwardY = 0f;
    if (x() < zone && (east < 0f || x() < zone * 0.5f)) {
      inwardX += (zone - x()) / zone;
    }
    if (world.maxX(drawWidth()) - x() < zone
        && (east > 0f || world.maxX(drawWidth()) - x() < zone * 0.5f)) {
      inwardX -= (zone - (world.maxX(drawWidth()) - x())) / zone;
    }
    if (y() < zone && (north < 0f || y() < zone * 0.5f)) {
      inwardY += (zone - y()) / zone;
    }
    if (world.maxY(drawHeight()) - y() < zone
        && (north > 0f || world.maxY(drawHeight()) - y() < zone * 0.5f)) {
      inwardY -= (zone - (world.maxY(drawHeight()) - y())) / zone;
    }
    if (inwardX == 0f && inwardY == 0f) return false;
    float target = (float) Math.toDegrees(Math.atan2(inwardX, inwardY));
    float difference = normalizeYaw(target - yawDegrees);
    float limit = FlightTuning.YAW_RATE_DEGREES * step;
    yawDegrees = normalizeYaw(yawDegrees + Math.max(-limit, Math.min(limit, difference)));
    return true;
  }

  private static float normalizeYaw(float yaw) {
    float normalized = yaw % 360f;
    if (normalized > 180f) normalized -= 360f;
    if (normalized <= -180f) normalized += 360f;
    return normalized;
  }

  /**
   * Builds a two-cannon emission snapshot from a box center and heading, freezing the direction and
   * both measured {@link HeroShipSheet.CannonMouth} origins so later turns or movement cannot bend
   * the shots. The heading uses {@code forward=(sin yaw, cos yaw)} and {@code right=(cos yaw,
   * -sin yaw)}; the mouths are transformed with the current pose's lateral/forward offsets.
   *
   * @param centerX world X of the stable box center
   * @param centerY world Y of the stable box center
   * @param yawDegrees heading at the emission instant; zero points north
   * @param range travel distance frozen into each projectile
   */
  public GunBurst.Shot shotAt(float centerX, float centerY, float yawDegrees, float range) {
    if (!Float.isFinite(centerX) || !Float.isFinite(centerY) || !Float.isFinite(yawDegrees)) {
      throw new IllegalArgumentException("Shot center and heading must be finite");
    }
    double radians = Math.toRadians(yawDegrees);
    float forwardX = (float) Math.sin(radians);
    float forwardY = (float) Math.cos(radians);
    float rightX = (float) Math.cos(radians);
    float rightY = (float) -Math.sin(radians);
    List<GunBurst.Muzzle> muzzles = new ArrayList<>(HeroShipSheet.cannonMouths(pose).size());
    for (HeroShipSheet.CannonMouth mouth : HeroShipSheet.cannonMouths(pose)) {
      float lateral = mouth.lateral() * drawWidth();
      float forward = mouth.forward() * drawHeight();
      muzzles.add(new GunBurst.Muzzle(
          centerX + rightX * lateral + forwardX * forward,
          centerY + rightY * lateral + forwardY * forward));
    }
    return new GunBurst.Shot(muzzles, forwardX, forwardY, range);
  }

  HeroShipSheet.Pose pose() {
    return pose;
  }

  boolean accelerating() {
    return accelerating;
  }

  @Override
  protected TextureRegion currentRegion() {
    if (visuals == null) {
      throw new IllegalStateException("Astra visuals have not been attached");
    }
    return visuals.region(pose, accelerating, muzzleFlashVisible);
  }

  /**
   * Keeps the body anchored between the flight and firing variants: while the muzzle flash is
   * visible the firing region is shifted by the pose/family correction measured on the sheets, with
   * the same uniform scale and the rotation pivot unchanged (the draw origin stays on the box
   * center).
   */
  @Override
  protected SpritePlacement spritePlacement(TextureRegion region) {
    SpritePlacement centered = super.spritePlacement(region);
    if (!muzzleFlashVisible) {
      return centered;
    }
    HeroShipSheet.FiringPlacement firing = HeroShipSheet.firingPlacement(pose, accelerating);
    return new SpritePlacement(centered.scale(),
        firing.offsetXPx() * centered.scale(), firing.offsetYPx() * centered.scale());
  }

  private static int maxRegionWidth() {
    int max = 0;
    for (HeroShipSheet.Sheet sheet : HeroShipSheet.sheets()) {
      for (HeroShipSheet.Slice slice : sheet.slices()) {
        max = Math.max(max, slice.width());
      }
    }
    return max;
  }

  private static int maxRegionHeight() {
    int max = 0;
    for (HeroShipSheet.Sheet sheet : HeroShipSheet.sheets()) {
      for (HeroShipSheet.Slice slice : sheet.slices()) {
        max = Math.max(max, slice.height());
      }
    }
    return max;
  }

  /** Loads flight and firing variants once; the owning screen disposes all four textures. */
  public static final class Visuals implements Disposable {
    private final Texture normalTexture;
    private final Texture accelerateTexture;
    private final Texture normalFiringTexture;
    private final Texture accelerateFiringTexture;
    private final TextureRegion[] normal;
    private final TextureRegion[] accelerate;
    private final TextureRegion[] normalFiring;
    private final TextureRegion[] accelerateFiring;

    public Visuals() {
      normalTexture = new Texture(Gdx.files.internal(HeroShipSheet.NORMAL.internalPath()));
      Texture loadedAccelerate = null;
      Texture loadedNormalFiring = null;
      Texture loadedAccelerateFiring = null;
      try {
        loadedAccelerate = new Texture(Gdx.files.internal(HeroShipSheet.ACCELERATE.internalPath()));
        loadedNormalFiring = new Texture(Gdx.files.internal(HeroShipSheet.NORMAL_FIRING.internalPath()));
        loadedAccelerateFiring = new Texture(
            Gdx.files.internal(HeroShipSheet.ACCELERATE_FIRING.internalPath()));
      } catch (RuntimeException | Error error) {
        if (loadedAccelerateFiring != null) loadedAccelerateFiring.dispose();
        if (loadedNormalFiring != null) loadedNormalFiring.dispose();
        if (loadedAccelerate != null) loadedAccelerate.dispose();
        normalTexture.dispose();
        throw error;
      }
      accelerateTexture = loadedAccelerate;
      normalFiringTexture = loadedNormalFiring;
      accelerateFiringTexture = loadedAccelerateFiring;
      normal = regions(HeroShipSheet.NORMAL, normalTexture);
      accelerate = regions(HeroShipSheet.ACCELERATE, accelerateTexture);
      normalFiring = regions(HeroShipSheet.NORMAL_FIRING, normalFiringTexture);
      accelerateFiring = regions(HeroShipSheet.ACCELERATE_FIRING, accelerateFiringTexture);
    }

    private TextureRegion region(HeroShipSheet.Pose pose, boolean accelerating) {
      return region(pose, accelerating, false);
    }

    /** Selects a prepared pose; the weapon's burst timing will supply the flashing flag. */
    public TextureRegion region(HeroShipSheet.Pose pose, boolean accelerating, boolean firing) {
      if (firing) return (accelerating ? accelerateFiring : normalFiring)[pose.ordinal()];
      return (accelerating ? accelerate : normal)[pose.ordinal()];
    }

    private static TextureRegion[] regions(HeroShipSheet.Sheet sheet, Texture texture) {
      TextureRegion[] result = new TextureRegion[HeroShipSheet.Pose.values().length];
      for (HeroShipSheet.Pose pose : HeroShipSheet.Pose.values()) {
        HeroShipSheet.Slice slice = sheet.slice(pose);
        result[pose.ordinal()] = new TextureRegion(texture, slice.x(), slice.y(),
            slice.width(), slice.height());
      }
      return result;
    }

    @Override
    public void dispose() {
      normalTexture.dispose();
      accelerateTexture.dispose();
      normalFiringTexture.dispose();
      accelerateFiringTexture.dispose();
    }
  }
}
