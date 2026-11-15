package com.davidpe.cosmicaces.domain.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Rules of the enemy turn wake (COS-154): it only exists while an active aircraft really turns with
 * displacement, it is laid behind the real path in world coordinates, its visibility follows the
 * speed actually travelled, and deaths, spawns or re-placements never join two lives.
 */
class TurnSmokeTrackerTest {

  private static final float STEP = 1f / 60f;
  /** Slice long enough to emit at least one puff per frame, so the newest one never aged. */
  private static final float EMISSION_STEP = 0.05f;
  private static final float HULL_HEIGHT = 80f;
  private static final float TAIL_AFT = HULL_HEIGHT * TurnSmokeTracker.TAIL_AFT_FRACTION;

  @Test
  void straightFlightNeverEmitsEvenAtHighSpeed() {
    TurnSmokeTracker tracker = new TurnSmokeTracker(HULL_HEIGHT);
    float y = 4000f;
    for (int frame = 0; frame < 240; frame++) {
      y += 500f * STEP;
      tracker.update(STEP, true, 1200f, y);
      assertFalse(tracker.isEmitting(), "straight flight has no turn to report");
      assertFalse(tracker.hasSamples());
      assertEquals(0, tracker.sampleCount());
    }
  }

  @Test
  void aStationaryShipNeverEmits() {
    TurnSmokeTracker tracker = new TurnSmokeTracker(HULL_HEIGHT);
    for (int frame = 0; frame < 120; frame++) {
      tracker.update(STEP, true, 900f, 900f);
      assertFalse(tracker.isEmitting());
      assertFalse(tracker.hasSamples());
    }
  }

  @Test
  void aSustainedTurnLaysACurvedWakeBehindTheTail() {
    TurnSmokeTracker tracker = new TurnSmokeTracker(HULL_HEIGHT);
    float centerX = 1600f;
    float centerY = 6000f;
    float radius = 200f;
    float speed = 300f;
    float radiansPerSecond = speed / radius;
    float angle = 0f;
    for (int frame = 0; frame < 60; frame++) {
      angle += radiansPerSecond * STEP;
      tracker.update(STEP, true, centerX + radius * (float) Math.cos(angle),
          centerY + radius * (float) Math.sin(angle));
    }

    assertTrue(tracker.isEmitting(), "a sustained orbit turns with displacement every frame");
    int count = tracker.sampleCount();
    assertTrue(count >= 5, "the arc must hold several puffs");

    float expectedRadius = (float) Math.hypot(radius, TAIL_AFT);
    float[] rememberedX = new float[count];
    float[] rememberedY = new float[count];
    for (int index = 0; index < count; index++) {
      TurnSmokeTrail.Sample sample = tracker.sample(index);
      rememberedX[index] = sample.x();
      rememberedY[index] = sample.y();
      float fromCenter = (float) Math.hypot(sample.x() - centerX, sample.y() - centerY);
      assertEquals(expectedRadius, fromCenter, 2f,
          "every puff leaves the hull behind the real path, not on it");
    }

    boolean curved = false;
    for (int index = 2; index < count; index++) {
      float firstX = rememberedX[index - 1] - rememberedX[index - 2];
      float firstY = rememberedY[index - 1] - rememberedY[index - 2];
      float secondX = rememberedX[index] - rememberedX[index - 1];
      float secondY = rememberedY[index] - rememberedY[index - 1];
      if (Math.abs(firstX * secondY - firstY * secondX) > 0.05f) {
        curved = true;
        break;
      }
    }
    assertTrue(curved, "the stored puffs must draw the curve, not a straight line");
  }

  @Test
  void oppositeTurnsLeaveIndependentArcs() {
    TurnSmokeTracker left = new TurnSmokeTracker(HULL_HEIGHT);
    TurnSmokeTracker right = new TurnSmokeTracker(HULL_HEIGHT);
    float radius = 240f;
    float speed = 320f;
    float radiansPerSecond = speed / radius;
    float angle = 0f;
    for (int frame = 0; frame < 60; frame++) {
      angle += radiansPerSecond * STEP;
      float offsetX = radius * (float) Math.cos(angle);
      float offsetY = radius * (float) Math.sin(angle);
      left.update(STEP, true, 1600f + offsetX, 6000f + offsetY);
      right.update(STEP, true, 2400f + radius * (float) Math.cos(-angle),
          6000f + radius * (float) Math.sin(-angle));
    }

    assertTrue(left.hasSamples(), "the left-hand turn leaves its own arc");
    assertTrue(right.hasSamples(), "the right-hand turn leaves its own arc");
    left.clear();
    assertFalse(left.hasSamples());
    assertTrue(right.hasSamples(), "clearing one wake never touches the other aircraft");
    assertEquals(0, left.sampleCount());
    assertTrue(right.sampleCount() > 0);
  }

