package com.davidpe.cosmicaces.infrastructure.gdx;

/**
 * Camera framing chosen before a flight begins. This is a presentation preference only: flight,
 * weapons and world rules are shared by both modes. Each constant carries a stable persistence id so
 * saved preferences never depend on a user-facing name; a missing or unknown id falls back to
 * {@link #FOLLOW_ASTRA}, the historical tracking camera.
 */
public enum CameraMode {

  /**
   * Current tracking camera: the view rotates with Astra's heading and keeps Astra at the persisted
   * bottom fraction with its position lag and forward offset.
   */
  FOLLOW_ASTRA("follow-astra"),

  /**
   * Fixed north: world north stays up, the view never rotates and Astra's centre stays exactly at the
   * centre of the world viewport. The persisted bottom fraction does not apply to this mode.
   */
  NORTH_UP("north-up");

  private final String id;

  CameraMode(String id) {
    this.id = id;
  }

  /** Stable identifier stored in the local preferences file. */
  public String id() {
    return id;
  }

  /** Resolves a saved id, returning {@link #FOLLOW_ASTRA} for missing, empty or unknown values. */
  public static CameraMode fromId(String id) {
    if (id == null || id.isEmpty()) {
      return FOLLOW_ASTRA;
    }
    for (CameraMode mode : values()) {
      if (mode.id.equals(id)) {
        return mode;
      }
    }
    return FOLLOW_ASTRA;
  }
}
