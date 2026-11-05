package com.davidpe.cosmicaces.infrastructure.gdx.input;

import java.util.Locale;

/**
 * Side of an individual Joy-Con reported by the backend.
 *
 * <p>SDL/Jamepad exposes a single Joy-Con as its own device named either {@code Joy-Con (L)} or
 * {@code Joy-Con (R)} (or a longer {@code Nintendo Switch Left/Right Joy-Con} name). The name is a
 * hint only: an unrecognised device stays {@link #UNKNOWN} and the player can still select it
 * explicitly and configure its profile later.
 */
public enum ControllerSide {
  LEFT,
  RIGHT,
  UNKNOWN;

  /** Classifies a device name without depending on a particular backend spelling. */
  public static ControllerSide fromName(String deviceName) {
    if (!isJoyCon(deviceName) || isCombined(deviceName)) {
      return UNKNOWN;
    }
    String normalized = deviceName.toLowerCase(Locale.ROOT);
    if (normalized.contains("(l)") || normalized.contains("left")) {
      return LEFT;
    }
    if (normalized.contains("(r)") || normalized.contains("right")) {
      return RIGHT;
    }
    return UNKNOWN;
  }

  /** True when the backend name identifies a Joy-Con family device. */
  public static boolean isJoyCon(String deviceName) {
    if (deviceName == null) {
      return false;
    }
    String normalized = deviceName.toLowerCase(Locale.ROOT);
    return normalized.contains("joy-con") || normalized.contains("joycon");
  }

  /**
   * True for the combined pair device some backends expose. Combining two Joy-Cons is outside the
   * approved product scope, so such a device is enumerated but cannot be selected.
   */
  public static boolean isCombined(String deviceName) {
    if (deviceName == null) {
      return false;
    }
    String normalized = deviceName.toLowerCase(Locale.ROOT);
    return normalized.contains("combined") || normalized.contains("pair")
        || normalized.contains(" + ");
  }
}
