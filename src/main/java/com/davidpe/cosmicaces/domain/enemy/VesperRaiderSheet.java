package com.davidpe.cosmicaces.domain.enemy;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.List;

/**
 * Metadata of the Vesper Raider v3 sprite sheet. Its five measured regions lie left to right:
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
   * firing PNG adds on the sides of the body.
   */
  public record CannonMouth(float lateral, float forward) {}

  /**
   * Registration correction, in sheet pixels, that keeps the firing variant's body anchored on the
   * flight variant for a pose. {@code offsetXPx} is {@code (bodyCx - w/2)} of the flight slice minus
   * that of the firing slice; {@code offsetYPx} uses {@code (h/2 - bodyCy)}, which stays valid in
   * the drawn frame where {@link #orientedForDescent} flips the region vertically because the flip
   * cancels in the difference. Measured on the bundled PNGs with the shared warm-flash color rule.
   */
  public record FiringPlacement(float offsetXPx, float offsetYPx) {
    public FiringPlacement {
      if (!Float.isFinite(offsetXPx) || !Float.isFinite(offsetYPx)) {
        throw new IllegalArgumentException("Firing placement offsets must be finite");
      }
    }
  }

  public static final int WIDTH = 1983;
  public static final int HEIGHT = 793;

  public static final int FIRING_WIDTH = 1983;
  public static final int FIRING_HEIGHT = 793;
  private static final String FIRING_INTERNAL_PATH =
      "assets/images/enemy/vesper_raider_roll_sheet_v3_firing.png";

  private static final List<Slice> FIRING_SLICES = List.of(
      new Slice(14, 125, 405, 563),
      new Slice(419, 127, 416, 559),
      new Slice(835, 129, 405, 556),
      new Slice(1244, 132, 291, 519),
      new Slice(1581, 130, 373, 520));

  private static final String INTERNAL_PATH = "assets/images/enemy/vesper_raider_roll_sheet_v3.png";

  private static final List<Slice> SLICES =
      List.of(
          new Slice(10, 130, 409, 552),
          new Slice(419, 134, 416, 548),
          new Slice(835, 134, 402, 548),
          new Slice(1242, 134, 291, 513),
          new Slice(1585, 135, 368, 513));

  /**
   * The two measured side cannon mouths per pose, ordered left then right. They are taken from the
   * symmetric warm flashes that only the firing sheet adds to the wing area; when a pose hides one
   * flash behind the body (right yaw) the pair is completed by mirroring, so a burst always leaves
   * both cannons. Forward is small because the guns sit near the middle of the body.
   */
  private static final List<List<CannonMouth>> CANNON_MOUTHS = List.of(
      List.of(new CannonMouth(-0.2418f, 0.0571f), new CannonMouth(0.2636f, 0.0208f)),
      List.of(new CannonMouth(-0.2935f, 0.0408f), new CannonMouth(0.2935f, 0.0426f)),
      List.of(new CannonMouth(-0.2618f, 0.0362f), new CannonMouth(0.2473f, 0.0471f)),
      List.of(new CannonMouth(-0.2074f, -0.0063f), new CannonMouth(0.2056f, -0.0063f)),
      List.of(new CannonMouth(-0.3034f, -0.0109f), new CannonMouth(0.2129f, -0.0145f)));

  /** Per-pose registration of the firing variant against the flight variant, in {@link Pose} order. */
  private static final List<FiringPlacement> FIRING_PLACEMENTS = List.of(
      new FiringPlacement(3.5f, -3.0f),
      new FiringPlacement(0.0f, 0.5f),
      new FiringPlacement(-0.5f, 0.5f),
      new FiringPlacement(2.0f, 0.0f),
      new FiringPlacement(1.5f, 3.0f));

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
