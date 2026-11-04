package com.davidpe.cosmicaces.domain.score;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One confirmed Hall of Fame row: the player's three initials, the points earned by the finished
 * game and the moment it ended.
 *
 * <p>The row carries its own durable identity so retrying a registration never duplicates a game.
 * {@code finishedAt} keeps both the exact instant (ranking and tie-breaking) and the local date at
 * which the game ended, so the displayed DD/MM/YY does not shift when the application restarts in
 * another time zone. The domain never reads the clock: the caller supplies the identity and time.
 */
public record HallOfFameEntry(String id, String initials, int points, ZonedDateTime finishedAt) {

  /** Exactly three uppercase letters, the arcade initials accepted by the Hall of Fame. */
  public static final Pattern INITIALS_PATTERN = Pattern.compile("[A-Z]{3}");

  public HallOfFameEntry {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Entry id must not be blank");
    }
    if (!isValidInitials(initials)) {
      throw new IllegalArgumentException(
          "Initials must be exactly three uppercase letters A-Z: " + initials);
    }
    if (points < 0) {
      throw new IllegalArgumentException("Points must not be negative: " + points);
    }
    Objects.requireNonNull(finishedAt, "finishedAt");
  }

  /** Returns whether the value is a valid three-letter A-Z initial set. */
  public static boolean isValidInitials(String initials) {
    return initials != null && INITIALS_PATTERN.matcher(initials).matches();
  }

  /** Exact instant at which the game ended; used for ordering and tie-breaking. */
  public Instant finishedInstant() {
    return finishedAt.toInstant();
  }

  /** Local calendar date of the end, preserved independently of the current time zone. */
  public LocalDate playedOn() {
    return finishedAt.toLocalDate();
  }
}
