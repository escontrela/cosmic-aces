package com.davidpe.cosmicaces.infrastructure.gdx.input;

import java.util.Arrays;

/** Simulated gamepad for pure adapter tests; no native library or window is involved. */
final class FakeGamepadDevice implements GamepadDevice {

  private final String uniqueId;
  private final String name;
  private final ControllerMappingView mapping;
  private final boolean[] buttons;
  private final float[] axes;

  FakeGamepadDevice(String uniqueId, String name, int buttonCount, int axisCount,
      ControllerMappingView mapping) {
    this.uniqueId = uniqueId;
    this.name = name;
    this.mapping = mapping;
    this.buttons = new boolean[Math.max(0, buttonCount)];
    this.axes = new float[Math.max(0, axisCount)];
  }

  void setButton(int index, boolean pressed) {
    buttons[index] = pressed;
  }

  void setAxis(int index, float value) {
    axes[index] = value;
  }

  @Override
  public String uniqueId() {
    return uniqueId;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public int buttonCount() {
    return buttons.length;
  }

  @Override
  public int axisCount() {
    return axes.length;
  }

  @Override
  public ControllerMappingView mapping() {
    return mapping;
  }

  @Override
  public boolean button(int index) {
    return buttons[index];
  }

  @Override
  public float axis(int index) {
    return axes[index];
  }

  @Override
  public String toString() {
    return "FakeGamepadDevice{" + uniqueId + ", buttons=" + Arrays.toString(buttons) + '}';
  }
}
