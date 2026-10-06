package com.davidpe.cosmicaces.domain.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GameStateTest {

  private static final GameId GAME = new GameId(1L);

  @Test
  void newStateExposesItsInitialValues() {
    GameState state = new GameState(GAME, GamePhase.PLAYING_PHASE_ONE, 0, 3);

    assertEquals(GAME, state.gameId());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, state.phase());
    assertEquals(0, state.points());
    assertEquals(3, state.lives());
  }

  @Test
  void rejectsNegativePointsOrLives() {
    assertThrows(
        IllegalArgumentException.class, () -> new GameState(GAME, GamePhase.WELCOME, -1, 3));
    assertThrows(
        IllegalArgumentException.class, () -> new GameState(GAME, GamePhase.WELCOME, 0, -1));
  }

  @Test
  void addPointsAccumulatesAndRejectsNonPositiveAmounts() {
    GameState state = new GameState(GAME, GamePhase.PLAYING_PHASE_ONE, 0, 3);

    state.addPoints(100);
    state.addPoints(50);

    assertEquals(150, state.points());
    assertThrows(IllegalArgumentException.class, () -> state.addPoints(0));
    assertThrows(IllegalArgumentException.class, () -> state.addPoints(-10));
  }

  @Test
  void loseLifeDecrementsAndNeverGoesBelowZero() {
    GameState state = new GameState(GAME, GamePhase.PLAYING_PHASE_ONE, 0, 2);

    state.loseLife();
    assertEquals(1, state.lives());

    state.loseLife();
    assertEquals(0, state.lives());

    state.loseLife();
    assertEquals(0, state.lives());
  }

  @Test
  void changePhaseUpdatesThePhase() {
    GameState state = new GameState(GAME, GamePhase.PLAYING_PHASE_ONE, 0, 3);

    state.changePhase(GamePhase.GAME_OVER);

    assertEquals(GamePhase.GAME_OVER, state.phase());
  }

  @Test
  void snapshotReflectsTheCurrentValues() {
    GameState state = new GameState(GAME, GamePhase.PLAYING_PHASE_ONE, 0, 3);
    state.addPoints(150);
    state.loseLife();

    PhaseResult result = state.snapshot();

    assertEquals(GAME, result.gameId());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, result.phase());
    assertEquals(150, result.points());
    assertEquals(2, result.lives());
  }

  @Test
  void completingAPhaseDoesNotDoubleCountPointsAlreadyEarned() {
    GameState state = new GameState(GAME, GamePhase.PLAYING_PHASE_ONE, 0, 3);
    state.addPoints(150);

    PhaseResult result = state.snapshot();
    state.completePhase(result);
    state.completePhase(result);

    assertEquals(150, state.points());
    assertEquals(3, state.lives());
  }
}
