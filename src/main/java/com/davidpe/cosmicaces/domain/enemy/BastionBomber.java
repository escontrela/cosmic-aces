package com.davidpe.cosmicaces.domain.enemy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.ship.Ship;
import java.util.Objects;

/**
 * Unarmed heavy bomber that waits on its runway, flies straight to the allied base, then patrols
 * above it.
 * Its state and flight path are deterministic; the owning screen attaches and disposes its visuals.
 */
public final class BastionBomber extends Ship {
  public static final float PARKED_SECONDS = 10f;
  /** Slower than Astra's default 150 u/s normal cruise speed. */
  public static final float FLIGHT_SPEED = 90f;
  public static final float ORBIT_RADIUS = 330f;
  public static final float ORBIT_ANGULAR_SPEED = 0.48f;
  public static final float DRAW_WIDTH = 160f;
  public static final float DRAW_HEIGHT = 200f;
  public static final float TURN_FRAME_SECONDS = 0.28f;

  public enum FlightState { PARKED, INBOUND, ORBITING }

  private final float targetX;
  private final float targetY;
  private final float orbitCenterX;
  private final float orbitCenterY;
  private FlightState flightState = FlightState.PARKED;
  private float parkedSeconds;
  private float animationSeconds;
  private float orbitAngle;
  private float headingDegrees = 180f;
  private Visuals visuals;

  public BastionBomber(float runwayCenterX, float runwayCenterY,
      float targetRunwayCenterX, float targetRunwayCenterY,
      float orbitCenterX, float orbitCenterY) {
    super(FLIGHT_SPEED, runwayCenterX - DRAW_WIDTH / 2f, runwayCenterY - DRAW_HEIGHT / 2f,
        DRAW_WIDTH, DRAW_HEIGHT, 416f, 506f);
    float[] coordinates = {runwayCenterX, runwayCenterY, targetRunwayCenterX,
        targetRunwayCenterY, orbitCenterX, orbitCenterY};
    for (float coordinate : coordinates) {
      if (!Float.isFinite(coordinate)) {
        throw new IllegalArgumentException("Bastion bomber coordinates must be finite");
      }
    }
    targetX = targetRunwayCenterX;
    targetY = targetRunwayCenterY;
    this.orbitCenterX = orbitCenterX;
    this.orbitCenterY = orbitCenterY;
  }

  public void setVisuals(Visuals visuals) {
    this.visuals = Objects.requireNonNull(visuals, "visuals");
  }

  public FlightState flightState() {
    return flightState;
  }

  public boolean hasTakenOff() {
    return flightState != FlightState.PARKED;
  }

  public float headingDegrees() {
    return headingDegrees;
  }

  public float centerX() {
    return x() + drawWidth() / 2f;
  }

  public float centerY() {
    return y() + drawHeight() / 2f;
  }

  public int frameIndex() {
    if (flightState == FlightState.PARKED) {
      return 1;
    }
    if (flightState == FlightState.ORBITING) {
      return BastionBomberSheet.FRAME_COUNT - 2
          + (int) (animationSeconds / TURN_FRAME_SECONDS) % 2;
    }
    // Hold a steady flight pose on the straight approach; the roll frames are only used to turn.
    return 0;
  }

