package com.davidpe.cosmicaces.infrastructure.gdx;

/**
 * Camera pull-back chosen before a flight begins. The closest level is the historical framing and
 * the fallback; each further level multiplies the camera's orthographic zoom by one shared factor,
 * so more world fits on screen while every world magnitude stays unchanged. Each constant carries a
 * stable persistence id so saved preferences never depend on a user-facing name or an ordinal; a
 * missing, empty or unknown id resolves to {@link #CURRENT}.
 */
public enum CameraDistance {

  /** Historical framing: the camera keeps its existing zoom unchanged. */
  CURRENT("current", 1.00f),

  /** Pulled back by one step: 12,5 % more visible world per axis. */
  FAR("far", 1.125f),

  /** Furthest available framing: 25 % more visible world per axis. */
  FARTHEST("farthest", 1.25f);

  private final String id;
  private final float zoomFactor;

  CameraDistance(String id, float zoomFactor) {
    this.id = id;
    this.zoomFactor = zoomFactor;
  }

  /** Stable identifier stored in the local preferences file. */
  public String id() {
    return id;
  }

  /** Multiplier applied to the camera zoom; {@link #CURRENT} preserves the historical framing. */
  public float zoomFactor() {
    return zoomFactor;
  }

  /** Resolves a saved id, returning {@link #CURRENT} for missing, empty or unknown values. */
  public static CameraDistance fromId(String id) {
    if (id == null || id.isEmpty()) {
      return CURRENT;
    }
    for (CameraDistance distance : values()) {
      if (distance.id.equals(id)) {
        return distance;
      }
    }
    return CURRENT;
  }
}
