package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.effect.AstraYawSmokeTrail;
import com.davidpe.cosmicaces.domain.effect.TurnSmokeIntensity;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.player.FlightSpeeds;
import org.junit.jupiter.api.Test;

class AstraYawSmokeEffectTest {

  private static final FlightSpeeds SPEEDS = new FlightSpeeds(150, 650, 750);
  private static final WorldBounds WORLD = new WorldBounds(3200f, 12000f);
  private static final float CENTER_X = 1600f;
  private static final float CENTER_Y = 6000f;
  private static final float STEP = 1f / 60f;

  @Test
  void onlyThePilotYawWhileMovingEmits() {
    Astra astra = astraAt(0f);
    AstraYawSmokeEffect effect = new AstraYawSmokeEffect();

    effect.update(STEP, true, true, astra);
    assertEquals(0, effect.sampleCount(), "a frozen start has no yaw signal to consume");

    astra.fly(FlightControls.neutral(), 0.5f, WORLD);
    effect.update(STEP, true, true, astra);
    assertEquals(0, effect.sampleCount(), "straight flight must not emit smoke");

    astra.fly(new FlightControls(false, true, false, false, false), 0.5f, WORLD);
    effect.update(STEP, true, true, astra);
    assertEquals(0, effect.sampleCount(), "pure strafe must not emit smoke");
    assertFalse(AstraYawSmokeEffect.isEmitting(true, true, astra));

    astra.fly(new FlightControls(false, true, true, false, false), STEP, WORLD);
    assertTrue(AstraYawSmokeEffect.isEmitting(true, true, astra));
    effect.update(STEP, true, true, astra);
    assertTrue(effect.hasSamples(), "yaw with displacement must leave smoke");
  }

  @Test
  void aBrakedTurnEmitsWhileTheShipStillMovesAndStopsWhenItStops() {
    Astra astra = astraAt(0f);
    AstraYawSmokeEffect effect = new AstraYawSmokeEffect();

    astra.fly(new FlightControls(false, true, false, true, false), 0.2f, WORLD);
    assertTrue(astra.flightSpeed() > 0f);
    assertTrue(AstraYawSmokeEffect.isEmitting(true, true, astra),
        "a braked turn that still displaces the ship keeps emitting");

    // Brake until the ship is stopped: the same turn order no longer displaces her.
    for (int frame = 0; frame < 60; frame++) {
      astra.fly(new FlightControls(false, true, false, true, false), STEP, WORLD);
    }
    assertEquals(0f, astra.flightSpeed(), 0.01f);
    assertFalse(AstraYawSmokeEffect.isEmitting(true, true, astra),
        "a turn without displacement must not emit");
  }

  @Test
  void borderAutoSteeringDoesNotEmit() {
    Astra astra = new Astra(SPEEDS);
    astra.placeAt(40f, 6000f, -80f, WORLD);
    astra.fly(FlightControls.neutral(), 0.5f, WORLD);
    assertFalse(AstraYawSmokeEffect.isEmitting(true, true, astra),
        "the border correction alone is not the pilot's yaw");
    AstraYawSmokeEffect effect = new AstraYawSmokeEffect();
    effect.update(0.5f, true, true, astra);
    assertEquals(0, effect.sampleCount());
  }

  @Test
  void emittedSmokeIsAnchoredInTheWorldAndIgnoresTheShipAndCamera() {
    Astra astra = astraAt(0f);
    AstraYawSmokeEffect effect = new AstraYawSmokeEffect();
    astra.fly(new FlightControls(false, true, true, false, false), 0.2f, WORLD);
    effect.update(0.2f, true, true, astra);
    assertTrue(effect.sampleCount() > 0);
    float firstX = effect.sample(0).x();
    float firstY = effect.sample(0).y();

    // Fly straight for a while: the already emitted puffs keep their world position.
    astra.fly(FlightControls.neutral(), 1f, WORLD);
    effect.update(0.2f, true, true, astra);
    assertEquals(firstX, effect.sample(0).x(), 1e-4f);
    assertEquals(firstY, effect.sample(0).y(), 1e-4f);
    assertTrue(astra.y() > CENTER_Y, "the ship really moved away from the emitted point");
  }

