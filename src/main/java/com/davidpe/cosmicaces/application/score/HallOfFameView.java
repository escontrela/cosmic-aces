package com.davidpe.cosmicaces.application.score;

import com.davidpe.cosmicaces.domain.score.HallOfFame;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable presentation contract for the Hall of Fame screen.
 *
 * <p>{@link Mode#ENTRY} means a finished game qualifies and the player must be asked for three
 * initials; {@link Mode#CONSULT} means the screen only shows the ranking. The view never mutates the
 * session, creates a game or resets points/lives, so a screen can read it once per visit.
 */
public record HallOfFameView(Mode mode, HallOfFame ranking, Optional<HallOfFameCandidate> candidate) {

  public enum Mode {
    /** Read-only ranking; no game is waiting for initials. */
    CONSULT,
    /** A qualifying finished game is waiting for three initials. */
    ENTRY
  }

  public HallOfFameView {
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(ranking, "ranking");
    Objects.requireNonNull(candidate, "candidate");
    if (mode == Mode.ENTRY && candidate.isEmpty()) {
      throw new IllegalArgumentException("Entry mode requires a pending candidate");
    }
  }

  /** True when the screen must collect three initials before showing the read-only ranking. */
  public boolean entryRequested() {
    return mode == Mode.ENTRY;
  }
}
