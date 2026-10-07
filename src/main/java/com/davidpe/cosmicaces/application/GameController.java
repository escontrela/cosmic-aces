package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.game.PlayArea;
import com.davidpe.cosmicaces.domain.game.PlayableRun;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.ship.MovementIntent;

/** Common run clock and hero controls shared by playable phase controllers. */
public abstract class GameController {

  private final PlayableRun run = new PlayableRun();
  private final Astra astra = new Astra(Astra.DEFAULT_SPEED);

  public final void start() {
    run.start();
  }

  public final void advanceRun(float deltaSeconds) {
    run.advance(deltaSeconds);
  }

  public final boolean isRunStarted() {
    return run.isStarted();
  }

  public final boolean isRunFinished() {
    return run.isFinished();
  }

  public final float remainingRunSeconds() {
    return run.remainingSeconds();
  }

  public final Astra astra() {
    return astra;
  }

  public final void applyMovementIntent(MovementIntent intent, float deltaSeconds, PlayArea area,
      boolean accelerating) {
    astra.move(intent, deltaSeconds, area, accelerating);
  }

  public final void placeShip(float x, float y, PlayArea area) {
    astra.placeAt(x, y, area);
  }

}
