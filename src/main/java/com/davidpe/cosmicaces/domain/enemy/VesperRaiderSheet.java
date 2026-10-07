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

  public static final int WIDTH = 1983;
  public static final int HEIGHT = 793;

  private static final String INTERNAL_PATH = "assets/images/enemy/vesper_raider_roll_sheet_v3.png";

  private static final List<Slice> SLICES =
      List.of(
          new Slice(10, 130, 409, 552),
          new Slice(419, 134, 416, 548),
          new Slice(835, 134, 402, 548),
          new Slice(1242, 134, 291, 513),
          new Slice(1585, 135, 368, 513));

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
