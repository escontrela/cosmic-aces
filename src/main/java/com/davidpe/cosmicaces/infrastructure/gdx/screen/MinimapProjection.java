package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.davidpe.cosmicaces.domain.game.WorldBounds;

/** Pure world-to-screen transform for the fixed PhaseOne minimap. */
final class MinimapProjection {
  private static final float PANEL_MAX_WIDTH = 232f;
  private static final float PANEL_MAX_HEIGHT = 360f;
  private static final float PANEL_PADDING = 8f;
  private static final float VIEWPORT_MARGIN = 12f;
  private static final float RIGHT_MARGIN = 24f;
  private static final float BOTTOM_MARGIN = 76f;

  record Point(float x, float y) {}
  record Rectangle(float x, float y, float width, float height) {}

  private final WorldBounds world;
  private final Rectangle panel;
  private final Rectangle map;

  private MinimapProjection(WorldBounds world, Rectangle panel, Rectangle map) {
    this.world = world;
    this.panel = panel;
    this.map = map;
  }

  static MinimapProjection fit(WorldBounds world, float viewportWidth, float viewportHeight) {
    if (world == null || !Float.isFinite(viewportWidth) || !Float.isFinite(viewportHeight)
        || viewportWidth <= RIGHT_MARGIN + VIEWPORT_MARGIN + PANEL_PADDING * 2f
        || viewportHeight <= BOTTOM_MARGIN + VIEWPORT_MARGIN + PANEL_PADDING * 2f) {
      throw new IllegalArgumentException("World and usable viewport dimensions are required");
    }

    float panelMaxWidth = Math.min(PANEL_MAX_WIDTH,
        viewportWidth - RIGHT_MARGIN - VIEWPORT_MARGIN);
    float panelMaxHeight = Math.min(PANEL_MAX_HEIGHT,
        viewportHeight - BOTTOM_MARGIN - VIEWPORT_MARGIN);
    float mapMaxWidth = panelMaxWidth - PANEL_PADDING * 2f;
    float mapMaxHeight = panelMaxHeight - PANEL_PADDING * 2f;
    float scale = Math.min(mapMaxWidth / world.width(), mapMaxHeight / world.height());
    float mapWidth = world.width() * scale;
    float mapHeight = world.height() * scale;
    float panelWidth = mapWidth + PANEL_PADDING * 2f;
    float panelHeight = mapHeight + PANEL_PADDING * 2f;
    float panelX = Math.max(VIEWPORT_MARGIN, viewportWidth - RIGHT_MARGIN - panelWidth);
    float panelY = BOTTOM_MARGIN;

    Rectangle panel = new Rectangle(panelX, panelY, panelWidth, panelHeight);
    Rectangle map = new Rectangle(panelX + PANEL_PADDING, panelY + PANEL_PADDING,
        mapWidth, mapHeight);
    return new MinimapProjection(world, panel, map);
  }

  Rectangle panel() {
    return panel;
  }

  Rectangle map() {
    return map;
  }

  Point project(float worldX, float worldY) {
    if (!Float.isFinite(worldX) || !Float.isFinite(worldY)) {
      throw new IllegalArgumentException("World coordinates must be finite");
    }
    float x = clamp(worldX, 0f, world.width());
    float y = clamp(worldY, 0f, world.height());
    return new Point(map.x() + x / world.width() * map.width(),
        map.y() + y / world.height() * map.height());
  }

  Point projectMarker(float worldX, float worldY, float screenMargin) {
    if (!Float.isFinite(screenMargin) || screenMargin < 0f
        || screenMargin * 2f >= Math.min(map.width(), map.height())) {
      throw new IllegalArgumentException("Marker margin must fit inside the map");
    }
    Point point = project(worldX, worldY);
    return new Point(clamp(point.x(), map.x() + screenMargin,
            map.x() + map.width() - screenMargin),
        clamp(point.y(), map.y() + screenMargin,
            map.y() + map.height() - screenMargin));
  }

  /** Astra's yaw is zero north and increases clockwise. */
  static Point astraDirection(float yawDegrees) {
    return direction(yawDegrees, 1f);
  }

  /** Vesper's heading is zero south and increases clockwise. */
  static Point raiderDirection(float headingDegrees) {
    return direction(headingDegrees, -1f);
  }

  private static Point direction(float degrees, float ySign) {
    if (!Float.isFinite(degrees)) {
      throw new IllegalArgumentException("Heading must be finite");
    }
    double radians = Math.toRadians(degrees);
    return new Point((float) Math.sin(radians), ySign * (float) Math.cos(radians));
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }
}
