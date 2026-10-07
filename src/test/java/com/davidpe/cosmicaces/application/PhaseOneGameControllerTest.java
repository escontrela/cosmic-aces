package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.PlayArea;
import com.davidpe.cosmicaces.domain.ship.MovementIntent;
import org.junit.jupiter.api.Test;

class PhaseOneGameControllerTest {

  private static final PlayArea AREA = new PlayArea(800f, 600f);
  private static final float EPSILON = 0.001f;

  @Test
  void runStartsAndFinishesAtSixtySeconds() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.start();
    assertEquals(60f, controller.remainingRunSeconds(), EPSILON);
    controller.advanceRun(30f);
    assertFalse(controller.isRunFinished());
    assertEquals(30f, controller.remainingRunSeconds(), EPSILON);
    controller.advanceRun(30f);
    assertTrue(controller.isRunFinished());
    assertEquals(0f, controller.remainingRunSeconds(), EPSILON);
  }

  @Test
  void commonControllerMovesAndClampsTheHero() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeShip(100f, 100f, AREA);
    controller.applyMovementIntent(
        MovementIntent.fromDirections(false, true, false, false), 1f, AREA, false);
    assertEquals(400f, controller.astra().x(), EPSILON);
    controller.applyMovementIntent(
        MovementIntent.fromDirections(true, false, false, true), 100f, AREA, false);
    assertEquals(0f, controller.astra().x(), EPSILON);
    assertEquals(0f, controller.astra().y(), EPSILON);
  }

  @Test
  void encounterOnlyAdvancesDuringTheRun() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.advanceEncounter(100f, AREA);
    assertFalse(controller.isRaiderActive());
    controller.start();
    controller.advanceEncounter(3f, AREA);
    assertTrue(controller.isRaiderActive());
    assertEquals(AREA.height(), controller.activeRaider().y(), EPSILON);
    controller.advanceEncounter(1f, AREA);
    assertEquals(-1, controller.activeRaider().bank());
    assertEquals(AREA.height() - 140f, controller.activeRaider().y(), EPSILON);
    controller.advanceRun(60f);
    controller.advanceEncounter(100f, AREA);
    assertEquals(AREA.height() - 140f, controller.activeRaider().y(), EPSILON);
  }
}