  @Test
  void endingTheMissionClearsTheTrailAndTheNextRunStartsEmpty() {
    Astra astra = astraAt(0f);
    AstraYawSmokeEffect effect = new AstraYawSmokeEffect();
    astra.fly(new FlightControls(false, true, true, false, false), 0.2f, WORLD);
    effect.update(0.2f, true, true, astra);
    assertTrue(effect.hasSamples());

    effect.update(0.1f, false, true, astra);
    assertFalse(effect.hasSamples(), "an inactive mission must not keep smoke on screen");
    assertEquals(0, effect.sampleCount());

    astra.fly(FlightControls.neutral(), 0.2f, WORLD);
    effect.update(0.1f, true, true, astra);
    assertFalse(effect.hasSamples(), "the new run does not inherit the old trail");
  }

  @Test
  void inactiveAstraStopsEmittingButTheLastSmokeFades() {
    Astra astra = astraAt(0f);
    AstraYawSmokeEffect effect = new AstraYawSmokeEffect();
    astra.fly(new FlightControls(false, true, true, false, false), 0.2f, WORLD);
    effect.update(0.2f, true, true, astra);
    int emitted = effect.sampleCount();
    assertTrue(emitted > 0);

    for (int step = 0; step < 3; step++) {
      effect.update(0.25f, true, false, astra);
      assertEquals(emitted, effect.sampleCount(),
          "a dead Astra stops emitting without discarding the smoke already in the world");
    }
    for (int step = 0; step < 4; step++) {
      effect.update(0.25f, true, false, astra);
    }
    assertEquals(0, effect.sampleCount(), "the emitted smoke finally expires");
  }

  @Test
  void resetDropsEveryPuffImmediately() {
    Astra astra = astraAt(0f);
    AstraYawSmokeEffect effect = new AstraYawSmokeEffect();
    astra.fly(new FlightControls(false, true, true, false, false), 0.2f, WORLD);
    effect.update(0.2f, true, true, astra);
    assertTrue(effect.hasSamples());

    effect.reset();
    assertFalse(effect.hasSamples());
    assertEquals(0, effect.sampleCount());
  }

  @Test
  void tailPointSitsBehindTheHullForBothHeadings() {
    Astra astra = astraAt(0f);
    AstraYawSmokeEffect.Tail tail = new AstraYawSmokeEffect.Tail();
    AstraYawSmokeEffect.tailPoint(astra, tail);
    float centerX = astra.x() + astra.drawWidth() / 2f;
    float centerY = astra.y() + astra.drawHeight() / 2f;
    float aft = astra.drawHeight() * AstraYawSmokeEffect.TAIL_AFT_FRACTION;
    assertEquals(centerX, tail.x(), 1e-3f);
    assertEquals(centerY - aft, tail.y(), 1e-3f, "at yaw 0 the emission point is south of the box");

    astra.placeAt(CENTER_X, CENTER_Y, 90f, WORLD);
    AstraYawSmokeEffect.tailPoint(astra, tail);
    centerX = astra.x() + astra.drawWidth() / 2f;
    centerY = astra.y() + astra.drawHeight() / 2f;
    assertEquals(centerX - aft, tail.x(), 1e-3f, "at yaw 90 the emission point is west of the box");
    assertEquals(centerY, tail.y(), 1e-3f);
  }

  @Test
  void emissionRequiresAPlayableMissionAndALiveAstra() {
    Astra astra = astraAt(0f);
    astra.fly(new FlightControls(false, true, true, false, false), 0.2f, WORLD);
    assertTrue(AstraYawSmokeEffect.isEmitting(true, true, astra));
    assertFalse(AstraYawSmokeEffect.isEmitting(false, true, astra));
    assertFalse(AstraYawSmokeEffect.isEmitting(true, false, astra));
    assertFalse(AstraYawSmokeEffect.isEmitting(true, true, null));
  }

  @Test
  void theFirstFramesOfACruiseTurnStaySubtle() {
    Astra astra = astraAt(0f);
    AstraYawSmokeEffect effect = new AstraYawSmokeEffect();
    astra.fly(new FlightControls(false, true, true, false, false), STEP, WORLD);
    effect.update(STEP, true, true, astra);

    float alpha = effect.sample(effect.sampleCount() - 1).alpha();
    assertEquals(AstraYawSmokeTrail.BASE_PEAK_ALPHA
            * TurnSmokeIntensity.factorForSpeed(astra.flightSpeed()),
        alpha, 1e-5f, "the effect must read the real integrated speed");
    assertTrue(alpha < AstraYawSmokeTrail.BASE_PEAK_ALPHA * 1.05f,
        "leaving cruise must not suddenly make the smoke strongly visible");
  }

