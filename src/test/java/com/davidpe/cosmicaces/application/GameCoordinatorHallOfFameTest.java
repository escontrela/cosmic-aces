package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.application.score.HallOfFameCandidate;
import com.davidpe.cosmicaces.application.score.HallOfFameSession;
import com.davidpe.cosmicaces.application.score.HallOfFameView;
import com.davidpe.cosmicaces.application.score.InMemoryHallOfFameRepository;
import com.davidpe.cosmicaces.domain.game.GameAbandoned;
import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.GameState;
import com.davidpe.cosmicaces.domain.game.LifeLost;
import com.davidpe.cosmicaces.domain.game.PhaseCompleted;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.game.PhaseResult;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import com.davidpe.cosmicaces.domain.game.StartRequested;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Connects the persistent game state with the Hall of Fame session: only an accepted terminal
 * completion captures a candidate, while abandonment, stale events and non-terminal completions do
 * not.
 */
class GameCoordinatorHallOfFameTest {

  private static final int STARTING_LIVES = 3;
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-11-03T12:34:56Z"), ZoneOffset.UTC);

  @Test
  void terminalCompletionCapturesTheAuthoritativeSnapshotOnce() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = loadedSession(repository);
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES, session);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameState playing = coordinator.state();
    coordinator.onEvent(new PointsEarned(playing.gameId(), GamePhase.PLAYING_PHASE_ONE, 120));
    coordinator.onEvent(new LifeLost(playing.gameId(), GamePhase.PLAYING_PHASE_ONE));
    PhaseResult result = coordinator.snapshot();

    coordinator.onEvent(new PhaseCompleted(result, PhaseOutcome.WIN));

    assertEquals(GamePhase.HALL_OF_FAME, coordinator.state().phase());
    assertEquals(GamePhase.HALL_OF_FAME, coordinator.consumePendingTransition());
    HallOfFameCandidate candidate = session.pendingCandidate().orElseThrow();
    assertEquals(playing.gameId(), candidate.gameId());
    assertEquals(120, candidate.points());
    assertEquals(2, candidate.lives());
    assertEquals(PhaseOutcome.WIN, candidate.outcome());
    String entryId = candidate.entryId();
    assertEquals(0, repository.saveCalls());

    // The coordinator already moved to HALL_OF_FAME, so a repeated completion is rejected before it
    // can create a second candidate or overwrite the first snapshot.
    coordinator.onEvent(new PhaseCompleted(result, PhaseOutcome.WIN));

    assertEquals(entryId, session.pendingCandidate().orElseThrow().entryId());
    assertEquals(0, repository.saveCalls());
  }

  @Test
  void terminalLossAlsoOpensTheHallOfFameAndKeepsTheCandidate() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = loadedSession(repository);
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES, session);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameState playing = coordinator.state();
    coordinator.onEvent(new PointsEarned(playing.gameId(), GamePhase.PLAYING_PHASE_ONE, 80));
    coin(coordinator, 3);
    PhaseResult result = coordinator.snapshot();

    coordinator.onEvent(new PhaseCompleted(result, PhaseOutcome.LOSE));

    assertEquals(GamePhase.HALL_OF_FAME, coordinator.state().phase());
    assertEquals(GamePhase.HALL_OF_FAME, coordinator.consumePendingTransition());
    HallOfFameCandidate candidate = session.pendingCandidate().orElseThrow();
    assertEquals(PhaseOutcome.LOSE, candidate.outcome());
    assertEquals(80, candidate.points());
    assertEquals(0, candidate.lives());
    assertEquals(HallOfFameView.Mode.ENTRY, session.view().mode());
    assertEquals(0, repository.saveCalls());
  }

  private static void coin(GameCoordinator coordinator, int losses) {
    GameState state = coordinator.state();
    for (int loss = 0; loss < losses; loss++) {
      coordinator.onEvent(new LifeLost(state.gameId(), GamePhase.PLAYING_PHASE_ONE));
    }
  }

  @Test
  void nonTerminalCompletionReconcilesTheSnapshotWithoutEndingTheGame() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = loadedSession(repository);
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES, session);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameState playing = coordinator.state();
    coordinator.onEvent(new PointsEarned(playing.gameId(), GamePhase.PLAYING_PHASE_ONE, 50));
    PhaseResult result =
        new PhaseResult(playing.gameId(), GamePhase.PLAYING_PHASE_ONE, 50, STARTING_LIVES);

    coordinator.onEvent(new PhaseCompleted(result, PhaseOutcome.IN_PROGRESS));

    assertEquals(GamePhase.PLAYING_PHASE_ONE, coordinator.state().phase());
    assertEquals(50, coordinator.state().points());
    assertEquals(STARTING_LIVES, coordinator.state().lives());
    assertTrue(session.pendingCandidate().isEmpty());
    assertEquals(0, repository.saveCalls());
  }

  @Test
  void abandonmentBeforeTerminalDoesNotCapture() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = loadedSession(repository);
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES, session);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameState playing = coordinator.state();

    coordinator.onEvent(new GameAbandoned(playing.gameId(), GamePhase.PLAYING_PHASE_ONE));

    assertEquals(GamePhase.WELCOME, coordinator.state().phase());
    assertTrue(session.pendingCandidate().isEmpty());
    assertEquals(0, repository.saveCalls());
  }

  @Test
  void abandonmentAfterTerminalIsRejectedAndKeepsThePendingCandidate() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = loadedSession(repository);
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES, session);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameState playing = coordinator.state();
    coordinator.onEvent(new PointsEarned(playing.gameId(), GamePhase.PLAYING_PHASE_ONE, 120));
    PhaseResult result = coordinator.snapshot();
    coordinator.onEvent(new PhaseCompleted(result, PhaseOutcome.WIN));
    coordinator.consumePendingTransition();
    HallOfFameCandidate candidate = session.pendingCandidate().orElseThrow();

    coordinator.onEvent(new GameAbandoned(playing.gameId(), GamePhase.PLAYING_PHASE_ONE));

    assertEquals(GamePhase.HALL_OF_FAME, coordinator.state().phase());
    assertEquals(120, coordinator.state().points());
    assertEquals(candidate.entryId(), session.pendingCandidate().orElseThrow().entryId());
    assertEquals(HallOfFameView.Mode.ENTRY, session.view().mode());
    assertEquals(0, repository.saveCalls());
  }

  @Test
  void queryingTheRankingDoesNotCreateAGameOrResetPoints() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = loadedSession(repository);
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES, session);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameState playing = coordinator.state();
    coordinator.onEvent(new PointsEarned(playing.gameId(), GamePhase.PLAYING_PHASE_ONE, 120));

    HallOfFameView view = session.view();

    assertEquals(HallOfFameView.Mode.CONSULT, view.mode());
    assertEquals(playing.gameId(), coordinator.state().gameId());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, coordinator.state().phase());
    assertEquals(120, coordinator.state().points());
    assertTrue(session.pendingCandidate().isEmpty());
  }

  @Test
  void lateEventsFromThePreviousGameAreIgnoredAfterAbandon() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = loadedSession(repository);
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES, session);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameState firstGame = coordinator.state();
    coordinator.onEvent(new PointsEarned(firstGame.gameId(), GamePhase.PLAYING_PHASE_ONE, 120));
    coordinator.onEvent(new GameAbandoned(firstGame.gameId(), GamePhase.PLAYING_PHASE_ONE));
    coordinator.consumePendingTransition();
    GameId lobbyId = coordinator.state().gameId();
    coordinator.onEvent(new StartRequested(lobbyId, GamePhase.WELCOME));
    coordinator.consumePendingTransition();

    coordinator.onEvent(new PointsEarned(firstGame.gameId(), GamePhase.PLAYING_PHASE_ONE, 50));
    coordinator.onEvent(new PhaseCompleted(
        new PhaseResult(firstGame.gameId(), GamePhase.PLAYING_PHASE_ONE, 50, STARTING_LIVES),
        PhaseOutcome.WIN));

    assertEquals(0, coordinator.state().points());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, coordinator.state().phase());
    assertTrue(session.pendingCandidate().isEmpty());
    assertEquals(0, repository.saveCalls());
  }

  private static HallOfFameSession loadedSession(InMemoryHallOfFameRepository repository) {
    AtomicInteger counter = new AtomicInteger();
    HallOfFameSession session = new HallOfFameSession(
        repository, FIXED_CLOCK, () -> "entry-" + counter.incrementAndGet());
    session.load();
    return session;
  }
}
