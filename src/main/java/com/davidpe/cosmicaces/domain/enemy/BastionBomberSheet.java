package com.davidpe.cosmicaces.domain.enemy;

import java.util.List;

/** Padded, non-overlapping top-left frame rectangles for the five-pose Bastion bomber sheet. */
public final class BastionBomberSheet {
  public static final int WIDTH = 2078;
  public static final int HEIGHT = 757;
  public static final int FRAME_COUNT = 5;
  /** Transparent vertical inset keeps the pointed nose and its filtered edge inside each crop. */
  public static final int VERTICAL_FRAME_PADDING = 16;
  private static final String INTERNAL_PATH =
      "assets/images/enemy/bastion_bomber/bastion_bomber_roll_sheet_v1.png";

  public record Slice(int x, int y, int width, int height) {}

  private static final List<Slice> FRAMES = List.of(
      new Slice(4, 93, 376, 529),
      new Slice(392, 94, 419, 542),
      new Slice(828, 94, 399, 535),
      new Slice(1235, 100, 388, 530),
      new Slice(1629, 91, 419, 540));

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
