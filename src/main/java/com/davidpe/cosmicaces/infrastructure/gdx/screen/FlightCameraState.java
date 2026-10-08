package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.FlightTuning;

/** Pure presentation state for the lagged world camera. */
final class FlightCameraState {
  private static final float YAW_FOLLOW_SECONDS = 0.25f;
  private static final float POSITION_FOLLOW_SECONDS = 0.1f;
  private static final float CAMERA_AHEAD_DISTANCE = 100f;

  private float x;
  private float y;
  private float yawDegrees;
  private float zoom;

  FlightCameraState(Astra astra) {
    yawDegrees = astra.yawDegrees();
    zoom = desiredZoom(astra);
    x = desiredX(astra, yawDegrees);
    y = desiredY(astra, yawDegrees);
  }

  float x() { return x; }
  float y() { return y; }
  float yawDegrees() { return yawDegrees; }
  float zoom() { return zoom; }

  void update(Astra astra, float deltaSeconds) {
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) return;
    float yawBlend = (float) (1d - Math.exp(-deltaSeconds / YAW_FOLLOW_SECONDS));
    float positionBlend = (float) (1d - Math.exp(-deltaSeconds / POSITION_FOLLOW_SECONDS));
    yawDegrees = normalize(yawDegrees
        + normalize(astra.yawDegrees() - yawDegrees) * yawBlend);
    x += (desiredX(astra, yawDegrees) - x) * positionBlend;
    y += (desiredY(astra, yawDegrees) - y) * positionBlend;
    zoom += (desiredZoom(astra) - zoom) * yawBlend;
  }

  private static float desiredX(Astra astra, float yaw) {
    return astra.x() + astra.drawWidth() / 2f
        + (float) Math.sin(Math.toRadians(yaw)) * CAMERA_AHEAD_DISTANCE;
  }

  private static float desiredY(Astra astra, float yaw) {
    return astra.y() + astra.drawHeight() / 2f
        + (float) Math.cos(Math.toRadians(yaw)) * CAMERA_AHEAD_DISTANCE;
  }

  private static float desiredZoom(Astra astra) {
    if (astra.ultraRemainingSeconds() > 0f) return 1.15f;
    return astra.flightSpeed() > FlightTuning.NORMAL_SPEED + 0.5f ? 1.08f : 1f;
  }

  private static float normalize(float angle) {
    float result = angle % 360f;
    if (result > 180f) result -= 360f;
    if (result <= -180f) result += 360f;
    return result;
  }
}
