package com.davidpe.cosmicaces.boot;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.davidpe.cosmicaces.application.CosmicAcesGame;
import com.davidpe.cosmicaces.application.score.HallOfFameSession;
import com.davidpe.cosmicaces.infrastructure.persistence.LocalHallOfFameRepository;
import java.time.Clock;
import java.util.UUID;

/** Starts the desktop (LWJGL3) version of Cosmic Aces. */
public final class DesktopLauncher {

  private DesktopLauncher() {}

  public static void main(String[] args) {

    CosmicAcesGame game = new CosmicAcesGame(createHallOfFameSession());
    Lwjgl3ApplicationConfiguration configuration =
        DesktopConfiguration.getInstance().createConfiguration();
    // Focus loss is a window event, not an application pause: require neutral input on both edges so
    // a held Joy-Con button or stick cannot act in the screen that regains focus.
    configuration.setWindowListener(new Lwjgl3WindowAdapter() {
      @Override
      public void focusLost() {
        game.resetInputAfterFocusChange();
      }

      @Override
      public void focusGained() {
        game.resetInputAfterFocusChange();
      }
    });
    new Lwjgl3Application(game, configuration);
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
