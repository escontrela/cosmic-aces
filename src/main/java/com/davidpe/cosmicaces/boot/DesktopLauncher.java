package com.davidpe.cosmicaces.boot;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.davidpe.cosmicaces.infrastructure.gdx.CosmicAcesGame;

/** Starts the desktop (LWJGL3) version of Cosmic Aces. */
public final class DesktopLauncher {

  private DesktopLauncher() {}

  public static void main(String[] args) {

    new Lwjgl3Application(
        new CosmicAcesGame(), DesktopConfiguration.getInstance().createConfiguration());
  }
}
