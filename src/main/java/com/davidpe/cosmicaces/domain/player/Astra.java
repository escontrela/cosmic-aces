package com.davidpe.cosmicaces.domain.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.ship.Ship;

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
    float strafe = controls.turning() ? 0f : lateral * FlightTuning.NORMAL_SPEED;
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
    return visuals.region(pose, accelerating);
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

  /** Loads the two Astra sheets once; the owning screen disposes them when it closes. */
  public static final class Visuals implements Disposable {
    private final Texture normalTexture;
    private final Texture accelerateTexture;
    private final TextureRegion[] normal;
    private final TextureRegion[] accelerate;

    public Visuals() {
      normalTexture = new Texture(Gdx.files.internal(HeroShipSheet.NORMAL.internalPath()));
      try {
        accelerateTexture = new Texture(Gdx.files.internal(HeroShipSheet.ACCELERATE.internalPath()));
      } catch (RuntimeException error) {
        normalTexture.dispose();
        throw error;
      }
      normal = regions(HeroShipSheet.NORMAL, normalTexture);
      accelerate = regions(HeroShipSheet.ACCELERATE, accelerateTexture);
    }

    private TextureRegion region(HeroShipSheet.Pose pose, boolean accelerating) {
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
    }
  }
}
