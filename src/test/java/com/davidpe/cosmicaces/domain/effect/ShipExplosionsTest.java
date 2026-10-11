package com.davidpe.cosmicaces.domain.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Lifecycle of the simultaneous-explosion pool used for the Ghostwing drone deaths: several
 * animations coexist, advance together and are dropped exactly when each one finishes, so the
 * presentation never keeps a destroyed drone or a stale explosion alive.
 */
class ShipExplosionsTest {

  @Test
  void spawnKeepsEveryConcurrentAnimationWithItsFrozenCentreAndPeak() {
    ShipExplosions explosions = new ShipExplosions();
    explosions.spawn(10f, 20f, 130f);
    explosions.spawn(30f, 40f, 131f);

    assertEquals(2, explosions.size());
    assertEquals(2, explosions.active().size());
    ShipExplosions.Entry first = explosions.active().get(0);
    assertEquals(10f, first.animation().centerX(), 0.0001f);
    assertEquals(20f, first.animation().centerY(), 0.0001f);
    assertEquals(130f, first.peakSize(), 0.0001f);
    assertEquals(30f, explosions.active().get(1).animation().centerX(), 0.0001f);
  }

  @Test
  void advanceKeepsRunningAnimationsAndDropsTheFinishedOnes() {
    ShipExplosions explosions = new ShipExplosions();
    explosions.spawn(0f, 0f, 130f);
    explosions.spawn(0f, 0f, 130f);

    explosions.advance(ShipExplosion.DURATION_SECONDS / 2f);
    assertEquals(2, explosions.size(), "both animations are still running at half duration");

    explosions.advance(ShipExplosion.DURATION_SECONDS);
    assertTrue(explosions.active().isEmpty(), "finished animations are removed");
    assertEquals(0, explosions.size());
  }

  @Test
  void invalidDeltasNeverAdvanceOrRemoveAnActiveAnimation() {
    ShipExplosions explosions = new ShipExplosions();
    explosions.spawn(1f, 2f, 130f);

    explosions.advance(0f);
    explosions.advance(-1f);
    explosions.advance(Float.NaN);
    explosions.advance(Float.POSITIVE_INFINITY);

    assertEquals(1, explosions.size());
    assertEquals(0f, explosions.active().get(0).animation().elapsedSeconds(), 0.0001f);
  }

  @Test
  void rejectsNonPositiveOrNonFinitePeakSize() {
    ShipExplosions explosions = new ShipExplosions();
    assertThrows(IllegalArgumentException.class, () -> explosions.spawn(0f, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> explosions.spawn(0f, 0f, -1f));
    assertThrows(IllegalArgumentException.class, () -> explosions.spawn(0f, 0f, Float.NaN));
  }
}
