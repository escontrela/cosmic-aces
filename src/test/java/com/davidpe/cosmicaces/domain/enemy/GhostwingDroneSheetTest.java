package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class GhostwingDroneSheetTest {

  @Test
  void bundledSheetHasThreeSquareFramesWithTheDrone() throws IOException {
    var resource = getClass().getResource("/" + GhostwingDroneSheet.internalPath());
    assertNotNull(resource, "sheet must be bundled with the game: " + GhostwingDroneSheet.internalPath());
    BufferedImage image = ImageIO.read(resource);
    assertNotNull(image, "sheet must be a readable PNG: " + GhostwingDroneSheet.internalPath());
    assertEquals(GhostwingDroneSheet.WIDTH, image.getWidth(),
        "measured width of " + GhostwingDroneSheet.internalPath());
    assertEquals(GhostwingDroneSheet.HEIGHT, image.getHeight(),
        "measured height of " + GhostwingDroneSheet.internalPath());
    assertTrue(image.getColorModel().hasAlpha());
    assertEquals(GhostwingDroneSheet.FRAME_COUNT, GhostwingDroneSheet.frameSlices().size());

    for (GhostwingDroneSheet.Slice slice : GhostwingDroneSheet.frameSlices()) {
      assertEquals(GhostwingDroneSheet.FRAME_SIZE, slice.width(),
          "every frame cell must be square: " + slice);
      assertEquals(GhostwingDroneSheet.FRAME_SIZE, slice.height(),
          "every frame cell must be square: " + slice);
      assertTrue(slice.x() >= 0 && slice.y() >= 0);
      assertTrue(slice.x() + slice.width() <= GhostwingDroneSheet.WIDTH,
          "frame exceeds sheet width: " + slice);
      assertTrue(slice.y() + slice.height() <= GhostwingDroneSheet.HEIGHT,
          "frame exceeds sheet height: " + slice);
      boolean containsDrone = false;
      for (int y = slice.y(); y < slice.y() + slice.height() && !containsDrone; y++) {
        for (int x = slice.x(); x < slice.x() + slice.width(); x++) {
          if ((image.getRGB(x, y) >>> 24) != 0) {
            containsDrone = true;
            break;
          }
        }
      }
      assertTrue(containsDrone, "every propeller frame must contain the drone: " + slice);
    }
  }

  @Test
  void framesAreContiguousSquareSlicesStartingAtEveryCell() {
    List<GhostwingDroneSheet.Slice> slices = GhostwingDroneSheet.frameSlices();
    for (int i = 0; i < slices.size(); i++) {
      assertEquals(i * GhostwingDroneSheet.FRAME_SIZE, slices.get(i).x());
      assertEquals(0, slices.get(i).y());
    }
    for (int i = 1; i < slices.size(); i++) {
      assertEquals(slices.get(i - 1).x() + slices.get(i - 1).width(), slices.get(i).x(),
          "consecutive frames must touch without gaps or overlaps");
    }
  }

  @Test
  void frameSliceMatchesTheOrderedSlices() {
    for (int i = 0; i < GhostwingDroneSheet.FRAME_COUNT; i++) {
      assertEquals(GhostwingDroneSheet.frameSlices().get(i), GhostwingDroneSheet.frameSlice(i));
    }
  }
}