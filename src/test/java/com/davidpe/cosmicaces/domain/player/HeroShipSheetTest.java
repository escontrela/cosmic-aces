package com.davidpe.cosmicaces.domain.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class HeroShipSheetTest {

  private static List<HeroShipSheet.Sheet> allSheets() {
    return java.util.stream.Stream.concat(HeroShipSheet.sheets().stream(),
        HeroShipSheet.firingSheets().stream()).toList();
  }

  @Test
  void everyFiringPoseIncludesVisibleMuzzleFlashes() throws IOException {
    for (HeroShipSheet.Sheet sheet : HeroShipSheet.firingSheets()) {
      BufferedImage image = ImageIO.read(getClass().getResource("/" + sheet.internalPath()));
      for (HeroShipSheet.Slice slice : sheet.slices()) {
        boolean hasFlash = false;
        for (int y = slice.y(); y < slice.y() + slice.height() / 2 && !hasFlash; y++) {
          for (int x = slice.x(); x < slice.x() + slice.width(); x++) {
            int pixel = image.getRGB(x, y);
            if ((pixel >>> 24) > 128 && ((pixel >> 16) & 255) > 200
                && ((pixel >> 8) & 255) > 130 && (pixel & 255) < 100) {
              hasFlash = true;
              break;
            }
          }
        }
        assertTrue(hasFlash, "firing pose must include the warm muzzle flash: " + slice);
      }
    }
  }

  @Test
  void allSheetsExposeFivePosesInSheetOrder() {
    for (HeroShipSheet.Sheet sheet : allSheets()) {
      List<HeroShipSheet.Slice> slices = sheet.slices();
      assertEquals(5, slices.size());
      assertEquals(List.of(
          HeroShipSheet.Pose.RIGHT,
          HeroShipSheet.Pose.NEUTRAL,
          HeroShipSheet.Pose.LEFT,
          HeroShipSheet.Pose.YAW_RIGHT,
          HeroShipSheet.Pose.YAW_LEFT),
          List.of(HeroShipSheet.Pose.values()));
      for (int i = 1; i < slices.size(); i++) {
        assertTrue(slices.get(i).x() > slices.get(i - 1).x(),
            "pose " + i + " must start to the right of pose " + (i - 1));
      }
    }
  }

  @Test
  void everySliceLiesInsideItsOwnSheet() {
    for (HeroShipSheet.Sheet sheet : allSheets()) {
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
  void neutralPoseIsTheWidestInAllSheets() {
    for (HeroShipSheet.Sheet sheet : allSheets()) {
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
    for (HeroShipSheet.Sheet sheet : allSheets()) {
      for (HeroShipSheet.Pose pose : HeroShipSheet.Pose.values()) {
        assertEquals(sheet.slices().get(pose.ordinal()), sheet.slice(pose));
      }
    }
  }

  @Test
  void allBundledSheetsHaveTheExpectedTransparentFivePoseLayout() throws IOException {
    for (HeroShipSheet.Sheet sheet : allSheets()) {
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
  void everyPoseExposesTwoDistinctLateralCannonMouths() {
    for (HeroShipSheet.Pose pose : HeroShipSheet.Pose.values()) {
      List<HeroShipSheet.CannonMouth> mouths = HeroShipSheet.cannonMouths(pose);
      assertEquals(2, mouths.size(), "Astra has two cannons: " + pose);
      HeroShipSheet.CannonMouth left = mouths.get(0);
      HeroShipSheet.CannonMouth right = mouths.get(1);
      assertTrue(left.lateral() < 0f, "left mouth must sit left of center: " + pose);
      assertTrue(right.lateral() > 0f, "right mouth must sit right of center: " + pose);
      assertNotEquals(left.lateral(), right.lateral());
      for (HeroShipSheet.CannonMouth mouth : mouths) {
        assertTrue(Float.isFinite(mouth.lateral()) && Float.isFinite(mouth.forward()),
            "mouth offsets must be finite: " + pose);
      }
    }
  }

  /**
   * The firing placement metadata must keep the cool body hull of each firing pose anchored on the
   * flight pose of the same speed family. The expected offset is re-measured from the bundled PNGs
   * with the same warm-flash color rule, so a slice edit or a stale constant is caught.
   */
  @Test
  void firingPlacementKeepsTheBodyHullAnchoredOnItsFlightVariant() throws IOException {
    List<HeroShipSheet.Sheet> flight = HeroShipSheet.sheets();
    List<HeroShipSheet.Sheet> firing = HeroShipSheet.firingSheets();
    for (int family = 0; family < flight.size(); family++) {
      boolean accelerating = family == 1;
      BufferedImage flightImage = ImageIO.read(
          getClass().getResource("/" + flight.get(family).internalPath()));
      BufferedImage firingImage = ImageIO.read(
          getClass().getResource("/" + firing.get(family).internalPath()));
      for (HeroShipSheet.Pose pose : HeroShipSheet.Pose.values()) {
        HeroShipSheet.Slice flightSlice = flight.get(family).slice(pose);
        HeroShipSheet.Slice firingSlice = firing.get(family).slice(pose);
        double[] flightHull = coolHullCenter(flightImage, flightSlice.x(), flightSlice.y(),
            flightSlice.width(), flightSlice.height());
        double[] firingHull = coolHullCenter(firingImage, firingSlice.x(), firingSlice.y(),
            firingSlice.width(), firingSlice.height());
        double expectedX = (flightHull[0] - flightSlice.width() / 2d)
            - (firingHull[0] - firingSlice.width() / 2d);
        double expectedY = (flightSlice.height() / 2d - flightHull[1])
            - (firingSlice.height() / 2d - firingHull[1]);
        HeroShipSheet.FiringPlacement placement =
            HeroShipSheet.firingPlacement(pose, accelerating);
        assertEquals(expectedX, placement.offsetXPx(), 1f, "deltaX of " + pose);
        assertEquals(expectedY, placement.offsetYPx(), 1f, "deltaY of " + pose);
      }
    }
  }

  /** Warm muzzle flash / engine flame pixel shared with the anchor measurement. */
  private static boolean warm(int pixel) {
    int r = (pixel >> 16) & 255;
    int g = (pixel >> 8) & 255;
    int b = pixel & 255;
    return r > 180 && r > b + 60 && g > b + 20;
  }

  /** Center of the cool opaque hull (alpha > 32, not warm) of a slice, in slice-local pixels. */
  private static double[] coolHullCenter(BufferedImage image, int sliceX, int sliceY,
      int width, int height) {
    int x0 = Integer.MAX_VALUE;
    int y0 = Integer.MAX_VALUE;
    int x1 = -1;
    int y1 = -1;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int pixel = image.getRGB(sliceX + x, sliceY + y);
        if ((pixel >>> 24) <= 32 || warm(pixel)) {
          continue;
        }
        x0 = Math.min(x0, x);
        y0 = Math.min(y0, y);
        x1 = Math.max(x1, x);
        y1 = Math.max(y1, y);
      }
    }
    assertTrue(x0 <= x1, "the slice must contain a cool opaque hull");
    return new double[] {(x0 + x1) / 2d, (y0 + y1) / 2d};
  }

  @Test
  void rejectsSlicesOutsideTheSheet() {
    assertThrows(IllegalArgumentException.class,
        () -> new HeroShipSheet.Sheet("assets/images/player/bad.png", 100, 100,
            List.of(new HeroShipSheet.Slice(-1, 0, 10, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10))));
    assertThrows(IllegalArgumentException.class,
        () -> new HeroShipSheet.Sheet("assets/images/player/bad.png", 100, 100,
            List.of(new HeroShipSheet.Slice(0, 0, 0, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10))));
    assertThrows(IllegalArgumentException.class,
        () -> new HeroShipSheet.Sheet("assets/images/player/bad.png", 100, 100,
            List.of(new HeroShipSheet.Slice(0, 0, 10, 10),
                new HeroShipSheet.Slice(0, 0, 10, 10))));
  }
}
