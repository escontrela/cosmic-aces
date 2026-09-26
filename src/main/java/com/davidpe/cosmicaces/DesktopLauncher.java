package com.davidpe.cosmicaces;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.davidpe.cosmicaces.application.CosmicAcesGame;

/** Starts the desktop (LWJGL3) version of Cosmic Aces. */
public class DesktopLauncher {

  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Cosmic Aces");
    config.setWindowedMode(
        (int) CosmicAcesGame.WORLD_WIDTH, (int) CosmicAcesGame.WORLD_HEIGHT);
    config.setResizable(true);
    config.useVsync(true);
    config.setForegroundFPS(60);

    new Lwjgl3Application(new CosmicAcesGame(), config);
  }
}
