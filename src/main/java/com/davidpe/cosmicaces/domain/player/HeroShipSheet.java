package com.davidpe.cosmicaces.domain.player;

import java.util.List;

/**
 * Metadata of the hero ship sprite sheets. Each sheet is described by its classpath location, its
 * dimensions and the pose slices measured from the alpha channel. The normal sheet shows the ship
 * cruising; the accelerate sheet is the same ship with the engine flame lit. Both v3 sheets lay
 * five images left to right: right bank, neutral, left bank, right yaw and left yaw.
 */
public final class HeroShipSheet {

  /** The five ship poses in sheet order. Yaw poses are ready for directional flight. */
  public enum Pose {
    RIGHT,
    NEUTRAL,
    LEFT,
    YAW_RIGHT,
    YAW_LEFT
  }

  /** A rectangular region of a sheet, measured from the alpha channel. */
  public record Slice(int x, int y, int width, int height) {}

  /** One hero ship sprite sheet: its classpath location, size and measured pose slices. */
  public static final class Sheet {

    private final String internalPath;
    private final int width;
    private final int height;
    private final List<Slice> slices;

    Sheet(String internalPath, int width, int height, List<Slice> slices) {
      if (slices.size() != Pose.values().length) {
        throw new IllegalArgumentException(
            "A sheet must define one slice per pose, got " + slices.size());
      }
      for (Slice slice : slices) {
        if (slice.x() < 0 || slice.y() < 0 || slice.width() <= 0 || slice.height() <= 0
            || slice.x() + slice.width() > width || slice.y() + slice.height() > height) {
          throw new IllegalArgumentException(
              "Slice must lie inside the " + width + "x" + height + " sheet: "
                  + "x=" + slice.x() + " y=" + slice.y()
                  + " w=" + slice.width() + " h=" + slice.height());
        }
      }
      this.internalPath = internalPath;
      this.width = width;
      this.height = height;
      this.slices = List.copyOf(slices);
    }

    /** Classpath path of the sprite sheet PNG, loadable as an internal LibGDX file. */
    public String internalPath() {
      return internalPath;
    }

    public int width() {
      return width;
    }

    public int height() {
      return height;
    }

    /** Returns the slice of the given pose. */
    public Slice slice(Pose pose) {
      return slices.get(pose.ordinal());
    }

    /** Returns the pose slices in {@link Pose} order. */
    public List<Slice> slices() {
      return slices;
    }
  }

  /** The normal hero sheet, with the engine flame off. */
  public static final Sheet NORMAL = new Sheet(
      "assets/images/player/hero_ship_v3.png", 2079, 756,
      List.of(
          new Slice(15, 83, 375, 577),
          new Slice(390, 112, 456, 530),
          new Slice(846, 83, 380, 577),
          new Slice(1240, 104, 406, 588),
          new Slice(1675, 104, 404, 586)));

  /** The accelerate hero sheet, with the engine flame lit. */
  public static final Sheet ACCELERATE = new Sheet(
      "assets/images/player/hero_ship_v3_accelerate.png", 2052, 766,
      List.of(
          new Slice(47, 89, 354, 579),
          new Slice(416, 115, 441, 537),
          new Slice(857, 89, 371, 579),
          new Slice(1233, 113, 358, 553),
          new Slice(1684, 113, 338, 557)));

  /** Both bundled hero sheets in visual order. */
  public static List<Sheet> sheets() {
    return List.of(NORMAL, ACCELERATE);
  }

  private HeroShipSheet() {}
}
