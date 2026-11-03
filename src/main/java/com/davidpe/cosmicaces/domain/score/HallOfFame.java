package com.davidpe.cosmicaces.domain.score;

import com.davidpe.cosmicaces.domain.score.HallOfFameRegistration.Status;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable local ranking of the best 50 finished games.
 *
 * <p>Rows are ordered by points descending and, on equal points, by the earlier terminal instant
 * first. When two rows share the exact same instant, the order in which they were persisted decides:
 * the ranking uses a stable sort, so an already registered row always stays ahead of a new row with
 * the same points and instant. The same comparator drives eligibility and insertion, so asking
 * whether a candidate qualifies never disagrees with actually registering it.
 *
 * <p>The ranking is a pure value: it reads no clock, file or graphics context, never mutates the
 * collection it was built from and never exposes a mutable list. Persistence and durable identities
 * belong to the caller.
 */
public final class HallOfFame {

  /** Maximum number of rows kept and persisted. */
  public static final int MAX_ENTRIES = 50;

  private static final Comparator<HallOfFameEntry> RANKING_ORDER =
      Comparator.comparingInt(HallOfFameEntry::points)
          .reversed()
          .thenComparing(HallOfFameEntry::finishedInstant);

  private final List<HallOfFameEntry> entries;

  private HallOfFame(List<HallOfFameEntry> canonicalEntries) {
    this.entries = List.copyOf(canonicalEntries);
  }

  /** Returns an empty ranking. */
  public static HallOfFame empty() {
    return new HallOfFame(List.of());
  }

  /**
   * Normalizes a loaded collection into the canonical top 50.
   *
   * <p>The collection's iteration order is treated as the persisted insertion order for rows that
   * share points and instant. Duplicate durable ids are rejected because they represent corrupt
   * data, not a ranking decision; the storage adapter validates its input before calling this
   * method.
   *
   * @throws IllegalArgumentException if two entries share the same id
   */
  public static HallOfFame of(Collection<HallOfFameEntry> loadedEntries) {
    Objects.requireNonNull(loadedEntries, "loadedEntries");
    List<HallOfFameEntry> sorted = new ArrayList<>(loadedEntries.size());
    Set<String> ids = new HashSet<>();
    for (HallOfFameEntry entry : loadedEntries) {
      Objects.requireNonNull(entry, "entry");
      if (!ids.add(entry.id())) {
        throw new IllegalArgumentException("Duplicate Hall of Fame entry id: " + entry.id());
      }
      sorted.add(entry);
    }
    sorted.sort(RANKING_ORDER);
    if (sorted.size() > MAX_ENTRIES) {
      sorted = new ArrayList<>(sorted.subList(0, MAX_ENTRIES));
    }
    return new HallOfFame(sorted);
  }

  /** Immutable snapshot of at most {@link #MAX_ENTRIES} rows in canonical order. */
  public List<HallOfFameEntry> entries() {
    return entries;
  }

  public int size() {
    return entries.size();
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  /**
   * Returns whether a candidate would enter the ranking without inserting it or reserving a row.
   *
   * <p>An id that is already registered never qualifies again. With fewer than 50 rows any valid
   * entry qualifies, including zero points. When the ranking is full, the candidate must strictly
   * beat the current last place under the ranking order; a tie keeps the older row.
   */
  public boolean qualifies(HallOfFameEntry candidate) {
    Objects.requireNonNull(candidate, "candidate");
    if (containsId(candidate.id())) {
      return false;
    }
    if (entries.size() < MAX_ENTRIES) {
      return true;
    }
    return RANKING_ORDER.compare(candidate, entries.get(entries.size() - 1)) < 0;
  }

  /**
   * Registers a candidate once and returns the explicit outcome with the authoritative ranking.
   *
   * <p>A candidate that does not qualify or whose durable id is already present leaves this ranking
   * untouched. Repeating the same registration therefore never inserts a second row.
   */
  public HallOfFameRegistration register(HallOfFameEntry candidate) {
    Objects.requireNonNull(candidate, "candidate");
    if (containsId(candidate.id())) {
      return new HallOfFameRegistration(Status.ALREADY_REGISTERED, this);
    }
    List<HallOfFameEntry> updated = new ArrayList<>(entries.size() + 1);
    updated.addAll(entries);
    updated.add(candidate);
    updated.sort(RANKING_ORDER);
    if (updated.size() > MAX_ENTRIES) {
      updated = new ArrayList<>(updated.subList(0, MAX_ENTRIES));
    }
    if (updated.stream().noneMatch(entry -> entry.id().equals(candidate.id()))) {
      return new HallOfFameRegistration(Status.DID_NOT_QUALIFY, this);
    }
    return new HallOfFameRegistration(Status.REGISTERED, new HallOfFame(updated));
  }

  private boolean containsId(String id) {
    return entries.stream().anyMatch(entry -> entry.id().equals(id));
  }
}