  @Test
  void aTurnWithoutDisplacementNeverEmits() {
    TurnSmokeTracker tracker = new TurnSmokeTracker(HULL_HEIGHT);
    // A ship that only rotates or is clamped in place reports the same centre: the smoke is tied to
    // the real displacement, exactly like Astra's braked turn.
    for (int frame = 0; frame < 120; frame++) {
      tracker.update(STEP, true, 700f, 700f);
    }
    assertFalse(tracker.isEmitting());
    assertEquals(0, tracker.sampleCount());
  }

  @Test
  void theVisibilityOfNewPuffsFollowsTheSpeedReallyTravelled() {
    TurnSmokeTracker slow = new TurnSmokeTracker(HULL_HEIGHT);
    TurnSmokeTracker fast = new TurnSmokeTracker(HULL_HEIGHT);
    TurnSmokeTracker sameSpeedOtherRadius = new TurnSmokeTracker(HULL_HEIGHT);
    float radiansPerSecond = 2.5f;
    float sameSpeedRadians = 500f / 260f;
    float angle = 0f;
    float sameSpeedAngle = 0f;
    for (int frame = 0; frame < 30; frame++) {
      angle += radiansPerSecond * EMISSION_STEP;
      sameSpeedAngle += sameSpeedRadians * EMISSION_STEP;
      slow.update(EMISSION_STEP, true, 1000f + 120f * (float) Math.cos(angle),
          6000f + 120f * (float) Math.sin(angle));
      fast.update(EMISSION_STEP, true, 2000f + 200f * (float) Math.cos(angle),
          6000f + 200f * (float) Math.sin(angle));
      sameSpeedOtherRadius.update(EMISSION_STEP, true,
          3000f + 260f * (float) Math.cos(sameSpeedAngle),
          8000f + 260f * (float) Math.sin(sameSpeedAngle));
    }

    float slowAlpha = newestFreshAlpha(slow, 300f);
    float fastAlpha = newestFreshAlpha(fast, 500f);
    assertTrue(slowAlpha > 0f && fastAlpha > slowAlpha,
        "a faster real flight must make the new smoke more visible");
    assertEquals(fastAlpha, newestFreshAlpha(sameSpeedOtherRadius, 500f), 1e-3f,
        "at the same real speed the intensity is identical whatever the turn radius is");
    assertTrue(slow.measuredSpeed() > 0f);
    assertTrue(fast.measuredSpeed() > slow.measuredSpeed());
  }

  @Test
  void aRespawnStartsANewSpanWithoutBridgingTheOldArc() {
    TurnSmokeTracker tracker = new TurnSmokeTracker(HULL_HEIGHT);
    float angle = 0f;
    for (int frame = 0; frame < 40; frame++) {
      angle += 1.5f * STEP;
      tracker.update(STEP, true, 1600f + 200f * (float) Math.cos(angle),
          6000f + 200f * (float) Math.sin(angle));
    }
    int oldCount = tracker.sampleCount();
    assertTrue(oldCount > 0);
    float lastOldX = tracker.sample(oldCount - 1).x();
    float lastOldY = tracker.sample(oldCount - 1).y();

    // A respawn elsewhere: the jump is not a turn and must not add smoke that crosses the gap.
    tracker.update(STEP, true, 7000f, 1200f);
    assertEquals(oldCount, tracker.sampleCount(), "the jump itself emits nothing");
    assertFalse(tracker.isEmitting());
    assertEquals(lastOldX, tracker.sample(oldCount - 1).x(), 1e-4f);
    assertEquals(lastOldY, tracker.sample(oldCount - 1).y(), 1e-4f);

    // The new life flies its own orbit: its puffs start at the new place, old puffs stay behind.
    for (int frame = 0; frame < 40; frame++) {
      angle += 1.5f * STEP;
      tracker.update(STEP, true, 7000f + 200f * (float) Math.cos(angle),
          1200f + 200f * (float) Math.sin(angle));
    }
    float expectedRadius = (float) Math.hypot(200f, TAIL_AFT);
    boolean oldArcVisible = false;
    boolean newArcVisible = false;
    for (int index = 0; index < tracker.sampleCount(); index++) {
      TurnSmokeTrail.Sample sample = tracker.sample(index);
      float fromOld = (float) Math.hypot(sample.x() - 1600f, sample.y() - 6000f);
      float fromNew = (float) Math.hypot(sample.x() - 7000f, sample.y() - 1200f);
      assertTrue(Math.min(Math.abs(fromOld - expectedRadius), Math.abs(fromNew - expectedRadius))
              < 3f,
          "every puff belongs to one of the two arcs; none crosses the respawn gap");
      oldArcVisible |= Math.abs(fromOld - expectedRadius) < 3f;
      newArcVisible |= Math.abs(fromNew - expectedRadius) < 3f;
    }
    assertTrue(oldArcVisible, "the smoke of the old life keeps fading where it was laid");
    assertTrue(newArcVisible, "the new manoeuvre starts its own wake");
  }

