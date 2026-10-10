package com.davidpe.cosmicaces.domain.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

/**
 * Time model of a ship hit-flash pulse: 100 ms full duration, linear decay, renewal without
 * accumulation, immediate clear and independent instances. The graphical interpretation belongs to
 * the screen's renderer; this class only owns the presentation timing.
 */
class ShipHitFlashTest {

  @Test
  void pulseStartsOnlyOnTriggerAndDecaysLinearlyToZero() {
    ShipHitFlash flash = new ShipHitFlash();

    assertEquals(0f, flash.intensity(), 0.0001f, "before any hit there is no flash");
    flash.advance(1f);
    assertEquals(0f, flash.intensity(), 0.0001f, "advance alone cannot create a pulse");

    flash.trigger();
    assertEquals(1f, flash.intensity(), 0.0001f, "a trigger starts at full intensity");

    flash.advance(0.05f);
    assertEquals(0.5f, flash.intensity(), 0.001f, "half the duration leaves half the intensity");

    flash.advance(0.05f);
    assertEquals(0f, flash.intensity(), 0.001f, "the pulse expires exactly at 100 ms");

    flash.advance(10f);
    assertEquals(0f, flash.intensity(), 0.001f, "an expired pulse never goes negative");
  }

  @Test
  void consecutiveTriggersRenewWithoutAccumulating() {
    ShipHitFlash flash = new ShipHitFlash();

    flash.trigger();
    flash.advance(0.08f);
    assertEquals(0.2f, flash.intensity(), 0.001f);

    flash.trigger();
    assertEquals(1f, flash.intensity(), 0.001f, "a new impact renews the full pulse");

    flash.advance(0.1f);
    assertEquals(0f, flash.intensity(), 0.001f, "renewed pulses never outlive 100 ms");
  }

  @Test
  void invalidDeltasDoNotAdvanceThePulse() {
    ShipHitFlash flash = new ShipHitFlash();
    flash.trigger();

    flash.advance(0f);
    flash.advance(-1f);
    flash.advance(Float.NaN);
    flash.advance(Float.POSITIVE_INFINITY);
    flash.advance(Float.NEGATIVE_INFINITY);

    assertEquals(1f, flash.intensity(), 0.0001f);
  }

  @Test
  void clearEndsThePulseImmediately() {
    ShipHitFlash flash = new ShipHitFlash();
    flash.trigger();
    flash.advance(0.02f);
    assertEquals(0.8f, flash.intensity(), 0.001f);

    flash.clear();
    assertEquals(0f, flash.intensity(), 0.0001f);

    flash.advance(0.1f);
    assertEquals(0f, flash.intensity(), 0.0001f, "a cleared pulse stays cleared");
  }

  @Test
  void twoInstancesDecayIndependently() {
    ShipHitFlash first = new ShipHitFlash();
    ShipHitFlash second = new ShipHitFlash();
    assertNotSame(first, second);

    first.trigger();
    first.advance(0.09f);
    second.trigger();
    second.advance(0.02f);

    assertEquals(0.1f, first.intensity(), 0.001f);
    assertEquals(0.8f, second.intensity(), 0.001f, "one ship's hit never affects the other");

    second.clear();
    assertEquals(0f, second.intensity(), 0.0001f);
    assertEquals(0.1f, first.intensity(), 0.001f, "clearing one instance keeps the other alive");
  }
}