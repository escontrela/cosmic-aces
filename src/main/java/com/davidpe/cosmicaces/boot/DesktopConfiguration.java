package com.davidpe.cosmicaces.boot;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;

/** Singleton factory for the desktop application's LWJGL3 configuration. */
public final class DesktopConfiguration {

  private static final DesktopConfiguration INSTANCE = new DesktopConfiguration();

  private DesktopConfiguration() {}

  public static DesktopConfiguration getInstance() {
    return INSTANCE;
  }

  /** Creates and returns the configuration used to launch the desktop application. */
  public Lwjgl3ApplicationConfiguration createConfiguration() {

    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Cosmic Aces");
    config.setWindowedMode((int) VirtualScreenSize.WIDTH, (int) VirtualScreenSize.HEIGHT);
    config.setResizable(true);
    config.useVsync(true);
    config.setForegroundFPS(60);
    return config;
  }
}
