package com.davidpe.cosmicaces.infrastructure.gdx.input;

import java.util.ArrayList;
import java.util.List;

/** Mutable simulated source so tests can connect and disconnect devices between frames. */
final class FakeGamepadSource implements GamepadSource {

  private final List<GamepadDevice> devices = new ArrayList<>();
  private RuntimeException failure;

  void add(GamepadDevice device) {
    devices.add(device);
  }

  void remove(GamepadDevice device) {
    devices.remove(device);
  }

  void clear() {
    devices.clear();
  }

  void failWith(RuntimeException failure) {
    this.failure = failure;
  }

  @Override
  public List<GamepadDevice> devices() {
    if (failure != null) {
      throw failure;
    }
    return List.copyOf(devices);
  }
}
