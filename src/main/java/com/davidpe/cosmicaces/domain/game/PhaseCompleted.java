package com.davidpe.cosmicaces.domain.game;

import java.util.Objects;

/**
 * A playing phase has completed, carrying its immutable {@link PhaseResult} and the authoritative
 * mission outcome at that moment.
 *
 * <p>{@link PhaseOutcome#IN_PROGRESS} means only the phase reconciled its snapshot: the game keeps
 * playing and no Hall of Fame entry is offered. A terminal {@link PhaseOutcome#WIN} or
 * {@link PhaseOutcome#LOSE} is the single game-ending decision, so the application, not the screen,
 * decides whether the whole game finished.
 */
public record PhaseCompleted(PhaseResult result, PhaseOutcome outcome) implements GameEvent {

  public PhaseCompleted {
    Objects.requireNonNull(result, "result");
    Objects.requireNonNull(outcome, "outcome");
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
