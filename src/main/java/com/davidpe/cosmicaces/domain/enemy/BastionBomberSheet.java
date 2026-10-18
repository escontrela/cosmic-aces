package com.davidpe.cosmicaces.domain.enemy;

import java.util.List;

/** Tight, non-overlapping top-left frame rectangles for the five-pose Bastion bomber sheet. */
public final class BastionBomberSheet {
  public static final int WIDTH = 2078;
  public static final int HEIGHT = 757;
  public static final int FRAME_COUNT = 5;
  private static final String INTERNAL_PATH =
      "assets/images/enemy/bastion_bomber/bastion_bomber_roll_sheet_v1.png";

  public record Slice(int x, int y, int width, int height) {}

  private static final List<Slice> FRAMES = List.of(
      new Slice(4, 109, 376, 497),
      new Slice(392, 110, 419, 510),
      new Slice(828, 110, 399, 503),
      new Slice(1235, 116, 388, 498),
      new Slice(1629, 107, 419, 508));

  static {
    if (FRAMES.size() != FRAME_COUNT) {
      throw new IllegalStateException("Bastion bomber sheet must contain five frames");
    }
    for (Slice frame : FRAMES) {
      if (frame.x() < 0 || frame.y() < 0 || frame.width() <= 0 || frame.height() <= 0
          || frame.x() + frame.width() > WIDTH || frame.y() + frame.height() > HEIGHT) {
        throw new IllegalStateException("Bastion bomber frame lies outside the sheet: " + frame);
      }
    }
  }

  public static String internalPath() {
    return INTERNAL_PATH;
  }

  public static List<Slice> frameSlices() {
    return FRAMES;
  }

  public static Slice frameSlice(int frameIndex) {
    return FRAMES.get(frameIndex);
  }

  private BastionBomberSheet() {}
}
