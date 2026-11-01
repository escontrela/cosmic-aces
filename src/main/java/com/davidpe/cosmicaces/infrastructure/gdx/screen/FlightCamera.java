package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.infrastructure.gdx.CameraMode;

/**
 * Applies the flight-camera state to a LibGDX orthographic camera. The mode is fixed for the
 * lifetime of the screen so a run cannot change framing mid-flight.
 */
final class FlightCamera {
  private final OrthographicCamera camera;
  private final FlightCameraState state;

  FlightCamera(OrthographicCamera camera, Astra astra) {
    this(camera, astra, FlightCameraState.ASTRA_BOTTOM_FRACTION, CameraMode.FOLLOW_ASTRA);
  }

  FlightCamera(OrthographicCamera camera, Astra astra, float astraBottomFraction) {
    this(camera, astra, astraBottomFraction, CameraMode.FOLLOW_ASTRA);
  }

  FlightCamera(OrthographicCamera camera, Astra astra, float astraBottomFraction,
      CameraMode mode) {
    this.camera = camera;
    state = new FlightCameraState(astra, astraBottomFraction, mode);
    // Applied eagerly so the first drawn frame already uses the selected framing.
    apply();
  }

  void update(Astra astra, float deltaSeconds, FlightCameraState.CombatTarget target) {
    state.update(astra, deltaSeconds, target);
    apply();
  }

  void update(Astra astra, float deltaSeconds, FlightCameraState.CombatTarget target,
      float viewWidth, float viewHeight) {
    state.update(astra, deltaSeconds, target, viewWidth, viewHeight);
    apply();
  }

  private void apply() {
    double radians = Math.toRadians(state.yawDegrees());
    camera.position.set(state.x(), state.y(), 0f);
    camera.up.set((float) Math.sin(radians), (float) Math.cos(radians), 0f);
    camera.zoom = state.zoom();
    camera.update();
  }
}
