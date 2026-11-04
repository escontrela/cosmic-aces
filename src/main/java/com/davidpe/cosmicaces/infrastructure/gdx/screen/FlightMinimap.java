package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.davidpe.cosmicaces.domain.enemy.BastionBomber;
import com.davidpe.cosmicaces.domain.enemy.BastionEscort;
import com.davidpe.cosmicaces.domain.enemy.GhostwingEncounter;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.scenery.SceneryPlacement;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissile;
import com.davidpe.cosmicaces.domain.weapon.MissileTarget;
import java.util.List;

/**
 * Optional fixed-screen map overlay for PhaseOne: the whole world, Astra's
 * cyan arrow, allied and enemy base markers, Vesper, both active Bastion escorts, the blinking
 * Bastion bomber after takeoff, and one amber hollow diamond per active Ghostwing group (the group's
 * representative point, never one marker per drone). It owns no native resources.
 */
final class FlightMinimap {
  /**
   * Translucent dark fills (COS-129): the panel and map let the scenario show through while still
   * keeping the cyan/amber markers readable.
   */
  static final float PANEL_ALPHA = 0.55f;
  static final float MAP_ALPHA = 0.30f;
  private static final Color PANEL_COLOR = new Color(0.008f, 0.022f, 0.04f, PANEL_ALPHA);
  private static final Color MAP_COLOR = new Color(0.012f, 0.036f, 0.06f, MAP_ALPHA);
  private static final Color BORDER_COLOR = new Color(0.08f, 0.52f, 0.70f, 1f);
  private static final Color ASTRA_COLOR = new Color(0.48f, 0.91f, 1f, 1f);
  private static final Color RAIDER_COLOR = new Color(1f, 0.18f, 0.16f, 1f);
  private static final Color RAIDER_TRAIL_COLOR = new Color(1f, 0.48f, 0.20f, 1f);
  private static final Color GROUP_COLOR = new Color(1f, 0.72f, 0.12f, 1f);
  private static final Color BASE_COLOR = new Color(0.30f, 0.92f, 1f, 1f);
  private static final Color ENEMY_BASE_COLOR = new Color(1f, 0.18f, 0.16f, 1f);
  private static final Color BOMBER_COLOR = new Color(1f, 0.24f, 0.12f, 1f);
  private static final Color BOMBER_CORE_COLOR = new Color(1f, 0.91f, 0.62f, 1f);
  private static final Color ESCORT_COLOR = new Color(1f, 0.36f, 0.28f, 1f);
  private static final Color AIM_COLOR = new Color(0.30f, 0.96f, 1f, 1f);
  private static final Color LOCK_COLOR = new Color(1f, 0.92f, 0.30f, 1f);
  private static final Color ASTRA_MISSILE_COLOR = new Color(0.28f, 1f, 0.56f, 1f);
  private static final Color ENEMY_MISSILE_COLOR = new Color(1f, 0.28f, 0.92f, 1f);
  /** Muted tan used for the approved village PNG milestones on the long map. */
  private static final Color VILLAGE_COLOR = new Color(0.86f, 0.72f, 0.46f, 1f);
  /** Half diagonal of the hollow amber group diamond, in virtual pixels. */
  static final float GROUP_MARKER_HALF = 6f;
  private static final float ASTRA_TIP_LENGTH = 8f;
  private static final float ASTRA_BASE_LENGTH = 5f;
  private static final float ASTRA_HALF_WIDTH = 4f;

  private boolean preferredVisible;
  private boolean targeting;
  private boolean missileTracking;

  record AimMarkers(MinimapProjection.Point cursor, MinimapProjection.Point selectedTarget) {}
  record MissileMarker(MinimapProjection.Point position, GuidedMissile.Owner owner) {}

  boolean isVisible() {
    return preferredVisible || targeting || missileTracking;
  }

  void toggle() {
    preferredVisible = !preferredVisible;
  }

  void setTargeting(boolean targeting) {
    this.targeting = targeting;
  }

  boolean isTargeting() {
    return targeting;
  }

  void setMissileTracking(boolean missileTracking) {
    this.missileTracking = missileTracking;
  }

  boolean hasMissileTracking() {
    return missileTracking;
  }

