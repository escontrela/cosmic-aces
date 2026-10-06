package com.davidpe.cosmicaces.domain.game;

import java.util.Objects;

/** The player lost one life during a playing phase. */
public record LifeLost(GameId gameId, GamePhase phase) implements GameEvent {

  public LifeLost {
    Objects.requireNonNull(gameId, "gameId");
    Objects.requireNonNull(phase, "phase");
  }
}
