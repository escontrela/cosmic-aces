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

  public ScreenFactory(GameCoordinator coordinator, GameEventPublisher publisher) {
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    this.publisher = Objects.requireNonNull(publisher, "publisher");
  }

  /** Creates the welcome screen for the current welcome state. */
  public Screen createWelcomeScreen() {
    GameState state = coordinator.state();
    return new WelcomeScreen(publisher, state.gameId(), state.phase());
  }

  /**
   * Creates a playable screen with a fresh per-run session, started and connected to the shared
   * publisher. The persistent identity and phase are read from the coordinator, which also supplies
   * the authoritative phase result when the run completes.
   */
  public Screen createPhaseOneScreen() {
    GameState state = coordinator.state();
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.start();
    return new PhaseOneScreen(
        publisher, controller, state.gameId(), state.phase(), coordinator::snapshot);
  }
}
