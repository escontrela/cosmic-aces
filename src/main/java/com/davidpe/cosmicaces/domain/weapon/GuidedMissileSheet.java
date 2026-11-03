package com.davidpe.cosmicaces.domain.weapon;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Layout and typed access to the guided missile atlas; the caller owns and disposes its texture. */
public final class GuidedMissileSheet {
  public static final int WIDTH = 1280;
  public static final int HEIGHT = 1280;
  public static final int COLUMNS = 8;
  public static final int ROWS = 8;
  public static final int CELL_SIZE = 160;
  public static final int HEADINGS_PER_MISSILE = 8;
  public static final int COMBUSTION_STAGES = 4;

  /** Grid columns, clockwise from north; the missile artwork points up in the first column. */
  public enum Heading {
    NORTH,
    NORTH_EAST,
    EAST,
    SOUTH_EAST,
    SOUTH,
    SOUTH_WEST,
    WEST,
    NORTH_WEST
  }

  /** Each missile's four consecutive rows show its combustion fading over time. */
  public enum CombustionStage {
    IGNITION,
    BURNING,
    FADING,
    SPENT
  }

  public enum Faction {
    ASTRA(0),
    ENEMY(4);

    private final int firstRow;

    Faction(int firstRow) {
      this.firstRow = firstRow;
    }
  }

  /** Integer pixel coordinates from the top-left of the PNG. */
  public record Slice(int x, int y, int width, int height) {}

  /** Missile body pivot in slice-local pixels, with the origin at the bottom left (LibGDX). */
  public record Anchor(float x, float y) {}

  // Measured body centers in the SPENT row, excluding bright exhaust and transparent padding.
  // Rows are ASTRA and ENEMY; columns follow Heading. PNG coordinates have Y pointing down.
  private static final int[][] BODY_X = {
      {89, 89, 80, 82, 79, 74, 79, 70},
      {89, 88, 83, 85, 78, 72, 76, 72}
  };
  private static final int[][] BODY_Y = {
      {78, 78, 79, 79, 79, 81, 79, 78},
      {61, 60, 58, 62, 67, 63, 59, 59}
  };
  // Translation of the body from SPENT for each faction, combustion row and heading. Matching
  // the body rather than the full alpha bounds prevents the short flame moving the pivot.
  private static final int[][][] BODY_DX = {
      {{0, 1, 0, 1, 0, -1, -1, -1}, {0, -1, 0, 0, 0, 0, 0, 0},
          {0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0}},
      {{-1, 1, 2, 1, 0, -3, -2, -3}, {0, 0, -1, 0, 1, 0, -1, -2},
          {0, 0, 0, 0, 1, 0, 0, -1}, {0, 0, 0, 0, 0, 0, 0, 0}}
  };
  private static final int[][][] BODY_DY = {
      {{14, 12, 14, 16, 16, 15, 14, 13}, {9, 10, 8, 11, 10, 10, 9, 10},
          {4, 4, 4, 4, 5, 4, 4, 4}, {0, 0, 0, 0, 0, 0, 0, 0}},
      {{10, 12, 15, 17, 15, 17, 14, 11}, {4, 9, 9, 10, 8, 9, 8, 7},
          {0, 4, 3, 4, 3, 5, 4, 2}, {0, 0, 0, 0, 0, 0, 0, 0}}
  };
  // The first exhaust crosses into the next grid row; that next missile starts below y=166.
  private static final int FIRST_TRAIL_END_Y = 166;

  static {
    if (COLUMNS * CELL_SIZE != WIDTH || ROWS * CELL_SIZE != HEIGHT
        || ROWS != Faction.values().length * COMBUSTION_STAGES) {
      throw new IllegalStateException("Guided missile sheet layout does not match its dimensions");
    }
  }

  public static String internalPath() {
    return "assets/images/projectiles/guided-missile-sheet-v1.png";
  }

  public static Slice slice(Faction faction, Heading heading, CombustionStage stage) {
    if (faction == null || heading == null || stage == null) {
      throw new IllegalArgumentException("Faction, heading and combustion stage are required");
    }
    int column = heading.ordinal();
    int row = faction.firstRow + stage.ordinal();
    if (faction == Faction.ASTRA && heading == Heading.NORTH) {
      if (stage == CombustionStage.IGNITION) {
        return new Slice(0, 0, CELL_SIZE, FIRST_TRAIL_END_Y);
      }
      if (stage == CombustionStage.BURNING) {
        return new Slice(0, FIRST_TRAIL_END_Y, CELL_SIZE, 2 * CELL_SIZE - FIRST_TRAIL_END_Y);
      }
    }
    return new Slice(column * CELL_SIZE, row * CELL_SIZE, CELL_SIZE, CELL_SIZE);
  }

  /** Returns the measured body pivot, independent of smoke and the cropped region's size. */
  public static Anchor bodyAnchor(Faction faction, Heading heading, CombustionStage stage) {
    Slice region = slice(faction, heading, stage);
    int f = faction.ordinal();
    int h = heading.ordinal();
    int s = stage.ordinal();
    int bodyX = h * CELL_SIZE + BODY_X[f][h] + BODY_DX[f][s][h];
    int bodyY = (faction.firstRow + s) * CELL_SIZE + BODY_Y[f][h] + BODY_DY[f][s][h];
    return new Anchor(bodyX - region.x(), region.height() - (bodyY - region.y()));
  }

  /** Builds a region from a texture owned by the calling screen or renderer. */
  public static TextureRegion region(Texture texture, Faction faction, Heading heading,
      CombustionStage stage) {
    if (texture == null) {
      throw new IllegalArgumentException("Missile sheet texture is required");
    }
    Slice slice = slice(faction, heading, stage);
    if (texture.getWidth() != WIDTH || texture.getHeight() != HEIGHT) {
      throw new IllegalArgumentException("Unexpected guided missile sheet dimensions: "
          + texture.getWidth() + "x" + texture.getHeight());
    }
    return new TextureRegion(texture, slice.x(), slice.y(), slice.width(), slice.height());
  }

  private GuidedMissileSheet() {}
}
