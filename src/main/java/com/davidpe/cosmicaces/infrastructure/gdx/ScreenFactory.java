package com.davidpe.cosmicaces.infrastructure.gdx;

import com.badlogic.gdx.Screen;
import com.davidpe.cosmicaces.application.AttractCycle;
import com.davidpe.cosmicaces.application.GameCoordinator;
import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.application.score.HallOfFameSession;
import com.davidpe.cosmicaces.domain.game.GameState;
import com.davidpe.cosmicaces.domain.weapon.MissileConfig;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventPublisher;
import com.davidpe.cosmicaces.infrastructure.gdx.input.GameInput;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.HallOfFameScreen;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.HallOfFameScroll;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneScreen;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.WelcomeScreen;
import java.util.Objects;

/**
 * Builds the LibGDX screens of the game and wires each one to the shared event publisher and to the
 * persistent game context (identity and phase). Screens never import the composition root, and every
 * playable screen gets its own {@link PhaseOneGameController}, so per-run state stays separate from the global
 * state owned by the {@link GameCoordinator}.
 */
public final class ScreenFactory {

  private final GameCoordinator coordinator;
  private final GameEventPublisher publisher;
  private final GameSettingsStore settingsStore = new GameSettingsStore();
  private final VideoDisplaySettings videoSettings = new VideoDisplaySettings();
  private final GamePresentationSettings presentationSettings = new GamePresentationSettings();
  private final HallOfFameSession hallOfFameSession;
  private final HallOfFameScroll hallOfFameScroll = new HallOfFameScroll();
  private final AttractCycle attractCycle;
  private final GameInput gameInput = new GameInput();

  public ScreenFactory(GameCoordinator coordinator, GameEventPublisher publisher,
      HallOfFameSession hallOfFameSession, AttractCycle attractCycle) {
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    this.publisher = Objects.requireNonNull(publisher, "publisher");
    this.hallOfFameSession = Objects.requireNonNull(hallOfFameSession, "hallOfFameSession");
    this.attractCycle = Objects.requireNonNull(attractCycle, "attractCycle");
  }

  /**
   * Loads file-backed preferences and the Hall of Fame once, after LibGDX creates its application
   * context. The ranking is not read again per frame, screen change or keystroke.
   */
  public void loadSettings() {
    settingsStore.initialize();
    gameInput.initialize();
    presentationSettings.loadFrom(settingsStore);
    videoSettings.useSettingsStore(settingsStore);
    videoSettings.restoreSavedPreference();
    hallOfFameSession.load();
  }

  /** Shared input service: one merged keyboard/gamepad sample per frame. */
  public GameInput gameInput() {
    return gameInput;
  }

  /** Polls input once for the frame; the composition root calls this before rendering. */
  public void pollInput() {
    gameInput.poll();
  }

  /** Clears held state and requires a neutral sample after a transition or focus change. */
  public void resetInput() {
    gameInput.reset();
  }

  /** Creates the welcome screen for the current welcome state. */
  public Screen createWelcomeScreen() {
    GameState state = coordinator.state();
    return new WelcomeScreen(
        publisher, state.gameId(), state.phase(), videoSettings, presentationSettings, settingsStore,
        attractCycle);
  }

  /**
   * Creates a playable screen with a fresh per-run session, connected to the shared publisher. The
   * run is not started here: the objective panel waits for the player's SPACE press, which starts
   * the controller. The persistent identity and phase are read from the coordinator, which also
   * supplies the authoritative phase result when the mission ends.
   */
  public Screen createPhaseOneScreen() {
    GameState state = coordinator.state();
    PhaseOneGameController controller = createPhaseOneController(settingsStore);
    return new PhaseOneScreen(
        publisher, controller, state.gameId(), state.phase(), coordinator::snapshot,
        presentationSettings);
  }

  /**
   * Creates the Hall of Fame screen from the shared application session. The screen reads the view
   * and load result once per visit, confirms the pending initials through the session and refreshes
   * its own view after a successful registration. The shared scroll keeps the reading position
   * between visits, and the shared attraction cycle alternates this page with the welcome lobby.
   * The screen receives the current identity and phase so a Y press in the consultation can request
   * the same start the welcome screen publishes.
   */
  public Screen createHallOfFameScreen() {
    GameState state = coordinator.state();
    return new HallOfFameScreen(hallOfFameSession, hallOfFameScroll, attractCycle,
        publisher, state.gameId(), state.phase());
  }

  /** Capture gameplay preferences once for a new run; subsequent edits affect future runs. */
  static PhaseOneGameController createPhaseOneController(GameSettingsStore store) {
    return new PhaseOneGameController(store.flightSpeeds(),
        MissileConfig.DEFAULTS.withAstraMissilesPerLife(store.astraMissilesPerLife())
            .withMaxSpeed(store.guidedMissileMaxSpeed()));
  }
}
