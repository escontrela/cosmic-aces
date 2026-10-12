package com.davidpe.cosmicaces.infrastructure.gdx;

import com.badlogic.gdx.Screen;
import com.davidpe.cosmicaces.application.GameCoordinator;
import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.domain.game.GameState;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventPublisher;
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

  public ScreenFactory(GameCoordinator coordinator, GameEventPublisher publisher) {
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    this.publisher = Objects.requireNonNull(publisher, "publisher");
  }

  /** Loads file-backed preferences after LibGDX creates its application context. */
  public void loadSettings() {
    settingsStore.initialize();
    presentationSettings.loadFrom(settingsStore);
    videoSettings.useSettingsStore(settingsStore);
    videoSettings.restoreSavedPreference();
  }

  /** Creates the welcome screen for the current welcome state. */
  public Screen createWelcomeScreen() {
    GameState state = coordinator.state();
    return new WelcomeScreen(
        publisher, state.gameId(), state.phase(), videoSettings, presentationSettings, settingsStore);
  }

  /**
   * Creates a playable screen with a fresh per-run session, connected to the shared publisher. The
   * run is not started here: the objective panel waits for the player's SPACE press, which starts
   * the controller. The persistent identity and phase are read from the coordinator, which also
   * supplies the authoritative phase result when the mission ends.
   */
  public Screen createPhaseOneScreen() {
    GameState state = coordinator.state();
    PhaseOneGameController controller = new PhaseOneGameController(settingsStore.flightSpeeds());
    return new PhaseOneScreen(
        publisher, controller, state.gameId(), state.phase(), coordinator::snapshot,
        presentationSettings);
  }
}
