package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.*;

import com.davidpe.cosmicaces.domain.enemy.GhostwingDrone;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.player.FlightTuning;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhaseOneGameControllerTest {

  @Test
  void phaseSceneryUsesAFixedSeedAndIsRetainedByTheRunController() {
    PhaseOneGameController controller = new PhaseOneGameController();

    PhaseOneScenery scenery = controller.phaseOneScenery();

    assertSame(scenery, controller.phaseOneScenery());
    assertEquals(PhaseOneScenery.DEFAULT_SEED, scenery.config().seed());
    assertEquals(128, scenery.terrainMap().columns());
    assertEquals(188, scenery.terrainMap().rows());
    assertFalse(scenery.sceneryField().instances().isEmpty());
  }

  @Test void flightAndRunStopAtTenMinutes() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.advanceFlight(FlightControls.neutral(), 1f, false);
    assertEquals(100f, controller.astra().y(), 0.001f);
    controller.start();
    controller.advanceRun(599.5f);
    float before = controller.astra().y();
    controller.advanceFlight(FlightControls.neutral(), 5f, false);
    assertTrue(controller.isRunFinished());
    assertEquals(0f, controller.remainingRunSeconds());
    assertEquals(before + FlightTuning.NORMAL_SPEED * 0.5f, controller.astra().y(), 0.2f);
    before = controller.astra().y();
    controller.advanceFlight(FlightControls.neutral(), 1f, false);
    assertEquals(before, controller.astra().y());
  }

  @Test void encounterOnlyAdvancesDuringTheRun() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f); // spawn wait = 3s
    controller.placeAstra(2000f, 600f, 0f);
    controller.advanceEncounter(100f);
    assertFalse(controller.isRaiderActive()); // the run has not started yet

    controller.start();
    controller.advanceEncounter(3f);
    assertTrue(controller.isRaiderActive());
    float spawnY = controller.activeRaider().y();

    controller.advanceEncounter(1f);
    assertTrue(controller.activeRaider().y() < spawnY,
        "the single raider must move in world coordinates toward Astra");

    controller.advanceRun(600f);
    float frozenY = controller.activeRaider().y();
    controller.advanceEncounter(100f);
    assertEquals(frozenY, controller.activeRaider().y(), 0.001f);
  }

  @Test void scoringDoesNotStartBeforeTheRun() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);

    assertEquals(0, controller.advanceFlight(FlightControls.neutral(), 1f, true));
    assertEquals(0, controller.scorePoints());
  }

  @Test void baseAndCoincidenceScoreAccumulateThenFreezeAtTenMinutes() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();

    for (int second = 0; second < 600; second++) {
      assertEquals(11, controller.advanceFlight(FlightControls.neutral(), 1f, true));
    }

    assertTrue(controller.isRunFinished());
    assertEquals(600 + 6000, controller.scorePoints());
    assertEquals(0, controller.advanceFlight(FlightControls.neutral(), 1f, true));
    assertEquals(6600, controller.scorePoints());
  }

  @Test void oversizedDeltaScoresOnlyTheRemainingRunFraction() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();

    assertEquals(30, controller.advanceFlight(FlightControls.neutral(), 30f, false));
    assertEquals(570, controller.advanceFlight(FlightControls.neutral(), 1000f, false));
    assertTrue(controller.isRunFinished());
    assertEquals(600, controller.scorePoints());
  }

  @Test void coincidenceBonusOnlyAccumulatesWhileVisible() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();

    for (int second = 0; second < 2; second++) {
      controller.advanceFlight(FlightControls.neutral(), 1f, true);
    }
    for (int second = 0; second < 8; second++) {
      controller.advanceFlight(FlightControls.neutral(), 1f, false);
    }

    assertEquals(30, controller.scorePoints());
  }

  @Test
  void ghostwingGroupsOnlyAdvanceDuringTheRun() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f, () -> 0f);
    controller.placeAstra(2000f, 600f, 0f);
    controller.advanceGhostwing(100f);
    assertTrue(controller.ghostwingEncounter().groups().isEmpty(),
        "the run has not started yet");

    controller.start();
    controller.advanceGhostwing(20f);
    assertEquals(1, controller.ghostwingEncounter().groups().size());
    float frozenX = controller.ghostwingEncounter().activeDrones().get(0).x();

    controller.advanceRun(600f);
    controller.advanceGhostwing(100f);
    assertEquals(frozenX, controller.ghostwingEncounter().activeDrones().get(0).x(), 0.001f,
        "groups must freeze once the run is finished");
  }

  @Test
  void ghostwingAdvanceFeedsOnlyTheActiveRunSlice() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f, () -> 0f);
    controller.placeAstra(2000f, 600f, 0f);
    controller.start();
    controller.advanceRun(580f); // only 20 s of run time remain
    controller.advanceGhostwing(30f); // feeds exactly the 20 active seconds
    List<GhostwingDrone> drones = controller.ghostwingEncounter().activeDrones();
    assertEquals(2, drones.size());
    assertEquals(-72f + 90f * 20f, drones.get(0).x(), 0.001f,
        "only the active run slice may drive scheduling and flight");
  }

  @Test
  void missionExposesTenMinuteGoalAndCountsDownAfterStart() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    assertEquals(600f, controller.missionTimeLimitSeconds());
    assertEquals(3, controller.vesperKillGoal());
    assertEquals(0, controller.vesperKills());
    assertEquals(600f, controller.remainingRunSeconds(), 0.001f,
        "the full 10:00 is available before the mission starts");
    assertFalse(controller.isRunStarted());

    controller.start();
    assertEquals(1, controller.advanceFlight(FlightControls.neutral(), 1f, false));
    assertEquals(599f, controller.remainingRunSeconds(), 0.001f,
        "one active second leaves 09:59 on the clock");
    assertFalse(controller.isRunFinished());
  }

  @Test
  void missionClockClampsToZeroAtTheTimeLimitAndStopsAdvancing() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();
    assertEquals(600, controller.advanceFlight(FlightControls.neutral(), 600f, false));
    assertEquals(0f, controller.remainingRunSeconds(), 0.001f);
    assertTrue(controller.isRunFinished());

    assertEquals(0, controller.advanceFlight(FlightControls.neutral(), 60f, false));
    assertEquals(0f, controller.remainingRunSeconds(), 0.001f,
        "time never accumulates after the run finishes");
  }

  @Test
  void scoreAndRemainingTimeAreIndependentOfDeltaPartitioning() {
    PhaseOneGameController big = new PhaseOneGameController();
    big.placeAstra(1600f, 100f, 0f);
    big.start();
    int bigEarned = big.advanceFlight(FlightControls.neutral(), 10f, true);

    PhaseOneGameController many = new PhaseOneGameController();
    many.placeAstra(1600f, 100f, 0f);
    many.start();
    int manyEarned = 0;
    for (int second = 0; second < 10; second++) {
      manyEarned += many.advanceFlight(FlightControls.neutral(), 1f, true);
    }

    assertEquals(bigEarned, manyEarned,
        "the same score must be earned whatever way the delta is partitioned");
    assertEquals(590f, big.remainingRunSeconds(), 0.001f);
    assertEquals(big.remainingRunSeconds(), many.remainingRunSeconds(), 0.001f,
        "the remaining clock must not depend on how the delta is split");
  }

  @Test
  void missionOutcomeStaysInProgressUntilTheAuthoritativeFactsDecideIt() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();
    assertEquals(PhaseOutcome.IN_PROGRESS, controller.missionOutcome());
    assertEquals(PhaseOutcome.IN_PROGRESS, controller.resolveMissionOutcome(3));
  }

  @Test
  void fewerThanThreeKillsNeverResolveWinThroughTheController() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();
    controller.recordVesperKill();
    controller.recordVesperKill();
    assertEquals(2, controller.vesperKills());
    assertEquals(PhaseOutcome.IN_PROGRESS, controller.resolveMissionOutcome(3));

    controller.advanceFlight(FlightControls.neutral(), 600f, false);
    assertEquals(PhaseOutcome.LOSE, controller.resolveMissionOutcome(3),
        "the deadline loses when the goal is not reached");
  }

  @Test
  void theJointGoalResolvesWinAndLosesOnlyWhenLivesAreExhausted() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();
    controller.recordVesperKill();
    controller.recordVesperKill();
    controller.recordVesperKill();
    assertEquals(3, controller.vesperKills());
    assertEquals(PhaseOutcome.IN_PROGRESS, controller.resolveMissionOutcome(3),
        "three kills alone are only half of the joint goal");
    assertTrue(controller.recordBastionBomberDestroyed(),
        "the Bastion bomber was downed before its arrival");
    assertEquals(PhaseOutcome.WIN, controller.resolveMissionOutcome(3));

    // A terminal outcome never changes, even when the very same facts are re-resolved later.
    assertEquals(PhaseOutcome.WIN, controller.resolveMissionOutcome(0));

    PhaseOneGameController tie = new PhaseOneGameController();
    tie.placeAstra(1600f, 100f, 0f);
    tie.start();
    tie.recordVesperKill();
    tie.recordVesperKill();
    tie.recordVesperKill();
    tie.recordBastionBomberDestroyed();
    assertEquals(PhaseOutcome.LOSE, tie.resolveMissionOutcome(0),
        "simultaneous last life and completed goals is a defeat");
  }

  @Test
  void terminalOutcomeSurvivesLaterFactsAndKillsAreCapped() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();
    controller.advanceFlight(FlightControls.neutral(), 600f, false);
    assertEquals(PhaseOutcome.LOSE, controller.resolveMissionOutcome(3));
    assertEquals(PhaseOutcome.LOSE, controller.resolveMissionOutcome(1));
    assertFalse(controller.recordVesperKill(),
        "a kill recorded after an already terminal outcome is ignored");
    assertEquals(0, controller.vesperKills());
  }
}
