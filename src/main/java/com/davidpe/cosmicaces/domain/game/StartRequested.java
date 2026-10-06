package com.davidpe.cosmicaces.domain.game;

import java.util.Objects;

/** A screen requests that a game (partida) starts. */
public record StartRequested(GameId gameId, GamePhase phase) implements GameEvent {

  public StartRequested {
    Objects.requireNonNull(gameId, "gameId");
    Objects.requireNonNull(phase, "phase");
  }
}
