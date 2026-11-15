package com.davidpe.cosmicaces.infrastructure.gdx.input;

/**
 * Backend-independent view of the semantic button and axis indices reported for a device.
 *
 * <p>Only the indices needed to derive recoverable defaults and to read the left stick are kept.
 * A value of {@link #UNDEFINED} means the backend did not identify that semantic control; the
 * adapter then falls back to a physical index instead of assuming a printed label.
 */
public record ControllerMappingView(
    int buttonA,
    int buttonB,
    int buttonX,
    int buttonY,
    int buttonStart,
    int buttonBack,
    int buttonL1,
    int buttonR1,
    int axisLeftX,
    int axisLeftY) {

  public static final int UNDEFINED = -1;

  /**
   * Mapping for a backend that does not describe the device: the first two axes are used as the
   * stick and every semantic button is undefined.
   */
  public static ControllerMappingView unknown() {
    return new ControllerMappingView(UNDEFINED, UNDEFINED, UNDEFINED, UNDEFINED, UNDEFINED,
        UNDEFINED, UNDEFINED, UNDEFINED, 0, 1);
  }

  /** True when the backend identified this semantic button. */
  public boolean isDefined(int button) {
    return button >= 0;
  }
}
