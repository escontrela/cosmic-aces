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

  /**
   * A cannon mouth of Astra, expressed as offsets from the center of the stable draw box: lateral is
   * positive towards the ship's right and forward is positive towards the nose, both as fractions of
   * the box width and height. Measured from the warm muzzle flashes of the prepared firing PNGs.
   */
  public record CannonMouth(float lateral, float forward) {}

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

  /** Normal-flight firing sheet, including both lateral muzzle flashes. */
  public static final Sheet NORMAL_FIRING = new Sheet(
      "assets/images/player/hero_ship_v3_firing.png", 2079, 756,
      List.of(
          new Slice(15, 83, 388, 583),
          new Slice(403, 112, 442, 531),
          new Slice(845, 81, 386, 582),
          new Slice(1242, 105, 414, 587),
          new Slice(1678, 106, 401, 586)));

  /** Accelerated-flight firing sheet; it has its own dimensions and measured cuts. */
  public static final Sheet ACCELERATE_FIRING = new Sheet(
      "assets/images/player/hero_ship_v3_accelerate_firing.png", 2078, 757,
      List.of(
          new Slice(19, 82, 386, 576),
          new Slice(405, 114, 434, 532),
          new Slice(839, 82, 385, 575),
          new Slice(1240, 104, 392, 558),
          new Slice(1674, 105, 391, 568)));

  /** Firing variants in the same pose order; separate from the existing flight-size reference. */
  public static List<Sheet> firingSheets() {
    return List.of(NORMAL_FIRING, ACCELERATE_FIRING);
  }

  /**
   * The two measured M61 Vulcan cannon mouths per pose, ordered left then right. Lateral and forward
   * are fractions of the stable draw box center, so every pose and speed variant shares the same
   * anchor: {@code Ship.draw} already centers each slice without stretching it. The values are the
   * warm muzzle flashes measured on {@link #NORMAL_FIRING}; the accelerate firing sheet keeps the
   * same body anchor but also carries the engine flame, which is not a cannon mouth. Projectile
   * origins and, later, the muzzle-flash registration both reuse this geometry.
   */
  public static List<CannonMouth> cannonMouths(Pose pose) {
    return CANNON_MOUTHS.get(pose.ordinal());
  }

  private static final List<List<CannonMouth>> CANNON_MOUTHS = List.of(
      List.of(new CannonMouth(-0.214f, 0.128f), new CannonMouth(0.220f, 0.129f)),
      List.of(new CannonMouth(-0.266f, 0.132f), new CannonMouth(0.260f, 0.133f)),
      List.of(new CannonMouth(-0.215f, 0.124f), new CannonMouth(0.216f, 0.123f)),
      List.of(new CannonMouth(-0.133f, 0.210f), new CannonMouth(0.340f, 0.083f)),
      List.of(new CannonMouth(-0.332f, 0.075f), new CannonMouth(0.162f, 0.179f)));

  private HeroShipSheet() {}
}
