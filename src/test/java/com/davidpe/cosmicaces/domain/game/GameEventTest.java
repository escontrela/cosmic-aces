package com.davidpe.cosmicaces.domain.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GameEventTest {

  private static final GameId GAME = new GameId(1L);
  private static final GameId OTHER_GAME = new GameId(2L);

  @Test
  void eventsExposeGameIdentityAndPhase() {
    GameEvent start = new StartRequested(GAME, GamePhase.WELCOME);
    GameEvent points = new PointsEarned(GAME, GamePhase.PLAYING_PHASE_ONE, 100);
    GameEvent life = new LifeLost(GAME, GamePhase.PLAYING_PHASE_ONE);
    GameEvent abandoned = new GameAbandoned(GAME, GamePhase.PLAYING_PHASE_ONE);

    assertEquals(GAME, start.gameId());
    assertEquals(GamePhase.WELCOME, start.phase());
    assertEquals(GAME, points.gameId());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, points.phase());
    assertEquals(GAME, life.gameId());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, life.phase());
    assertEquals(GAME, abandoned.gameId());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, abandoned.phase());
  }

  @Test
  void pointsEarnedRequiresAPositiveAmount() {
    assertThrows(
        IllegalArgumentException.class, () -> new PointsEarned(GAME, GamePhase.PLAYING_PHASE_ONE, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PointsEarned(GAME, GamePhase.PLAYING_PHASE_ONE, -5));
  }

  @Test
  void phaseCompletedDelegatesIdentityAndPhaseToItsResult() {
    PhaseResult result = new PhaseResult(GAME, GamePhase.PLAYING_PHASE_ONE, 150, 2);

    PhaseCompleted completed = new PhaseCompleted(result);

    assertEquals(GAME, completed.gameId());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, completed.phase());
    assertSame(result, completed.result());
  }

  @Test
  void phaseResultIsAnImmutableValue() {
    PhaseResult result = new PhaseResult(GAME, GamePhase.PLAYING_PHASE_ONE, 150, 2);
    PhaseResult same = new PhaseResult(GAME, GamePhase.PLAYING_PHASE_ONE, 150, 2);

    assertEquals(result, same);
    assertEquals(result.hashCode(), same.hashCode());
    assertNotEquals(result, new PhaseResult(GAME, GamePhase.PLAYING_PHASE_ONE, 151, 2));
  }

  @Test
  void eventsFromAPreviousGameOrPhaseRemainDistinguishable() {
    GameEvent current = new PointsEarned(GAME, GamePhase.PLAYING_PHASE_ONE, 10);
    GameEvent previousGame = new PointsEarned(OTHER_GAME, GamePhase.PLAYING_PHASE_ONE, 10);
    GameEvent previousPhase = new PointsEarned(GAME, GamePhase.WELCOME, 10);

    assertNotEquals(current.gameId(), previousGame.gameId());
    assertNotEquals(current.phase(), previousPhase.phase());
  }

  @Test
  void gameIdRejectsNegativeValues() {
    assertThrows(IllegalArgumentException.class, () -> new GameId(-1L));
  }
}
