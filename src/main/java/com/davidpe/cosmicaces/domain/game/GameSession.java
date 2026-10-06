package com.davidpe.cosmicaces.domain.game;

import com.davidpe.cosmicaces.domain.player.MovementIntent;
import com.davidpe.cosmicaces.domain.player.Ship;

/** Domain state and rules for a game session: progression, the playable run and the ship. */
public final class GameSession {

  private final PlayableRun run = new PlayableRun();
  private final Ship ship = new Ship(Ship.DEFAULT_SPEED);
  private GamePhase phase = GamePhase.WELCOME;

  public GamePhase phase() {
    return phase;
  }

  /** Starts the session and its playable run. Repeated start requests leave it in the playing phase. */
  public void start() {
    phase = GamePhase.PLAYING_PHASE_ONE;
    run.start();
  }

  /** Advances the playable run by the given delta in seconds. */
  public void advanceRun(float deltaSeconds) {
    run.advance(deltaSeconds);
  }

  /** Applies the movement intent to the ship for the given delta, bounded by the play area. */
  public void applyMovementIntent(MovementIntent intent, float deltaSeconds, PlayArea area) {
    ship.move(intent, deltaSeconds, area);
  }

  /** Places the ship at the given position, clamped to the play area. */
  public void placeShip(float x, float y, PlayArea area) {
    ship.placeAt(x, y, area);
  }

  public boolean isRunFinished() {
    return run.isFinished();
  }

  public float remainingRunSeconds() {
    return run.remainingSeconds();
  }

  public float shipX() {
    return ship.x();
  }

  public float shipY() {
    return ship.y();
  }
}