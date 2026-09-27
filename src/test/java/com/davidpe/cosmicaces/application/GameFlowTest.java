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
}