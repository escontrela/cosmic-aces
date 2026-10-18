package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.enemy.BastionBomber;
import com.davidpe.cosmicaces.domain.enemy.BastionEscort;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.scenery.WorldRect;
import org.junit.jupiter.api.Test;

/**
 * Integration of the Bastion bomber with the mission of PhaseOne (COS-98): the controller translates
 * the real simulation into the two semantic facts of
 * {@link com.davidpe.cosmicaces.domain.game.PhaseOneMission} — the bomber downed before arriving and
 * the arrival of a bomber still alive — and the mission keeps the single decision point. Every test
 * drives the controller with the same call order the screen uses and asserts only the player-visible
 * outcome; appearance and layout are not inspected here and remain the human PO's visual QA.
 *
 * <p>The scenery is procedural but the bomber path is fixed: both bases share the world centre X, so
 * the bomber flies straight south from its runway to the operations base. The tests rely on that
 * geometry instead of hard-coding scenery coordinates.
 */
class PhaseOneBastionMissionTest {

  /** Whole active slice used to fly the bomber and, in the race test, to hold the trigger. */
  private static final float SLICE = 5f;
  private static final float BURST_DELTA = 1f / 60f;
  private static final float RANGE = 10_000f;
  private static final int APPROACH_SECONDS = 300;

  @Test
  void liveArrivalLosesTheRunEvenWithTheThreeVesperKills() {
    PhaseOneGameController controller = startedController();
    recordVesperKillsUpToTheGoal(controller);
    assertEquals(PhaseOutcome.IN_PROGRESS, controller.resolveMissionOutcome(3),
        "three kills alone are only half of the joint goal");

    advanceTheEncounterUntilTheBomberArrives(controller);

    assertEquals(BastionBomber.FlightState.ORBITING, controller.bastionBomber().flightState(),
        "the live bomber reached its destination ring");
    assertEquals(600f, controller.remainingRunSeconds(), 0.001f,
        "the defeat cannot come from an expired clock: the run clock was never advanced");
    assertEquals(PhaseOutcome.LOSE, controller.resolveMissionOutcome(3),
        "the arrival of a live bomber loses the run even with 3/3 Vesper and lives left");
    assertEquals(PhaseOutcome.LOSE, controller.resolveMissionOutcome(0),
        "the arrival defeat is the single terminal outcome already decided");
  }

  @Test
  void bomberDownedBeforeItsArrivalKeepsTheRunAlive() {
    PhaseOneGameController controller = startedController();
    advanceTheEncounterUntilTheBomberHasTakenOff(controller);
    assertEquals(BastionBomber.FlightState.INBOUND, controller.bastionBomber().flightState(),
        "the bomber is inbound when Astra downs it");
    isolateTheBomberOnTheFiringLine(controller);
    placeAstraWatchingTheBomber(controller, 300f);

    fireUntilTheBomberFalls(controller);

    assertTrue(controller.bastionBomberCombat().isDestroyed(),
        "Astra's burst must down the inbound bomber");
    assertEquals(0, controller.vesperKills(),
        "an isolated burst against the bomber counts no Vesper kill");
    assertEquals(PhaseOutcome.IN_PROGRESS, controller.resolveMissionOutcome(3),
        "a bomber downed before its arrival must not latch any defeat");

    // The encounter keeps running far beyond the time the bomber needed to arrive: a downed bomber
    // never reaches the destination ring, so the run survives on lives and clock alone.
    for (int second = 0; second < APPROACH_SECONDS; second++) {
      controller.advanceEncounter(1f);
    }
    assertEquals(PhaseOutcome.IN_PROGRESS, controller.resolveMissionOutcome(3),
        "no arrival can be latched after the bomber was destroyed");
    assertEquals(600f, controller.remainingRunSeconds(), 0.001f,
        "the surviving run keeps its full clock: no timeout happened");

    recordVesperKillsUpToTheGoal(controller);
    assertEquals(PhaseOutcome.WIN, controller.resolveMissionOutcome(3),
        "the three kills after the downing complete the joint goal: the real destruction was recorded");
  }

  @Test
  void theArrivalOfTheSliceWinsTheRaceAgainstTheKillingBlowOfTheSameSlice() {
    PhaseOneGameController controller = startedController();

    // Fly whole slices until the bomber needs less than one final slice to reach the ring, so the
    // next encounter advance makes it arrive inside that slice and still alive.
    int guard = 0;
    while (distanceToTheDestinationRing(controller) > BastionBomber.FLIGHT_SPEED * SLICE
        && guard < 1000) {
      controller.advanceEncounter(SLICE);
      guard++;
    }
    assertEquals(BastionBomber.FlightState.INBOUND, controller.bastionBomber().flightState(),
        "the bomber is one slice away from arriving when the final slice opens");

    // The screen order inside one authorized slice: the encounter advances first, so the bomber
    // arrives alive and the defeat is latched before the projectiles of the same slice resolve.
    controller.beginActiveFrame(SLICE);
    controller.advanceEncounter(SLICE);
    assertEquals(BastionBomber.FlightState.ORBITING, controller.bastionBomber().flightState(),
        "the bomber reaches its destination inside the final slice");
    isolateTheBomberOnTheFiringLine(controller);
    placeAstraWatchingTheBomber(controller, 150f);
    controller.advanceWeapons(SLICE, SLICE, true, false, RANGE);
    controller.endActiveFrame();

    assertTrue(controller.bastionBomberCombat().isDestroyed(),
        "Astra's burst still lands the killing blow inside the same slice");
    assertEquals(0, controller.vesperKills(),
        "an isolated burst against the bomber counts no Vesper kill");
    assertEquals(PhaseOutcome.LOSE, controller.resolveMissionOutcome(3),
        "the arrival wins the race: same-slice destruction records no downing and cannot rescue the run");

    // Terminal and idempotent: later frames, impacts and sightings never change the result.
    controller.beginActiveFrame(1f);
    controller.advanceEncounter(1f);
    controller.advanceWeapons(1f, 1f, true, false, RANGE);
    controller.endActiveFrame();
    assertEquals(PhaseOutcome.LOSE, controller.resolveMissionOutcome(3),
        "the arrival defeat stays terminal and idempotent");
  }

