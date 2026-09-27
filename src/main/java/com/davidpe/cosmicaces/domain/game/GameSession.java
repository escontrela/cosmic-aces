package com.davidpe.cosmicaces.domain.game;

/** Domain state and rules for starting a game session. */
public final class GameSession {

  private GamePhase phase = GamePhase.WELCOME;

  public GamePhase phase() {
    return phase;
  }

  /** Starts the session. Repeated start requests leave it in the playing phase. */
  public void start() {
    phase = GamePhase.PLAYING;
  }
}
