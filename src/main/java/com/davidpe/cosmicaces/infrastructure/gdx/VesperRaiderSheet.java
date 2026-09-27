package com.davidpe.cosmicaces.infrastructure.gdx;

import java.util.List;

/**
 * Metadata of the Vesper Raider roll sprite sheet. The sheet is described by its classpath
 * location, its dimensions and the pose cells. The three images lay left to right: left bank,
 * neutral and right bank, each in a 724×724 cell of the 2172×724 sheet.
 */
public final class VesperRaiderSheet {

  /** The three roll poses in sheet order. */
  public enum Pose {
    LEFT,
    NEUTRAL,
    RIGHT
  }

  /** A rectangular region of the sheet. */
  public record Slice(int x, int y, int width, int height) {}

  public static final int WIDTH = 2172;
  public static final int HEIGHT = 724;

  private static final String INTERNAL_PATH = "assets/images/enemy/vesper_raider_roll_sheet.png";
  private static final int CELL = 724;

  private static final List<Slice> SLICES =
      List.of(
          new Slice(0, 0, CELL, CELL),
          new Slice(CELL, 0, CELL, CELL),
          new Slice(CELL * 2, 0, CELL, CELL));

  static {
    if (SLICES.size() != Pose.values().length) {
      throw new IllegalStateException(
          "The raider sheet must define one slice per pose, got " + SLICES.size());
    }
    for (Slice slice : SLICES) {
      if (slice.x() < 0 || slice.y() < 0 || slice.width() <= 0 || slice.height() <= 0
          || slice.x() + slice.width() > WIDTH || slice.y() + slice.height() > HEIGHT) {
        throw new IllegalStateException(
            "Slice must lie inside the " + WIDTH + "x" + HEIGHT + " sheet: "
                + "x=" + slice.x() + " y=" + slice.y()
                + " w=" + slice.width() + " h=" + slice.height());
      }
    }
  }

  /** Classpath path of the sprite sheet PNG, loadable as an internal LibGDX file. */
  public static String internalPath() {
    return INTERNAL_PATH;
  }

  /** Returns the slice of the given pose. */
  public static Slice slice(Pose pose) {
    return SLICES.get(pose.ordinal());
  }

  /** Returns the pose slices in {@link Pose} order. */
  public static List<Slice> slices() {
    return SLICES;
  }

  private VesperRaiderSheet() {}
}