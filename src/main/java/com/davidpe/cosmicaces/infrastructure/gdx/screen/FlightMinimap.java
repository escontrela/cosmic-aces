package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.graphics.Color;
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
  private static final Color PANEL_COLOR = new Color(0.008f, 0.022f, 0.04f, 1f);
  private static final Color MAP_COLOR = new Color(0.012f, 0.036f, 0.06f, 1f);
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

    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(PANEL_COLOR);
    shapes.rect(panel.x(), panel.y(), panel.width(), panel.height());
    shapes.setColor(MAP_COLOR);
    shapes.rect(map.x(), map.y(), map.width(), map.height());
    shapes.end();

    shapes.begin(ShapeRenderer.ShapeType.Line);
    shapes.setColor(BORDER_COLOR);
    shapes.rect(map.x(), map.y(), map.width(), map.height());
    drawBaseMarker(shapes, projection, baseCenterX, baseCenterY);
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
          raider.y() + raider.drawHeight() / 2f, 5f);
      shapes.setColor(RAIDER_COLOR);
      shapes.circle(point.x(), point.y(), 4f, 12);
    }
    if (escorts != null) {
      for (BastionEscort escort : escorts) {
        if (escort == null || !escort.isActive()) continue;
        MinimapProjection.Point point = projection.projectMarker(
            escort.raider().centerX(), escort.raider().centerY(), 5f);
        shapes.setColor(ESCORT_COLOR);
        shapes.circle(point.x(), point.y(), 3.5f, 12);
      }
    }
    drawMissileMarkers(shapes, projection, guidedMissiles);
    shapes.end();
    shapes.setColor(previousColor);
  }

  private static void drawVillageMarkers(ShapeRenderer shapes, MinimapProjection projection,
      List<SceneryPlacement> villagePlacements) {
    shapes.setColor(VILLAGE_COLOR);
    for (MinimapProjection.Point point : villageMarkers(projection, villagePlacements)) {
      shapes.rect(point.x() - 2f, point.y() - 2f, 4f, 4f);
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
            (float) placement.bounds().centerX(), (float) placement.bounds().centerY(), 4f))
        .toList();
  }

  private static void drawMissileMarkers(ShapeRenderer shapes, MinimapProjection projection,
      List<GuidedMissile> missiles) {
    for (MissileMarker marker : missileMarkers(projection, missiles)) {
      MinimapProjection.Point point = marker.position();
      shapes.setColor(marker.owner() == GuidedMissile.Owner.ASTRA
          ? ASTRA_MISSILE_COLOR : ENEMY_MISSILE_COLOR);
      shapes.circle(point.x(), point.y(), 5f, 12);
      shapes.setColor(Color.WHITE);
      shapes.circle(point.x(), point.y(), 1.8f, 8);
    }
  }

  /** Current projected positions; recalculated each draw so the dots follow live missile motion. */
  static List<MissileMarker> missileMarkers(MinimapProjection projection,
      List<GuidedMissile> missiles) {
    if (projection == null || missiles == null) {
      throw new IllegalArgumentException("Projection and missiles are required");
    }
    return missiles.stream().filter(GuidedMissile::isActive)
        .map(missile -> new MissileMarker(
            projection.projectMarker(missile.x(), missile.y(), 5f), missile.owner()))
        .toList();
  }

  /** The cursor and selected target have deliberately different shapes and colors. */
  private static void drawAimMarker(ShapeRenderer shapes, MinimapProjection projection,
      float aimX, float aimY, MissileTarget selectedTarget) {
    AimMarkers markers = aimMarkers(projection, aimX, aimY, selectedTarget);
    MinimapProjection.Point cursor = markers.cursor();
    shapes.setColor(AIM_COLOR);
    float[] cursorVertices = diamondVertices(cursor, 5f);
    shapes.line(cursorVertices[0], cursorVertices[1], cursorVertices[2], cursorVertices[3]);
    shapes.line(cursorVertices[2], cursorVertices[3], cursorVertices[4], cursorVertices[5]);
    shapes.line(cursorVertices[4], cursorVertices[5], cursorVertices[6], cursorVertices[7]);
    shapes.line(cursorVertices[6], cursorVertices[7], cursorVertices[0], cursorVertices[1]);
    if (markers.selectedTarget() == null) return;
    MinimapProjection.Point locked = markers.selectedTarget();
    shapes.setColor(LOCK_COLOR);
    shapes.circle(locked.x(), locked.y(), 7f, 16);
    shapes.line(locked.x() - 10f, locked.y(), locked.x() - 5f, locked.y());
    shapes.line(locked.x() + 5f, locked.y(), locked.x() + 10f, locked.y());
    shapes.line(locked.x(), locked.y() - 10f, locked.x(), locked.y() - 5f);
    shapes.line(locked.x(), locked.y() + 5f, locked.x(), locked.y() + 10f);
  }

  static AimMarkers aimMarkers(MinimapProjection projection, float aimX, float aimY,
      MissileTarget selectedTarget) {
    if (projection == null || !Float.isFinite(aimX) || !Float.isFinite(aimY)) {
      throw new IllegalArgumentException("Projection and finite aim coordinates are required");
    }
    MinimapProjection.Point cursor = projection.projectMarker(aimX, aimY, 7f);
    MinimapProjection.Point target = selectedTarget == null ? null
        : projection.projectMarker(selectedTarget.x(), selectedTarget.y(), 8f);
    return new AimMarkers(cursor, target);
  }

  static boolean bomberBlinkVisible(float elapsedSeconds) {
    return Float.isFinite(elapsedSeconds) && elapsedSeconds >= 0f
        && ((int) (elapsedSeconds / 0.3f) & 1) == 0;
  }

  private static void drawBastionBomberMarker(ShapeRenderer shapes,
      MinimapProjection projection, BastionBomber bomber) {
    MinimapProjection.Point point = projection.projectMarker(bomber.centerX(), bomber.centerY(), 7f);
    shapes.setColor(BOMBER_COLOR);
    shapes.circle(point.x(), point.y(), 5f, 16);
    shapes.setColor(BOMBER_CORE_COLOR);
    shapes.circle(point.x(), point.y(), 2f, 12);
  }

  private static void drawEnemyBaseMarker(ShapeRenderer shapes, MinimapProjection projection,
      float centerX, float centerY) {
    MinimapProjection.Point point = projection.projectMarker(centerX, centerY, 8f);
    shapes.setColor(ENEMY_BASE_COLOR);
    drawThickLine(shapes, point.x() - 5f, point.y() - 5f,
        point.x() + 5f, point.y() + 5f, 3f);
    drawThickLine(shapes, point.x() - 5f, point.y() + 5f,
        point.x() + 5f, point.y() - 5f, 3f);
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
      float centerX, float centerY) {
    MinimapProjection.Point point = projection.projectMarker(centerX, centerY, 6f);
    shapes.setColor(BASE_COLOR);
    shapes.circle(point.x(), point.y(), 4f, 16);
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
