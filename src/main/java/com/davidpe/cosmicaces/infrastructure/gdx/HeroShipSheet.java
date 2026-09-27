package com.davidpe.cosmicaces.infrastructure.gdx;

import java.util.List;

/**
 * Metadata of the hero ship sprite sheet: its classpath location, sheet dimensions and the five
 * poses sliced by their measured alpha bounds. Poses run left to right: two left-bank poses, the
 * neutral pose and two right-bank poses, matching the layout confirmed by the PO.
 */
public final class HeroShipSheet {

  /** Classpath path of the sprite sheet PNG, loadable as an internal LibGDX file. */
  public static final String INTERNAL_PATH = "assets/images/player/hero_ship.png";

  public static final int SHEET_WIDTH = 2172;
  public static final int SHEET_HEIGHT = 724;

  /** The five ship poses in sheet order. */
  public enum Pose {
    LEFT_A,
    LEFT_B,
    NEUTRAL,
    RIGHT_A,
    RIGHT_B
  }

  /** A rectangular region of the sheet, measured from the alpha channel. */
  public record Slice(int x, int y, int width, int height) {

    public Slice {
      if (x < 0 || y < 0 || width <= 0 || height <= 0
          || x + width > SHEET_WIDTH || y + height > SHEET_HEIGHT) {
        throw new IllegalArgumentException(
            "Slice must lie inside the " + SHEET_WIDTH + "x" + SHEET_HEIGHT + " sheet: "
                + "x=" + x + " y=" + y + " w=" + width + " h=" + height);
      }
    }
  }

  /** Measured alpha bounds of the five poses, kept in {@link Pose} order. */
  private static final List<Slice> SLICES = List.of(
      new Slice(0, 106, 399, 470),
      new Slice(414, 105, 368, 479),
      new Slice(876, 103, 421, 492),
      new Slice(1390, 106, 368, 478),
      new Slice(1772, 106, 400, 470));

  private HeroShipSheet() {}

  /** Returns the slice of the given pose. */
  public static Slice slice(Pose pose) {
    return SLICES.get(pose.ordinal());
  }

  /** Returns the five slices in {@link Pose} order. */
  public static List<Slice> slices() {
    return SLICES;
  }
}