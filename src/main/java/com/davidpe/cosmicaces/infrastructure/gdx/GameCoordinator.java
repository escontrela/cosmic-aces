package com.davidpe.cosmicaces.infrastructure.gdx;

import com.davidpe.cosmicaces.domain.game.GameAbandoned;
import com.davidpe.cosmicaces.domain.game.GameEvent;
import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.GameState;
import com.davidpe.cosmicaces.domain.game.LifeLost;
import com.davidpe.cosmicaces.domain.game.PhaseCompleted;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import com.davidpe.cosmicaces.domain.game.StartRequested;
import java.util.Objects;

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
  private long nextGameId;
  private GameState state;
  private GamePhase pendingTransition;

  public GameCoordinator(int startingLives) {
    if (startingLives < 0) {
      throw new IllegalArgumentException("Starting lives must not be negative: " + startingLives);
    }
    this.startingLives = startingLives;
    this.state = newInstanceState(GamePhase.WELCOME);
  }

  /** Returns the single state of the game currently in progress. */
  public GameState state() {
    return state;
  }

  /**
   * Applies one event. {@link StartRequested} begins a new game with a fresh identity and state;
   * stale events that do not belong to the current game and phase are ignored.
   */
  public void onEvent(GameEvent event) {
    Objects.requireNonNull(event, "event");
    switch (event) {
      case StartRequested ignored -> startNewGame();
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
          state.changePhase(GamePhase.GAME_OVER);
        }
      }
      case GameAbandoned abandoned -> {
        if (state.gameId().equals(abandoned.gameId())) {
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

  private void startNewGame() {
    state = newInstanceState(GamePhase.PLAYING_PHASE_ONE);
    pendingTransition = GamePhase.PLAYING_PHASE_ONE;
  }

  private GameState newInstanceState(GamePhase phase) {
    GameState created = new GameState(new GameId(nextGameId), phase, 0, startingLives);
    nextGameId++;
    return created;
  }

  private boolean accepts(GameEvent event) {
    return state.gameId().equals(event.gameId()) && state.phase() == event.phase();
  }
}
