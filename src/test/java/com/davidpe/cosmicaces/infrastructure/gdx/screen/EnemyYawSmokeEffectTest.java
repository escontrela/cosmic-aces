package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.domain.enemy.BastionBomber;
import com.davidpe.cosmicaces.domain.enemy.BastionEscort;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import org.junit.jupiter.api.Test;

/**
 * Wiring of the enemy turn wakes (COS-154) over the real PhaseOne encounters: only turning aircraft
 * with displacement emit, each aircraft owns its own arc, death fades it and a finished run clears
 * it. Drawing, layout and colours are not inspected here: visual QA belongs to the human PO.
 */
class EnemyYawSmokeEffectTest {

  private static final float STEP = 1f / 60f;

  @Test
  void aParkedAndAStraightInboundBomberLeaveNoWake() {
    PhaseOneGameController controller = startedController();
    EnemyYawSmokeEffect effect = new EnemyYawSmokeEffect();
    BastionBomber bomber = controller.bastionBomber();
    assertEquals(BastionBomber.FlightState.PARKED, bomber.flightState());

    for (int frame = 0; frame < 30; frame++) {
      advanceFrame(controller, effect);
    }
    assertFalse(effect.isEmitting(bomber));
    assertFalse(effect.hasSamples(bomber), "a parked ship never leaves a turn wake");

    int guard = 0;
    while (!bomber.hasTakenOff() && guard++ < 400) {
      advanceFrame(controller, effect, 0.25f);
    }
    assertTrue(bomber.hasTakenOff(), "the bomber must leave the runway inside the guard window");

    for (int frame = 0; frame < 60; frame++) {
      if (bomber.flightState() != BastionBomber.FlightState.INBOUND) break;
      advanceFrame(controller, effect);
    }
    assertFalse(effect.isEmitting(bomber));
    assertFalse(effect.hasSamples(bomber),
        "the straight inbound leg is not a turn and must not emit smoke");
  }

  @Test
  void theOrbitingBomberAndItsEscortsLeaveTheirOwnWakes() {
    PhaseOneGameController controller = startedController();
    EnemyYawSmokeEffect effect = new EnemyYawSmokeEffect();
    BastionBomber bomber = controller.bastionBomber();

    advanceUntilOrbiting(controller, effect);
    for (int frame = 0; frame < 240; frame++) {
      advanceFrame(controller, effect);
    }

    assertTrue(effect.hasSamples(bomber),
        "the bomber turning in orbit must draw a wake in the world");
    assertTrue(effect.sampleCount(bomber) >= 2);

    boolean anyEscortWake = false;
    boolean distinctArc = false;
    for (BastionEscort escort : controller.bastionEscorts()) {
      VesperRaider wingman = escort.raider();
      if (wingman == null) continue;
      if (effect.hasSamples(wingman)) {
        anyEscortWake = true;
        int count = effect.sampleCount(wingman);
        if (count > 0) {
          TurnSample bomberPuff = sample(bomber, effect);
          TurnSample escortPuff = new TurnSample(effect.sample(wingman, count - 1));
          distinctArc |= Math.abs(bomberPuff.x() - escortPuff.x()) > 1f
              || Math.abs(bomberPuff.y() - escortPuff.y()) > 1f;
        }
      }
    }
    assertTrue(anyEscortWake, "a wingman flying the same orbit turns with displacement too");
    assertTrue(distinctArc, "each aircraft keeps its own arc, not a shared one");
  }

  @Test
  void theVesperRaiderWakeFollowsItsOwnDogfight() {
    PhaseOneGameController controller = startedController();
    EnemyYawSmokeEffect effect = new EnemyYawSmokeEffect();

    int guard = 0;
    while (controller.activeRaider() == null && guard++ < 1200) {
      advanceFrame(controller, effect);
    }
    VesperRaider raider = controller.activeRaider();
    assertTrue(raider != null, "the raider must appear inside the guard window");

    int frames = 0;
    while (!effect.hasSamples(raider) && frames++ < 4000) {
      advanceFrame(controller, effect);
    }
    assertTrue(effect.hasSamples(raider),
        "the raider's own reversal arcs must feed its wake from real displacement");
    assertTrue(effect.sampleCount(raider) > 0);
  }

  @Test
  void aDestroyedBomberStopsEmittingAndItsWakeFades() {
    PhaseOneGameController controller = startedController();
    EnemyYawSmokeEffect effect = new EnemyYawSmokeEffect();
    BastionBomber bomber = controller.bastionBomber();

    advanceUntilOrbiting(controller, effect);
    for (int frame = 0; frame < 180; frame++) {
      advanceFrame(controller, effect);
    }
    assertTrue(effect.hasSamples(bomber));

    controller.bastionBomberCombat().destroy();
    for (int frame = 0; frame < 5; frame++) {
      advanceFrame(controller, effect);
    }
    assertFalse(effect.isEmitting(bomber), "a destroyed bomber neither turns nor emits");
    for (int frame = 0; frame < 60; frame++) {
      advanceFrame(controller, effect);
    }
    assertEquals(0, effect.sampleCount(bomber),
        "the wake left before the destruction expires instead of freezing on screen");
  }

  @Test
  void endingTheMissionClearsEveryEnemyWake() {
    PhaseOneGameController controller = startedController();
    EnemyYawSmokeEffect effect = new EnemyYawSmokeEffect();

    advanceUntilOrbiting(controller, effect);
    for (int frame = 0; frame < 120; frame++) {
      advanceFrame(controller, effect);
    }
    assertTrue(effect.hasSamples());

    effect.update(STEP, false, controller);
    assertFalse(effect.hasSamples(), "a finished run must not keep enemy smoke on screen");
    assertEquals(0, effect.trackedCount());

    // The next run starts its own spans: nothing of the previous one is inherited.
    effect.update(STEP, true, controller);
    assertEquals(0, effect.totalSampleCount(), "a new run starts empty");
  }

  private static void advanceUntilOrbiting(PhaseOneGameController controller,
      EnemyYawSmokeEffect effect) {
    BastionBomber bomber = controller.bastionBomber();
    int guard = 0;
    while (bomber.flightState() != BastionBomber.FlightState.ORBITING && guard++ < 2000) {
      // Coarse slices while it waits and flies straight: the tracker clamps the observed slice and
      // the wake only matters once it really turns, so the test does not need 60 Hz for the trip.
      advanceFrame(controller, effect, 0.25f);
    }
    assertEquals(BastionBomber.FlightState.ORBITING, bomber.flightState(),
        "the bomber must reach its destination inside the guard window");
  }

  private static void advanceFrame(PhaseOneGameController controller,
      EnemyYawSmokeEffect effect) {
    advanceFrame(controller, effect, STEP);
  }

  private static void advanceFrame(PhaseOneGameController controller,
      EnemyYawSmokeEffect effect, float deltaSeconds) {
    controller.advanceEncounter(deltaSeconds);
    effect.update(deltaSeconds, true, controller);
  }

  private static TurnSample sample(BastionBomber bomber, EnemyYawSmokeEffect effect) {
    int count = effect.sampleCount(bomber);
    return new TurnSample(effect.sample(bomber, count - 1));
  }

  private static PhaseOneGameController startedController() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f, () -> 0f);
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    return controller;
  }

  /** Minimal immutable copy of one puff, so assertions do not depend on ring reuse later. */
  private record TurnSample(float x, float y) {
    private TurnSample(com.davidpe.cosmicaces.domain.effect.TurnSmokeTrail.Sample sample) {
      this(sample.x(), sample.y());
    }
  }
}
