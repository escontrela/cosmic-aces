package com.davidpe.cosmicaces.domain.enemy;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.List;

/**
 * Metadata of the Vesper Raider v4 sprite sheet. Its five measured regions lie left to right:
 * left bank, neutral, right bank, right yaw and left yaw. The yaw poses accompany bounded heading
 * changes and the bank poses cover drift or straight flight.
 */
public final class VesperRaiderSheet {

  /** The five poses in sheet order. */
  public enum Pose {
    LEFT,
    NEUTRAL,
    RIGHT,
    YAW_RIGHT,
    YAW_LEFT
  }

  /** A rectangular region of the sheet. */
  public record Slice(int x, int y, int width, int height) {}

  /**
   * A cannon mouth of the Raider, expressed as offsets from the center of the stable draw box:
   * lateral is positive towards the ship's right and forward is positive towards the nose, both as
   * fractions of the box width and height. Measured from the two warm muzzle flashes that the
   * firing PNG adds at the nose.
   */
  public record CannonMouth(float lateral, float forward) {}

  /**
   * Registration correction in sheet pixels, anchored on the blue cockpit lens rather than the
   * muzzle flash or engine bounds. X is the firing lens offset from its slice center minus the
   * flight lens offset; Y is the same source-image difference. Ship.draw subtracts these offsets
   * from the image position after orientedForDescent flips it, keeping the cockpit fixed.
   */
  public record FiringPlacement(float offsetXPx, float offsetYPx) {
    public FiringPlacement {
      if (!Float.isFinite(offsetXPx) || !Float.isFinite(offsetYPx)) {
        throw new IllegalArgumentException("Firing placement offsets must be finite");
      }
    }
  }

  public static final int WIDTH = 2079;
  public static final int HEIGHT = 756;

  public static final int FIRING_WIDTH = 2079;
  public static final int FIRING_HEIGHT = 756;
  private static final String FIRING_INTERNAL_PATH =
      "assets/images/enemy/vesper_raider/vesper_raider_roll_sheet_v4_firing.png";

  private static final List<Slice> FIRING_SLICES = List.of(
      new Slice(7, 91, 371, 560),
      new Slice(383, 94, 405, 555),
      new Slice(804, 87, 396, 562),
      new Slice(1228, 95, 416, 551),
      new Slice(1672, 85, 407, 566));

  private static final String INTERNAL_PATH =
      "assets/images/enemy/vesper_raider/vesper_raider_roll_sheet_v4.png";

  // Measured alpha bounds, with two pixels of padding. These are not uniform grid cells.
  private static final List<Slice> SLICES = List.of(
      new Slice(16, 116, 374, 535),
      new Slice(398, 127, 434, 526),
      new Slice(839, 116, 377, 535),
      new Slice(1229, 119, 418, 542),
      new Slice(1676, 118, 401, 542));

  /** Nose cannon exits measured at the bases of the two v4 muzzle flashes. */
  private static final List<List<CannonMouth>> CANNON_MOUTHS = List.of(
      List.of(new CannonMouth(-0.0086f, 0.4147f), new CannonMouth(0.0910f, 0.4147f)),
      List.of(new CannonMouth(-0.0424f, 0.4117f), new CannonMouth(0.0535f, 0.4117f)),
      List.of(new CannonMouth(-0.0923f, 0.4148f), new CannonMouth(0.0037f, 0.4148f)),
      List.of(new CannonMouth(0.1156f, 0.4401f), new CannonMouth(0.1875f, 0.3903f)),
      List.of(new CannonMouth(-0.2611f, 0.3663f), new CannonMouth(-0.1808f, 0.4253f)));

  /** Per-pose registration of the firing variant against the flight variant, in Pose order. */
  private static final List<FiringPlacement> FIRING_PLACEMENTS = List.of(
      new FiringPlacement(-13.31f, 16.25f),
      new FiringPlacement(-17.00f, 19.64f),
      new FiringPlacement(21.51f, 15.81f),
      new FiringPlacement(-3.61f, 27.52f),
      new FiringPlacement(41.98f, 25.52f));

  static {
    validate(SLICES, WIDTH, HEIGHT);
    validate(FIRING_SLICES, FIRING_WIDTH, FIRING_HEIGHT);
    if (CANNON_MOUTHS.size() != Pose.values().length) {
      throw new IllegalStateException(
          "The raider sheet must define cannon mouths for every pose, got " + CANNON_MOUTHS.size());
    }
    if (FIRING_PLACEMENTS.size() != Pose.values().length) {
      throw new IllegalStateException(
          "The raider sheet must define firing placement for every pose, got "
              + FIRING_PLACEMENTS.size());
    }
  }

  private static void validate(List<Slice> slices, int width, int height) {
    if (slices.size() != Pose.values().length) {
      throw new IllegalStateException(
          "The raider sheet must define one slice per pose, got " + slices.size());
    }
    for (Slice slice : slices) {
      if (slice.x() < 0 || slice.y() < 0 || slice.width() <= 0 || slice.height() <= 0
          || slice.x() + slice.width() > width || slice.y() + slice.height() > height) {
        throw new IllegalStateException(
            "Slice must lie inside the " + width + "x" + height + " sheet: "
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

  /** Firing variant with flashes included, using the same {@link Pose} order. */
  public static String firingInternalPath() {
    return FIRING_INTERNAL_PATH;
  }

  public static Slice firingSlice(Pose pose) {
    return FIRING_SLICES.get(pose.ordinal());
  }

  public static List<Slice> firingSlices() {
    return FIRING_SLICES;
  }

  /** The two measured cannon mouths of the given pose, ordered left then right. */
  public static List<CannonMouth> cannonMouths(Pose pose) {
    return CANNON_MOUTHS.get(pose.ordinal());
  }

  /** Correction that keeps the firing variant anchored on the flight pose. */
  public static FiringPlacement firingPlacement(Pose pose) {
    return FIRING_PLACEMENTS.get(pose.ordinal());
  }

  public static int maxSliceWidth() {
    return SLICES.stream().mapToInt(Slice::width).max().orElseThrow();
  }

  public static int maxSliceHeight() {
    return SLICES.stream().mapToInt(Slice::height).max().orElseThrow();
  }

  /** Maps the model's turn direction to the matching bank frame in the descent-oriented sheet. */
  public static Pose poseForBank(int bank) {
    return bank < 0 ? Pose.RIGHT : bank > 0 ? Pose.LEFT : Pose.NEUTRAL;
  }

  /**
   * Maps the direction of the last bounded heading change to the matching yaw frame: {@code -1}
   * turning left, {@code +1} turning right, {@code 0} straight.
   */
  public static Pose poseForTurn(int turnDirection) {
    return turnDirection < 0 ? Pose.YAW_LEFT : turnDirection > 0 ? Pose.YAW_RIGHT : Pose.NEUTRAL;
  }

  /**
   * Orients a pose region for the raider's descent. The source sheet faces up (nose at the top,
   * engines at the bottom) while the encounter model descends downward, so the region is mirrored
   * across the horizontal axis; the horizontal axis is left unchanged so the bank poses keep their
   * left/right sense. The flip is applied at most once, so calling this again is a no-op.
   */
  public static TextureRegion orientedForDescent(TextureRegion region) {
    if (!region.isFlipY()) {
      region.flip(false, true);
    }
    return region;
  }

  private VesperRaiderSheet() {}
}
