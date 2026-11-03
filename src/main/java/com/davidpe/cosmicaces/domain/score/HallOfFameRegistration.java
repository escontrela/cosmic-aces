package com.davidpe.cosmicaces.domain.score;

/**
 * Outcome of asking a {@link HallOfFame} to register a candidate entry.
 *
 * <p>The result always carries the ranking that remains authoritative. Only
 * {@link Status#REGISTERED} replaces it with a new snapshot; a rejected candidate leaves the
 * previous ranking untouched. Retrying the same durable entry id never inserts a second row.
 */
public record HallOfFameRegistration(Status status, HallOfFame ranking) {

  public enum Status {
    /** The candidate entered the top 50 and the ranking is the new snapshot. */
    REGISTERED,
    /** The ranking was full and the candidate did not beat the last place. */
    DID_NOT_QUALIFY,
    /** An entry with the same durable id was already registered. */
    ALREADY_REGISTERED
  }

  public HallOfFameRegistration {
    if (status == null) {
      throw new IllegalArgumentException("Status must not be null");
    }
    if (ranking == null) {
      throw new IllegalArgumentException("Ranking must not be null");
    }
  }

  /** Returns whether the candidate was newly inserted. */
  public boolean registered() {
    return status == Status.REGISTERED;
  }
}
