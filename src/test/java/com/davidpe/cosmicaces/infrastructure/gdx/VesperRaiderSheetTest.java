package com.davidpe.cosmicaces.infrastructure.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class VesperRaiderSheetTest {

  @Test
  void exposesThreePosesInSheetOrder() {
    List<VesperRaiderSheet.Slice> slices = VesperRaiderSheet.slices();
    assertEquals(3, slices.size());
    assertEquals(
        List.of(
            VesperRaiderSheet.Pose.LEFT,
            VesperRaiderSheet.Pose.NEUTRAL,
            VesperRaiderSheet.Pose.RIGHT),
        List.of(VesperRaiderSheet.Pose.values()));
    for (int i = 1; i < slices.size(); i++) {
      assertTrue(slices.get(i).x() > slices.get(i - 1).x(),
          "pose " + i + " must start to the right of pose " + (i - 1));
    }
  }

  @Test
  void everySliceLiesInsideTheSheet() {
    for (VesperRaiderSheet.Pose pose : VesperRaiderSheet.Pose.values()) {
      VesperRaiderSheet.Slice slice = VesperRaiderSheet.slice(pose);
      assertTrue(slice.x() >= 0 && slice.y() >= 0);
      assertTrue(slice.x() + slice.width() <= VesperRaiderSheet.WIDTH,
          "slice of " + pose + " exceeds width " + VesperRaiderSheet.WIDTH + ": " + slice);
      assertTrue(slice.y() + slice.height() <= VesperRaiderSheet.HEIGHT,
          "slice of " + pose + " exceeds height " + VesperRaiderSheet.HEIGHT + ": " + slice);
    }
  }

  @Test
  void sliceAndPoseAreConsistentByOrdinal() {
    for (VesperRaiderSheet.Pose pose : VesperRaiderSheet.Pose.values()) {
      assertEquals(VesperRaiderSheet.slices().get(pose.ordinal()), VesperRaiderSheet.slice(pose));
    }
  }

  @Test
  void bundledSheetHasTheExpectedTransparentThreeCellLayout() throws IOException {
    var resource = getClass().getResource("/" + VesperRaiderSheet.internalPath());
    assertNotNull(resource, "sheet must be bundled with the game: " + VesperRaiderSheet.internalPath());
    BufferedImage image = ImageIO.read(resource);
    assertNotNull(image, "sheet must be a readable PNG: " + VesperRaiderSheet.internalPath());
    assertEquals(VesperRaiderSheet.WIDTH, image.getWidth(),
        "measured width of " + VesperRaiderSheet.internalPath());
    assertEquals(VesperRaiderSheet.HEIGHT, image.getHeight(),
        "measured height of " + VesperRaiderSheet.internalPath());
    assertTrue(image.getColorModel().hasAlpha());

    for (VesperRaiderSheet.Slice slice : VesperRaiderSheet.slices()) {
      boolean containsRaider = false;
      for (int y = slice.y(); y < slice.y() + slice.height() && !containsRaider; y++) {
        for (int x = slice.x(); x < slice.x() + slice.width(); x++) {
          if ((image.getRGB(x, y) >>> 24) != 0) {
            containsRaider = true;
            break;
          }
        }
      }
      assertTrue(containsRaider, "every pose slice must contain part of the raider: " + slice);
    }
  }

  @Test
  void lastCellIsInsideTheSheetBounds() {
    VesperRaiderSheet.Slice last = VesperRaiderSheet.slice(VesperRaiderSheet.Pose.RIGHT);
    assertEquals(VesperRaiderSheet.WIDTH, last.x() + last.width(),
        "the rightmost cell must end exactly at the sheet width");
  }
}