  @Test
  void anImplausibleDirectionSnapDoesNotEmit() {
    TurnSmokeTracker tracker = new TurnSmokeTracker(HULL_HEIGHT);
    float x = 1000f;
    for (int frame = 0; frame < 20; frame++) {
      x += 300f * STEP;
      tracker.update(STEP, true, x, 1000f);
    }
    assertEquals(0, tracker.sampleCount());

    // A one-frame 90-degree reversal at normal flight speed is a snap, not a manoeuvre.
    tracker.update(STEP, true, x, 1000f + 300f * STEP);
    assertFalse(tracker.isEmitting());
    assertEquals(0, tracker.sampleCount());

    // The new direction is straight again, so no smoke is invented afterwards either.
    float y = 1000f + 300f * STEP;
    for (int frame = 0; frame < 30; frame++) {
      y += 300f * STEP;
      tracker.update(STEP, true, x, y);
    }
    assertFalse(tracker.isEmitting());
    assertEquals(0, tracker.sampleCount());
  }

  @Test
  void deathStopsTheEmissionButTheEmittedSmokeFades() {
    TurnSmokeTracker tracker = new TurnSmokeTracker(HULL_HEIGHT);
    float angle = 0f;
    for (int frame = 0; frame < 40; frame++) {
      angle += 1.5f * STEP;
      tracker.update(STEP, true, 1600f + 200f * (float) Math.cos(angle),
          6000f + 200f * (float) Math.sin(angle));
    }
    int emitted = tracker.sampleCount();
    assertTrue(emitted > 0);

    tracker.update(STEP, false, 1600f, 6000f);
    assertFalse(tracker.isEmitting());
    assertEquals(emitted, tracker.sampleCount(),
        "a dead ship stops emitting without discarding the smoke in the world");

    for (int step = 0; step < 80; step++) {
      tracker.update(STEP, false, 1600f, 6000f);
    }
    assertEquals(0, tracker.sampleCount(), "every puff expires after its lifetime");
    assertFalse(tracker.hasSamples());
  }

  @Test
  void invalidDeltasAndCentresAreHandledWithoutInventingSmoke() {
    TurnSmokeTracker tracker = new TurnSmokeTracker(HULL_HEIGHT);
    tracker.update(STEP, true, 100f, 100f);
    tracker.update(Float.NaN, true, 140f, 100f);
    tracker.update(0f, true, 180f, 100f);
    tracker.update(-1f, true, 220f, 100f);
    assertEquals(0, tracker.sampleCount(), "frozen frames never emit");
    assertFalse(tracker.isEmitting());

    assertThrows(IllegalArgumentException.class,
        () -> tracker.update(STEP, true, Float.NaN, 100f));
    assertThrows(IllegalArgumentException.class, () -> new TurnSmokeTracker(0f));
    assertThrows(IllegalArgumentException.class, () -> new TurnSmokeTracker(Float.NaN));
  }

  @Test
  void clearDropsTheWholeWakeAndItsAnchor() {
    TurnSmokeTracker tracker = new TurnSmokeTracker(HULL_HEIGHT);
    float angle = 0f;
    for (int frame = 0; frame < 30; frame++) {
      angle += 1.5f * STEP;
      tracker.update(STEP, true, 1600f + 200f * (float) Math.cos(angle),
          6000f + 200f * (float) Math.sin(angle));
    }
    assertTrue(tracker.hasSamples());

    tracker.clear();
    assertFalse(tracker.hasSamples());
    assertFalse(tracker.isEmitting());
    assertEquals(0f, tracker.measuredSpeed(), 0f);
    assertThrows(IndexOutOfBoundsException.class, () -> tracker.sample(0));
  }

  /**
   * Alpha of the newest puff, checked against the published intensity curve for the real speed. The
   * newest puff is emitted inside the latest frame, so it has not aged yet.
   */
  private static float newestFreshAlpha(TurnSmokeTracker tracker, float expectedSpeed) {
    TurnSmokeTrail.Sample newest = tracker.sample(tracker.sampleCount() - 1);
    assertEquals(0f, newest.ageSeconds(), 1e-6f, "the check reads a puff emitted this frame");
    assertEquals(expectedSpeed, tracker.measuredSpeed(), 1f);
    assertEquals(TurnSmokeTrail.BASE_PEAK_ALPHA
            * TurnSmokeIntensity.factorForSpeed(tracker.measuredSpeed()),
        newest.alpha(), 1e-5f, "the puff takes the visibility of the real travelled speed");
    return newest.alpha();
  }
}
