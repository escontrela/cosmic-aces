package com.davidpe.cosmicaces.domain.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.game.PlayArea;
import com.davidpe.cosmicaces.domain.ship.MovementIntent;
import com.davidpe.cosmicaces.domain.ship.Ship;

/** The player's ship, with bounded movement and its own normal and accelerated poses. */
public final class Astra extends Ship {

  public static final float DEFAULT_SPEED = 300f;
  private static final float SPRITE_SCALE = 0.11f;
  private static final int MAX_REGION_WIDTH = maxRegionWidth();
  private static final int MAX_REGION_HEIGHT = maxRegionHeight();

  private Visuals visuals;
  private HeroShipSheet.Pose pose = HeroShipSheet.Pose.NEUTRAL;
  private boolean accelerating;

  public Astra(float speed) {
    this(speed, 0f, 0f);
  }

  public Astra(float speed, float x, float y) {
    super(speed, x, y, MAX_REGION_WIDTH * SPRITE_SCALE, MAX_REGION_HEIGHT * SPRITE_SCALE,
        MAX_REGION_WIDTH, MAX_REGION_HEIGHT);
  }

  public void setVisuals(Visuals visuals) {
    this.visuals = visuals;
  }

  public void placeAt(float x, float y, PlayArea area) {
    setPosition(area.clampX(x), area.clampY(y));
  }

  public void move(MovementIntent intent, float deltaSeconds, PlayArea area) {
    move(intent, deltaSeconds, area, false);
  }

  public void move(MovementIntent intent, float deltaSeconds, PlayArea area, boolean accelerating) {
    this.accelerating = accelerating;
    pose = intent.horizontal() < 0f ? HeroShipSheet.Pose.LEFT
        : intent.horizontal() > 0f ? HeroShipSheet.Pose.RIGHT : HeroShipSheet.Pose.NEUTRAL;
    advance(intent, deltaSeconds);
    setPosition(area.clampX(x()), area.clampY(y()));
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
