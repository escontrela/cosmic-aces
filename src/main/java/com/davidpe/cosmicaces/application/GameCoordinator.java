package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.application.score.HallOfFameSession;
import com.davidpe.cosmicaces.domain.game.GameAbandoned;
import com.davidpe.cosmicaces.domain.game.GameEvent;
import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.GameState;
import com.davidpe.cosmicaces.domain.game.LifeLost;
import com.davidpe.cosmicaces.domain.game.PhaseCompleted;
import com.davidpe.cosmicaces.domain.game.PhaseResult;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import com.davidpe.cosmicaces.domain.game.StartRequested;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-free coordinator that keeps the single {@link GameState} of a game alive across phase
 * and screen transitions and decides the next navigation from game events.
 *
 * <p>{@code CosmicAcesGame} is the LibGDX composition root: it delivers events to this class and
 * materialises the pending transition as a screen. Change requests are queued and returned by
 * {@link #consumePendingTransition()} so the caller can apply them once, at a safe frame boundary,
 * after the current screen finished rendering.
 *
 * <p>Events mutate points, lives or phase only when they belong to the current game and phase, so a
 * late message from a previous screen can never alter the persistent state nor be counted twice.
 * The class has no LibGDX or Guava dependency, so it can be unit tested without a window.
 */
public final class GameCoordinator {

  private static final long FIRST_GAME_ID = 1L;

  private final int startingLives;
  private final HallOfFameSession hallOfFameSession;
  private long nextGameId;
  private GameState state;
  private GamePhase pendingTransition;

  /**
   * Creates a coordinator without Hall of Fame capture. Kept for tests and callers that only need the
   * persistent points/lives state; the desktop application always injects a loaded session.
   */
  public GameCoordinator(int startingLives) {
    this(startingLives, null);
  }

  /**
   * Creates a coordinator that captures the terminal result of every finished game in the given Hall
   * of Fame session.
   *
   * @param startingLives lives of a new game
   * @param hallOfFameSession session loaded before the first event, or {@code null} to disable capture
   */
  public GameCoordinator(int startingLives, HallOfFameSession hallOfFameSession) {
    if (startingLives < 0) {
      throw new IllegalArgumentException("Starting lives must not be negative: " + startingLives);
    }
    this.startingLives = startingLives;
    this.hallOfFameSession = hallOfFameSession;
    this.state = newInstanceState(GamePhase.WELCOME);
  }

  /** Hall of Fame session used to capture terminal results, when the coordinator was given one. */
  public Optional<HallOfFameSession> hallOfFameSession() {
    return Optional.ofNullable(hallOfFameSession);
  }

  /** Returns the single state of the game currently in progress. */
  public GameState state() {
    return state;
  }

  /**
   * Returns an immutable snapshot of the persistent state. Callers that must report a phase result
   * (for example a screen whose run completed) use it so points and lives always come from the
   * coordinator rather than from a renderer.
   */
  public PhaseResult snapshot() {
    return state.snapshot();
  }

  /**
   * Applies one event. {@link StartRequested} begins a new game with a fresh identity and state only
   * when it comes from the welcome lobby or from a Hall of Fame consultation that no longer waits for
   * initials, always with the identity and phase currently shown; events that do not belong to the
   * current game and phase (for example a late start or abandon from a previous screen) are ignored.
   */
  public void onEvent(GameEvent event) {
    Objects.requireNonNull(event, "event");
    switch (event) {
      case StartRequested requested -> {
        if (acceptsStart(requested)) {
          startNewGame();
        }
      }
      case PointsEarned points -> {
        if (accepts(points)) {
          state.addPoints(points.points());
        }
      }
      case LifeLost life -> {
        if (accepts(life)) {
          state.loseLife();
        }
      }
      case PhaseCompleted completed -> {
        if (accepts(completed)) {
          state.completePhase(completed.result());
          if (completed.outcome().isFinished()) {
            // The terminal result is captured before the transition so the screen built at the end of
            // the frame already sees the pending candidate and its eligibility.
            state.changePhase(GamePhase.HALL_OF_FAME);
            if (hallOfFameSession != null) {
              hallOfFameSession.captureTerminalResult(completed.result(), completed.outcome());
            }
            pendingTransition = GamePhase.HALL_OF_FAME;
          }
        }
      }
      case GameAbandoned abandoned -> {
        if (acceptsAbandon(abandoned)) {
          state = newInstanceState(GamePhase.WELCOME);
          pendingTransition = GamePhase.WELCOME;
        }
      }
    }
  }

  /**
   * Returns and clears the phase transition requested since the last call, or {@code null} when the
   * current screen must remain. The caller applies the transition at the end of the frame.
   */
  public GamePhase consumePendingTransition() {
    GamePhase transition = pendingTransition;
    pendingTransition = null;
    return transition;
  }

  /**
   * Returns the persistent state to the welcome lobby after the Hall of Fame consult has been shown
   * during the attraction loop. It is not an abandonment: no {@link GameAbandoned} is emitted and no
   * candidate is discarded while it still needs initials.
   *
   * <p>A qualifying candidate keeps the state in {@link GamePhase#HALL_OF_FAME} so the editor stays
   * reachable; a resolved non-qualifying candidate is dismissed. A state that is already
   * {@link GamePhase#WELCOME} is left untouched, which keeps an attraction visit from inventing a
   * new game identity.
   *
   * @return {@code true} when the lobby may be shown
   */
  public boolean returnToAttractionLobby() {
    if (state.phase() == GamePhase.WELCOME) {
      return true;
    }
    if (state.phase() != GamePhase.HALL_OF_FAME) {
      return false;
    }
    if (hallOfFameSession != null && hallOfFameSession.view().entryRequested()) {
      return false;
    }
    if (hallOfFameSession != null) {
      hallOfFameSession.dismissResolvedCandidate();
    }
    state = newInstanceState(GamePhase.WELCOME);
    return true;
  }

  private void startNewGame() {
    if (hallOfFameSession != null) {
      hallOfFameSession.dismissResolvedCandidate();
    }
    state = newInstanceState(GamePhase.PLAYING_PHASE_ONE);
    pendingTransition = GamePhase.PLAYING_PHASE_ONE;
  }

  private GameState newInstanceState(GamePhase phase) {
    GameState created = new GameState(new GameId(nextGameId), phase, 0, startingLives);
    nextGameId++;
    return created;
  }

  /**
   * A start is valid only from the rest state currently shown and with its exact identity and phase.
   * The welcome lobby always accepts it; the Hall of Fame consult accepts it only after the terminal
   * result was resolved and no qualifying candidate is still waiting for initials. Duplicate or late
   * starts from a previous lobby, and stale events from another screen, are therefore discarded
   * before any new state is created.
   */
  private boolean acceptsStart(StartRequested event) {
    if (!state.gameId().equals(event.gameId()) || state.phase() != event.phase()) {
      return false;
    }
    return switch (state.phase()) {
      case WELCOME -> true;
      case HALL_OF_FAME -> hallOfFameSession == null
          || !hallOfFameSession.view().entryRequested();
      default -> false;
    };
  }

  /**
   * An abandon is accepted only from the playable screen of the current game. Once the terminal
   * result was accepted the state is {@link GamePhase#HALL_OF_FAME}; a later abandon from the
   * already-replaced screen is rejected so it cannot discard the captured candidate. Any other phase
   * (for example a stale abandon that still says {@code WELCOME}) is rejected too.
   */
  private boolean acceptsAbandon(GameAbandoned event) {
    if (event.phase() != GamePhase.PLAYING_PHASE_ONE || !state.gameId().equals(event.gameId())) {
      return false;
    }
    return state.phase() == GamePhase.PLAYING_PHASE_ONE;
  }

  private boolean accepts(GameEvent event) {
    return state.gameId().equals(event.gameId()) && state.phase() == event.phase();
  }
}
