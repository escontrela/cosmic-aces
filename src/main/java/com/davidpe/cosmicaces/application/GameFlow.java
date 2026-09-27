package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.GameSession;
import com.davidpe.cosmicaces.domain.game.PlayArea;
import com.davidpe.cosmicaces.domain.player.MovementIntent;

/** Application use cases for moving through the current game flow. */
public final class GameFlow {

  private final GameSession session;

  public GameFlow(GameSession session) {
    this.session = session;
  }

  public boolean startGame() {
    session.start();
    return session.phase() == GamePhase.PLAYING;
  }

  /** Advances the playable run by the given delta in seconds. */
  public void advanceRun(float deltaSeconds) {
    session.advanceRun(deltaSeconds);
  }

  /** Advances the raider encounters by the given delta while the playable run is active. */
  public void advanceEncounter(float deltaSeconds, PlayArea playArea) {
    session.advanceEncounter(deltaSeconds, playArea);
  }

  /** Applies the movement intent to the ship for the given delta, bounded by the play area. */
  public void applyMovementIntent(MovementIntent intent, float deltaSeconds, PlayArea playArea) {
    session.applyMovementIntent(intent, deltaSeconds, playArea);
  }

  /** Places the ship at the given position, clamped to the play area. */
  public void placeShip(float x, float y, PlayArea playArea) {
    session.placeShip(x, y, playArea);
  }

  public boolean isRunFinished() {
    return session.isRunFinished();
  }

  public float remainingRunSeconds() {
    return session.remainingRunSeconds();
  }

  public float shipX() {
    return session.shipX();
  }

  public float shipY() {
    return session.shipY();
  }

  public boolean isRaiderActive() {
    return session.isRaiderActive();
  }

  public float raiderX() {
    return session.raiderX();
  }

  public float raiderY() {
    return session.raiderY();
  }

  public int raiderBank() {
    return session.raiderBank();
  }
}
