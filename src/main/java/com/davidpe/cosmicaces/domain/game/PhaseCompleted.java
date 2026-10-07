package com.davidpe.cosmicaces.domain.game;

import java.util.Objects;

/** A playing phase has completed, carrying its immutable {@link PhaseResult}. */
public record PhaseCompleted(PhaseResult result) implements GameEvent {

  public PhaseCompleted {
    Objects.requireNonNull(result, "result");
  }

  @Override
  public GameId gameId() {
    return result.gameId();
  }

  @Override
  public GamePhase phase() {
    return result.phase();
  }
}
