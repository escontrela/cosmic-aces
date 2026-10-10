package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.*;

import com.davidpe.cosmicaces.domain.player.FlightControls;
import org.junit.jupiter.api.Test;

class PhaseOneGameControllerTest {

  @Test void flightAndRunStopAtSixtySeconds() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.advanceFlight(FlightControls.neutral(), 1f, false);
    assertEquals(100f, controller.astra().y(), 0.001f);
    controller.start();
    controller.advanceRun(59.5f);
    float before = controller.astra().y();
    controller.advanceFlight(FlightControls.neutral(), 5f, false);
    assertTrue(controller.isRunFinished());
    assertEquals(0f, controller.remainingRunSeconds());
    assertEquals(before + 115f, controller.astra().y(), 0.2f);
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

    controller.advanceRun(60f);
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

  @Test void baseAndCoincidenceScoreAccumulateThenFreezeAtSixtySeconds() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();

    for (int second = 0; second < 60; second++) {
      assertEquals(11, controller.advanceFlight(FlightControls.neutral(), 1f, true));
    }

    assertTrue(controller.isRunFinished());
    assertEquals(60 + 600, controller.scorePoints());
    assertEquals(0, controller.advanceFlight(FlightControls.neutral(), 1f, true));
    assertEquals(660, controller.scorePoints());
  }

  @Test void oversizedDeltaScoresOnlyTheRemainingRunFraction() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();

    assertEquals(30, controller.advanceFlight(FlightControls.neutral(), 30f, false));
    assertEquals(30, controller.advanceFlight(FlightControls.neutral(), 100f, false));
    assertTrue(controller.isRunFinished());
    assertEquals(60, controller.scorePoints());
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
}