  void draw(ShapeRenderer shapes, WorldBounds world, Astra astra,
      VesperRaider raider, List<GhostwingEncounter.Point> ghostwingGroups,
      BastionBomber bomber, float elapsedSeconds,
      float baseCenterX, float baseCenterY, float enemyBaseCenterX, float enemyBaseCenterY,
      float viewportWidth, float viewportHeight) {
    draw(shapes, world, astra, raider, ghostwingGroups, bomber, List.of(), elapsedSeconds,
        baseCenterX, baseCenterY, enemyBaseCenterX, enemyBaseCenterY, viewportWidth,
        viewportHeight, false, 0f, 0f, null, List.of());
  }

  void draw(ShapeRenderer shapes, WorldBounds world, Astra astra,
      VesperRaider raider, List<GhostwingEncounter.Point> ghostwingGroups,
      BastionBomber bomber, List<BastionEscort> escorts, float elapsedSeconds,
      float baseCenterX, float baseCenterY, float enemyBaseCenterX, float enemyBaseCenterY,
      float viewportWidth, float viewportHeight, boolean aiming, float aimX, float aimY,
      MissileTarget selectedTarget) {
    draw(shapes, world, astra, raider, ghostwingGroups, bomber, escorts, elapsedSeconds,
        baseCenterX, baseCenterY, enemyBaseCenterX, enemyBaseCenterY, viewportWidth,
        viewportHeight, aiming, aimX, aimY, selectedTarget, List.of());
  }

  void draw(ShapeRenderer shapes, WorldBounds world, Astra astra,
      VesperRaider raider, List<GhostwingEncounter.Point> ghostwingGroups,
      BastionBomber bomber, List<BastionEscort> escorts, float elapsedSeconds,
      float baseCenterX, float baseCenterY, float enemyBaseCenterX, float enemyBaseCenterY,
      float viewportWidth, float viewportHeight, boolean aiming, float aimX, float aimY,
      MissileTarget selectedTarget, List<GuidedMissile> guidedMissiles) {
    draw(shapes, world, astra, raider, ghostwingGroups, bomber, escorts, elapsedSeconds,
        baseCenterX, baseCenterY, enemyBaseCenterX, enemyBaseCenterY, viewportWidth,
        viewportHeight, aiming, aimX, aimY, selectedTarget, guidedMissiles, List.of());
  }

  void draw(ShapeRenderer shapes, WorldBounds world, Astra astra,
      VesperRaider raider, List<GhostwingEncounter.Point> ghostwingGroups,
      BastionBomber bomber, List<BastionEscort> escorts, float elapsedSeconds,
      float baseCenterX, float baseCenterY, float enemyBaseCenterX, float enemyBaseCenterY,
      float viewportWidth, float viewportHeight, boolean aiming, float aimX, float aimY,
      MissileTarget selectedTarget, List<GuidedMissile> guidedMissiles,
      List<SceneryPlacement> villagePlacements) {
    if (!isVisible()) return;
    Color previousColor = shapes.getColor().cpy();
    MinimapProjection projection = MinimapProjection.fit(world, viewportWidth, viewportHeight);
    MinimapProjection.Rectangle panel = projection.panel();
    MinimapProjection.Rectangle map = projection.map();
    float scale = projection.visualScale();

    drawTranslucentBackground(shapes, panel, map);

    shapes.begin(ShapeRenderer.ShapeType.Line);
    shapes.setColor(BORDER_COLOR);
    shapes.rect(map.x(), map.y(), map.width(), map.height());
    drawBaseMarker(shapes, projection, scale, baseCenterX, baseCenterY);
    if (raider != null) drawRaiderTrail(shapes, projection, raider);
    drawGroupMarkers(shapes, projection, ghostwingGroups);
    if (aiming) drawAimMarker(shapes, projection, aimX, aimY, selectedTarget);
    shapes.end();

    shapes.begin(ShapeRenderer.ShapeType.Filled);
    drawVillageMarkers(shapes, projection, villagePlacements);
    drawAstra(shapes, projection, astra);
    drawEnemyBaseMarker(shapes, projection, enemyBaseCenterX, enemyBaseCenterY);
    if (bomber != null && bomber.hasTakenOff() && bomberBlinkVisible(elapsedSeconds)) {
      drawBastionBomberMarker(shapes, projection, bomber);
    }
    if (raider != null) {
      MinimapProjection.Point point = projection.projectMarker(
          raider.x() + raider.drawWidth() / 2f,
          raider.y() + raider.drawHeight() / 2f, markerMargin(projection, 5f));
      shapes.setColor(RAIDER_COLOR);
      shapes.circle(point.x(), point.y(), 4f * scale, 12);
    }
    if (escorts != null) {
      for (BastionEscort escort : escorts) {
        if (escort == null || !escort.isActive()) continue;
        MinimapProjection.Point point = projection.projectMarker(
            escort.raider().centerX(), escort.raider().centerY(),
            markerMargin(projection, 5f));
        shapes.setColor(ESCORT_COLOR);
        shapes.circle(point.x(), point.y(), 3.5f * scale, 12);
      }
    }
    drawMissileMarkers(shapes, projection, guidedMissiles);
    shapes.end();
    shapes.setColor(previousColor);
  }

