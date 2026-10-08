package com.davidpe.cosmicaces.domain.effect;

import java.util.List;

/** Measured frames of the shared ship explosion, in playback order; no native resources. */
public final class ShipExplosionSheet {
  public static final int WIDTH = 1774;
  public static final int HEIGHT = 887;
  public static final int FRAME_COUNT = 8;
  /** Largest padded frame dimension; use one scale for the whole animation. */
  public static final int REFERENCE_SIZE = 417;

  /** Top-left PNG coordinates, with a centered geometric anchor in slice-local pixels. */
  public record Slice(int x, int y, int width, int height) {
    public float anchorX() {
      return width / 2f;
    }

    public float anchorY() {
      return height / 2f;
    }
  }

  // Alpha > 8 bounds measured on the original PNG, padded by two pixels. This excludes
  // near-transparent background noise. Read left-to-right in the top row, then bottom row.
  private static final List<Slice> FRAMES = List.of(
      new Slice(165, 180, 145, 147),
      new Slice(558, 121, 233, 253),
      new Slice(936, 77, 336, 333),
      new Slice(1346, 14, 406, 417),
      new Slice(52, 484, 373, 367),
      new Slice(514, 497, 326, 341),
      new Slice(973, 533, 284, 288),
      new Slice(1464, 596, 172, 192));

  public static String internalPath() {
    return "assets/images/effects/ship_explosion_sheet.png";
  }

  /** Immutable metadata; frame zero is ignition and frame seven is the last debris. */
  public static List<Slice> frameSlices() {
    return FRAMES;
  }

  public static Slice frameSlice(int frameIndex) {
    return FRAMES.get(frameIndex);
  }

  private ShipExplosionSheet() {}
}
