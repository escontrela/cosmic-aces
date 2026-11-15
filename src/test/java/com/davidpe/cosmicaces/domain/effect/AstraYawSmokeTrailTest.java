package com.davidpe.cosmicaces.domain.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AstraYawSmokeTrailTest {

  private static final float STEP = 1f / 60f;

  @Test
  void straightFlightNeverEmitsAnyPuff() {
    AstraYawSmokeTrail trail = new AstraYawSmokeTrail();
    for (int frame = 0; frame < 240; frame++) {
      trail.update(STEP, false, 1200f, 4000f + frame * 2f);
      assertFalse(trail.hasSamples(), "a non-emitting update must not create smoke");
      assertFalse(trail.isEmitting());
      assertEquals(0, trail.sampleCount());
    }
  }

  @Test
  void emittingLaysPuffsAtAStableSpacingAlongTheTailPath() {
    AstraYawSmokeTrail trail = new AstraYawSmokeTrail();
    trail.update(STEP, true, 100f, 100f);
    assertEquals(1, trail.sampleCount(), "the manoeuvre starts with a puff at the current tail");
    assertEquals(100f, trail.sample(0).x(), 1e-4f);
    assertEquals(100f, trail.sample(0).y(), 1e-4f);
    assertTrue(trail.isEmitting());

    float tailX = 100f;
    for (int frame = 0; frame < 30; frame++) {
      tailX += 3f;
      trail.update(STEP, true, tailX, 100f);
    }

    int count = trail.sampleCount();
    assertTrue(count >= 3, "a sustained turn must leave a visible trail");
    for (int index = 0; index < count; index++) {
      AstraYawSmokeTrail.Sample sample = trail.sample(index);
      assertEquals(100f, sample.y(), 1e-3f, "the puff keeps its emitted row");
    }
    for (int index = 1; index < count; index++) {
      float previousX = trail.sample(index - 1).x();
      float currentX = trail.sample(index).x();
      assertTrue(currentX > previousX, "samples stay ordered from oldest to newest");
      assertEquals(AstraYawSmokeTrail.SAMPLE_SPACING, currentX - previousX, 1e-3f,
          "consecutive puffs keep a stable world-space spacing");
    }
    assertEquals(100f + AstraYawSmokeTrail.SAMPLE_SPACING, trail.sample(1).x(), 1e-3f);
  }

  @Test
  void emittedPuffsStayInTheWorldAndKeepTheCurvedPath() {
    AstraYawSmokeTrail trail = new AstraYawSmokeTrail();
    float centerX = 1600f;
    float centerY = 6000f;
    float radius = 120f;
    // Half a second of a tight turn: short enough that no puff expires while it is laid.
    for (int degree = 0; degree <= 45; degree++) {
      double radians = Math.toRadians(degree);
      float x = centerX + radius * (float) Math.sin(radians);
      float y = centerY - radius + radius * (float) Math.cos(radians);
      trail.update(STEP, true, x, y);
    }
    int count = trail.sampleCount();
    assertTrue(count >= 8, "the arc must hold several puffs");

    float[] rememberedX = new float[count];
    float[] rememberedY = new float[count];
    for (int index = 0; index < count; index++) {
      rememberedX[index] = trail.sample(index).x();
      rememberedY[index] = trail.sample(index).y();
    }
    boolean curved = false;
    for (int index = 2; index < count; index++) {
      float firstDx = rememberedX[index - 1] - rememberedX[index - 2];
      float firstDy = rememberedY[index - 1] - rememberedY[index - 2];
      float secondDx = rememberedX[index] - rememberedX[index - 1];
      float secondDy = rememberedY[index] - rememberedY[index - 1];
      if (Math.abs(firstDx * secondDy - firstDy * secondDx) > 0.05f) {
        curved = true;
        break;
      }
    }
    assertTrue(curved, "the stored puffs must bend with the manoeuvre, not form a straight line");

    // The ship flies on: an update at a far-away tail must not move or rebuild old puffs.
    trail.update(STEP, false, centerX + 5000f, centerY + 5000f);
    assertEquals(count, trail.sampleCount());
    for (int index = 0; index < count; index++) {
      assertEquals(rememberedX[index], trail.sample(index).x(), 1e-4f);
      assertEquals(rememberedY[index], trail.sample(index).y(), 1e-4f);
    }
  }

  @Test
  void puffsLoseOpacityAndExpireAfterTheTurnEnds() {
    AstraYawSmokeTrail trail = new AstraYawSmokeTrail();
    float tailX = 400f;
    for (int frame = 0; frame < 20; frame++) {
      tailX += 4f;
      trail.update(STEP, true, tailX, 900f);
    }
    int count = trail.sampleCount();
    assertTrue(count > 0);
    // Follow one specific puff across updates: indices shift when older puffs expire.
    AstraYawSmokeTrail.Sample newest = trail.sample(count - 1);
    float initialAlpha = newest.alpha();
    float initialRadius = newest.radius();
    assertTrue(initialAlpha > 0f, "a fresh puff is visible");
    assertTrue(initialAlpha <= 1f);

    trail.update(0.25f, false, tailX, 900f);
    assertFalse(trail.isEmitting(), "releasing yaw stops the emission");
    assertTrue(trail.sampleCount() > 0, "the emitted smoke outlives the manoeuvre");
    assertEquals(count, trail.sampleCount(), "no puff expires in a quarter of a second");
    assertTrue(newest.alpha() < initialAlpha, "alpha must decrease with age");
    assertTrue(newest.radius() > initialRadius, "the puff expands gently as it ages");

    float fadedAlpha = newest.alpha();
    trail.update(0.25f, false, tailX, 900f);
    assertTrue(newest.alpha() < fadedAlpha, "alpha keeps decreasing while the turn is over");

    int guard = 0;
    while (trail.hasSamples() && guard++ < 10) {
      trail.update(0.25f, false, tailX, 900f);
    }
    assertEquals(0, trail.sampleCount(), "every puff must expire after its lifetime");
    assertFalse(trail.hasSamples());
  }

  @Test
  void poolStaysBoundedDuringAThousandFrameTurn() {
    AstraYawSmokeTrail trail = new AstraYawSmokeTrail();
    float x = 0f;
    for (int frame = 0; frame < 1000; frame++) {
      x += 12f;
      trail.update(STEP, true, x, 200f);
      assertTrue(trail.sampleCount() <= AstraYawSmokeTrail.MAX_SAMPLES,
          "a long turn must never grow the pool beyond its cap");
    }
    assertTrue(trail.sampleCount() > 0);
  }

  @Test
  void aSaturatedTrailRecyclesTheOldestPuffInsteadOfGrowing() {
    AstraYawSmokeTrail trail = new AstraYawSmokeTrail();
    float x = 0f;
    // Without ageing, the pool is forced to fill and then recycle.
    for (int frame = 0; frame < 400; frame++) {
      x += 12f;
      trail.update(0f, true, x, 200f);
      assertTrue(trail.sampleCount() <= AstraYawSmokeTrail.MAX_SAMPLES);
    }
    assertEquals(AstraYawSmokeTrail.MAX_SAMPLES, trail.sampleCount());
    AstraYawSmokeTrail.Sample newest = trail.sample(trail.sampleCount() - 1);
    assertEquals(x, newest.x(), AstraYawSmokeTrail.SAMPLE_SPACING,
        "the newest puff is the one closest to the current tail");
  }

  @Test
  void separateManoeuvresAreNeverBridgedByAStraightLine() {
    AstraYawSmokeTrail trail = new AstraYawSmokeTrail();
    for (int frame = 0; frame < 30; frame++) {
      trail.update(STEP, true, 100f + frame * 4f, 300f);
    }
    int firstManoeuvre = trail.sampleCount();
    assertTrue(firstManoeuvre > 0);

    // The pilot stops turning and Astra flies on somewhere else before the next turn starts.
    trail.update(STEP, false, 600f, 300f);
    trail.update(STEP, false, 1500f, 900f);
    trail.update(STEP, true, 1500f, 900f);

    assertEquals(firstManoeuvre + 1, trail.sampleCount());
    AstraYawSmokeTrail.Sample newest = trail.sample(trail.sampleCount() - 1);
    assertEquals(1500f, newest.x(), 1e-3f);
    assertEquals(900f, newest.y(), 1e-3f);
    for (int index = 0; index < firstManoeuvre; index++) {
      assertTrue(trail.sample(index).x() < 300f, "old puffs stay where the first turn left them");
    }
    for (int index = firstManoeuvre; index < trail.sampleCount(); index++) {
      assertEquals(1500f, trail.sample(index).x(), 1e-3f,
          "the new manoeuvre starts at the new tail, not interpolated from the old one");
    }
  }

  @Test
  void aLongFrameCannotBurstMoreThanThePerFrameCap() {
    AstraYawSmokeTrail trail = new AstraYawSmokeTrail();
    trail.update(STEP, true, 0f, 0f);
    trail.update(5f, true, 9000f, 0f);
    assertTrue(trail.sampleCount() <= 1 + 8,
        "one long frame must not dump an unbounded burst of puffs");
  }

  @Test
  void invalidDeltasNeverCreateOrAgeSamples() {
    AstraYawSmokeTrail trail = new AstraYawSmokeTrail();
    trail.update(STEP, true, 100f, 100f);
    float alpha = trail.sample(0).alpha();
    trail.update(Float.NaN, false, 100f, 100f);
    trail.update(-1f, false, 100f, 100f);
    trail.update(0f, false, 100f, 100f);
    assertEquals(1, trail.sampleCount());
    assertEquals(alpha, trail.sample(0).alpha(), 0f);
    trail.update(STEP, false, 100f, 100f);
    assertTrue(trail.sample(0).alpha() < alpha, "a valid delta ages the puff");
    assertThrows(IllegalArgumentException.class,
        () -> trail.update(STEP, false, Float.NaN, 100f));
  }

  @Test
  void clearDropsEveryPuffAndTheEmissionAnchor() {
    AstraYawSmokeTrail trail = new AstraYawSmokeTrail();
    for (int frame = 0; frame < 20; frame++) {
      trail.update(STEP, true, 500f + frame * 5f, 700f);
    }
    assertTrue(trail.hasSamples());

    trail.clear();
    assertFalse(trail.hasSamples());
    assertFalse(trail.isEmitting());
    assertEquals(0, trail.sampleCount());
    assertThrows(IndexOutOfBoundsException.class, () -> trail.sample(0));

    // A new life starts its own arc at its own tail instead of continuing the dropped one.
    trail.update(STEP, true, 2000f, 100f);
    assertEquals(1, trail.sampleCount());
    assertEquals(2000f, trail.sample(0).x(), 1e-4f);
    assertEquals(100f, trail.sample(0).y(), 1e-4f);
  }
}
