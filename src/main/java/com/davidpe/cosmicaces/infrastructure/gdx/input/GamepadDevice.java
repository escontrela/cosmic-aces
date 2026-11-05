package com.davidpe.cosmicaces.infrastructure.gdx.input;

/**
 * Read-only physical state of one connected gamepad.
 *
 * <p>The interface has no LibGDX dependency so the adapter can be exercised with a simulated
 * source. Implementations are resolved again on every frame; no disconnected device object is
 * retained by the input service.
 */
public interface GamepadDevice {

  /** Backend identifier used for explicit selection; not persisted as a user profile. */
  String uniqueId();

  /** Backend device name, used to classify the Joy-Con side and to show status. */
  String name();

  /** Number of physical buttons reported by the backend. */
  int buttonCount();

  /** Number of axes reported by the backend. */
  int axisCount();

  /** Semantic button and axis indices reported by the backend. */
  ControllerMappingView mapping();

  /** Current state of a physical button. */
  boolean button(int index);

  /** Current value of an axis, normally -1..1. */
  float axis(int index);
}
