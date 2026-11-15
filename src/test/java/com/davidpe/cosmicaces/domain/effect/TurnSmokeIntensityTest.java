package com.davidpe.cosmicaces.domain.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurnSmokeIntensityTest {

  @Test
  void cruiseAndSlowerSpeedsKeepTheSubtleFactor() {
    assertEquals(TurnSmokeIntensity.SUBTLE_FACTOR,
        TurnSmokeIntensity.factorForSpeed(0f), 0f);
    assertEquals(TurnSmokeIntensity.SUBTLE_FACTOR,
        TurnSmokeIntensity.factorForSpeed(TurnSmokeIntensity.SUBTLE_SPEED * 0.5f), 0f);
    assertEquals(TurnSmokeIntensity.SUBTLE_FACTOR,
        TurnSmokeIntensity.factorForSpeed(TurnSmokeIntensity.SUBTLE_SPEED), 0f);
  }

  @Test
  void factorGrowsContinuouslyAndStaysBounded() {
    float previous = TurnSmokeIntensity.factorForSpeed(0f);
    assertEquals(TurnSmokeIntensity.SUBTLE_FACTOR, previous, 0f);
    for (float speed = 1f; speed <= 1200f; speed += 1f) {
      float factor = TurnSmokeIntensity.factorForSpeed(speed);
      assertTrue(factor >= previous,
          "the visibility factor must never fall as real speed rises: " + speed);
      assertTrue(factor >= TurnSmokeIntensity.SUBTLE_FACTOR - 1e-6f);
      assertTrue(factor <= TurnSmokeIntensity.PEAK_FACTOR + 1e-6f,
          "the factor must stay bounded: " + speed + " -> " + factor);
      previous = factor;
    }
    assertEquals(TurnSmokeIntensity.PEAK_FACTOR,
        TurnSmokeIntensity.factorForSpeed(TurnSmokeIntensity.PEAK_SPEED), 0f);
    assertEquals(TurnSmokeIntensity.PEAK_FACTOR,
        TurnSmokeIntensity.factorForSpeed(5000f), 0f);
  }

  @Test
  void intermediateSpeedsSitStrictlyBetweenSubtleAndPeak() {
    float turbo = TurnSmokeIntensity.factorForSpeed(650f);
    assertTrue(turbo > TurnSmokeIntensity.SUBTLE_FACTOR);
    assertTrue(turbo < TurnSmokeIntensity.PEAK_FACTOR);
    assertTrue(TurnSmokeIntensity.factorForSpeed(300f) < turbo,
        "a slower real speed can never look stronger than a faster one");
    assertTrue(TurnSmokeIntensity.factorForSpeed(700f) > turbo);
  }

  @Test
  void theFactorDependsOnlyOnTheRealSpeed() {
    // The mapping takes no mode, pose or configured target: two readings of the same real speed are
    // identical whatever produced them, so a mode switch alone cannot jump the visibility.
    assertEquals(TurnSmokeIntensity.factorForSpeed(480f),
        TurnSmokeIntensity.factorForSpeed(480f), 0f);
    assertEquals(TurnSmokeIntensity.factorForSpeed(650f),
        TurnSmokeIntensity.factorForSpeed(650f), 0f);
  }

  @Test
  void invalidSpeedsFallBackToTheSubtleFactor() {
    assertEquals(TurnSmokeIntensity.SUBTLE_FACTOR,
        TurnSmokeIntensity.factorForSpeed(Float.NaN), 0f);
    assertEquals(TurnSmokeIntensity.SUBTLE_FACTOR,
        TurnSmokeIntensity.factorForSpeed(Float.POSITIVE_INFINITY), 0f);
    assertEquals(TurnSmokeIntensity.SUBTLE_FACTOR,
        TurnSmokeIntensity.factorForSpeed(Float.NEGATIVE_INFINITY), 0f);
    assertEquals(TurnSmokeIntensity.SUBTLE_FACTOR,
        TurnSmokeIntensity.factorForSpeed(-120f), 0f);
  }
}
