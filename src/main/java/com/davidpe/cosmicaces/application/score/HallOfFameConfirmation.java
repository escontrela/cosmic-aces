package com.davidpe.cosmicaces.application.score;

import com.davidpe.cosmicaces.domain.score.HallOfFame;
import com.davidpe.cosmicaces.domain.score.HallOfFameEntry;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of confirming the three initials of the pending Hall of Fame candidate.
 *
 * <p>The result always carries the ranking that remains authoritative. Only
 * {@link Status#REGISTERED} replaces it with a new, already persisted snapshot. A storage failure
 * keeps the previous ranking and the pending candidate so the caller can retry with the same durable
 * entry id and date; any other status is final for that candidate.
 */
public record HallOfFameConfirmation(
    Status status, HallOfFame ranking, Optional<HallOfFameEntry> entry) {

  public enum Status {
    /** The candidate entered the top 50 and the new ranking was persisted. */
    REGISTERED,
    /** The candidate did not beat the last place of a full top 50. */
    NOT_ELIGIBLE,
    /** The supplied initials are not exactly three uppercase A-Z letters. */
    INVALID_INITIALS,
    /** No finished game is waiting for initials. */
    NO_PENDING_ENTRY,
    /** This game was already confirmed in this session. */
    ALREADY_CONFIRMED,
    /** The snapshot could not be persisted; the candidate and previous ranking are intact. */
    STORAGE_FAILURE
  }

  public HallOfFameConfirmation {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(ranking, "ranking");
    Objects.requireNonNull(entry, "entry");
  }

  /** True only when a new row was persisted successfully. */
  public boolean registered() {
    return status == Status.REGISTERED;
  }
}
