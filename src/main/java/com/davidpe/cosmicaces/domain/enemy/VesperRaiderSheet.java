package com.davidpe.cosmicaces.domain.enemy;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
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

  /** Maps the model's turn direction to the matching bank frame in the descent-oriented sheet. */
  public static Pose poseForBank(int bank) {
    return bank < 0 ? Pose.RIGHT : bank > 0 ? Pose.LEFT : Pose.NEUTRAL;
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
