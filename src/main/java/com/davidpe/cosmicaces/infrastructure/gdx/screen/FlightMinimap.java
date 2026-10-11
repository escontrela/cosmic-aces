package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.davidpe.cosmicaces.domain.enemy.GhostwingEncounter;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.scenery.WorldScenery;
import java.util.List;

/**
 * Optional fixed-screen map overlay for PhaseOne: the whole world, the persistent islands, Astra's
 * cyan arrow, the red Vesper point with its direction trail, and one amber hollow diamond per
 * active Ghostwing group (the group's representative point, never one marker per drone). It owns no
 * native resources.
 */
final class FlightMinimap {
  private static final Color PANEL_COLOR = new Color(0.008f, 0.022f, 0.04f, 1f);
  private static final Color MAP_COLOR = new Color(0.012f, 0.036f, 0.06f, 1f);
  private static final Color BORDER_COLOR = new Color(0.08f, 0.52f, 0.70f, 1f);
  private static final Color ISLAND_COLOR = new Color(0.20f, 0.58f, 0.75f, 1f);
  private static final Color ASTRA_COLOR = new Color(0.48f, 0.91f, 1f, 1f);
  private static final Color RAIDER_COLOR = new Color(1f, 0.18f, 0.16f, 1f);
  private static final Color RAIDER_TRAIL_COLOR = new Color(1f, 0.48f, 0.20f, 1f);
  private static final Color GROUP_COLOR = new Color(1f, 0.72f, 0.12f, 1f);
  /** Half diagonal of the hollow amber group diamond, in virtual pixels. */
  static final float GROUP_MARKER_HALF = 6f;
  private static final float ASTRA_TIP_LENGTH = 8f;
  private static final float ASTRA_BASE_LENGTH = 5f;
  private static final float ASTRA_HALF_WIDTH = 4f;

  private boolean visible;

  boolean isVisible() {
    return visible;
  }

  void toggle() {
    visible = !visible;
  }

  void draw(ShapeRenderer shapes, WorldBounds world, WorldScenery scenery, Astra astra,
      VesperRaider raider, List<GhostwingEncounter.Point> ghostwingGroups,
      float viewportWidth, float viewportHeight) {
    if (!visible) return;
    MinimapProjection projection = MinimapProjection.fit(world, viewportWidth, viewportHeight);
    MinimapProjection.Rectangle panel = projection.panel();
    MinimapProjection.Rectangle map = projection.map();

    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(PANEL_COLOR);
    shapes.rect(panel.x(), panel.y(), panel.width(), panel.height());
    shapes.setColor(MAP_COLOR);
    shapes.rect(map.x(), map.y(), map.width(), map.height());
    shapes.setColor(ISLAND_COLOR);
    for (WorldScenery.Island island : scenery.islands()) {
      MinimapProjection.Point point = projection.project(
          island.x() + 3.5f * island.tileSize(), island.y() + 3f * island.tileSize());
      shapes.circle(point.x(), point.y(), 2f, 8);
    }
    shapes.end();

    shapes.begin(ShapeRenderer.ShapeType.Line);
    shapes.setColor(BORDER_COLOR);
    shapes.rect(map.x(), map.y(), map.width(), map.height());
    if (raider != null) drawRaiderTrail(shapes, projection, raider);
    drawGroupMarkers(shapes, projection, ghostwingGroups);
    shapes.end();

    shapes.begin(ShapeRenderer.ShapeType.Filled);
    drawAstra(shapes, projection, astra);
    if (raider != null) {
      MinimapProjection.Point point = projection.projectMarker(
          raider.x() + raider.drawWidth() / 2f,
          raider.y() + raider.drawHeight() / 2f, 5f);
      shapes.setColor(RAIDER_COLOR);
      shapes.circle(point.x(), point.y(), 4f, 12);
    }
    shapes.end();
  }

  /**
   * Draws exactly one hollow amber diamond per active group, centred on the group's representative
   * world point (the centroid of its live members). A group with no live members is absent from the
   * encounter's group list, so it contributes no marker.
   */
  private static void drawGroupMarkers(ShapeRenderer shapes, MinimapProjection projection,
      List<GhostwingEncounter.Point> ghostwingGroups) {
    if (ghostwingGroups == null || ghostwingGroups.isEmpty()) {
      return;
    }
    shapes.setColor(GROUP_COLOR);
    for (GhostwingEncounter.Point group : ghostwingGroups) {
      MinimapProjection.Point point = projection.projectMarker(
          group.x(), group.y(), GROUP_MARKER_HALF + 1f);
      float[] diamond = diamondVertices(point, GROUP_MARKER_HALF);
      shapes.line(diamond[0], diamond[1], diamond[2], diamond[3]);
      shapes.line(diamond[2], diamond[3], diamond[4], diamond[5]);
      shapes.line(diamond[4], diamond[5], diamond[6], diamond[7]);
      shapes.line(diamond[6], diamond[7], diamond[0], diamond[1]);
    }
  }

  /**
   * Clockwise diamond vertices (top, right, bottom, left) for one group marker, as eight packed
   * screen coordinates. Pure geometry so the marker shape is unit-testable without a renderer.
   */
  static float[] diamondVertices(MinimapProjection.Point center, float halfSize) {
    if (center == null || !Float.isFinite(center.x()) || !Float.isFinite(center.y())
        || !Float.isFinite(halfSize) || halfSize <= 0f) {
      throw new IllegalArgumentException("Marker centre and positive half size are required");
    }
    return new float[] {
        center.x(), center.y() + halfSize,
        center.x() + halfSize, center.y(),
        center.x(), center.y() - halfSize,
        center.x() - halfSize, center.y()
    };
  }

  private static void drawAstra(ShapeRenderer shapes, MinimapProjection projection, Astra astra) {
    MinimapProjection.Point center = projection.projectMarker(
        astra.x() + astra.drawWidth() / 2f,
        astra.y() + astra.drawHeight() / 2f, ASTRA_TIP_LENGTH + 1f);
    MinimapProjection.Point direction = MinimapProjection.astraDirection(astra.yawDegrees());
    float perpendicularX = direction.y();
    float perpendicularY = -direction.x();
    float tipX = center.x() + direction.x() * ASTRA_TIP_LENGTH;
    float tipY = center.y() + direction.y() * ASTRA_TIP_LENGTH;
    float baseX = center.x() - direction.x() * ASTRA_BASE_LENGTH;
    float baseY = center.y() - direction.y() * ASTRA_BASE_LENGTH;
    shapes.setColor(ASTRA_COLOR);
    shapes.triangle(tipX, tipY,
        baseX + perpendicularX * ASTRA_HALF_WIDTH,
        baseY + perpendicularY * ASTRA_HALF_WIDTH,
        baseX - perpendicularX * ASTRA_HALF_WIDTH,
        baseY - perpendicularY * ASTRA_HALF_WIDTH);
  }

  private static void drawRaiderTrail(ShapeRenderer shapes, MinimapProjection projection,
      VesperRaider raider) {
    MinimapProjection.Point center = projection.projectMarker(
        raider.x() + raider.drawWidth() / 2f,
        raider.y() + raider.drawHeight() / 2f, 13f);
    MinimapProjection.Point direction = MinimapProjection.raiderDirection(raider.headingDegrees());
    shapes.setColor(RAIDER_TRAIL_COLOR);
    shapes.line(center.x() - direction.x() * 12f, center.y() - direction.y() * 12f,
        center.x() - direction.x() * 4f, center.y() - direction.y() * 4f);
  }
}
