package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.*;

import com.davidpe.cosmicaces.domain.game.PlayArea;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import org.junit.jupiter.api.Test;

class PhaseOneGameControllerTest {
  private static final PlayArea RAIDER_AREA = new PlayArea(800f, 600f);

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
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.advanceEncounter(100f, RAIDER_AREA);
    assertFalse(controller.isRaiderActive());
    controller.start();
    controller.advanceEncounter(3f, RAIDER_AREA);
    assertTrue(controller.isRaiderActive());
    controller.advanceEncounter(1f, RAIDER_AREA);
    assertEquals(RAIDER_AREA.height() - 140f, controller.activeRaider().y(), 0.001f);
    controller.advanceRun(60f);
    controller.advanceEncounter(100f, RAIDER_AREA);
    assertEquals(RAIDER_AREA.height() - 140f, controller.activeRaider().y(), 0.001f);
  }
}
