package com.davidpe.cosmicaces.domain.enemy;

import java.util.List;

/** Metadata for the existing three-frame Ghostwing propeller sheet; owns no LibGDX resource. */
public final class GhostwingDroneSheet {
  public static final int WIDTH = 2172;
  public static final int HEIGHT = 724;
  public static final int FRAME_COUNT = 3;
  public static final int FRAME_SIZE = 724;

  /** Top-left PNG coordinates. The body stays centered while the propeller changes phase. */
  public record Slice(int x, int y, int width, int height) {}

  private static final List<Slice> FRAMES = List.of(
      new Slice(0, 0, FRAME_SIZE, FRAME_SIZE),
      new Slice(FRAME_SIZE, 0, FRAME_SIZE, FRAME_SIZE),
      new Slice(2 * FRAME_SIZE, 0, FRAME_SIZE, FRAME_SIZE));

  static {
    if (FRAMES.size() != FRAME_COUNT || WIDTH != FRAME_COUNT * FRAME_SIZE
        || HEIGHT != FRAME_SIZE) {
      throw new IllegalStateException("Ghostwing sheet layout does not match its dimensions");
    }
    for (Slice frame : FRAMES) {
      if (frame.x() < 0 || frame.y() < 0 || frame.width() <= 0 || frame.height() <= 0
          || frame.x() + frame.width() > WIDTH || frame.y() + frame.height() > HEIGHT) {
        throw new IllegalStateException("Ghostwing frame lies outside the sheet: " + frame);
      }
    }
  }

  public static String internalPath() {
    return "assets/images/enemy/ghostwing_drone/ghostwing_scout_propeller_blur_sheet_v1.png";
  }

  /** Source nose points up (+Y); rotate a region around its center for other travel directions. */
  public static List<Slice> frameSlices() {
    return FRAMES;
  }

  public static Slice frameSlice(int frameIndex) {
    return FRAMES.get(frameIndex);
  }

  private GhostwingDroneSheet() {}
}
