package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.*;

import com.davidpe.cosmicaces.domain.player.FlightControls;
import org.junit.jupiter.api.Test;

class PhaseOneGameControllerTest {

  @Test void flightAndRunStopAtSixtySeconds() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.advanceFlight(FlightControls.neutral(), 1f);
    assertEquals(100f, controller.astra().y(), 0.001f);
    controller.start();
    controller.advanceRun(59.5f);
    float before = controller.astra().y();
    controller.advanceFlight(FlightControls.neutral(), 5f);
    assertTrue(controller.isRunFinished());
    assertEquals(0f, controller.remainingRunSeconds());
    assertEquals(before + 150f, controller.astra().y(), 0.2f);
    before = controller.astra().y();
    controller.advanceFlight(FlightControls.neutral(), 1f);
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
}
