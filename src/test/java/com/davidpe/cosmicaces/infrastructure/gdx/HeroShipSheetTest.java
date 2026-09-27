package com.davidpe.cosmicaces.infrastructure.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HeroShipSheetTest {

  @Test
  void exposesFivePosesInLeftToRightOrder() {
    List<HeroShipSheet.Slice> slices = HeroShipSheet.slices();
    assertEquals(5, slices.size());
    assertEquals(List.of(
        HeroShipSheet.Pose.LEFT_A,
        HeroShipSheet.Pose.LEFT_B,
        HeroShipSheet.Pose.NEUTRAL,
        HeroShipSheet.Pose.RIGHT_A,
        HeroShipSheet.Pose.RIGHT_B),
        List.of(HeroShipSheet.Pose.values()));
    for (int i = 1; i < slices.size(); i++) {
      assertTrue(slices.get(i).x() > slices.get(i - 1).x(),
          "pose " + i + " must start to the right of pose " + (i - 1));
    }
  }

  @Test
  void everySliceLiesInsideTheSheet() {
    for (HeroShipSheet.Pose pose : HeroShipSheet.Pose.values()) {
      HeroShipSheet.Slice slice = HeroShipSheet.slice(pose);
      assertTrue(slice.x() >= 0 && slice.y() >= 0);
      assertTrue(slice.x() + slice.width() <= HeroShipSheet.SHEET_WIDTH);
      assertTrue(slice.y() + slice.height() <= HeroShipSheet.SHEET_HEIGHT);
    }
  }

  @Test
  void neutralPoseIsTheWidest() {
    HeroShipSheet.Slice neutral = HeroShipSheet.slice(HeroShipSheet.Pose.NEUTRAL);
    for (HeroShipSheet.Pose pose : HeroShipSheet.Pose.values()) {
      if (pose != HeroShipSheet.Pose.NEUTRAL) {
        assertTrue(neutral.width() > HeroShipSheet.slice(pose).width(),
            "neutral must be wider than " + pose);
      }
    }
  }

  @Test
  void sliceAndPoseAreConsistentByOrdinal() {
    for (HeroShipSheet.Pose pose : HeroShipSheet.Pose.values()) {
      assertEquals(HeroShipSheet.slices().get(pose.ordinal()), HeroShipSheet.slice(pose));
    }
  }

  @Test
  void rejectsSlicesOutsideTheSheet() {
    assertThrows(IllegalArgumentException.class, () -> new HeroShipSheet.Slice(-1, 0, 10, 10));
    assertThrows(IllegalArgumentException.class, () -> new HeroShipSheet.Slice(0, 0, 0, 10));
    assertThrows(IllegalArgumentException.class,
        () -> new HeroShipSheet.Slice(HeroShipSheet.SHEET_WIDTH - 5, 0, 10, 10));
  }
}