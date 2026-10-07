package com.davidpe.cosmicaces.domain.game;

import java.util.Objects;

/** The player abandoned the current game (partida). */
public record GameAbandoned(GameId gameId, GamePhase phase) implements GameEvent {

  public GameAbandoned {
    Objects.requireNonNull(gameId, "gameId");
    Objects.requireNonNull(phase, "phase");
  }
}
