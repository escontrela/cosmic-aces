package com.davidpe.cosmicaces.infrastructure.gdx;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.davidpe.cosmicaces.application.GameFlow;
import com.davidpe.cosmicaces.domain.game.GameAbandoned;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.GameSession;
import com.davidpe.cosmicaces.domain.game.LifeLost;
import com.davidpe.cosmicaces.domain.game.PhaseCompleted;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import com.davidpe.cosmicaces.domain.game.StartRequested;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventBus;
import com.davidpe.cosmicaces.infrastructure.gdx.event.Subscription;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PlayableScreen;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.WelcomeScreen;
import java.util.ArrayList;
import java.util.List;

/**
 * LibGDX composition root that coordinates screens and delegates game rules to the application. It
 * keeps the event bus and the {@link GameCoordinator} that owns the persistent player state, and it
 * applies screen transitions only after the current frame finished rendering, so a screen that
 * requests a change during its own {@code render} is never disposed mid-frame.
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
  private final List<Subscription> subscriptions = new ArrayList<>();

  private GameFlow gameFlow;

  @Override
  public void create() {
    subscribeCoordinator();
    showScreen(new WelcomeScreen(this::requestStart));
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

  /** Published by the welcome screen while it renders; the transition is deferred to frame end. */
  private void requestStart() {
    bus.publish(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
  }

  /** Published by the playing screen while it renders; the transition is deferred to frame end. */
  private void requestReturnToWelcome() {
    bus.publish(new GameAbandoned(coordinator.state().gameId(), coordinator.state().phase()));
  }

  private void applyPendingTransition() {
    GamePhase next = coordinator.consumePendingTransition();
    if (next == null) {
      return;
    }
    switch (next) {
      case WELCOME -> showScreen(new WelcomeScreen(this::requestStart));
      case PLAYING_PHASE_ONE -> showPlayingScreen();
      case GAME_OVER -> {
        // No game-over screen is part of this ticket.
      }
    }
  }

  private void showPlayingScreen() {
    gameFlow = new GameFlow(new GameSession());
    gameFlow.startGame();
    showScreen(new PlayableScreen(gameFlow, this::requestReturnToWelcome));
  }

  private void showScreen(Screen nextScreen) {
    Screen previousScreen = getScreen();
    setScreen(nextScreen);
    if (previousScreen != null) {
      previousScreen.dispose();
    }
  }
}
