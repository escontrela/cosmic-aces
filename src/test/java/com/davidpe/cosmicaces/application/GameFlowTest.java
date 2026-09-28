package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.GameSession;
import com.davidpe.cosmicaces.domain.game.PlayArea;
import com.davidpe.cosmicaces.domain.player.MovementIntent;
import org.junit.jupiter.api.Test;

class GameFlowTest {

  private static final PlayArea AREA = new PlayArea(800f, 600f);
  private static final float EPSILON = 0.001f;

  @Test
  void startGameEntersPlayingAndStartsTheRun() {
    GameFlow flow = new GameFlow(new GameSession());
    assertTrue(flow.startGame());
    assertFalse(flow.isRunFinished());
    assertEquals(60f, flow.remainingRunSeconds(), EPSILON);
  }

  @Test
  void advanceRunFinishesAtSixtySeconds() {
    GameFlow flow = new GameFlow(new GameSession());
    flow.startGame();
    flow.advanceRun(30f);
    assertFalse(flow.isRunFinished());
    assertEquals(30f, flow.remainingRunSeconds(), EPSILON);

    flow.advanceRun(30f);
    assertTrue(flow.isRunFinished());
    assertEquals(0f, flow.remainingRunSeconds(), EPSILON);
  }

  @Test
  void movementIntentMovesTheShipThroughTheUseCase() {
    GameFlow flow = new GameFlow(new GameSession());
    flow.startGame();
    flow.placeShip(100f, 100f, AREA);
    flow.applyMovementIntent(new MovementIntent(false, true, false, false), 1f, AREA);
    assertEquals(400f, flow.shipX(), EPSILON);
    assertEquals(100f, flow.shipY(), EPSILON);
  }

  @Test
  void shipMovementIsClampedToPlayArea() {
    GameFlow flow = new GameFlow(new GameSession());
    flow.startGame();
    flow.placeShip(0f, 0f, AREA);
    flow.applyMovementIntent(new MovementIntent(true, false, false, true), 100f, AREA);
    assertEquals(0f, flow.shipX(), EPSILON);
    assertEquals(0f, flow.shipY(), EPSILON);
  }

  @Test
  void encounterDoesNotSpawnBeforeTheRunStarts() {
    GameFlow flow = new GameFlow(new GameSession(() -> 0f));
    flow.advanceEncounter(100f, AREA);
    assertFalse(flow.isRaiderActive());
  }

  @Test
  void encounterAdvancesAndIsExposedThroughTheUseCase() {
    GameFlow flow = new GameFlow(new GameSession(() -> 0f)); // wait = 3s
    flow.startGame();
    flow.advanceEncounter(3f, AREA);
    assertTrue(flow.isRaiderActive());
    assertEquals(0f, flow.raiderX(), EPSILON);
    assertEquals(AREA.height(), flow.raiderY(), EPSILON);
    assertEquals(0, flow.raiderBank());

    flow.advanceEncounter(1f, AREA); // first turn with the 0f source: heading -20
    assertEquals(-1, flow.raiderBank());
    assertEquals(AREA.height() - 140f, flow.raiderY(), EPSILON);
  }

  @Test
  void encounterStopsAfterTheRunFinishes() {
    GameFlow flow = new GameFlow(new GameSession(() -> 0f));
    flow.startGame();
    flow.advanceRun(60f);
    assertTrue(flow.isRunFinished());
    flow.advanceEncounter(100f, AREA);
    assertFalse(flow.isRaiderActive());
  }
}