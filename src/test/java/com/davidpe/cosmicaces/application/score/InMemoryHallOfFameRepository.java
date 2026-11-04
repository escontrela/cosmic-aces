package com.davidpe.cosmicaces.application.score;

import com.davidpe.cosmicaces.domain.score.HallOfFame;
import java.util.Objects;

/**
 * Deterministic in-memory {@link HallOfFameRepository} for tests. It counts loads and saves and can
 * simulate a recoverable write failure without touching the previous snapshot.
 */
public final class InMemoryHallOfFameRepository implements HallOfFameRepository {

  private HallOfFameLoadResult loadResult;
  private HallOfFame ranking;
  private int loadCalls;
  private int saveCalls;
  private int failuresRemaining;

  public InMemoryHallOfFameRepository() {
    this(HallOfFameLoadResult.empty());
  }

  public InMemoryHallOfFameRepository(HallOfFame ranking) {
    this(HallOfFameLoadResult.loaded(ranking));
  }

  public InMemoryHallOfFameRepository(HallOfFameLoadResult loadResult) {
    this.loadResult = Objects.requireNonNull(loadResult, "loadResult");
    this.ranking = loadResult.ranking();
  }

  /** Makes the next {@code failures} save attempts throw before replacing the snapshot. */
  public void failNextSaves(int failures) {
    if (failures < 0) {
      throw new IllegalArgumentException("Failures must not be negative: " + failures);
    }
    failuresRemaining = failures;
  }

  @Override
  public HallOfFameLoadResult load() {
    loadCalls++;
    return loadResult;
  }

  @Override
  public void save(HallOfFame ranking) {
    saveCalls++;
    if (failuresRemaining > 0) {
      failuresRemaining--;
      throw new HallOfFameStorageException("Simulated Hall of Fame write failure");
    }
    this.ranking = Objects.requireNonNull(ranking, "ranking");
    this.loadResult = HallOfFameLoadResult.loaded(ranking);
  }

  public int loadCalls() {
    return loadCalls;
  }

  public int saveCalls() {
    return saveCalls;
  }

  public HallOfFame ranking() {
    return ranking;
  }

  public HallOfFameLoadResult loadResult() {
    return loadResult;
  }
}
