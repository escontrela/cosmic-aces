package com.davidpe.cosmicaces.infrastructure.gdx;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.davidpe.cosmicaces.application.GameFlow;
import com.davidpe.cosmicaces.domain.game.GameSession;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PlayableScreen;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.WelcomeScreen;

/** LibGDX composition root that coordinates screens and delegates game rules to the application. */
public final class CosmicAcesGame extends Game {

  private GameFlow gameFlow;

  @Override
  public void create() {
    gameFlow = new GameFlow(new GameSession());
    showScreen(new WelcomeScreen(this::startGame));
  }

  private void startGame() {
    if (gameFlow.startGame()) {
      showScreen(new PlayableScreen(gameFlow));
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
