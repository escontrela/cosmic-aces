package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class Geran4UavSheetTest {

  @Test
  void bundledSheetHasThreeSquareFramesWithTheUav() throws IOException {
    var resource = getClass().getResource("/" + Geran4UavSheet.internalPath());
    assertNotNull(resource, "sheet must be bundled with the game: " + Geran4UavSheet.internalPath());
    BufferedImage image = ImageIO.read(resource);
    assertNotNull(image, "sheet must be a readable PNG: " + Geran4UavSheet.internalPath());
    assertEquals(Geran4UavSheet.WIDTH, image.getWidth(),
        "measured width of " + Geran4UavSheet.internalPath());
    assertEquals(Geran4UavSheet.HEIGHT, image.getHeight(),
        "measured height of " + Geran4UavSheet.internalPath());
    assertTrue(image.getColorModel().hasAlpha());
    assertEquals(Geran4UavSheet.FRAME_COUNT, Geran4UavSheet.frameSlices().size());

    for (Geran4UavSheet.Slice slice : Geran4UavSheet.frameSlices()) {
      assertEquals(Geran4UavSheet.FRAME_SIZE, slice.width(),
          "every frame cell must be square: " + slice);
      assertEquals(Geran4UavSheet.FRAME_SIZE, slice.height(),
          "every frame cell must be square: " + slice);
      assertTrue(slice.x() >= 0 && slice.y() >= 0);
      assertTrue(slice.x() + slice.width() <= Geran4UavSheet.WIDTH,
          "frame exceeds sheet width: " + slice);
      assertTrue(slice.y() + slice.height() <= Geran4UavSheet.HEIGHT,
          "frame exceeds sheet height: " + slice);
      boolean containsUav = false;
      for (int y = slice.y(); y < slice.y() + slice.height() && !containsUav; y++) {
        for (int x = slice.x(); x < slice.x() + slice.width(); x++) {
          if ((image.getRGB(x, y) >>> 24) != 0) {
            containsUav = true;
            break;
          }
        }
      }
      assertTrue(containsUav, "every propeller frame must contain the UAV: " + slice);
    }
  }

  @Test
  void framesAreContiguousSquareSlicesStartingAtEveryCell() {
    List<Geran4UavSheet.Slice> slices = Geran4UavSheet.frameSlices();
    for (int i = 0; i < slices.size(); i++) {
      assertEquals(i * Geran4UavSheet.FRAME_SIZE, slices.get(i).x());
      assertEquals(0, slices.get(i).y());
    }
    for (int i = 1; i < slices.size(); i++) {
      assertEquals(slices.get(i - 1).x() + slices.get(i - 1).width(), slices.get(i).x(),
          "consecutive frames must touch without gaps or overlaps");
    }
  }
}
