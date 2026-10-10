package com.davidpe.cosmicaces.domain.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShipExplosionTest {

  @Test
  void playsEveryFrameOnceAndFinishesWithoutLooping() {
    ShipExplosion explosion = new ShipExplosion(10f, 20f);

    for (int frame = 0; frame < ShipExplosionSheet.FRAME_COUNT; frame++) {
      assertEquals(frame, explosion.frameIndex(), "frame " + frame + " before its time slot");
      assertFalse(explosion.isFinished());
      explosion.advance(ShipExplosion.SECONDS_PER_FRAME);
    }

    assertTrue(explosion.isFinished(), "the animation ends at 0.8 s");
    assertEquals(ShipExplosionSheet.FRAME_COUNT - 1, explosion.frameIndex());

    explosion.advance(10f);
    assertTrue(explosion.isFinished(), "a finished animation never restarts");
    assertEquals(ShipExplosionSheet.FRAME_COUNT - 1, explosion.frameIndex());
  }

  @Test
  void invalidDeltasDoNotAdvanceTheClock() {
    ShipExplosion explosion = new ShipExplosion(0f, 0f);
    explosion.advance(0f);
    explosion.advance(-1f);
    explosion.advance(Float.NaN);
    explosion.advance(Float.POSITIVE_INFINITY);

    assertEquals(0, explosion.frameIndex());
    assertEquals(0f, explosion.elapsedSeconds(), 0.0001f);
    assertFalse(explosion.isFinished());
  }

  @Test
  void twoInstancesAdvanceIndependently() {
    ShipExplosion first = new ShipExplosion(1f, 2f);
    ShipExplosion second = new ShipExplosion(3f, 4f);
    assertNotSame(first, second);

    first.advance(0.45f);
    second.advance(0.05f);

    assertEquals(4, first.frameIndex());
    assertEquals(0, second.frameIndex());
    assertFalse(first.isFinished());
    assertFalse(second.isFinished());
    assertEquals(1f, first.centerX(), 0.0001f);
    assertEquals(2f, first.centerY(), 0.0001f);
    assertEquals(3f, second.centerX(), 0.0001f);
    assertEquals(4f, second.centerY(), 0.0001f);

    second.advance(0.75f);
    first.advance(0.35f);
    assertTrue(first.isFinished());
    assertTrue(second.isFinished());
  }

  @Test
  void rejectsNonFiniteCentre() {
    assertThrows(IllegalArgumentException.class, () -> new ShipExplosion(Float.NaN, 0f));
    assertThrows(IllegalArgumentException.class,
        () -> new ShipExplosion(0f, Float.POSITIVE_INFINITY));
  }
}
