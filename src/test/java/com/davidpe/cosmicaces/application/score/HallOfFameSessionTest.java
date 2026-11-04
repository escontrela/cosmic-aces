package com.davidpe.cosmicaces.application.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.game.PhaseResult;
import com.davidpe.cosmicaces.domain.score.HallOfFame;
import com.davidpe.cosmicaces.domain.score.HallOfFameEntry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HallOfFameSessionTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-11-03T12:34:56Z");
  private static final ZonedDateTime FIXED_DATE = ZonedDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
  private static final GameId GAME_ONE = new GameId(1L);
  private static final GameId GAME_TWO = new GameId(2L);
  private static final PhaseResult WIN_RESULT =
      new PhaseResult(GAME_ONE, GamePhase.PLAYING_PHASE_ONE, 620, 2);
  private static final PhaseResult LOSE_RESULT =
      new PhaseResult(GAME_TWO, GamePhase.PLAYING_PHASE_ONE, 150, 0);

  @Test
  void loadsThePersistedRankingOnce() {
    InMemoryHallOfFameRepository repository =
        new InMemoryHallOfFameRepository(fullRanking(100));
    HallOfFameSession session = new HallOfFameSession(
        repository, FIXED_CLOCK, () -> "entry-1");

    assertEquals(0, repository.loadCalls());
    HallOfFameLoadResult first = session.load();
    HallOfFameLoadResult second = session.load();

    assertSame(first, second);
    assertEquals(1, repository.loadCalls());
    assertEquals(HallOfFameLoadResult.Status.LOADED, first.status());
    assertEquals(HallOfFame.MAX_ENTRIES, session.ranking().size());
  }

  @Test
  void refusesToOperateBeforeLoading() {
    HallOfFameSession session = new HallOfFameSession(
        new InMemoryHallOfFameRepository(), FIXED_CLOCK, () -> "entry-1");

    assertThrows(IllegalStateException.class, session::view);
    assertThrows(IllegalStateException.class,
        () -> session.captureTerminalResult(WIN_RESULT, PhaseOutcome.WIN));
    assertThrows(IllegalStateException.class, () -> session.confirm("ABC"));
  }

  @Test
  void queryingTheRankingDoesNotCreateACandidateOrWriteStorage() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = session(repository);

    HallOfFameView view = session.view();

    assertEquals(HallOfFameView.Mode.CONSULT, view.mode());
    assertTrue(view.candidate().isEmpty());
    assertTrue(session.pendingCandidate().isEmpty());
    assertTrue(session.ranking().isEmpty());
    assertEquals(0, repository.saveCalls());
  }

  @Test
  void capturesATerminalWinWithTheAuthoritativeSnapshotAndDate() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = session(repository);

    assertTrue(session.captureTerminalResult(WIN_RESULT, PhaseOutcome.WIN));

    HallOfFameCandidate candidate = session.pendingCandidate().orElseThrow();
    assertEquals("entry-1", candidate.entryId());
    assertEquals(GAME_ONE, candidate.gameId());
    assertEquals(620, candidate.points());
    assertEquals(2, candidate.lives());
    assertEquals(PhaseOutcome.WIN, candidate.outcome());
    assertEquals(FIXED_DATE, candidate.finishedAt());
    assertEquals(HallOfFameView.Mode.ENTRY, session.view().mode());
    assertEquals(0, repository.saveCalls());
  }

  @Test
  void capturesATerminalLossAfterThePreviousGameWasConfirmed() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = session(repository);
    assertTrue(session.captureTerminalResult(WIN_RESULT, PhaseOutcome.WIN));
    assertEquals(HallOfFameConfirmation.Status.REGISTERED, session.confirm("ABC").status());

    assertTrue(session.captureTerminalResult(LOSE_RESULT, PhaseOutcome.LOSE));

    HallOfFameCandidate candidate = session.pendingCandidate().orElseThrow();
    assertEquals("entry-2", candidate.entryId());
    assertEquals(GAME_TWO, candidate.gameId());
    assertEquals(150, candidate.points());
    assertEquals(0, candidate.lives());
    assertEquals(PhaseOutcome.LOSE, candidate.outcome());
  }

  @Test
  void ignoresANonTerminalCompletion() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = session(repository);
    PhaseResult inProgress = new PhaseResult(GAME_ONE, GamePhase.PLAYING_PHASE_ONE, 10, 3);

    assertFalse(session.captureTerminalResult(inProgress, PhaseOutcome.IN_PROGRESS));

    assertTrue(session.pendingCandidate().isEmpty());
    assertEquals(HallOfFameView.Mode.CONSULT, session.view().mode());
  }

  @Test
  void capturesEachGameOnlyOnceEvenAfterItWasConfirmed() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = session(repository);
    assertTrue(session.captureTerminalResult(WIN_RESULT, PhaseOutcome.WIN));
    String entryId = session.pendingCandidate().orElseThrow().entryId();
    assertEquals(HallOfFameConfirmation.Status.REGISTERED, session.confirm("ABC").status());

    assertFalse(session.captureTerminalResult(WIN_RESULT, PhaseOutcome.WIN));

    assertTrue(session.pendingCandidate().isEmpty());
    assertEquals(1, repository.saveCalls());
    assertEquals(entryId, repository.ranking().entries().get(0).id());
  }

  @Test
  void doesNotReplaceAQualifyingCandidateWithANewGame() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = session(repository);
    assertTrue(session.captureTerminalResult(WIN_RESULT, PhaseOutcome.WIN));

    assertFalse(session.captureTerminalResult(LOSE_RESULT, PhaseOutcome.WIN));

    assertEquals(GAME_ONE, session.pendingCandidate().orElseThrow().gameId());
  }

  @Test
  void replacesANonQualifyingCandidateWhenANewGameFinishes() {
    InMemoryHallOfFameRepository repository =
        new InMemoryHallOfFameRepository(fullRanking(1000));
    HallOfFameSession session = session(repository);
    PhaseResult low = new PhaseResult(GAME_ONE, GamePhase.PLAYING_PHASE_ONE, 0, 3);
    PhaseResult high = new PhaseResult(GAME_TWO, GamePhase.PLAYING_PHASE_ONE, 1001, 2);

    assertTrue(session.captureTerminalResult(low, PhaseOutcome.WIN));
    assertEquals(HallOfFameView.Mode.CONSULT, session.view().mode());
    assertEquals(GAME_ONE, session.pendingCandidate().orElseThrow().gameId());

    assertTrue(session.captureTerminalResult(high, PhaseOutcome.WIN));

    assertEquals(GAME_TWO, session.pendingCandidate().orElseThrow().gameId());
    assertEquals(HallOfFameView.Mode.ENTRY, session.view().mode());
  }

  @Test
  void dismissingANonQualifyingCandidateNeverWritesOrBlocksTheLobby() {
    InMemoryHallOfFameRepository repository =
        new InMemoryHallOfFameRepository(fullRanking(1000));
    HallOfFameSession session = session(repository);
    PhaseResult low = new PhaseResult(GAME_ONE, GamePhase.PLAYING_PHASE_ONE, 100, 3);
    assertTrue(session.captureTerminalResult(low, PhaseOutcome.WIN));
    assertEquals(HallOfFameView.Mode.CONSULT, session.view().mode());

    assertTrue(session.dismissResolvedCandidate());

    assertTrue(session.pendingCandidate().isEmpty());
    assertEquals(0, repository.saveCalls());
    assertEquals(HallOfFame.MAX_ENTRIES, session.ranking().size());
  }

  @Test
  void dismissingNeverRemovesAQualifyingCandidateOrAStorageFailure() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    repository.failNextSaves(1);
    HallOfFameSession session = session(repository);
    assertTrue(session.captureTerminalResult(WIN_RESULT, PhaseOutcome.WIN));
    assertEquals(HallOfFameView.Mode.ENTRY, session.view().mode());
    assertEquals(HallOfFameConfirmation.Status.STORAGE_FAILURE, session.confirm("ABC").status());

    assertFalse(session.dismissResolvedCandidate());

    assertTrue(session.pendingCandidate().isPresent());
    assertEquals(HallOfFameView.Mode.ENTRY, session.view().mode());
  }

  @Test
  void confirmsOncePersistsTheRankingAndClearsTheCandidate() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = session(repository);
    assertTrue(session.captureTerminalResult(WIN_RESULT, PhaseOutcome.WIN));

    HallOfFameConfirmation confirmation = session.confirm("ABC");

    assertEquals(HallOfFameConfirmation.Status.REGISTERED, confirmation.status());
    assertTrue(confirmation.registered());
    HallOfFameEntry entry = confirmation.entry().orElseThrow();
    assertEquals("entry-1", entry.id());
    assertEquals("ABC", entry.initials());
    assertEquals(620, entry.points());
    assertEquals(FIXED_DATE, entry.finishedAt());
    assertEquals(1, confirmation.ranking().size());
    assertEquals(1, repository.saveCalls());
    assertEquals(1, repository.ranking().size());
    assertTrue(session.pendingCandidate().isEmpty());

    HallOfFameConfirmation repeated = session.confirm("ABC");

    assertEquals(HallOfFameConfirmation.Status.NO_PENDING_ENTRY, repeated.status());
    assertEquals(1, repository.saveCalls());
  }

  @Test
  void invalidInitialsKeepTheCandidateForANewAttempt() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = session(repository);
    assertTrue(session.captureTerminalResult(WIN_RESULT, PhaseOutcome.WIN));

    HallOfFameConfirmation invalid = session.confirm("ab");

    assertEquals(HallOfFameConfirmation.Status.INVALID_INITIALS, invalid.status());
    assertTrue(session.pendingCandidate().isPresent());
    assertEquals(0, repository.saveCalls());

    assertEquals(HallOfFameConfirmation.Status.REGISTERED, session.confirm("XYZ").status());
  }

  @Test
  void storageFailureKeepsTheCandidateAndRankingForARetryWithTheSameIdentity() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    repository.failNextSaves(1);
    HallOfFameSession session = session(repository);
    assertTrue(session.captureTerminalResult(WIN_RESULT, PhaseOutcome.WIN));
    HallOfFameCandidate before = session.pendingCandidate().orElseThrow();

    HallOfFameConfirmation failed = session.confirm("ABC");

    assertEquals(HallOfFameConfirmation.Status.STORAGE_FAILURE, failed.status());
    assertEquals(1, repository.saveCalls());
    assertEquals(0, session.ranking().size());
    assertTrue(session.pendingCandidate().isPresent());
    assertEquals(before.entryId(), session.pendingCandidate().orElseThrow().entryId());
    assertEquals(before.finishedAt(), session.pendingCandidate().orElseThrow().finishedAt());

    HallOfFameConfirmation retried = session.confirm("ABC");

    assertEquals(HallOfFameConfirmation.Status.REGISTERED, retried.status());
    assertEquals(2, repository.saveCalls());
    assertEquals(1, repository.ranking().size());
    HallOfFameEntry saved = repository.ranking().entries().get(0);
    assertEquals(before.entryId(), saved.id());
    assertEquals(before.finishedAt(), saved.finishedAt());
  }

  @Test
  void aFullRankingRejectsANonQualifyingCandidateWithoutSaving() {
    InMemoryHallOfFameRepository repository =
        new InMemoryHallOfFameRepository(fullRanking(1000));
    HallOfFameSession session = session(repository);
    PhaseResult low = new PhaseResult(GAME_ONE, GamePhase.PLAYING_PHASE_ONE, 100, 3);
    assertTrue(session.captureTerminalResult(low, PhaseOutcome.WIN));
    assertEquals(HallOfFameView.Mode.CONSULT, session.view().mode());

    HallOfFameConfirmation confirmation = session.confirm("ABC");

    assertEquals(HallOfFameConfirmation.Status.NOT_ELIGIBLE, confirmation.status());
    assertEquals(0, repository.saveCalls());
    assertTrue(session.pendingCandidate().isEmpty());
    assertEquals(HallOfFame.MAX_ENTRIES, repository.ranking().size());
  }

  @Test
  void aFailedLoadIsNeverOverwritten() {
    InMemoryHallOfFameRepository repository =
        new InMemoryHallOfFameRepository(HallOfFameLoadResult.invalidData());
    HallOfFameSession session = session(repository);

    assertFalse(session.captureTerminalResult(WIN_RESULT, PhaseOutcome.WIN));

    assertEquals(HallOfFameView.Mode.CONSULT, session.view().mode());
    assertTrue(session.pendingCandidate().isEmpty());
    HallOfFameConfirmation confirmation = session.confirm("ABC");
    assertEquals(HallOfFameConfirmation.Status.NO_PENDING_ENTRY, confirmation.status());
    assertEquals(0, repository.saveCalls());
  }

  @Test
  void twoSuccessiveGamesBothRegister() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    HallOfFameSession session = session(repository);
    assertTrue(session.captureTerminalResult(WIN_RESULT, PhaseOutcome.WIN));
    assertEquals(HallOfFameConfirmation.Status.REGISTERED, session.confirm("ABC").status());
    assertTrue(session.captureTerminalResult(LOSE_RESULT, PhaseOutcome.LOSE));
    assertEquals(HallOfFameConfirmation.Status.REGISTERED, session.confirm("XYZ").status());

    assertEquals(2, repository.ranking().size());
    assertNotEquals(repository.ranking().entries().get(0).id(),
        repository.ranking().entries().get(1).id());
  }

  @Test
  void restartWithAPriorRankingDoesNotCollideWithTheSessionGameIdCounter() {
    HallOfFameEntry persisted = new HallOfFameEntry(
        "persisted-row", "OLD", 900, FIXED_DATE.minusSeconds(60));
    InMemoryHallOfFameRepository repository =
        new InMemoryHallOfFameRepository(HallOfFame.of(List.of(persisted)));
    HallOfFameSession session = session(repository);
    PhaseResult firstGameAfterRestart =
        new PhaseResult(new GameId(0L), GamePhase.PLAYING_PHASE_ONE, 950, 2);

    assertTrue(session.captureTerminalResult(firstGameAfterRestart, PhaseOutcome.WIN));
    assertTrue(session.confirm("NEW").registered());

    assertEquals(2, session.ranking().size());
    assertEquals("entry-1", session.ranking().entries().get(0).id());
    assertEquals("persisted-row", session.ranking().entries().get(1).id());
  }

  private static HallOfFameSession session(InMemoryHallOfFameRepository repository) {
    AtomicInteger counter = new AtomicInteger();
    HallOfFameSession session = new HallOfFameSession(
        repository, FIXED_CLOCK, () -> "entry-" + counter.incrementAndGet());
    session.load();
    return session;
  }

  private static HallOfFame fullRanking(int points) {
    List<HallOfFameEntry> entries = new ArrayList<>(HallOfFame.MAX_ENTRIES);
    for (int index = 0; index < HallOfFame.MAX_ENTRIES; index++) {
      entries.add(new HallOfFameEntry(
          "full-" + index, "AAA", points, FIXED_DATE.minusSeconds(index + 1L)));
    }
    return HallOfFame.of(entries);
  }
}
