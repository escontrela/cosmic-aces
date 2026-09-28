package com.davidpe.cosmicaces.infrastructure.gdx;

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
  void mapsTurnDirectionToTheMatchingDescentOrientedBankPose() {
    assertEquals(VesperRaiderSheet.Pose.RIGHT, VesperRaiderSheet.poseForBank(-1));
    assertEquals(VesperRaiderSheet.Pose.NEUTRAL, VesperRaiderSheet.poseForBank(0));
    assertEquals(VesperRaiderSheet.Pose.LEFT, VesperRaiderSheet.poseForBank(1));
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
