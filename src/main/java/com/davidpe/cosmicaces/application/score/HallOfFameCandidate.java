package com.davidpe.cosmicaces.application.score;

import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.score.HallOfFameEntry;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Snapshot of one finished game that may enter the Hall of Fame.
 *
 * <p>It is created exactly once per game, when the application accepts its terminal result, and it
 * keeps the authoritative points and lives of that moment together with the durable entry identity
 * and the terminal date. The identity and the date never change between retries, so a failed save can
 * be retried without creating a second row. Initials are not part of the candidate: they are chosen
 * only if the player is offered the entry mode and are validated again on confirmation.
 *
 * @param entryId durable identity of the row that this game would create
 * @param gameId game (partida) that produced the snapshot, used only for session correlation
 * @param points authoritative points of the finished game
 * @param lives remaining lives reported by the terminal snapshot
 * @param outcome terminal WIN/LOSE decision that ended the game
 * @param finishedAt moment and zone at which the game ended
 */
public record HallOfFameCandidate(
    String entryId,
    GameId gameId,
    int points,
    int lives,
    PhaseOutcome outcome,
    ZonedDateTime finishedAt) {

  public HallOfFameCandidate {
    if (entryId == null || entryId.isBlank()) {
      throw new IllegalArgumentException("Candidate entry id must not be blank");
    }
    Objects.requireNonNull(gameId, "gameId");
    if (points < 0) {
      throw new IllegalArgumentException("Points must not be negative: " + points);
    }
    if (lives < 0) {
      throw new IllegalArgumentException("Lives must not be negative: " + lives);
    }
    Objects.requireNonNull(outcome, "outcome");
    if (!outcome.isFinished()) {
      throw new IllegalArgumentException("A Hall of Fame candidate requires a terminal outcome");
    }
    Objects.requireNonNull(finishedAt, "finishedAt");
  }

  /** Builds the confirmed row for the initials chosen by the player. */
  public HallOfFameEntry toEntry(String initials) {
    return new HallOfFameEntry(entryId, initials, points, finishedAt);
  }
}
