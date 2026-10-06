package com.davidpe.cosmicaces.infrastructure.gdx.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.davidpe.cosmicaces.infrastructure.gdx.CosmicAcesGame;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;

/** Starts the desktop (LWJGL3) version of Cosmic Aces. */
public final class DesktopLauncher {

  private DesktopLauncher() {}

  public static void main(String[] args) {

    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Cosmic Aces");
    config.setWindowedMode((int) VirtualScreenSize.WIDTH, (int) VirtualScreenSize.HEIGHT);
    config.setResizable(true);
    config.useVsync(true);
    config.setForegroundFPS(60);

    new Lwjgl3Application(new CosmicAcesGame(), config);
  }
}