  /** Advances the park timer, straight inbound flight and a stable circular orbit. */
  public void advance(float deltaSeconds) {
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) return;
    float remaining = deltaSeconds;
    if (flightState == FlightState.PARKED) {
      float untilTakeoff = PARKED_SECONDS - parkedSeconds;
      if (remaining < untilTakeoff) {
        parkedSeconds += remaining;
        return;
      }
      parkedSeconds = PARKED_SECONDS;
      flightState = FlightState.INBOUND;
      remaining -= untilTakeoff;
    }
    if (remaining <= 0f) return;
    animationSeconds += remaining;
    if (flightState == FlightState.INBOUND) {
      advanceInbound(remaining);
    } else {
      advanceOrbit(remaining);
    }
  }

  private void advanceInbound(float deltaSeconds) {
    float dx = targetX - centerX();
    float dy = targetY - centerY();
    float distance = (float) Math.hypot(dx, dy);
    if (distance <= ORBIT_RADIUS) {
      enterOrbit(dx, dy);
      advanceOrbit(deltaSeconds);
      return;
    }
    float travel = FLIGHT_SPEED * deltaSeconds;
    if (distance - ORBIT_RADIUS <= travel) {
      float ratio = ORBIT_RADIUS / distance;
      float arrivalX = targetX + dx * ratio;
      float arrivalY = targetY + dy * ratio;
      float secondsToOrbit = (distance - ORBIT_RADIUS) / FLIGHT_SPEED;
      setCenter(arrivalX, arrivalY);
      setHeadingTo(dx, dy);
      enterOrbit(arrivalX - orbitCenterX, arrivalY - orbitCenterY);
      advanceOrbit(deltaSeconds - secondsToOrbit);
      return;
    }
    float step = travel / distance;
    float nextX = centerX() + dx * step;
    float nextY = centerY() + dy * step;
    setHeadingTo(dx, dy);
    setCenter(nextX, nextY);
  }

  private void enterOrbit(float offsetX, float offsetY) {
    flightState = FlightState.ORBITING;
    orbitAngle = (float) Math.atan2(offsetY, offsetX);
    float distance = (float) Math.hypot(offsetX, offsetY);
    if (distance < 0.001f) {
      orbitAngle = (float) (Math.PI / 2.0);
      setCenter(orbitCenterX, orbitCenterY + ORBIT_RADIUS);
    }
    updateOrbitPose();
  }

  private void advanceOrbit(float deltaSeconds) {
    orbitAngle += ORBIT_ANGULAR_SPEED * deltaSeconds;
    updateOrbitPose();
  }

  private void updateOrbitPose() {
    float cosine = (float) Math.cos(orbitAngle);
    float sine = (float) Math.sin(orbitAngle);
    setCenter(orbitCenterX + cosine * ORBIT_RADIUS,
        orbitCenterY + sine * ORBIT_RADIUS);
    headingDegrees = normalizeDegrees((float) Math.toDegrees(Math.atan2(-sine, cosine)));
  }

  private void setHeadingTo(float dx, float dy) {
    if (Math.hypot(dx, dy) > 0.0001f) {
      headingDegrees = normalizeDegrees((float) Math.toDegrees(Math.atan2(dx, dy)));
    }
  }

  private void setCenter(float centerX, float centerY) {
    setPosition(centerX - drawWidth() / 2f, centerY - drawHeight() / 2f);
  }

  private static float normalizeDegrees(float degrees) {
    float normalized = degrees % 360f;
    return normalized < 0f ? normalized + 360f : normalized;
  }

  @Override
  protected TextureRegion currentRegion() {
    if (visuals == null) {
      throw new IllegalStateException("Bastion bomber visuals have not been attached");
    }
    return visuals.region(frameIndex());
  }

  public static final class Visuals implements Disposable {
    private final Texture texture;
    private final TextureRegion[] frames = new TextureRegion[BastionBomberSheet.FRAME_COUNT];

    public Visuals() {
      texture = new Texture(Gdx.files.internal(BastionBomberSheet.internalPath()));
      texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
      for (int index = 0; index < frames.length; index++) {
        BastionBomberSheet.Slice slice = BastionBomberSheet.frameSlice(index);
        // Sheet measurements use image coordinates (origin at top-left); TextureRegion uses
        // texture coordinates with the origin at bottom-left.
        int textureY = BastionBomberSheet.HEIGHT - slice.y() - slice.height();
        frames[index] = new TextureRegion(texture, slice.x(), textureY,
            slice.width(), slice.height());
      }
    }

    private TextureRegion region(int index) {
      return frames[index];
    }

    @Override
    public void dispose() {
      texture.dispose();
    }
  }
}
