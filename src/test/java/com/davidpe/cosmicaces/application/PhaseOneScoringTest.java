package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.PhaseCompleted;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import com.davidpe.cosmicaces.domain.game.StartRequested;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.infrastructure.gdx.event.GameEventBus;
import org.junit.jupiter.api.Test;

/**
 * Wires the phase-one controller to the real synchronous event bus and coordinator to check that the
 * per-frame score reported through {@link PointsEarned} is fully included in the final snapshot and
 * never double counted when the phase completes.
 */
class PhaseOneScoringTest {

  private static final int STARTING_LIVES = 3;

  @Test
  void finalScoreIncludesTheLastSecondReportedBeforeCompletion() {
    GameEventBus bus = new GameEventBus();
    GameCoordinator coordinator = new GameCoordinator(STARTING_LIVES);
    bus.subscribe(StartRequested.class, coordinator::onEvent);
    bus.subscribe(PointsEarned.class, coordinator::onEvent);
    bus.subscribe(PhaseCompleted.class, coordinator::onEvent);

    bus.publish(new StartRequested(coordinator.state().gameId(), GamePhase.WELCOME));
    coordinator.consumePendingTransition();
    GameId gameId = coordinator.state().gameId();

    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();

    for (int second = 0; second < 60; second++) {
      boolean coincident = second < 2;
      int earned = controller.advanceFlight(FlightControls.neutral(), 1f, coincident);
      if (earned > 0) {
        bus.publish(new PointsEarned(gameId, GamePhase.PLAYING_PHASE_ONE, earned));
      }
      if (controller.isRunFinished()) {
        bus.publish(new PhaseCompleted(coordinator.snapshot()));
      }
    }

    // 60 complete run seconds (60) plus 2 visible coincidence seconds (20).
    assertEquals(80, coordinator.state().points());
    assertEquals(GamePhase.GAME_OVER, coordinator.state().phase());
  }
}
