package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.application.score.HallOfFameConfirmation;
import com.davidpe.cosmicaces.application.score.HallOfFameSession;
import com.davidpe.cosmicaces.application.score.HallOfFameView;
import com.davidpe.cosmicaces.application.score.InMemoryHallOfFameRepository;
import com.davidpe.cosmicaces.domain.game.GameAbandoned;
import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.GameState;
import com.davidpe.cosmicaces.domain.game.PhaseCompleted;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import com.davidpe.cosmicaces.domain.game.StartRequested;
import com.davidpe.cosmicaces.domain.score.HallOfFame;
import com.davidpe.cosmicaces.domain.score.HallOfFameEntry;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.HallOfFameLayout;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.HallOfFameScroll;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Integration of the arcade attraction loop with the application state and the Hall of Fame session,
 * using a fake screen action harness and no LibGDX backend. The harness mirrors the composition root:
 * a start/confirmation handled by the active screen wins over a timeout, at most one navigation is
 * applied per frame, the cycle is paused by Ajustes/the editor/an active mission, and the scroll is
 * advanced only while the consultation is visible.
 */
class AttractionFlowTest {

  private static final int STARTING_LIVES = 3;
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-11-03T12:34:56Z"), ZoneOffset.UTC);
  private static final ZonedDateTime FIXED_DATE =
      ZonedDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC);
  private static final AtomicInteger ENTRY_IDS = new AtomicInteger();

  @Test
  void alternatesWelcomeAndConsultEveryTenSecondsWithoutCreatingAGame() {
    Harness harness = new Harness(new InMemoryHallOfFameRepository());
    GameId lobbyId = harness.coordinator.state().gameId();

    harness.frame(9.999);
    assertEquals(List.of(Screen.WELCOME), harness.screens);

    harness.frame(0.001);
    assertEquals(List.of(Screen.WELCOME, Screen.HALL_OF_FAME), harness.screens);
    assertEquals(GamePhase.WELCOME, harness.coordinator.state().phase());
    assertEquals(lobbyId, harness.coordinator.state().gameId(),
        "a rest visit never invents a new game identity");

    harness.frame(10.0);
    assertEquals(List.of(Screen.WELCOME, Screen.HALL_OF_FAME, Screen.WELCOME), harness.screens);
    assertEquals(lobbyId, harness.coordinator.state().gameId());

    harness.frame(10.0);
    assertEquals(Screen.HALL_OF_FAME, harness.current());
  }

  @Test
  void yInTheWelcomeStartsOneGameAndTheCoincidentTimeoutIsIgnored() {
    Harness harness = new Harness(new InMemoryHallOfFameRepository());

    harness.frame(10.0, true);

    assertEquals(List.of(Screen.WELCOME, Screen.PHASE_ONE), harness.screens);
    assertEquals(GamePhase.PLAYING_PHASE_ONE, harness.coordinator.state().phase());
    assertTrue(harness.cycle.paused());

    harness.frame(30.0);
    assertEquals(List.of(Screen.WELCOME, Screen.PHASE_ONE), harness.screens);
  }

  @Test
  void yInTheHallConsultStartsOneGameWithoutOpeningAStaleWelcome() {
    Harness harness = new Harness(new InMemoryHallOfFameRepository());
    harness.frame(10.0);
    assertEquals(Screen.HALL_OF_FAME, harness.current());

    harness.frame(10.0, true);

    assertEquals(Screen.PHASE_ONE, harness.current());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, harness.coordinator.state().phase());
    assertEquals(1, harness.screens.stream().filter(Screen.PHASE_ONE::equals).count());

    harness.frame(30.0);
    assertEquals(Screen.PHASE_ONE, harness.current());
  }

  @Test
  void aStaleStartFromAnEarlierConsultIsIgnoredAfterTheGameStarts() {
    Harness harness = new Harness(new InMemoryHallOfFameRepository());
    harness.frame(10.0);
    GameState consult = harness.coordinator.state();
    StartRequested stale = new StartRequested(consult.gameId(), consult.phase());

    harness.frame(10.0, true);
    GameState playing = harness.coordinator.state();
    harness.coordinator.onEvent(new PointsEarned(playing.gameId(), GamePhase.PLAYING_PHASE_ONE, 70));

    harness.coordinator.onEvent(stale);

    assertEquals(GamePhase.PLAYING_PHASE_ONE, harness.coordinator.state().phase());
    assertEquals(playing.gameId(), harness.coordinator.state().gameId());
    assertEquals(70, harness.coordinator.state().points());
    assertTrue(harness.coordinator.consumePendingTransition() == null);
  }

  @Test
  void settingsOpenLongerThanTheIntervalKeepWelcomeAndRearmAFullInterval() {
    Harness harness = new Harness(new InMemoryHallOfFameRepository());
    harness.settingsOpen = true;

    harness.frame(45.0);

    assertEquals(List.of(Screen.WELCOME), harness.screens);

    harness.settingsOpen = false;
    harness.frame(9.999);
    assertEquals(List.of(Screen.WELCOME), harness.screens);
    harness.frame(0.001);
    assertEquals(Screen.HALL_OF_FAME, harness.current());
  }

  @Test
  void theEditorSurvivesTheIntervalAndStorageFailureDoesNotUnlockTheCycle() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    Harness harness = new Harness(repository);
    harness.playTerminal(PhaseOutcome.WIN, 500);

    assertEquals(Screen.HALL_OF_FAME, harness.current());
    assertTrue(harness.editing);
    harness.frame(45.0);
    assertEquals(Screen.HALL_OF_FAME, harness.current(), "the editor is never expelled");
    assertTrue(harness.session.pendingCandidate().isPresent());

    repository.failNextSaves(1);
    HallOfFameConfirmation failed = harness.confirm("ABC");
    assertEquals(HallOfFameConfirmation.Status.STORAGE_FAILURE, failed.status());
    assertTrue(harness.editing);
    harness.frame(45.0);
    assertEquals(Screen.HALL_OF_FAME, harness.current());

    HallOfFameConfirmation registered = harness.confirm("ABC");
    assertTrue(registered.registered());
    assertFalse(harness.editing);
    assertEquals(2, repository.saveCalls());

    harness.frame(9.999);
    assertEquals(Screen.HALL_OF_FAME, harness.current(),
        "the successful registration starts a complete consultation interval");
    harness.frame(0.001);
    assertEquals(Screen.WELCOME, harness.current());
    assertEquals(GamePhase.WELCOME, harness.coordinator.state().phase());
  }

  @Test
  void aNonQualifyingTerminalOffersAFullConsultationThenDismissesTheNotice() {
    InMemoryHallOfFameRepository repository =
        new InMemoryHallOfFameRepository(fullRanking());
    Harness harness = new Harness(repository);
    harness.playTerminal(PhaseOutcome.LOSE, 5);

    assertEquals(Screen.HALL_OF_FAME, harness.current());
    assertFalse(harness.editing);
    assertTrue(harness.session.pendingCandidate().isPresent());
    assertEquals(HallOfFameView.Mode.CONSULT, harness.session.view().mode());

    harness.frame(9.999);
    assertEquals(Screen.HALL_OF_FAME, harness.current());
    harness.frame(0.001);
    assertEquals(Screen.WELCOME, harness.current());
    assertTrue(harness.session.pendingCandidate().isEmpty(),
        "the resolved non-qualifying notice does not survive into the next attraction visit");

    harness.frame(10.0);
    assertEquals(Screen.HALL_OF_FAME, harness.current());
    assertTrue(harness.session.pendingCandidate().isEmpty());
    assertEquals(0, repository.saveCalls());
  }

  @Test
  void yInTheTerminalConsultStartsOneGameAndDiscardsTheResolvedNotice() {
    InMemoryHallOfFameRepository repository =
        new InMemoryHallOfFameRepository(fullRanking());
    Harness harness = new Harness(repository);
    harness.playTerminal(PhaseOutcome.LOSE, 5);
    assertEquals(Screen.HALL_OF_FAME, harness.current());
    assertTrue(harness.session.pendingCandidate().isPresent());

    harness.frame(1.0, true);

    assertEquals(Screen.PHASE_ONE, harness.current());
    assertEquals(GamePhase.PLAYING_PHASE_ONE, harness.coordinator.state().phase());
    assertTrue(harness.session.pendingCandidate().isEmpty());
    assertEquals(0, repository.saveCalls());
  }

  @Test
  void theScrollReachesTheLastPlaceAcrossVisitsAndResizeKeepsItBounded() {
    Harness harness = new Harness(new InMemoryHallOfFameRepository(fullRanking()));

    double previousOffset = 0.0;
    for (int visit = 0; visit < 5; visit++) {
      harness.frame(10.0);
      assertEquals(Screen.HALL_OF_FAME, harness.current());
      harness.frame(10.0);
      assertEquals(Screen.WELCOME, harness.current());
      assertTrue(harness.scroll.offsetRows() > previousOffset,
          "each visit continues from the previous reading position: " + harness.scroll);
      previousOffset = harness.scroll.offsetRows();
    }

    harness.frame(10.0);
    assertEquals(Screen.HALL_OF_FAME, harness.current());
    harness.frame(2.6);
    assertEquals(Screen.HALL_OF_FAME, harness.current());

    assertEquals(50, harness.session.ranking().size());
    assertTrue(harness.scroll.atEnd(), harness.scroll.toString());
    assertTrue(harness.scroll.firstVisibleRow() + harness.scroll.visibleRows() >= 50,
        "the fiftieth place must become visible: " + harness.scroll);

    double reached = harness.scroll.offsetRows();
    harness.scroll.sync(50, 20);
    assertEquals(Math.min(reached, harness.scroll.maxOffsetRows()), harness.scroll.offsetRows());
    harness.scroll.sync(50, 8);
    assertTrue(harness.scroll.offsetRows() <= harness.scroll.maxOffsetRows());
  }

  @Test
  void restartingTheApplicationRecoversTheRankingWithoutDuplicatingRows() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    Harness first = new Harness(repository);
    first.playTerminal(PhaseOutcome.WIN, 900);
    assertTrue(first.confirm("ACE").registered());

    Harness restarted = new Harness(repository);
    assertEquals(1, restarted.session.ranking().size());
    HallOfFameEntry recovered = restarted.session.ranking().entries().get(0);
    assertEquals("ACE", recovered.initials());
    assertEquals(900, recovered.points());
    assertEquals(FIXED_DATE, recovered.finishedAt());

    restarted.playTerminal(PhaseOutcome.LOSE, 100);
    assertTrue(restarted.confirm("ZED").registered());

    Harness again = new Harness(repository);
    List<HallOfFameEntry> entries = again.session.ranking().entries();
    assertEquals(2, entries.size());
    assertEquals("ACE", entries.get(0).initials());
    assertEquals(900, entries.get(0).points());
    assertEquals("ZED", entries.get(1).initials());
    assertEquals(100, entries.get(1).points());
    assertNotEquals(entries.get(0).id(), entries.get(1).id());
    assertEquals(FIXED_DATE, entries.get(1).finishedAt());
  }

  @Test
  void abandoningAMissionNeverCreatesAnEntry() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    Harness harness = new Harness(repository);
    harness.frame(10.0, true);
    assertEquals(GamePhase.PLAYING_PHASE_ONE, harness.coordinator.state().phase());

    harness.abandon();

    assertEquals(Screen.WELCOME, harness.current());
    assertEquals(GamePhase.WELCOME, harness.coordinator.state().phase());
    assertTrue(harness.session.pendingCandidate().isEmpty());
    assertEquals(0, repository.saveCalls());
  }

  @Test
  void returningToTheLobbyIsRefusedWhileAQualifyingCandidateWaits() {
    InMemoryHallOfFameRepository repository = new InMemoryHallOfFameRepository();
    Harness harness = new Harness(repository);
    harness.playTerminal(PhaseOutcome.WIN, 400);
    GameId terminalId = harness.coordinator.state().gameId();

    assertFalse(harness.coordinator.returnToAttractionLobby());
    assertEquals(GamePhase.HALL_OF_FAME, harness.coordinator.state().phase());
    assertTrue(harness.session.pendingCandidate().isPresent());

    assertTrue(harness.confirm("ABC").registered());
    assertTrue(harness.coordinator.returnToAttractionLobby());

    assertEquals(GamePhase.WELCOME, harness.coordinator.state().phase());
    assertNotEquals(terminalId, harness.coordinator.state().gameId());
    assertTrue(harness.session.pendingCandidate().isEmpty());
    assertEquals(1, repository.saveCalls());
  }

  private enum Screen {
    WELCOME,
    HALL_OF_FAME,
    PHASE_ONE
  }

  /**
   * Fake composition root: the only production types exercised are the coordinator, the attraction
   * cycle and the Hall of Fame session. No screen or LibGDX backend is instantiated.
   */
  private static final class Harness {

    private final InMemoryHallOfFameRepository repository;
    private final HallOfFameSession session;
    private final GameCoordinator coordinator;
    private final AttractCycle cycle = new AttractCycle();
    private final HallOfFameScroll scroll = new HallOfFameScroll();
    private final List<Screen> screens = new ArrayList<>();
    private boolean settingsOpen;
    private boolean editing;

    private Harness(InMemoryHallOfFameRepository repository) {
      this.repository = repository;
      this.session = loadedSession(repository);
      this.coordinator = new GameCoordinator(STARTING_LIVES, session);
      screens.add(Screen.WELCOME);
      cycle.enter(AttractCycle.Page.WELCOME);
    }

    /**
     * One application frame: screen input first, the coordinator transition second, and only then
     * the attraction timeout. This is the same order as {@code CosmicAcesGame.render()}.
     */
    private void frame(double delta) {
      frame(delta, false);
    }

    private void frame(double delta, boolean yPressed) {
      if (yPressed && acceptsRestInput()) {
        startGame();
      }
      GamePhase transition = coordinator.consumePendingTransition();
      if (transition != null) {
        applyTransition(transition);
        return;
      }
      if (!isRestState()) {
        return;
      }
      if (current() == Screen.HALL_OF_FAME) {
        scroll.sync(session.ranking().size(), visibleRows());
        if (!session.view().entryRequested()) {
          advanceScrollLikeFrames(delta);
        }
      }
      cycle.setPaused(settingsOpen || editing);
      cycle.tick(delta).ifPresent(this::applyAttractionPage);
    }

    private boolean acceptsRestInput() {
      if (current() == Screen.PHASE_ONE) {
        return false;
      }
      return current() != Screen.HALL_OF_FAME || !session.view().entryRequested();
    }

    private void startGame() {
      GameState state = coordinator.state();
      coordinator.onEvent(new StartRequested(state.gameId(), state.phase()));
    }

    private void applyTransition(GamePhase phase) {
      switch (phase) {
        case WELCOME -> {
          screens.add(Screen.WELCOME);
          cycle.enter(AttractCycle.Page.WELCOME);
          editing = false;
        }
        case PLAYING_PHASE_ONE -> {
          screens.add(Screen.PHASE_ONE);
          cycle.pause();
        }
        case HALL_OF_FAME -> {
          screens.add(Screen.HALL_OF_FAME);
          cycle.enter(AttractCycle.Page.HALL_OF_FAME);
          editing = session.view().entryRequested();
        }
      }
    }

    private void applyAttractionPage(AttractCycle.Page page) {
      if (page == AttractCycle.Page.WELCOME) {
        if (!coordinator.returnToAttractionLobby()) {
          cycle.enter(AttractCycle.Page.HALL_OF_FAME);
          return;
        }
        screens.add(Screen.WELCOME);
        cycle.enter(AttractCycle.Page.WELCOME);
        editing = false;
        return;
      }
      screens.add(Screen.HALL_OF_FAME);
      cycle.enter(AttractCycle.Page.HALL_OF_FAME);
      editing = session.view().entryRequested();
    }

    private void playTerminal(PhaseOutcome outcome, int points) {
      if (coordinator.state().phase() != GamePhase.PLAYING_PHASE_ONE) {
        startGame();
        GamePhase playing = coordinator.consumePendingTransition();
        if (playing != null) {
          applyTransition(playing);
        }
      }
      GameState state = coordinator.state();
      if (points > 0) {
        coordinator.onEvent(new PointsEarned(state.gameId(), GamePhase.PLAYING_PHASE_ONE, points));
      }
      coordinator.onEvent(new PhaseCompleted(coordinator.snapshot(), outcome));
      GamePhase transition = coordinator.consumePendingTransition();
      if (transition != null) {
        applyTransition(transition);
      }
    }

    private HallOfFameConfirmation confirm(String initials) {
      HallOfFameConfirmation confirmation = session.confirm(initials);
      editing = session.view().entryRequested();
      if (confirmation.registered()) {
        cycle.restartInterval();
      }
      return confirmation;
    }

    private void abandon() {
      GameState state = coordinator.state();
      coordinator.onEvent(new GameAbandoned(state.gameId(), state.phase()));
      GamePhase transition = coordinator.consumePendingTransition();
      if (transition != null) {
        applyTransition(transition);
      }
    }

    private boolean isRestState() {
      GamePhase phase = coordinator.state().phase();
      return phase == GamePhase.WELCOME || phase == GamePhase.HALL_OF_FAME;
    }

    private Screen current() {
      return screens.get(screens.size() - 1);
    }

    /**
     * The real screen advances the scroll once per rendered frame, so a ten-second visit is many
     * bounded steps instead of one clamped step.
     */
    private void advanceScrollLikeFrames(double delta) {
      double step = 1.0 / 60.0;
      double remaining = delta;
      while (remaining > 0.0) {
        double slice = Math.min(step, remaining);
        scroll.advance(slice);
        remaining -= slice;
      }
    }

    private static int visibleRows() {
      float artHeight = HallOfFameLayout.fitCentered(800f, 600f).height();
      HallOfFameLayout.Area table = HallOfFameLayout.tableArea(800f, 600f);
      return HallOfFameLayout.visibleRows(table, HallOfFameLayout.rowHeight(artHeight));
    }
  }

  private static HallOfFameSession loadedSession(InMemoryHallOfFameRepository repository) {
    HallOfFameSession session = new HallOfFameSession(
        repository, FIXED_CLOCK, () -> "entry-" + ENTRY_IDS.incrementAndGet());
    session.load();
    return session;
  }

  private static HallOfFame fullRanking() {
    List<HallOfFameEntry> entries = new ArrayList<>(HallOfFame.MAX_ENTRIES);
    for (int row = 0; row < HallOfFame.MAX_ENTRIES; row++) {
      entries.add(new HallOfFameEntry(
          "seed-" + row, "AAA", 1000 - row, FIXED_DATE.minusSeconds(row + 1L)));
    }
    return HallOfFame.of(entries);
  }
}
