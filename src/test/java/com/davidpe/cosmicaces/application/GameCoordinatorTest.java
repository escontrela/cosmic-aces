package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidpe.cosmicaces.domain.game.GameAbandoned;
import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.GameState;
import com.davidpe.cosmicaces.domain.game.LifeLost;
import com.davidpe.cosmicaces.domain.game.PhaseCompleted;
import com.davidpe.cosmicaces.domain.game.PhaseResult;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import com.davidpe.cosmicaces.domain.game.StartRequested;
import org.junit.jupiter.api.Test;

class GameCoordinatorTest {

  private static final int STARTING_LIVES = 3;

  @Test
  void startsInWelcomeWithAFreshStateAndNoPendingTransition() {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);

    GameState state = coordinator.state();

    assertEquals(GamePhase.WELCOME, state.phase());
    assertEquals(0, state.points());
    assertEquals(STARTING_LIVES, state.lives());
    assertNull(coordinator.consumePendingTransition());
  }

  @Test
  void rejectsNegativeStartingLives() {
    assertThrows(IllegalArgumentException.class, () -> new GameCoordinator(-1));
  }

  @Test
  void startRequestCreatesANewGameAndQueuesThePlayingTransition() {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);
    GameId welcomeId = coordinator.state().gameId();

    coordinator.onEvent(new StartRequested(welcomeId, GamePhase.WELCOME));

    GameState state = coordinator.state();
    assertNotEquals(welcomeId, state.gameId());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, state.phase());
    assertEquals(0, state.points());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, coordinator.consumePendingTransition());
    assertNull(coordinator.consumePendingTransition());
  }

  @Test
  void pointsAndLivesContinueAcrossPhaseCompletion() {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameState state = coordinator.state();

    coordinator.onEvent(new PointsEarned(state.gameId(), GamePhase.PLAYING_PHASE_ONE, 120));
    coordinator.onEvent(new LifeLost(state.gameId(), GamePhase.PLAYING_PHASE_ONE));
    coordinator.onEvent(
        new PhaseCompleted(
            new PhaseResult(
                state.gameId(), GamePhase.PLAYING_PHASE_ONE, 120, STARTING_LIVES - 1)));

    assertEquals(120, coordinator.state().points());
    assertEquals(STARTING_LIVES - 1, coordinator.state().lives());
    assertEquals(GamePhase.GAME_OVER, coordinator.state().phase());
  }

  @Test
  void completingAPhaseDoesNotDoubleCountAlreadyEarnedPoints() {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameState state = coordinator.state();
    coordinator.onEvent(new PointsEarned(state.gameId(), GamePhase.PLAYING_PHASE_ONE, 120));
    PhaseResult result =
        new PhaseResult(state.gameId(), GamePhase.PLAYING_PHASE_ONE, 120, STARTING_LIVES);

    coordinator.onEvent(new PhaseCompleted(result));
    coordinator.onEvent(new PhaseCompleted(result));

    assertEquals(120, coordinator.state().points());
    assertEquals(STARTING_LIVES, coordinator.state().lives());
  }

  @Test
  void ignoresEventsFromAnotherGameOrPhase() {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameState state = coordinator.state();

    coordinator.onEvent(new PointsEarned(new GameId(999L), GamePhase.PLAYING_PHASE_ONE, 50));
    coordinator.onEvent(new PointsEarned(state.gameId(), GamePhase.WELCOME, 50));
    coordinator.onEvent(new LifeLost(new GameId(999L), GamePhase.PLAYING_PHASE_ONE));

    assertEquals(0, coordinator.state().points());
    assertEquals(STARTING_LIVES, coordinator.state().lives());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, coordinator.state().phase());
  }

  @Test
  void abandoningCreatesANewIdentityAndQueuesWelcome() {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameId playingId = coordinator.state().gameId();

    coordinator.onEvent(new GameAbandoned(playingId, GamePhase.PLAYING_PHASE_ONE));

    assertNotEquals(playingId, coordinator.state().gameId());
    assertEquals(GamePhase.WELCOME, coordinator.state().phase());
    assertEquals(GamePhase.WELCOME, coordinator.consumePendingTransition());
    assertNull(coordinator.consumePendingTransition());
  }

  @Test
  void ignoresAbandonFromAnotherGame() {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameId playingId = coordinator.state().gameId();

    coordinator.onEvent(new GameAbandoned(new GameId(999L), GamePhase.PLAYING_PHASE_ONE));

    assertEquals(playingId, coordinator.state().gameId());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, coordinator.state().phase());
    assertNull(coordinator.consumePendingTransition());
  }

  @Test
  void restartAfterAbandonUsesAFreshIdentityAndClearsPointsAndLives() {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameState firstGame = coordinator.state();
    coordinator.onEvent(new PointsEarned(firstGame.gameId(), GamePhase.PLAYING_PHASE_ONE, 200));
    coordinator.onEvent(new GameAbandoned(firstGame.gameId(), GamePhase.PLAYING_PHASE_ONE));
    coordinator.consumePendingTransition();
    GameId lobbyId = coordinator.state().gameId();

    coordinator.onEvent(new StartRequested(lobbyId, GamePhase.WELCOME));

    assertNotEquals(firstGame.gameId(), coordinator.state().gameId());
    assertNotEquals(lobbyId, coordinator.state().gameId());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, coordinator.state().phase());
    assertEquals(0, coordinator.state().points());
    assertEquals(STARTING_LIVES, coordinator.state().lives());
  }

  @Test
  void ignoresStartRequestSavedFromAPreviousWelcome() {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);
    StartRequested staleStart = new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME);
    coordinator.onEvent(staleStart);
    coordinator.consumePendingTransition();
    GameId playingId = coordinator.state().gameId();
    coordinator.onEvent(new PointsEarned(playingId, GamePhase.PLAYING_PHASE_ONE, 120));

    // Replaying the welcome's saved start must not restart the active game.
    coordinator.onEvent(staleStart);

    assertEquals(playingId, coordinator.state().gameId());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, coordinator.state().phase());
    assertEquals(120, coordinator.state().points());
    assertEquals(STARTING_LIVES, coordinator.state().lives());
    assertNull(coordinator.consumePendingTransition());
  }

  @Test
  void ignoresStartRequestThatDoesNotMatchTheCurrentWelcomeState() {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);
    GameState welcome = coordinator.state();

    coordinator.onEvent(new StartRequested(new GameId(999L), GamePhase.WELCOME));
    coordinator.onEvent(new StartRequested(welcome.gameId(), GamePhase.PLAYING_PHASE_ONE));

    assertSame(welcome, coordinator.state());
    assertEquals(GamePhase.WELCOME, coordinator.state().phase());
    assertNull(coordinator.consumePendingTransition());
  }

  @Test
  void ignoresAbandonWithTheWrongPhaseWhilePlaying() {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameState playing = coordinator.state();
    coordinator.onEvent(new PointsEarned(playing.gameId(), GamePhase.PLAYING_PHASE_ONE, 80));

    coordinator.onEvent(new GameAbandoned(playing.gameId(), GamePhase.WELCOME));

    assertSame(playing, coordinator.state());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, coordinator.state().phase());
    assertEquals(80, coordinator.state().points());
    assertNull(coordinator.consumePendingTransition());
  }

  @Test
  void acceptsAbandonAfterTheRunFinishedWhileTheScreenIsStillAlive() {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameState playing = coordinator.state();
    coordinator.onEvent(new PointsEarned(playing.gameId(), GamePhase.PLAYING_PHASE_ONE, 120));
    coordinator.onEvent(
        new PhaseCompleted(
            new PhaseResult(playing.gameId(), GamePhase.PLAYING_PHASE_ONE, 120, STARTING_LIVES)));
    assertEquals(GamePhase.GAME_OVER, coordinator.state().phase());
    assertNull(coordinator.consumePendingTransition());

    // The playable screen stays alive after GAME_OVER and still reports its original phase, so its
    // ESC/SPACE abandon must be accepted.
    coordinator.onEvent(new GameAbandoned(playing.gameId(), GamePhase.PLAYING_PHASE_ONE));

    assertNotEquals(playing.gameId(), coordinator.state().gameId());
    assertEquals(GamePhase.WELCOME, coordinator.state().phase());
    assertEquals(0, coordinator.state().points());
    assertEquals(STARTING_LIVES, coordinator.state().lives());
    assertEquals(GamePhase.WELCOME, coordinator.consumePendingTransition());
    assertNull(coordinator.consumePendingTransition());
  }

  @Test
  void rejectedEventsDoNotChangeStateOrPendingTransition() {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);
    StartRequested start = new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME);
    coordinator.onEvent(start);
    GameState playing = coordinator.state();

    // Both a duplicate start and a wrong-phase abandon are rejected while a transition is queued.
    coordinator.onEvent(start);
    coordinator.onEvent(new GameAbandoned(playing.gameId(), GamePhase.WELCOME));

    assertSame(playing, coordinator.state());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, coordinator.state().phase());
    assertEquals(0, coordinator.state().points());
    assertEquals(STARTING_LIVES, coordinator.state().lives());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, coordinator.consumePendingTransition());
    assertNull(coordinator.consumePendingTransition());
  }
}
