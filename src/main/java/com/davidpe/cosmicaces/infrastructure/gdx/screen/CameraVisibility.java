package com.davidpe.cosmicaces.infrastructure.gdx.screen;

/**
 * Pure geometry deciding whether a ship's bounding box intersects the real camera view. It is the
 * single visibility rule shared by PhaseOne's drawing and by the world-coincidence authorization of
 * the autonomous raider weapon, so no second, divergent rule exists in the screen.
 *
 * <p>The camera view is a rotated rectangle: {@code camera.up} encodes its yaw and {@code zoom}
 * scales both visible half extents. A ship is represented by its stable box center and the half of
 * its larger side, added as a margin.
 */
public final class CameraVisibility {

  private CameraVisibility() {}

  /**
   * Projects a world point into the camera's rotated view frame and checks it against the visible
   * half extents, including the ship's half box as margin.
   *
   * @param centerX world X of the ship box center
   * @param centerY world Y of the ship box center
   * @param halfBox half of the ship box's larger side, used as margin
   * @param cameraX world X of the camera position
   * @param cameraY world Y of the camera position
   * @param upX X of the camera {@code up} vector (encodes yaw)
   * @param upY Y of the camera {@code up} vector (encodes yaw)
   * @param viewWidth virtual viewport width before zoom
   * @param viewHeight virtual viewport height before zoom
   * @param zoom camera zoom, positive
   * @return whether the ship box intersects the camera view
   */
  public static boolean shipVisible(float centerX, float centerY, float halfBox,
      float cameraX, float cameraY, float upX, float upY,
      float viewWidth, float viewHeight, float zoom) {
    float dx = centerX - cameraX;
    float dy = centerY - cameraY;
    float localRight = dx * upY - dy * upX;
    float localUp = dx * upX + dy * upY;
    float halfWidth = viewWidth / 2f * zoom + halfBox;
    float halfHeight = viewHeight / 2f * zoom + halfBox;
    return Math.abs(localRight) <= halfWidth && Math.abs(localUp) <= halfHeight;
  }
}
