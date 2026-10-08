package com.davidpe.cosmicaces.domain.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ShipExplosionSheetTest {

  @Test
  void bundledSheetMatchesTheMeasuredMetadata() throws IOException {
    var resource = getClass().getResource("/" + ShipExplosionSheet.internalPath());
    assertNotNull(resource, "the explosion sheet must be bundled with the game");
    BufferedImage image = ImageIO.read(resource);
    assertNotNull(image, "the explosion sheet must be a readable PNG");

    assertEquals(ShipExplosionSheet.WIDTH, image.getWidth());
    assertEquals(ShipExplosionSheet.HEIGHT, image.getHeight());
    assertTrue(image.getColorModel().hasAlpha());
  }

  @Test
  void thereAreEightFramesInsideTheSheetInPlaybackOrder() {
    List<ShipExplosionSheet.Slice> slices = ShipExplosionSheet.frameSlices();
    assertEquals(ShipExplosionSheet.FRAME_COUNT, slices.size());
    assertEquals(8, slices.size());

    for (int i = 0; i < slices.size(); i++) {
      ShipExplosionSheet.Slice slice = slices.get(i);
      assertEquals(slice, ShipExplosionSheet.frameSlice(i));
      assertTrue(slice.x() >= 0 && slice.y() >= 0 && slice.width() > 0 && slice.height() > 0);
      assertTrue(slice.x() + slice.width() <= ShipExplosionSheet.WIDTH, "width of frame " + i);
      assertTrue(slice.y() + slice.height() <= ShipExplosionSheet.HEIGHT, "height of frame " + i);
    }
  }

  @Test
  void framesAreLaidOutLeftToRightAcrossTheTopRowThenTheBottomRow() {
    List<ShipExplosionSheet.Slice> slices = ShipExplosionSheet.frameSlices();
    int half = ShipExplosionSheet.HEIGHT / 2;
    for (int i = 1; i < 4; i++) {
      assertTrue(slices.get(i).x() > slices.get(i - 1).x(), "top row must advance in x");
      assertTrue(slices.get(i).y() < half, "frame " + i + " belongs to the top row");
    }
    assertTrue(slices.get(0).y() < half, "frame 0 belongs to the top row");
    for (int i = 5; i < slices.size(); i++) {
      assertTrue(slices.get(i).x() > slices.get(i - 1).x(), "bottom row must advance in x");
      assertTrue(slices.get(i).y() >= half, "frame " + i + " belongs to the bottom row");
    }
    assertTrue(slices.get(4).y() >= half, "frame 4 starts the bottom row");
  }

  @Test
  void referenceSizeIsTheLargestFrameDimensionAndAnchorsAreCentered() {
    int max = 0;
    for (ShipExplosionSheet.Slice slice : ShipExplosionSheet.frameSlices()) {
      max = Math.max(max, Math.max(slice.width(), slice.height()));
      assertEquals(slice.width() / 2f, slice.anchorX(), 0.0001f);
      assertEquals(slice.height() / 2f, slice.anchorY(), 0.0001f);
    }
    assertEquals(ShipExplosionSheet.REFERENCE_SIZE, max);
  }

  @Test
  void everyMeasuredFrameContainsVisiblePixels() throws IOException {
    BufferedImage image = ImageIO.read(
        getClass().getResource("/" + ShipExplosionSheet.internalPath()));
    for (int index = 0; index < ShipExplosionSheet.FRAME_COUNT; index++) {
      ShipExplosionSheet.Slice slice = ShipExplosionSheet.frameSlice(index);
      boolean hasPixels = false;
      for (int y = slice.y(); y < slice.y() + slice.height() && !hasPixels; y++) {
        for (int x = slice.x(); x < slice.x() + slice.width(); x++) {
          if ((image.getRGB(x, y) >>> 24) > 8) {
            hasPixels = true;
            break;
          }
        }
      }
      assertTrue(hasPixels, "frame " + index + " must contain visible pixels: " + slice);
    }
  }
}