  /**
   * Draws the dark panel and map with their alpha channel active, then restores the previous GL
   * blending state so the rest of the scene, HUD and later screens keep their opacity.
   */
  private static void drawTranslucentBackground(ShapeRenderer shapes,
      MinimapProjection.Rectangle panel, MinimapProjection.Rectangle map) {
    boolean blendingWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
    if (!blendingWasEnabled) {
      Gdx.gl.glEnable(GL20.GL_BLEND);
    }
    try {
      shapes.begin(ShapeRenderer.ShapeType.Filled);
      shapes.setColor(PANEL_COLOR);
      shapes.rect(panel.x(), panel.y(), panel.width(), panel.height());
      shapes.setColor(MAP_COLOR);
      shapes.rect(map.x(), map.y(), map.width(), map.height());
      shapes.end();
    } finally {
      if (!blendingWasEnabled) {
        Gdx.gl.glDisable(GL20.GL_BLEND);
      }
    }
  }

  /**
   * Scales a marker clamp so a bigger map gets bigger symbols while keeping the marker inside the
   * map even on very small viewports.
   */
  static float markerMargin(MinimapProjection projection, float baseMargin) {
    if (projection == null || !Float.isFinite(baseMargin) || baseMargin < 0f) {
      throw new IllegalArgumentException("Projection and non-negative margin are required");
    }
    float limit = Math.min(projection.map().width(), projection.map().height()) / 2f - 0.5f;
    return Math.max(0f, Math.min(baseMargin * projection.visualScale(), limit));
  }

  private static void drawVillageMarkers(ShapeRenderer shapes, MinimapProjection projection,
      List<SceneryPlacement> villagePlacements) {
    float half = 2f * projection.visualScale();
    shapes.setColor(VILLAGE_COLOR);
    for (MinimapProjection.Point point : villageMarkers(projection, villagePlacements)) {
      shapes.rect(point.x() - half, point.y() - half, half * 2f, half * 2f);
    }
  }

  /** One projected marker per approved village cell, in the south-to-north order received. */
  static List<MinimapProjection.Point> villageMarkers(MinimapProjection projection,
      List<SceneryPlacement> villagePlacements) {
    if (projection == null || villagePlacements == null) {
      throw new IllegalArgumentException("Projection and village placements are required");
    }
    return villagePlacements.stream()
        .map(placement -> projection.projectMarker(
            (float) placement.bounds().centerX(), (float) placement.bounds().centerY(),
            VillageLabelLayout.MARKER_MARGIN))
        .toList();
  }

  private static void drawMissileMarkers(ShapeRenderer shapes, MinimapProjection projection,
      List<GuidedMissile> missiles) {
    float scale = projection.visualScale();
    for (MissileMarker marker : missileMarkers(projection, missiles)) {
      MinimapProjection.Point point = marker.position();
      shapes.setColor(marker.owner() == GuidedMissile.Owner.ASTRA
          ? ASTRA_MISSILE_COLOR : ENEMY_MISSILE_COLOR);
      shapes.circle(point.x(), point.y(), 5f * scale, 12);
      shapes.setColor(Color.WHITE);
      shapes.circle(point.x(), point.y(), 1.8f * scale, 8);
    }
  }

