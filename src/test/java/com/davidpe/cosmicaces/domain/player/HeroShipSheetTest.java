package com.davidpe.cosmicaces.domain.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class HeroShipSheetTest {

  @Test
  void bothSheetsExposeThreePosesInSheetOrder() {
    for (HeroShipSheet.Sheet sheet : HeroShipSheet.sheets()) {
      List<HeroShipSheet.Slice> slices = sheet.slices();
      assertEquals(3, slices.size());
      assertEquals(List.of(
          HeroShipSheet.Pose.RIGHT,
          HeroShipSheet.Pose.NEUTRAL,
          HeroShipSheet.Pose.LEFT),
          List.of(HeroShipSheet.Pose.values()));
      for (int i = 1; i < slices.size(); i++) {
        assertTrue(slices.get(i).x() > slices.get(i - 1).x(),
            "pose " + i + " must start to the right of pose " + (i - 1));
      }
    }
  }

  @Test
  void everySliceLiesInsideItsOwnSheet() {
    for (HeroShipSheet.Sheet sheet : HeroShipSheet.sheets()) {
      for (HeroShipSheet.Pose pose : HeroShipSheet.Pose.values()) {
        HeroShipSheet.Slice slice = sheet.slice(pose);
        assertTrue(slice.x() >= 0 && slice.y() >= 0);
        assertTrue(slice.x() + slice.width() <= sheet.width(),
            "slice of " + pose + " exceeds width " + sheet.width() + ": " + slice);
        assertTrue(slice.y() + slice.height() <= sheet.height(),
            "slice of " + pose + " exceeds height " + sheet.height() + ": " + slice);
      }
    }
  }

  @Test
  void neutralPoseIsTheWidestInBothSheets() {
    for (HeroShipSheet.Sheet sheet : HeroShipSheet.sheets()) {
      HeroShipSheet.Slice neutral = sheet.slice(HeroShipSheet.Pose.NEUTRAL);
      for (HeroShipSheet.Pose pose : HeroShipSheet.Pose.values()) {
        if (pose != HeroShipSheet.Pose.NEUTRAL) {
          assertTrue(neutral.width() > sheet.slice(pose).width(),
              "neutral must be wider than " + pose);
        }
      }
    }
  }

  @Test
  void sliceAndPoseAreConsistentByOrdinal() {
    for (HeroShipSheet.Sheet sheet : HeroShipSheet.sheets()) {
      for (HeroShipSheet.Pose pose : HeroShipSheet.Pose.values()) {
        assertEquals(sheet.slices().get(pose.ordinal()), sheet.slice(pose));
      }
    }
  }

  @Test
  void bothBundledSheetsHaveTheExpectedTransparentThreePoseLayout() throws IOException {
    for (HeroShipSheet.Sheet sheet : HeroShipSheet.sheets()) {
      var resource = getClass().getResource("/" + sheet.internalPath());
      assertNotNull(resource, "sheet must be bundled with the game: " + sheet.internalPath());
      BufferedImage image = ImageIO.read(resource);
      assertNotNull(image, "sheet must be a readable PNG: " + sheet.internalPath());
      assertEquals(sheet.width(), image.getWidth(),
          "measured width of " + sheet.internalPath());
      assertEquals(sheet.height(), image.getHeight(),
          "measured height of " + sheet.internalPath());
      assertTrue(image.getColorModel().hasAlpha());
      assertEquals(0, image.getRGB(0, 0) >>> 24, "background must be transparent");

      for (HeroShipSheet.Slice slice : sheet.slices()) {
        boolean containsShip = false;
        for (int y = slice.y(); y < slice.y() + slice.height() && !containsShip; y++) {
          for (int x = slice.x(); x < slice.x() + slice.width(); x++) {
            if ((image.getRGB(x, y) >>> 24) != 0) {
              containsShip = true;
              break;
            }
          }
        }
        assertTrue(containsShip, "every pose slice must contain part of the ship");
      }
    }
  }

  @Test
  void rejectsSlicesOutsideTheSheet() {
    assertThrows(IllegalArgumentException.class,
        () -> new HeroShipSheet.Sheet("assets/images/player/bad.png", 100, 100,
            List.of(new HeroShipSheet.Slice(-1, 0, 10, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10))));
    assertThrows(IllegalArgumentException.class,
        () -> new HeroShipSheet.Sheet("assets/images/player/bad.png", 100, 100,
            List.of(new HeroShipSheet.Slice(0, 0, 0, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10))));
    assertThrows(IllegalArgumentException.class,
        () -> new HeroShipSheet.Sheet("assets/images/player/bad.png", 100, 100,
            List.of(new HeroShipSheet.Slice(0, 0, 10, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10))));
  }
}
