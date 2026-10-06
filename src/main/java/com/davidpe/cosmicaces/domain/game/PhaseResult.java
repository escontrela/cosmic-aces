package com.davidpe.cosmicaces.domain.game;

import java.util.Objects;

/**
 * Immutable outcome of a completed phase: an authoritative snapshot of the game identity, the phase
 * and the player's points and lives at the moment the phase finished. It is a value, never an extra
 * score contribution.
 */
public record PhaseResult(GameId gameId, GamePhase phase, int points, int lives) {

  public PhaseResult {
    Objects.requireNonNull(gameId, "gameId");
    Objects.requireNonNull(phase, "phase");
    if (points < 0) {
      throw new IllegalArgumentException("Points must not be negative: " + points);
    }
    if (lives < 0) {
      throw new IllegalArgumentException("Lives must not be negative: " + lives);
    }
  }
}
