package com.davidpe.cosmicaces.domain.game;

/**
 * The player's progression through the game.
 *
 * <p>{@link #HALL_OF_FAME} is the terminal state of a finished game: the run is over, its
 * authoritative result has been captured and the next visible screen is the Hall of Fame. A new
 * {@link #WELCOME} state is only created by starting over, so a finished game can never fall back to
 * the lobby implicitly.
 */
public enum GamePhase {
  WELCOME,
  PLAYING_PHASE_ONE,
  HALL_OF_FAME
}
