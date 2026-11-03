package com.davidpe.cosmicaces.application;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.davidpe.cosmicaces.application.score.HallOfFameSession;
import com.davidpe.cosmicaces.domain.game.GameAbandoned;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.LifeLost;
import com.davidpe.cosmicaces.domain.game.PhaseCompleted;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import com.davidpe.cosmicaces.domain.game.StartRequested;
import com.davidpe.cosmicaces.infrastructure.gdx.ScreenFactory;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventBus;
import com.davidpe.cosmicaces.infrastructure.gdx.event.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * LibGDX composition root that coordinates screens and delegates game rules to the application. It
 * keeps the event bus and the {@link GameCoordinator} that owns the persistent player state, uses
 * the {@link ScreenFactory} to build screens wired to the same bus, and applies screen transitions
 * only after the current frame finished rendering, so a screen that requests a change during its
 * own {@code render} is never disposed mid-frame.
 *
 * <p>It also owns the {@link AttractCycle} of the arcade rest loop: a start, settings or confirmation
 * handled by the active screen wins over a timeout that expires in the same frame, and the cycle is
 * advanced at most once after the pending transition was applied. The cycle is paused while a
 * mission is active and when LibGDX pauses the application; the screens pause it for their own
 * modal states.
 */
public final class CosmicAcesGame extends Game {

  /**
   * Lives Astra starts every PhaseOne mission with (COS-66): each destruction costs exactly one and
   * an exhausted pool is a defeat. The coordinator owns the authoritative remaining lives; the
   * screen decides where the pool reached zero.
   */
  private static final int STARTING_LIVES = 3;

  private final GameEventBus bus = new GameEventBus();
  private final GameCoordinator coordinator;
  private final AttractCycle attractCycle = new AttractCycle();
  private final ScreenFactory screenFactory;
  private final List<Subscription> subscriptions = new ArrayList<>();

  /**
   * @param hallOfFameSession Hall of Fame session assembled by the boot composition root and loaded
   *     once by {@link ScreenFactory#loadSettings()}
   */
  public CosmicAcesGame(HallOfFameSession hallOfFameSession) {
    Objects.requireNonNull(hallOfFameSession, "hallOfFameSession");
    this.coordinator = new GameCoordinator(STARTING_LIVES, hallOfFameSession);
    this.screenFactory = new ScreenFactory(coordinator, bus, hallOfFameSession, attractCycle);
  }

  @Override
  public void create() {
    screenFactory.loadSettings();
    subscribeCoordinator();
    showScreen(screenFactory.createWelcomeScreen());
  }

  @Override
  public void render() {
    super.render();
    // A start, settings change or confirmation handled by the screen wins over a timeout that
    // expires in this same frame.
    if (applyPendingTransition()) {
      return;
    }
    applyAttractionTransition();
  }

  @Override
  public void pause() {
    super.pause();
    attractCycle.pause();
  }

  @Override
  public void resume() {
    super.resume();
    attractCycle.resume();
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

  /**
   * Applies the single transition queued by the coordinator, if any.
   *
   * @return {@code true} when a screen was replaced, so the attraction loop must not also rotate in
   *     the same frame
   */
  private boolean applyPendingTransition() {
    GamePhase next = coordinator.consumePendingTransition();
    if (next == null) {
      return false;
    }
    switch (next) {
      case WELCOME -> showScreen(screenFactory.createWelcomeScreen());
      case PLAYING_PHASE_ONE -> {
        attractCycle.pause();
        showScreen(screenFactory.createPhaseOneScreen());
      }
      case HALL_OF_FAME -> showScreen(screenFactory.createHallOfFameScreen());
    }
    return true;
  }

  /**
   * Advances the arcade rest loop once after the current frame and materialises the page change. A
   * return to the welcome page resets a finished game to a fresh lobby; the cycle stays on the Hall
   * if a qualifying candidate still needs initials.
   */
  private void applyAttractionTransition() {
    GamePhase phase = coordinator.state().phase();
    if (phase != GamePhase.WELCOME && phase != GamePhase.HALL_OF_FAME) {
      return;
    }
    attractCycle.tick(Gdx.graphics.getDeltaTime()).ifPresent(page -> {
      if (page == AttractCycle.Page.WELCOME) {
        if (!coordinator.returnToAttractionLobby()) {
          attractCycle.enter(AttractCycle.Page.HALL_OF_FAME);
          return;
        }
        showScreen(screenFactory.createWelcomeScreen());
      } else {
        showScreen(screenFactory.createHallOfFameScreen());
      }
    });
  }

  private void showScreen(Screen nextScreen) {
    Screen previousScreen = getScreen();
    setScreen(nextScreen);
    if (previousScreen != null) {
      previousScreen.dispose();
    }
  }
}