  @Test
  void aTurboTurnEmitsClearlyMoreVisibleSmokeThanCruise() {
    Astra astra = astraAt(0f);
    AstraYawSmokeEffect effect = new AstraYawSmokeEffect();
    // Build real speed flying straight; only the turn itself emits smoke.
    astra.fly(new FlightControls(false, false, true, false, false), 2f, WORLD);
    assertEquals(SPEEDS.turbo(), astra.flightSpeed(), 0.5f);

    astra.fly(new FlightControls(false, true, true, false, false), STEP, WORLD);
    effect.update(STEP, true, true, astra);
    float turboAlpha = effect.sample(effect.sampleCount() - 1).alpha();
    assertEquals(AstraYawSmokeTrail.BASE_PEAK_ALPHA
            * TurnSmokeIntensity.factorForSpeed(astra.flightSpeed()),
        turboAlpha, 1e-5f);
    assertTrue(turboAlpha > AstraYawSmokeTrail.BASE_PEAK_ALPHA * 2f,
        "a turn at Turbo speed must be clearly more visible than the cruise trail");
  }

  @Test
  void brakingDuringTheTurnMakesOnlyTheNewSmokeSubtler() {
    Astra astra = astraAt(0f);
    AstraYawSmokeEffect effect = new AstraYawSmokeEffect();
    astra.fly(new FlightControls(false, false, true, false, false), 2f, WORLD);
    astra.fly(new FlightControls(false, true, true, false, false), STEP, WORLD);
    effect.update(STEP, true, true, astra);
    float fastAlpha = effect.sample(effect.sampleCount() - 1).alpha();
    float fastX = effect.sample(effect.sampleCount() - 1).x();

    float previousNewest = fastAlpha;
    for (int frame = 0; frame < 20; frame++) {
      astra.fly(new FlightControls(false, true, false, true, false), STEP, WORLD);
      effect.update(STEP, true, true, astra);
      float newest = effect.sample(effect.sampleCount() - 1).alpha();
      assertTrue(newest < previousNewest,
          "each new puff emitted while braking must be subtler than the previous one");
      previousNewest = newest;
    }
    assertTrue(astra.flightSpeed() > TurnSmokeIntensity.SUBTLE_SPEED,
        "the check runs while the ship is still flying fast enough to emit");
    assertTrue(AstraYawSmokeEffect.isEmitting(true, true, astra));
    assertEquals(fastX, effect.sample(0).x(), 1e-4f,
        "the fastest puff stays anchored where it was emitted");
    assertTrue(previousNewest < fastAlpha,
        "the trail becomes subtler as the real speed drops");
  }

  @Test
  void configuredSpeedTargetsNeverLeakIntoTheVisibleIntensity() {
    Astra standard = astraAt(new FlightSpeeds(150, 650, 750), 0f);
    Astra faster = astraAt(new FlightSpeeds(150, 900, 1000), 0f);
    AstraYawSmokeEffect standardEffect = new AstraYawSmokeEffect();
    AstraYawSmokeEffect fasterEffect = new AstraYawSmokeEffect();

    standard.fly(new FlightControls(false, true, true, false, false), STEP, WORLD);
    faster.fly(new FlightControls(false, true, true, false, false), STEP, WORLD);
    assertEquals(standard.flightSpeed(), faster.flightSpeed(), 0f,
        "both ships really fly at the same speed in this step");
    standardEffect.update(STEP, true, true, standard);
    fasterEffect.update(STEP, true, true, faster);

    assertEquals(standardEffect.sample(0).alpha(), fasterEffect.sample(0).alpha(), 1e-6f,
        "at the same real speed the trail looks the same whatever the configured targets are");
  }

  private static Astra astraAt(float yawDegrees) {
    return astraAt(SPEEDS, yawDegrees);
  }

  private static Astra astraAt(FlightSpeeds speeds, float yawDegrees) {
    Astra astra = new Astra(speeds);
    astra.placeAt(CENTER_X, CENTER_Y, yawDegrees, WORLD);
    return astra;
  }
}
