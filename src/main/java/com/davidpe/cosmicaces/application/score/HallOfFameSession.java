package com.davidpe.cosmicaces.application.score;

import com.davidpe.cosmicaces.application.score.HallOfFameConfirmation.Status;
import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.game.PhaseResult;
import com.davidpe.cosmicaces.domain.score.HallOfFame;
import com.davidpe.cosmicaces.domain.score.HallOfFameEntry;
import com.davidpe.cosmicaces.domain.score.HallOfFameRegistration;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Application session of the Hall of Fame: the ranking loaded once per application run, the single
 * finished game that may still need initials, and the confirmation that persists a new top 50.
 *
 * <p>The session is framework-free. It reads the clock and generates the durable entry identity
 * through injected collaborators, so tests are deterministic. It never reads or writes storage per
 * frame: {@link #load()} is called once by the composition root, {@link #view()} is a pure query and
 * {@link #confirm(String)} performs at most one snapshot save.
 *
 * <p>Only a terminal {@link PhaseOutcome#WIN} or {@link PhaseOutcome#LOSE} accepted by the
 * application creates a candidate. A phase completed with {@link PhaseOutcome#IN_PROGRESS}, an
 * abandoned game, a stale event and a duplicate completion never do. A candidate keeps the
 * authoritative points/lives of its {@link PhaseResult}, the terminal date and one durable entry id
 * for every retry. A qualifying candidate is never replaced by a later game until it is confirmed;
 * a non-qualifying one does not require initials and may be replaced.
 *
 * <p>A confirmation revalidates eligibility, builds the new ranking, persists it and only then
 * publishes the new snapshot. If the save fails, the previous ranking and the candidate remain
 * untouched so the player can retry with the same identity and date. A repeated confirmation never
 * saves a second row. {@link #dismissResolvedCandidate()} may discard only a non-qualifying result
 * that no longer needs the editor, never a qualifying one that is still waiting for initials.
 */
public final class HallOfFameSession {

  private final HallOfFameRepository repository;
  private final Clock clock;
  private final Supplier<String> entryIds;
  private final Set<GameId> capturedGames = new HashSet<>();
  private final Set<String> processedEntryIds = new HashSet<>();
  private boolean loaded;
  private HallOfFameLoadResult loadResult = HallOfFameLoadResult.empty();
  private HallOfFame ranking = HallOfFame.empty();
  private HallOfFameCandidate pendingCandidate;

  /**
   * Creates a session that has not read storage yet.
   *
   * @param repository persistence of the complete top 50 snapshot
   * @param clock source of the terminal date
   * @param entryIds generator of durable, unique row identities
   */
  public HallOfFameSession(
      HallOfFameRepository repository, Clock clock, Supplier<String> entryIds) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.entryIds = Objects.requireNonNull(entryIds, "entryIds");
  }

  /**
   * Loads the persisted ranking exactly once per session and returns the outcome. Later calls return
   * the same result without touching storage again.
   */
  public HallOfFameLoadResult load() {
    if (!loaded) {
      HallOfFameLoadResult result = Objects.requireNonNull(repository.load(), "repository.load()");
      loadResult = result;
      ranking = result.ranking();
      loaded = true;
    }
    return loadResult;
  }

  /** Result of the single load, including whether the persisted data may be replaced. */
  public HallOfFameLoadResult loadResult() {
    requireLoaded();
    return loadResult;
  }

  /** Current authoritative ranking. */
  public HallOfFame ranking() {
    requireLoaded();
    return ranking;
  }

  /** Finished game still waiting for initials or for its eligibility to be shown. */
  public Optional<HallOfFameCandidate> pendingCandidate() {
    requireLoaded();
    return Optional.ofNullable(pendingCandidate);
  }

  /**
   * Pure presentation query: {@link HallOfFameView.Mode#ENTRY} only when a pending candidate would
   * enter the ranking; otherwise {@link HallOfFameView.Mode#CONSULT}. It never creates a game, resets
   * points/lives or persists anything.
   */
  public HallOfFameView view() {
    requireLoaded();
    if (pendingCandidate == null) {
      return new HallOfFameView(
          HallOfFameView.Mode.CONSULT, ranking, Optional.empty());
    }
    HallOfFameView.Mode mode = qualifies(pendingCandidate)
        ? HallOfFameView.Mode.ENTRY
        : HallOfFameView.Mode.CONSULT;
    return new HallOfFameView(mode, ranking, Optional.of(pendingCandidate));
  }

  /**
   * Captures the authoritative snapshot of a finished game once.
   *
   * @param result snapshot reported by the completed phase
   * @param outcome terminal decision supplied by the application
   * @return {@code true} only when this call created the candidate
   */
  public boolean captureTerminalResult(PhaseResult result, PhaseOutcome outcome) {
    requireLoaded();
    Objects.requireNonNull(result, "result");
    Objects.requireNonNull(outcome, "outcome");
    if (!outcome.isFinished()) {
      return false;
    }
    // A failed load must never be followed by a blind save, so this session cannot offer entry.
    if (!loadResult.writable()) {
      return false;
    }
    if (capturedGames.contains(result.gameId())) {
      return false;
    }
    // A qualifying candidate still needs initials and must not be lost because another game ended.
    if (pendingCandidate != null && qualifies(pendingCandidate)) {
      return false;
    }
    String entryId = entryIds.get();
    if (entryId == null || entryId.isBlank()) {
      throw new IllegalStateException("Entry id generator returned a blank id");
    }
    pendingCandidate = new HallOfFameCandidate(entryId, result.gameId(), result.points(),
        result.lives(), outcome, ZonedDateTime.now(clock));
    capturedGames.add(result.gameId());
    return true;
  }

  /**
   * Discards a terminal result that no longer needs player action because it did not qualify for the
   * ranking. A qualifying candidate still waiting for initials is never removed, so an open editor
   * or a storage failure keeps blocking the attraction loop until the entry is resolved.
   *
   * @return {@code true} only when a non-qualifying candidate was discarded
   */
  public boolean dismissResolvedCandidate() {
    requireLoaded();
    if (pendingCandidate == null || qualifies(pendingCandidate)) {
      return false;
    }
    processedEntryIds.add(pendingCandidate.entryId());
    pendingCandidate = null;
    return true;
  }

  /**
   * Confirms the initials of the pending candidate and persists the new ranking.
   *
   * <p>The candidate is revalidated against the current ranking. On success the new snapshot is
   * saved before it replaces the session ranking. A storage failure keeps both the previous ranking
   * and the candidate so a retry reuses the same entry id and date.
   */
  public HallOfFameConfirmation confirm(String initials) {
    requireLoaded();
    HallOfFameCandidate candidate = pendingCandidate;
    if (candidate == null) {
      return new HallOfFameConfirmation(Status.NO_PENDING_ENTRY, ranking, Optional.empty());
    }
    if (processedEntryIds.contains(candidate.entryId())) {
      pendingCandidate = null;
      return new HallOfFameConfirmation(Status.ALREADY_CONFIRMED, ranking, Optional.empty());
    }
    if (!HallOfFameEntry.isValidInitials(initials)) {
      return new HallOfFameConfirmation(Status.INVALID_INITIALS, ranking, Optional.empty());
    }
    HallOfFameEntry entry = candidate.toEntry(initials);
    if (!ranking.qualifies(entry)) {
      processedEntryIds.add(candidate.entryId());
      pendingCandidate = null;
      return new HallOfFameConfirmation(Status.NOT_ELIGIBLE, ranking, Optional.of(entry));
    }
    HallOfFameRegistration registration = ranking.register(entry);
    if (!registration.registered()) {
      processedEntryIds.add(candidate.entryId());
      pendingCandidate = null;
      Status status = registration.status() == HallOfFameRegistration.Status.ALREADY_REGISTERED
          ? Status.ALREADY_CONFIRMED
          : Status.NOT_ELIGIBLE;
      return new HallOfFameConfirmation(status, ranking, Optional.of(entry));
    }
    if (!loadResult.writable()) {
      return new HallOfFameConfirmation(Status.STORAGE_FAILURE, ranking, Optional.of(entry));
    }
    try {
      repository.save(registration.ranking());
    } catch (HallOfFameStorageException failure) {
      return new HallOfFameConfirmation(Status.STORAGE_FAILURE, ranking, Optional.of(entry));
    }
    ranking = registration.ranking();
    processedEntryIds.add(candidate.entryId());
    pendingCandidate = null;
    return new HallOfFameConfirmation(Status.REGISTERED, ranking, Optional.of(entry));
  }

  private boolean qualifies(HallOfFameCandidate candidate) {
    return ranking.qualifies(probeEntry(candidate));
  }

  /**
   * Eligibility depends only on the durable id, points and terminal instant. The placeholder
   * initials are never persisted; they only satisfy the entry value object until the player chooses
   * real ones on confirmation.
   */
  private static HallOfFameEntry probeEntry(HallOfFameCandidate candidate) {
    return new HallOfFameEntry(candidate.entryId(), "AAA", candidate.points(),
        candidate.finishedAt());
  }

  private void requireLoaded() {
    if (!loaded) {
      throw new IllegalStateException(
          "HallOfFameSession.load() must be called before using the session");
    }
  }
}
