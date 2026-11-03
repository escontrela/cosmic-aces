package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.application.score.HallOfFameCandidate;
import com.davidpe.cosmicaces.application.score.HallOfFameConfirmation;
import com.davidpe.cosmicaces.application.score.HallOfFameSession;
import com.davidpe.cosmicaces.application.score.HallOfFameView;
import com.davidpe.cosmicaces.application.score.InMemoryHallOfFameRepository;
import com.davidpe.cosmicaces.domain.game.GameAbandoned;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.LifeLost;
import com.davidpe.cosmicaces.domain.game.PhaseCompleted;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.game.PhaseResult;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import com.davidpe.cosmicaces.domain.game.StartRequested;
import com.davidpe.cosmicaces.domain.score.HallOfFame;
import com.davidpe.cosmicaces.domain.score.HallOfFameEntry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * End-to-end application flow of COS-124 without LibGDX: a terminal mission opens the Hall of Fame,
 * the qualifying candidate is only persisted when the initials are confirmed, a non-qualifying game
 * never offers the editor or writes, and abandonment before the result never creates an entry.
 */
class HallOfFameTerminalFlowTest {

  private static final int STARTING_LIVES = 3;

  @Test
  void terminalResultOpensTheHallAndConfirmationPersistsTheRanking() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = loadedSession(repository);
    GameCoordinator coordinator = startedCoordinator(session, 321);

    PhaseResult terminal = coordinator.snapshot();
    coordinator.onEvent(new PhaseCompleted(terminal, PhaseOutcome.WIN));

    assertEquals(GamePhase.HALL_OF_FAME, coordinator.state().phase());
    assertEquals(GamePhase.HALL_OF_FAME, coordinator.consumePendingTransition());
    HallOfFameCandidate candidate = session.pendingCandidate().orElseThrow();
    assertEquals(321, candidate.points());
    assertEquals(2, candidate.lives());
    assertEquals(HallOfFameView.Mode.ENTRY, session.view().mode());
    assertEquals(0, repository.saveCalls(), "opening the Hall never writes");

    HallOfFameConfirmation confirmation = session.confirm("ACE");

    assertTrue(confirmation.registered());
    assertEquals(1, repository.saveCalls());
    HallOfFameEntry persisted = repository.ranking().entries().get(0);
    assertEquals("ACE", persisted.initials());
    assertEquals(321, persisted.points());
    assertEquals(candidate.finishedAt(), persisted.finishedAt());
    assertEquals(candidate.entryId(), persisted.id());
    assertEquals(HallOfFameView.Mode.CONSULT, session.view().mode());
    assertTrue(session.pendingCandidate().isEmpty());

