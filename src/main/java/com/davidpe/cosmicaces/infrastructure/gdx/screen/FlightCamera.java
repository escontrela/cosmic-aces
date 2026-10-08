package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.davidpe.cosmicaces.domain.player.Astra;

/** Applies lagged flight-camera state to a LibGDX orthographic camera. */
final class FlightCamera {
  private final OrthographicCamera camera;
  private final FlightCameraState state;

  FlightCamera(OrthographicCamera camera, Astra astra) {
    this.camera = camera;
    state = new FlightCameraState(astra);
    apply();
  }

  void update(Astra astra, float deltaSeconds) {
    state.update(astra, deltaSeconds);
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
