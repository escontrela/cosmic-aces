package com.davidpe.cosmicaces.application.score;

import com.davidpe.cosmicaces.domain.score.HallOfFame;

/**
 * Persistence contract for the local Hall of Fame ranking.
 *
 * <p>Implementations store the complete top 50 snapshot, never individual rows or a live game. The
 * application loads the ranking once per session before offering registration; screens must not read
 * or write this storage per frame or per keystroke.
 *
 * <p>{@link #load()} is total: a missing file, corrupt data and I/O failures are reported through
 * {@link HallOfFameLoadResult} instead of exceptions, so the caller can distinguish "no ranking yet"
 * from "data that must not be overwritten". {@link #save(HallOfFame)} replaces the whole snapshot
 * and either completes it or throws {@link HallOfFameStorageException}; it never reports success for
 * a partial write. An implementation must not replace data that could not be loaded.
 */
public interface HallOfFameRepository {

  /**
   * Reads the persisted ranking once per session.
   *
   * @return the immutable snapshot plus an explicit status; never {@code null}
   */
  HallOfFameLoadResult load();

  /**
   * Replaces the persisted ranking with the given complete snapshot.
   *
   * @param ranking authoritative top 50 snapshot to persist
   * @throws HallOfFameStorageException if the snapshot could not be fully written; the previous
   *     snapshot remains intact and the caller may retry
   */
  void save(HallOfFame ranking);
}
