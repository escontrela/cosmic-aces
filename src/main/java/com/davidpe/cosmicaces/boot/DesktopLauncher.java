package com.davidpe.cosmicaces.boot;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.davidpe.cosmicaces.application.CosmicAcesGame;
import com.davidpe.cosmicaces.application.score.HallOfFameSession;
import com.davidpe.cosmicaces.infrastructure.persistence.LocalHallOfFameRepository;
import java.time.Clock;
import java.util.UUID;

/** Starts the desktop (LWJGL3) version of Cosmic Aces. */
public final class DesktopLauncher {

  private DesktopLauncher() {}

  public static void main(String[] args) {

    new Lwjgl3Application(
        new CosmicAcesGame(createHallOfFameSession()),
        DesktopConfiguration.getInstance().createConfiguration());
  }

  /**
   * Assembles the file-backed Hall of Fame session at the boot boundary. The repository resolves the
   * per-user data directory; tests and smoke runs redirect {@code user.home} to a repository-local
   * directory.
   */
  private static HallOfFameSession createHallOfFameSession() {
    return new HallOfFameSession(
        new LocalHallOfFameRepository(LocalHallOfFameRepository.defaultFilePath()),
        Clock.systemDefaultZone(),
        () -> UUID.randomUUID().toString());
  }
}
