package com.davidpe.cosmicaces.domain.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.CombustionStage;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Faction;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Heading;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class GuidedMissileSheetTest {
  @Test
  void bundledPngContainsAll64DistinctFramesAndMeasuredBodyAnchors() throws IOException {
    var resource = getClass().getResource("/" + GuidedMissileSheet.internalPath());
    assertNotNull(resource);
    BufferedImage image = ImageIO.read(resource);
    assertNotNull(image);
    assertEquals(1280, image.getWidth());
    assertEquals(1280, image.getHeight());
    assertTrue(image.getColorModel().hasAlpha());
    int frameCount = 0;
    for (Faction faction : Faction.values()) {
      for (Heading heading : Heading.values()) {
        for (CombustionStage stage : CombustionStage.values()) {
          var slice = GuidedMissileSheet.slice(faction, heading, stage);
          var anchor = GuidedMissileSheet.bodyAnchor(faction, heading, stage);
          assertEquals(heading.ordinal() * 160, slice.x());
          assertTrue(slice.x() >= 0 && slice.y() >= 0);
          assertTrue(slice.x() + slice.width() <= image.getWidth());
          assertTrue(slice.y() + slice.height() <= image.getHeight());
          assertTrue(anchor.x() > 0 && anchor.x() < slice.width());
          assertTrue(anchor.y() > 0 && anchor.y() < slice.height());

          // Inspect muted, opaque body pixels near the grid center; the bright exhaust is excluded.
          // This checks that metadata follows the actual body rather than merely the cell center.
          int row = faction.ordinal() * 4 + stage.ordinal();
          double totalX = 0, totalY = 0;
          int bodyPixels = 0;
          for (int y = 35; y < 125; y++) {
            for (int x = 35; x < 125; x++) {
              int pixel = image.getRGB(heading.ordinal() * 160 + x, row * 160 + y);
              int a = pixel >>> 24, r = (pixel >>> 16) & 255;
              int g = (pixel >>> 8) & 255, b = pixel & 255;
              int max = Math.max(r, Math.max(g, b));
              int min = Math.min(r, Math.min(g, b));
              if (a > 180 && max > 35 && max < 175 && max - min < 35) {
                totalX += heading.ordinal() * 160 + x - slice.x();
                totalY += slice.height() - (row * 160 + y - slice.y());
                bodyPixels++;
              }
            }
          }
          String frame = faction + "/" + heading + "/" + stage;
          assertTrue(bodyPixels > 200, "missile body missing: " + frame);
          assertEquals(totalX / bodyPixels, anchor.x(), 8, "body pivot X: " + frame);
          assertEquals(totalY / bodyPixels, anchor.y(), 8, "body pivot Y: " + frame);
          frameCount++;
        }
      }
    }
    assertEquals(64, frameCount);
  }

  @Test
  void firstExhaustIsKeptOutOfTheFollowingFrame() throws IOException {
    BufferedImage image = ImageIO.read(getClass().getResource("/" + GuidedMissileSheet.internalPath()));
    var ignition = GuidedMissileSheet.slice(Faction.ASTRA, Heading.NORTH, CombustionStage.IGNITION);
    var burning = GuidedMissileSheet.slice(Faction.ASTRA, Heading.NORTH, CombustionStage.BURNING);
    assertEquals(166, ignition.height());
    assertEquals(166, burning.y());
    assertEquals(320, burning.y() + burning.height());
    assertTrue((image.getRGB(89, 160) >>> 24) > 128, "exhaust crosses the grid boundary");
    for (int x = 0; x < burning.width(); x++) {
      assertTrue((image.getRGB(x, burning.y()) >>> 24) <= 8,
          "previous exhaust must not contaminate the next frame");
    }
  }

  @Test
  void rejectsMissingSelectorsAndTexture() {
    assertThrows(IllegalArgumentException.class,
        () -> GuidedMissileSheet.slice(null, Heading.NORTH, CombustionStage.IGNITION));
    assertThrows(IllegalArgumentException.class,
        () -> GuidedMissileSheet.bodyAnchor(Faction.ASTRA, null, CombustionStage.IGNITION));
    assertThrows(IllegalArgumentException.class,
        () -> GuidedMissileSheet.region(null, Faction.ASTRA, Heading.NORTH, CombustionStage.IGNITION));
  }
}
