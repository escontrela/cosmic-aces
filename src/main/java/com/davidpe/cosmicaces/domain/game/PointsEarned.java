package com.davidpe.cosmicaces.domain.game;

import java.util.Objects;

/** The player earned points during a playing phase. */
public record PointsEarned(GameId gameId, GamePhase phase, int points) implements GameEvent {

  public PointsEarned {
    Objects.requireNonNull(gameId, "gameId");
    Objects.requireNonNull(phase, "phase");
    if (points <= 0) {
      throw new IllegalArgumentException("Earned points must be positive: " + points);
    }
  }
}
