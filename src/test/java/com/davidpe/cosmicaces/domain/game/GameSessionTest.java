package com.davidpe.cosmicaces.domain.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.player.MovementIntent;
import org.junit.jupiter.api.Test;

/**
 * Covers the per-run behavior that the retired {@code GameFlowTest} verified through the application
 * use case: the run starts and times out at 60 seconds, and the ship moves through the session,
 * clamped to the play area.
 */
class GameSessionTest {

  private static final PlayArea AREA = new PlayArea(800f, 600f);
  private static final float EPSILON = 0.001f;

  @Test
  void startStartsTheRun() {
    GameSession session = new GameSession();

    session.start();

    assertFalse(session.isRunFinished());
    assertEquals(60f, session.remainingRunSeconds(), EPSILON);
  }

  @Test
  void advanceRunFinishesAtSixtySeconds() {
    GameSession session = new GameSession();
    session.start();

    session.advanceRun(30f);
    assertFalse(session.isRunFinished());
    assertEquals(30f, session.remainingRunSeconds(), EPSILON);

    session.advanceRun(30f);
    assertTrue(session.isRunFinished());
    assertEquals(0f, session.remainingRunSeconds(), EPSILON);
  }

  @Test
  void movementIntentMovesTheShip() {
    GameSession session = new GameSession();
    session.start();
    session.placeShip(100f, 100f, AREA);

    session.applyMovementIntent(new MovementIntent(false, true, false, false), 1f, AREA);

    assertEquals(400f, session.shipX(), EPSILON);
    assertEquals(100f, session.shipY(), EPSILON);
  }

  @Test
  void shipMovementIsClampedToPlayArea() {
    GameSession session = new GameSession();
    session.start();
    session.placeShip(0f, 0f, AREA);

    session.applyMovementIntent(new MovementIntent(true, false, false, true), 100f, AREA);

    assertEquals(0f, session.shipX(), EPSILON);
    assertEquals(0f, session.shipY(), EPSILON);
  }
}
