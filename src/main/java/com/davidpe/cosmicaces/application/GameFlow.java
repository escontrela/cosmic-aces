package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.GameSession;

/** Application use cases for moving through the current game flow. */
public final class GameFlow {

  private final GameSession session;

  public GameFlow(GameSession session) {
    this.session = session;
  }

  public boolean startGame() {
    session.start();
    return session.phase() == GamePhase.PLAYING;
  }
}