  /** Current projected positions; recalculated each draw so the dots follow live missile motion. */
  static List<MissileMarker> missileMarkers(MinimapProjection projection,
      List<GuidedMissile> missiles) {
    if (projection == null || missiles == null) {
      throw new IllegalArgumentException("Projection and missiles are required");
    }
    float margin = markerMargin(projection, 5f);
    return missiles.stream().filter(GuidedMissile::isActive)
        .map(missile -> new MissileMarker(
            projection.projectMarker(missile.x(), missile.y(), margin), missile.owner()))
        .toList();
  }

  /** The cursor and selected target have deliberately different shapes and colors. */
  private static void drawAimMarker(ShapeRenderer shapes, MinimapProjection projection,
      float aimX, float aimY, MissileTarget selectedTarget) {
    float scale = projection.visualScale();
    AimMarkers markers = aimMarkers(projection, aimX, aimY, selectedTarget);
    MinimapProjection.Point cursor = markers.cursor();
    shapes.setColor(AIM_COLOR);
    float[] cursorVertices = diamondVertices(cursor, 5f * scale);
    shapes.line(cursorVertices[0], cursorVertices[1], cursorVertices[2], cursorVertices[3]);
    shapes.line(cursorVertices[2], cursorVertices[3], cursorVertices[4], cursorVertices[5]);
    shapes.line(cursorVertices[4], cursorVertices[5], cursorVertices[6], cursorVertices[7]);
    shapes.line(cursorVertices[6], cursorVertices[7], cursorVertices[0], cursorVertices[1]);
    if (markers.selectedTarget() == null) return;
    MinimapProjection.Point locked = markers.selectedTarget();
    shapes.setColor(LOCK_COLOR);
    shapes.circle(locked.x(), locked.y(), 7f * scale, 16);
    shapes.line(locked.x() - 10f * scale, locked.y(), locked.x() - 5f * scale, locked.y());
    shapes.line(locked.x() + 5f * scale, locked.y(), locked.x() + 10f * scale, locked.y());
    shapes.line(locked.x(), locked.y() - 10f * scale, locked.x(), locked.y() - 5f * scale);
    shapes.line(locked.x(), locked.y() + 5f * scale, locked.x(), locked.y() + 10f * scale);
  }

  static AimMarkers aimMarkers(MinimapProjection projection, float aimX, float aimY,
      MissileTarget selectedTarget) {
    if (projection == null || !Float.isFinite(aimX) || !Float.isFinite(aimY)) {
      throw new IllegalArgumentException("Projection and finite aim coordinates are required");
    }
    float cursorMargin = markerMargin(projection, 7f);
    float targetMargin = markerMargin(projection, 10f);
    MinimapProjection.Point cursor = projection.projectMarker(aimX, aimY, cursorMargin);
    MinimapProjection.Point target = selectedTarget == null ? null
        : projection.projectMarker(selectedTarget.x(), selectedTarget.y(), targetMargin);
    return new AimMarkers(cursor, target);
  }

  static boolean bomberBlinkVisible(float elapsedSeconds) {
    return Float.isFinite(elapsedSeconds) && elapsedSeconds >= 0f
        && ((int) (elapsedSeconds / 0.3f) & 1) == 0;
  }

  private static void drawBastionBomberMarker(ShapeRenderer shapes,
      MinimapProjection projection, BastionBomber bomber) {
    float scale = projection.visualScale();
    MinimapProjection.Point point = projection.projectMarker(bomber.centerX(), bomber.centerY(),
        markerMargin(projection, 7f));
    shapes.setColor(BOMBER_COLOR);
    shapes.circle(point.x(), point.y(), 5f * scale, 16);
    shapes.setColor(BOMBER_CORE_COLOR);
    shapes.circle(point.x(), point.y(), 2f * scale, 12);
  }