    // A new session reads the snapshot persisted by the confirmation.
    HallOfFameSession reloaded = loadedSession(repository);
    assertEquals(1, reloaded.ranking().size());
    assertEquals("ACE", reloaded.ranking().entries().get(0).initials());
    assertEquals(321, reloaded.ranking().entries().get(0).points());
  }

  @Test
  void aZeroScoreStillQualifiesWhileTheRankingIsShort() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = loadedSession(repository);
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES, session);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    for (int life = 0; life < STARTING_LIVES; life++) {
      coordinator.onEvent(new LifeLost(
          coordinator.state().gameId(), GamePhase.PLAYING_PHASE_ONE));
    }

    coordinator.onEvent(new PhaseCompleted(coordinator.snapshot(), PhaseOutcome.LOSE));

    HallOfFameCandidate candidate = session.pendingCandidate().orElseThrow();
    assertEquals(0, candidate.points());
    assertEquals(HallOfFameView.Mode.ENTRY, session.view().mode());
    assertTrue(session.confirm("ZED").registered());
    assertEquals(1, repository.ranking().size());
    assertEquals(0, repository.ranking().entries().get(0).points());
  }

  @Test
  void aNonQualifyingTerminalShowsConsultationWithoutEditorOrWrite() {
    InMemoryHallOfFameRepository repository =
        new InMemoryHallOfFameRepository(fullRanking());
    HallOfFameSession session = loadedSession(repository);
    GameCoordinator coordinator = startedCoordinator(session, 5);

    coordinator.onEvent(new PhaseCompleted(coordinator.snapshot(), PhaseOutcome.LOSE));

    assertEquals(GamePhase.HALL_OF_FAME, coordinator.state().phase());
    assertEquals(GamePhase.HALL_OF_FAME, coordinator.consumePendingTransition());
    assertTrue(session.pendingCandidate().isPresent(),
        "the screen needs the score to explain the non-qualifying game");
    assertFalse(session.view().entryRequested(), "the editor is never offered");
    assertEquals(0, repository.saveCalls());

    HallOfFameConfirmation refused = session.confirm("AAA");
    assertEquals(HallOfFameConfirmation.Status.NOT_ELIGIBLE, refused.status());
    assertEquals(0, repository.saveCalls(), "a non-qualifying game never inserts a row");
    assertEquals(HallOfFame.MAX_ENTRIES, repository.ranking().size());
  }

  @Test
  void aStorageFailureKeepsTheCandidateAndTheRetrySavesOnlyOneRow() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = loadedSession(repository);
    GameCoordinator coordinator = startedCoordinator(session, 200);
    coordinator.onEvent(new PhaseCompleted(coordinator.snapshot(), PhaseOutcome.WIN));
    HallOfFameCandidate candidate = session.pendingCandidate().orElseThrow();
    repository.failNextSaves(1);

    HallOfFameConfirmation failed = session.confirm("XYZ");

    assertEquals(HallOfFameConfirmation.Status.STORAGE_FAILURE, failed.status());
    assertEquals(candidate.entryId(), session.pendingCandidate().orElseThrow().entryId());
    assertEquals(200, session.pendingCandidate().orElseThrow().points());
    assertEquals(HallOfFameView.Mode.ENTRY, session.view().mode());
    assertTrue(repository.ranking().isEmpty());
    assertEquals(1, repository.saveCalls());

    HallOfFameConfirmation retried = session.confirm("XYZ");

    assertTrue(retried.registered());
    assertEquals(2, repository.saveCalls());
    assertEquals(1, repository.ranking().size());
    assertEquals(candidate.entryId(), repository.ranking().entries().get(0).id());
    assertTrue(session.pendingCandidate().isEmpty());
  }

  @Test
  void repeatedConfirmationNeverInsertsASecondRow() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = loadedSession(repository);
    GameCoordinator coordinator = startedCoordinator(session, 75);
    coordinator.onEvent(new PhaseCompleted(coordinator.snapshot(), PhaseOutcome.WIN));

    assertTrue(session.confirm("ABC").registered());
    HallOfFameConfirmation repeated = session.confirm("ABC");

    assertEquals(HallOfFameConfirmation.Status.NO_PENDING_ENTRY, repeated.status());
    assertEquals(1, repository.saveCalls());
    assertEquals(1, repository.ranking().size());
  }

  @Test
  void abandonmentBeforeTheResultReturnsToWelcomeWithoutAnEntry() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = loadedSession(repository);
    GameCoordinator coordinator = startedCoordinator(session, 90);

    coordinator.onEvent(new GameAbandoned(
        coordinator.state().gameId(), GamePhase.PLAYING_PHASE_ONE));

    assertEquals(GamePhase.WELCOME, coordinator.state().phase());
    assertEquals(GamePhase.WELCOME, coordinator.consumePendingTransition());
    assertEquals(0, coordinator.state().points());
    assertTrue(session.pendingCandidate().isEmpty());
    assertEquals(HallOfFameView.Mode.CONSULT, session.view().mode());
    assertEquals(0, repository.saveCalls());
  }

  @Test
  void confirmationKeepsTheTerminalDateWhenItHappensAfterMidnight() {
    MutableClock clock = new MutableClock(Instant.parse("2026-11-03T23:59:59Z"));
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = loadedSession(repository, clock);
    GameCoordinator coordinator = startedCoordinator(session, 120);
    coordinator.onEvent(new PhaseCompleted(coordinator.snapshot(), PhaseOutcome.WIN));
    HallOfFameCandidate candidate = session.pendingCandidate().orElseThrow();
    clock.advance(Duration.ofMinutes(5));

    assertTrue(session.confirm("MID").registered());

    HallOfFameEntry persisted = repository.ranking().entries().get(0);
    assertEquals(candidate.finishedAt(), persisted.finishedAt());
    assertEquals(ZonedDateTime.parse("2026-11-03T23:59:59Z"), persisted.finishedAt());
  }

  private static GameCoordinator startedCoordinator(HallOfFameSession session, int points) {
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES, session);
    coordinator.onEvent(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    assertEquals(GamePhase.PLAYING_PHASE_ONE, coordinator.consumePendingTransition());
    coordinator.onEvent(new PointsEarned(
        coordinator.state().gameId(), GamePhase.PLAYING_PHASE_ONE, points));
    coordinator.onEvent(new LifeLost(
        coordinator.state().gameId(), GamePhase.PLAYING_PHASE_ONE));
    return coordinator;
  }

  private static HallOfFameSession loadedSession(InMemoryHallOfFameRepository repository) {
    return loadedSession(repository,
        Clock.fixed(Instant.parse("2026-11-03T12:34:56Z"), ZoneOffset.UTC));
  }

  private static HallOfFameSession loadedSession(
      InMemoryHallOfFameRepository repository, Clock clock) {
    AtomicInteger counter = new AtomicInteger();
    HallOfFameSession session =
        new HallOfFameSession(repository, clock, () -> "entry-" + counter.incrementAndGet());
    session.load();
    return session;
  }

  private static HallOfFame fullRanking() {
    List<HallOfFameEntry> entries = new ArrayList<>(HallOfFame.MAX_ENTRIES);
    ZonedDateTime finishedAt = ZonedDateTime.parse("2026-10-01T10:00:00Z");
    for (int row = 0; row < HallOfFame.MAX_ENTRIES; row++) {
      entries.add(new HallOfFameEntry(
          "seed-" + row, "AAA", 1000 - row, finishedAt.plusSeconds(row)));
    }
    return HallOfFame.of(entries);
  }

  /** Clock whose instant can be moved forward without changing its zone. */
  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
