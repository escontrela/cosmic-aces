package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.game.PlayableRun;
import com.davidpe.cosmicaces.domain.player.Astra;

/** Common run clock and hero state shared by playable phase controllers. */
public abstract class GameController {

  private final PlayableRun run = new PlayableRun();
  private final Astra astra = new Astra();

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
}
