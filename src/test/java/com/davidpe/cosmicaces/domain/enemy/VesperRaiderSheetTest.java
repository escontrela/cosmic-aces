package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class VesperRaiderSheetTest {

  @Test
  void firingSheetHasFiveOrderedSlicesWithMuzzleFlashes() throws IOException {
    BufferedImage image = ImageIO.read(
        getClass().getResource("/" + VesperRaiderSheet.firingInternalPath()));
    assertNotNull(image);
    assertEquals(VesperRaiderSheet.FIRING_WIDTH, image.getWidth());
    assertEquals(VesperRaiderSheet.FIRING_HEIGHT, image.getHeight());
    assertEquals(5, VesperRaiderSheet.firingSlices().size());
    int previousEnd = 0;
    for (VesperRaiderSheet.Pose pose : VesperRaiderSheet.Pose.values()) {
      VesperRaiderSheet.Slice slice = VesperRaiderSheet.firingSlice(pose);
      assertEquals(VesperRaiderSheet.firingSlices().get(pose.ordinal()), slice);
      assertTrue(slice.x() >= previousEnd);
      assertTrue(slice.y() >= 0 && slice.x() + slice.width() <= image.getWidth()
          && slice.y() + slice.height() <= image.getHeight());
      previousEnd = slice.x() + slice.width();
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
      assertTrue(hasFlash, "firing slice must include a muzzle flash: " + pose);
    }
  }

  @Test
  void exposesFivePosesInSheetOrder() {
    List<VesperRaiderSheet.Slice> slices = VesperRaiderSheet.slices();
    assertEquals(5, slices.size());
    assertEquals(
        List.of(
            VesperRaiderSheet.Pose.LEFT,
            VesperRaiderSheet.Pose.NEUTRAL,
            VesperRaiderSheet.Pose.RIGHT,
            VesperRaiderSheet.Pose.YAW_RIGHT,
            VesperRaiderSheet.Pose.YAW_LEFT),
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
  void mapsTurnDirectionToTheMatchingDescentOrientedBankPose() {
    assertEquals(VesperRaiderSheet.Pose.RIGHT, VesperRaiderSheet.poseForBank(-1));
    assertEquals(VesperRaiderSheet.Pose.NEUTRAL, VesperRaiderSheet.poseForBank(0));
    assertEquals(VesperRaiderSheet.Pose.LEFT, VesperRaiderSheet.poseForBank(1));
  }

  @Test
  void bundledSheetHasTheExpectedTransparentFivePoseLayout() throws IOException {
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

  /**
   * The firing placement metadata must keep the cool body hull of each firing pose anchored on the
   * flight pose, re-measured from the bundled PNGs with the same warm-flash color rule.
   */
  @Test
  void firingPlacementKeepsTheBodyHullAnchoredOnTheFlightVariant() throws IOException {
    BufferedImage flightImage = ImageIO.read(
        getClass().getResource("/" + VesperRaiderSheet.internalPath()));
    BufferedImage firingImage = ImageIO.read(
        getClass().getResource("/" + VesperRaiderSheet.firingInternalPath()));
    for (VesperRaiderSheet.Pose pose : VesperRaiderSheet.Pose.values()) {
      VesperRaiderSheet.Slice flightSlice = VesperRaiderSheet.slice(pose);
      VesperRaiderSheet.Slice firingSlice = VesperRaiderSheet.firingSlice(pose);
      double[] flightHull = coolHullCenter(flightImage, flightSlice.x(), flightSlice.y(),
          flightSlice.width(), flightSlice.height());
      double[] firingHull = coolHullCenter(firingImage, firingSlice.x(), firingSlice.y(),
          firingSlice.width(), firingSlice.height());
      double expectedX = (flightHull[0] - flightSlice.width() / 2d)
          - (firingHull[0] - firingSlice.width() / 2d);
      double expectedY = (flightSlice.height() / 2d - flightHull[1])
          - (firingSlice.height() / 2d - firingHull[1]);
      VesperRaiderSheet.FiringPlacement placement = VesperRaiderSheet.firingPlacement(pose);
      assertEquals(expectedX, placement.offsetXPx(), 1f, "deltaX of " + pose);
      assertEquals(expectedY, placement.offsetYPx(), 1f, "deltaY of " + pose);
    }
  }

  /** Warm muzzle flash pixel shared with the anchor measurement. */
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
  void lastPoseLeavesTheTransparentRightMargin() {
    VesperRaiderSheet.Slice last = VesperRaiderSheet.slice(VesperRaiderSheet.Pose.YAW_LEFT);
    assertTrue(last.x() + last.width() < VesperRaiderSheet.WIDTH,
        "the rightmost pose must leave the sheet's transparent margin");
  }

  @Test
  void orientedForDescentMirrorsVerticallyOnceAndKeepsBankAxis() throws Exception {
    TextureRegion region = regionWith(0.25f, 0.75f, 0.5f, 1.0f);
    assertFalse(region.isFlipY(), "the fresh region must not be flipped yet");

    TextureRegion oriented = VesperRaiderSheet.orientedForDescent(region);

    assertSame(region, oriented, "orientation must reuse the given region");
    assertEquals(1.0f, region.getV(), 0f, "vertical mirror must swap v and v2");
    assertEquals(0.75f, region.getV2(), 0f, "vertical mirror must swap v and v2");
    assertEquals(0.25f, region.getU(), 0f, "horizontal axis must stay unchanged");
    assertEquals(0.5f, region.getU2(), 0f, "horizontal axis must stay unchanged");
    assertTrue(region.isFlipY(), "region must be reported as vertically flipped");
    assertFalse(region.isFlipX(), "region must not be horizontally flipped");
  }

  @Test
  void orientedForDescentNeverFlipsTwice() throws Exception {
    TextureRegion region = regionWith(0.1f, 0.2f, 0.9f, 0.8f);

    VesperRaiderSheet.orientedForDescent(region);
    VesperRaiderSheet.orientedForDescent(region);

    assertEquals(0.8f, region.getV(), 0f, "v must keep the flipped value after a second call");
    assertEquals(0.2f, region.getV2(), 0f, "v2 must keep the flipped value after a second call");
    assertTrue(region.isFlipY(), "region must stay vertically flipped");
  }

  /** Builds a texture-less region with the given UVs, so the flip logic is testable without GL. */
  private static TextureRegion regionWith(float u, float v, float u2, float v2) throws Exception {
    TextureRegion region = new TextureRegion();
    setUv(region, "u", u);
    setUv(region, "v", v);
    setUv(region, "u2", u2);
    setUv(region, "v2", v2);
    return region;
  }

  private static void setUv(TextureRegion region, String field, float value) throws Exception {
    Field f = TextureRegion.class.getDeclaredField(field);
    f.setAccessible(true);
    f.setFloat(region, value);
  }
}
