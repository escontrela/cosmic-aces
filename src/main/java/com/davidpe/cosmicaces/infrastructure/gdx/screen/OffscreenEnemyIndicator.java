package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Discrete off-screen enemy indicator for PhaseOne (COS-39): a small amber triangle at the HUD edge
 * pointing toward the active Vesper Raider while it stays outside the camera view.
 *
 * <p>The placement is pure geometry in the virtual HUD frame and is deliberately derived from the
 * same camera transform that draws the world: it converts the enemy centre through {@link
 * CameraVisibility#localFrame} (identical formulas to the shared visibility rule), hides whenever
 * {@link CameraVisibility#shipVisible} reports the enemy inside the view, and never scales itself
 * with the world zoom. The triangle tip sits on the interior rectangle edge; the base lies toward
 * the screen centre, so no part of the triangle enters the reserved bottom band (the flight
 * readings at y≈34/58 stay below {@link MinimapProjection#BOTTOM_MARGIN}). While the player keeps
 * the minimap open, its real panel rectangle is passed as a reservation and the placement slides
 * along the same edge to the nearest free segment, preserving the direction.
 *
 * <p>The class owns no native resources and never opens or closes a {@link ShapeRenderer} session:
 * the caller supplies the opened renderer, exactly like {@code GunBurstVisual}.
 */
final class OffscreenEnemyIndicator {
  /** Outer margin of the interior rectangle on the left, right and top HUD edges. */
  static final float EDGE_MARGIN = 20f;
  /** Side length of the amber triangle in virtual pixels. */
  static final float TRIANGLE_SIZE = 11f;
  /** Free gap kept between the placement and a reserved rectangle (triangle reach + margin). */
  static final float RESERVATION_GAP = 18f;
  private static final Color INDICATOR_COLOR = new Color(1f, 0.76f, 0.03f, 1f);
  /** Float tolerance when deciding which interior edge a placement lies on. */
  private static final float EDGE_EPSILON = 0.01f;

  /**
   * Where to draw the off-screen enemy: the HUD point (tip) plus the normalized direction toward
   * the enemy. {@link #none()} is the inactive result so callers never pass null.
   */
  record Placement(boolean active, float x, float y, float directionX, float directionY) {
    static Placement none() {
      return new Placement(false, 0f, 0f, 0f, 0f);
    }
  }

  /**
   * Pure placement for the camera frame being drawn.
   *
   * @param enemyActive whether a live enemy is eligible (not destroyed, not waiting to respawn, the
   *     phase still running and the raider already appeared)
   * @return {@link Placement#none()} when inactive, non-finite inputs, an unusably small viewport,
   *     an invalid zoom or the enemy intersecting the view (same rule as the enemy drawing)
   */
  static Placement compute(boolean enemyActive, float enemyCenterX, float enemyCenterY,
      float enemyHalfBox, float cameraX, float cameraY, float upX, float upY,
      float viewWidth, float viewHeight, float zoom) {
    if (!enemyActive) {
      return Placement.none();
    }
    if (viewWidth <= 2f * EDGE_MARGIN || viewHeight <= bottomBand() + EDGE_MARGIN) {
      return Placement.none();
    }
    if (zoom <= 0f || !allFinite(enemyCenterX, enemyCenterY, enemyHalfBox,
        cameraX, cameraY, upX, upY, viewWidth, viewHeight, zoom)) {
      return Placement.none();
    }
    if (CameraVisibility.shipVisible(enemyCenterX, enemyCenterY, enemyHalfBox,
        cameraX, cameraY, upX, upY, viewWidth, viewHeight, zoom)) {
      return Placement.none();
    }
    CameraVisibility.LocalFrame frame =
        CameraVisibility.localFrame(enemyCenterX, enemyCenterY, cameraX, cameraY, upX, upY);
    double magnitude = Math.hypot(frame.right(), frame.up());
    // Zero vector and NaN both fail this comparison, so no division by zero can happen here.
    if (!(magnitude > 0d)) {
      return Placement.none();
    }
    float directionX = (float) (frame.right() / magnitude);
    float directionY = (float) (frame.up() / magnitude);
    float centerX = viewWidth / 2f;
    float centerY = viewHeight / 2f;
    float maxX = viewWidth - EDGE_MARGIN;
    float maxY = viewHeight - EDGE_MARGIN;
    float distance = Float.MAX_VALUE;
    // Clip the ray from the HUD centre to the interior rectangle; axes are handled naturally by
    // leaving the opposite dimension out of the min.
    if (directionX > 0f) distance = Math.min(distance, (maxX - centerX) / directionX);
    else if (directionX < 0f) distance = Math.min(distance, (EDGE_MARGIN - centerX) / directionX);
    if (directionY > 0f) distance = Math.min(distance, (maxY - centerY) / directionY);
    else if (directionY < 0f) distance = Math.min(distance, (bottomBand() - centerY) / directionY);
    if (!Float.isFinite(distance) || distance < 0f) {
      return Placement.none();
    }
    return new Placement(true, centerX + directionX * distance,
        centerY + directionY * distance, directionX, directionY);
  }

  /**
   * Slides an active placement along the same HUD edge to the nearest free segment whenever it lands
   * inside a reserved screen rectangle (the minimap panel while the player keeps it open). The
   * direction is preserved and the point stays inside the interior rectangle.
   */
  static Placement avoidReservations(Placement placement, float viewWidth, float viewHeight,
      MinimapProjection.Rectangle... reservations) {
    if (placement == null || !placement.active()) {
      return placement;
    }
    float maxX = viewWidth - EDGE_MARGIN;
    float maxY = viewHeight - EDGE_MARGIN;
    Placement current = placement;
    boolean moved = true;
    int guard = reservations.length + 1;
    while (moved && guard-- > 0) {
      moved = false;
      for (MinimapProjection.Rectangle reservation : reservations) {
        if (reservation == null || !inside(current, reservation)) {
          continue;
        }
        current = slideOnEdge(current, maxX, maxY, reservation);
        moved = true;
      }
    }
    return current;
  }

  /**
   * Draws the amber triangle at the placement edge into the already-open {@code shapes} session.
   * The caller has set the HUD projection and owns begin/end; the previous renderer colour is
   * restored on every path.
   */
  static void draw(ShapeRenderer shapes, Placement placement) {
    if (shapes == null || placement == null || !placement.active()) {
      return;
    }
    if (!Float.isFinite(placement.x()) || !Float.isFinite(placement.y())
        || !Float.isFinite(placement.directionX()) || !Float.isFinite(placement.directionY())) {
      throw new IllegalArgumentException("Active placement must be finite");
    }
    Color previous = shapes.getColor();
    float r = previous.r, g = previous.g, b = previous.b, a = previous.a;
    try {
      shapes.setColor(INDICATOR_COLOR);
      float tipX = placement.x();
      float tipY = placement.y();
      // Base sits behind the tip, toward the screen centre; wings are perpendicular to the
      // direction, so the triangle always points at the off-screen enemy.
      float baseX = tipX - placement.directionX() * TRIANGLE_SIZE;
      float baseY = tipY - placement.directionY() * TRIANGLE_SIZE;
      float sideX = -placement.directionY() * TRIANGLE_SIZE / 2f;
      float sideY = placement.directionX() * TRIANGLE_SIZE / 2f;
      shapes.triangle(tipX, tipY, baseX + sideX, baseY + sideY, baseX - sideX, baseY - sideY);
    } finally {
      shapes.setColor(r, g, b, a);
    }
  }

  /** Interior rectangle's bottom: the shared fixed HUD band above every flight reading. */
  private static float bottomBand() {
    return MinimapProjection.BOTTOM_MARGIN;
  }

  private static boolean inside(Placement placement, MinimapProjection.Rectangle reservation) {
    return placement.x() >= reservation.x()
        && placement.x() <= reservation.x() + reservation.width()
        && placement.y() >= reservation.y()
        && placement.y() <= reservation.y() + reservation.height();
  }

  /**
   * Moves a placement that lies on the interior rectangle boundary and inside a reservation to the
   * nearest free coordinate along that same edge, keeping at least {@link #RESERVATION_GAP} from
   * the reserved rectangle. A corner placement (on two edges) slides on the first edge whose free
   * segment is nearest; the caller re-checks against remaining reservations.
   */
  private static Placement slideOnEdge(Placement placement, float maxX, float maxY,
      MinimapProjection.Rectangle reservation) {
    float x = placement.x();
    float y = placement.y();
    float reservationMaxX = reservation.x() + reservation.width();
    float reservationMaxY = reservation.y() + reservation.height();
    boolean onVerticalEdge = (x <= EDGE_MARGIN + EDGE_EPSILON || x >= maxX - EDGE_EPSILON)
        && y >= reservation.y() && y <= reservationMaxY;
    if (onVerticalEdge) {
      float above = reservationMaxY + RESERVATION_GAP;
      float below = reservation.y() - RESERVATION_GAP;
      float newY = Math.abs(y - above) <= Math.abs(y - below) ? above : below;
      newY = Math.max(bottomBand(), Math.min(maxY, newY));
      return new Placement(true, x, newY, placement.directionX(), placement.directionY());
    }
    boolean onHorizontalEdge = (y <= bottomBand() + EDGE_EPSILON || y >= maxY - EDGE_EPSILON)
        && x >= reservation.x() && x <= reservationMaxX;
    if (onHorizontalEdge) {
      float right = reservationMaxX + RESERVATION_GAP;
      float left = reservation.x() - RESERVATION_GAP;
      float newX = Math.abs(x - right) <= Math.abs(x - left) ? right : left;
      newX = Math.max(EDGE_MARGIN, Math.min(maxX, newX));
      return new Placement(true, newX, y, placement.directionX(), placement.directionY());
    }
    return placement;
  }

  private static boolean allFinite(float... values) {
    for (float value : values) {
      if (!Float.isFinite(value)) {
        return false;
      }
    }
    return true;
  }
}