  /** A deterministic controller whose run is already started, with Astra placed in the world. */
  private static PhaseOneGameController startedController() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f, () -> 0f);
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    return controller;
  }

  /** Counts the accepted Vesper kills up to the mission goal through the deterministic seam. */
  private static void recordVesperKillsUpToTheGoal(PhaseOneGameController controller) {
    while (controller.vesperKills() < controller.vesperKillGoal()) {
      assertTrue(controller.recordVesperKill(), "each recorded Vesper kill must advance the counter");
    }
  }

  /** Advances the encounter until the bomber has left its runway and is flying to its destination. */
  private static void advanceTheEncounterUntilTheBomberHasTakenOff(
      PhaseOneGameController controller) {
    BastionBomber bomber = controller.bastionBomber();
    int guard = 0;
    while (!bomber.hasTakenOff() && guard < 1000) {
      controller.advanceEncounter(1f);
      guard++;
    }
    assertTrue(bomber.hasTakenOff(), "the bomber must leave its runway inside the guard window");
  }

  /** Advances the encounter (and nothing else) until the live bomber enters its destination ring. */
  private static void advanceTheEncounterUntilTheBomberArrives(PhaseOneGameController controller) {
    BastionBomber bomber = controller.bastionBomber();
    int guard = 0;
    while (bomber.flightState() != BastionBomber.FlightState.ORBITING && guard < 1000) {
      controller.advanceEncounter(1f);
      guard++;
    }
    assertEquals(BastionBomber.FlightState.ORBITING, bomber.flightState(),
        "the bomber must reach its destination inside the guard window");
  }

  /** Units the bomber still has to fly before it enters the ring around its destination. */
  private static float distanceToTheDestinationRing(PhaseOneGameController controller) {
    WorldRect destination = controller.phaseOneScenery().operationsBasePlacement().bounds();
    BastionBomber bomber = controller.bastionBomber();
    return (float) Math.hypot(bomber.centerX() - destination.centerX(),
        bomber.centerY() - destination.centerY()) - BastionBomber.ORBIT_RADIUS;
  }

  /**
   * Leaves only the bomber in the firing line: the two wingmen are removed with their own idempotent
   * destruction (as if Astra had downed them without using the controller kill path) and the single
   * Raider is parked in the south-west corner, behind any muzzle that fires at the bomber. This is
   * test isolation only; it does not change any rule under test.
   */
  private static void isolateTheBomberOnTheFiringLine(PhaseOneGameController controller) {
    controller.bastionEscorts().forEach(BastionEscort::destroy);
    VesperRaider raider = controller.activeRaider();
    if (raider != null) {
      raider.placeAt(100f, 100f, 0f, PhaseOneGameController.WORLD);
    }
  }

  /** Holds the trigger until the burst completes the third damage step of the bomber. */
  private static void fireUntilTheBomberFalls(PhaseOneGameController controller) {
    int guard = 0;
    while (!controller.bastionBomberCombat().isDestroyed() && guard < 1200) {
      controller.advanceWeapons(BURST_DELTA, BURST_DELTA, true, false, RANGE);
      guard++;
    }
  }

  /**
   * Places Astra on the base side of the bomber, {@code standoff} units away, with her nose aimed at
   * the bomber's current centre. The bomber keeps that position while the same slice's projectiles
   * are resolved, so the burst crosses its hull.
   */
  private static void placeAstraWatchingTheBomber(PhaseOneGameController controller,
      float standoff) {
    WorldRect destination = controller.phaseOneScenery().operationsBasePlacement().bounds();
    BastionBomber bomber = controller.bastionBomber();
    float toDestinationX = (float) (destination.centerX() - bomber.centerX());
    float toDestinationY = (float) (destination.centerY() - bomber.centerY());
    float length = (float) Math.hypot(toDestinationX, toDestinationY);
    float unitX = toDestinationX / length;
    float unitY = toDestinationY / length;
    controller.placeAstra(
        bomber.centerX() + unitX * standoff - controller.astra().drawWidth() / 2f,
        bomber.centerY() + unitY * standoff - controller.astra().drawHeight() / 2f, 0f);
    // Re-aim from Astra's clamped centre so the burst always crosses the bomber hull.
    float aimX = bomber.centerX() - (controller.astra().x() + controller.astra().drawWidth() / 2f);
    float aimY = bomber.centerY() - (controller.astra().y() + controller.astra().drawHeight() / 2f);
    controller.placeAstra(controller.astra().x(), controller.astra().y(),
        (float) Math.toDegrees(Math.atan2(aimX, aimY)));
  }
}
