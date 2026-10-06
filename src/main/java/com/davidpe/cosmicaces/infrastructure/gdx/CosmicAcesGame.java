package com.davidpe.cosmicaces.infrastructure.gdx;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.davidpe.cosmicaces.domain.game.GameAbandoned;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.LifeLost;
import com.davidpe.cosmicaces.domain.game.PhaseCompleted;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import com.davidpe.cosmicaces.domain.game.StartRequested;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventBus;
import com.davidpe.cosmicaces.infrastructure.gdx.event.Subscription;
import java.util.ArrayList;
import java.util.List;

/**
 * LibGDX composition root that coordinates screens and delegates game rules to the application. It
 * keeps the event bus and the {@link GameCoordinator} that owns the persistent player state, uses
 * the {@link ScreenFactory} to build screens wired to the same bus, and applies screen transitions
 * only after the current frame finished rendering, so a screen that requests a change during its own
 * {@code render} is never disposed mid-frame.
 */
public final class CosmicAcesGame extends Game {

  /**
   * Placeholder number of lives for a new game. COS-16 explicitly defers the lives policy to a later
   * product ticket, so this value is only what the model needs to create a state. No mechanic reads
   * or changes lives yet and it has no player-visible effect.
   */
  private static final int PLACEHOLDER_STARTING_LIVES = 1;

  private final GameEventBus bus = new GameEventBus();
  private final GameCoordinator coordinator = new GameCoordinator(PLACEHOLDER_STARTING_LIVES);
  private final ScreenFactory screenFactory = new ScreenFactory(coordinator, bus);
  private final List<Subscription> subscriptions = new ArrayList<>();

  @Override
  public void create() {
    subscribeCoordinator();
    showScreen(screenFactory.createWelcomeScreen());
  }

  @Override
  public void render() {
    super.render();
    applyPendingTransition();
  }

  @Override
  public void dispose() {
    Screen activeScreen = getScreen();
    super.dispose();
    if (activeScreen != null) {
      activeScreen.dispose();
    }
    subscriptions.forEach(Subscription::cancel);
    subscriptions.clear();
  }

  private void subscribeCoordinator() {
    subscriptions.add(bus.subscribe(StartRequested.class, coordinator::onEvent));
    subscriptions.add(bus.subscribe(PointsEarned.class, coordinator::onEvent));
    subscriptions.add(bus.subscribe(LifeLost.class, coordinator::onEvent));
    subscriptions.add(bus.subscribe(PhaseCompleted.class, coordinator::onEvent));
    subscriptions.add(bus.subscribe(GameAbandoned.class, coordinator::onEvent));
  }

  private void applyPendingTransition() {
    GamePhase next = coordinator.consumePendingTransition();
    if (next == null) {
      return;
    }
    switch (next) {
      case WELCOME -> showScreen(screenFactory.createWelcomeScreen());
      case PLAYING_PHASE_ONE -> showScreen(screenFactory.createPlayableScreen());
      case GAME_OVER -> {
        // No game-over screen is part of this ticket.
      }
    }
  }

  private void showScreen(Screen nextScreen) {
    Screen previousScreen = getScreen();
    setScreen(nextScreen);
    if (previousScreen != null) {
      previousScreen.dispose();
    }
  }
}
