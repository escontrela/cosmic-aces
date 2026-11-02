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
    return new Slice(column * CELL_SIZE, row * CELL_SIZE, CELL_SIZE, CELL_SIZE);
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