  private static void drawEnemyBaseMarker(ShapeRenderer shapes, MinimapProjection projection,
      float centerX, float centerY) {
    float scale = projection.visualScale();
    MinimapProjection.Point point = projection.projectMarker(centerX, centerY,
        markerMargin(projection, 8f));
    shapes.setColor(ENEMY_BASE_COLOR);
    drawThickLine(shapes, point.x() - 5f * scale, point.y() - 5f * scale,
        point.x() + 5f * scale, point.y() + 5f * scale, 3f * scale);
    drawThickLine(shapes, point.x() - 5f * scale, point.y() + 5f * scale,
        point.x() + 5f * scale, point.y() - 5f * scale, 3f * scale);
  }

  private static void drawThickLine(ShapeRenderer shapes, float startX, float startY,
      float endX, float endY, float thickness) {
    float dx = endX - startX;
    float dy = endY - startY;
    float halfOffsetX = -dy / (float) Math.sqrt(dx * dx + dy * dy) * thickness / 2f;
    float halfOffsetY = dx / (float) Math.sqrt(dx * dx + dy * dy) * thickness / 2f;
    float ax = startX + halfOffsetX;
    float ay = startY + halfOffsetY;
    float bx = startX - halfOffsetX;
    float by = startY - halfOffsetY;
    float cx = endX - halfOffsetX;
    float cy = endY - halfOffsetY;
    float dx2 = endX + halfOffsetX;
    float dy2 = endY + halfOffsetY;
    shapes.triangle(ax, ay, bx, by, cx, cy);
    shapes.triangle(ax, ay, cx, cy, dx2, dy2);
  }

  private static void drawBaseMarker(ShapeRenderer shapes, MinimapProjection projection,
      float scale, float centerX, float centerY) {
    MinimapProjection.Point point = projection.projectMarker(centerX, centerY,
        markerMargin(projection, 6f));
    shapes.setColor(BASE_COLOR);
    shapes.circle(point.x(), point.y(), 4f * scale, 16);
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
    float scale = projection.visualScale();
    for (GhostwingEncounter.Point group : ghostwingGroups) {
      MinimapProjection.Point point = projection.projectMarker(
          group.x(), group.y(), markerMargin(projection, GROUP_MARKER_HALF + 1f));
      float[] diamond = diamondVertices(point, GROUP_MARKER_HALF * scale);
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
    float scale = projection.visualScale();
    MinimapProjection.Point center = projection.projectMarker(
        astra.x() + astra.drawWidth() / 2f,
        astra.y() + astra.drawHeight() / 2f,
        markerMargin(projection, ASTRA_TIP_LENGTH + 1f));
    MinimapProjection.Point direction = MinimapProjection.astraDirection(astra.yawDegrees());
    float perpendicularX = direction.y();
    float perpendicularY = -direction.x();
    float tipX = center.x() + direction.x() * ASTRA_TIP_LENGTH * scale;
    float tipY = center.y() + direction.y() * ASTRA_TIP_LENGTH * scale;
    float baseX = center.x() - direction.x() * ASTRA_BASE_LENGTH * scale;
    float baseY = center.y() - direction.y() * ASTRA_BASE_LENGTH * scale;
    shapes.setColor(ASTRA_COLOR);
    shapes.triangle(tipX, tipY,
        baseX + perpendicularX * ASTRA_HALF_WIDTH * scale,
        baseY + perpendicularY * ASTRA_HALF_WIDTH * scale,
        baseX - perpendicularX * ASTRA_HALF_WIDTH * scale,
        baseY - perpendicularY * ASTRA_HALF_WIDTH * scale);
  }

  private static void drawRaiderTrail(ShapeRenderer shapes, MinimapProjection projection,
      VesperRaider raider) {
    float scale = projection.visualScale();
    MinimapProjection.Point center = projection.projectMarker(
        raider.x() + raider.drawWidth() / 2f,
        raider.y() + raider.drawHeight() / 2f,
        markerMargin(projection, 13f));
    MinimapProjection.Point direction = MinimapProjection.raiderDirection(raider.headingDegrees());
    shapes.setColor(RAIDER_TRAIL_COLOR);
    shapes.line(center.x() - direction.x() * 12f * scale,
        center.y() - direction.y() * 12f * scale,
        center.x() - direction.x() * 4f * scale,
        center.y() - direction.y() * 4f * scale);
  }
}
