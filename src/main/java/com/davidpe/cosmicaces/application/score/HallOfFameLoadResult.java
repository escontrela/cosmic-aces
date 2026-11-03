package com.davidpe.cosmicaces.application.score;

import com.davidpe.cosmicaces.domain.score.HallOfFame;
import java.util.Objects;

/**
 * Outcome of reading the persisted Hall of Fame.
 *
 * <p>The result always carries an immutable ranking snapshot, even when the stored data could not be
 * used: in that case the ranking is empty and the status explains why. Absence of a file is not an
 * error ({@link Status#EMPTY}); corrupt or unsupported data ({@link Status#INVALID_DATA}) and I/O
 * failures ({@link Status#STORAGE_FAILURE}) are recoverable errors that must never be treated as an
 * empty ranking worth overwriting.
 */
public record HallOfFameLoadResult(Status status, HallOfFame ranking) {

  public enum Status {
    /** A valid document was read. */
    LOADED,
    /** No ranking has been persisted yet. */
    EMPTY,
    /** The document is corrupt, unsupported or violates the ranking contract. */
    INVALID_DATA,
    /** The document exists but could not be read because of an I/O problem. */
    STORAGE_FAILURE
  }

  public HallOfFameLoadResult {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(ranking, "ranking");
  }

  /** No ranking has been persisted yet. */
  public static HallOfFameLoadResult empty() {
    return new HallOfFameLoadResult(Status.EMPTY, HallOfFame.empty());
  }

  /** A valid persisted ranking was read. */
  public static HallOfFameLoadResult loaded(HallOfFame ranking) {
    return new HallOfFameLoadResult(Status.LOADED, Objects.requireNonNull(ranking, "ranking"));
  }

  /** The persisted document could not be interpreted and was left untouched. */
  public static HallOfFameLoadResult invalidData() {
    return new HallOfFameLoadResult(Status.INVALID_DATA, HallOfFame.empty());
  }

  /** The persisted document could not be read and was left untouched. */
  public static HallOfFameLoadResult storageFailure() {
    return new HallOfFameLoadResult(Status.STORAGE_FAILURE, HallOfFame.empty());
  }

  /**
   * Whether the persisted data was read successfully and may be safely replaced by a new snapshot.
   *
   * <p>Only {@link Status#LOADED} and {@link Status#EMPTY} are writable. A failed load keeps the
   * original data intact and must not be followed by a blind save.
   */
  public boolean writable() {
    return status == Status.LOADED || status == Status.EMPTY;
  }
}
