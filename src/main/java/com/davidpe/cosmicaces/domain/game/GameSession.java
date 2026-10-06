package com.davidpe.cosmicaces.domain.game;

import com.davidpe.cosmicaces.domain.enemy.RaiderEncounter;
import com.davidpe.cosmicaces.domain.enemy.UnitRandom;
import com.davidpe.cosmicaces.domain.player.MovementIntent;
import com.davidpe.cosmicaces.domain.player.Ship;
import java.util.concurrent.ThreadLocalRandom;

/** Domain state and rules for a game session: the playable run, the ship and encounters; global phase lives in GameState. */
public final class GameSession {

  private final PlayableRun run = new PlayableRun();
  private final Ship ship = new Ship(Ship.DEFAULT_SPEED);
  private final RaiderEncounter raiderEncounter;

  public GameSession() {
    this(ThreadLocalRandom.current()::nextFloat);
  }

  /** Creates a session whose raider encounter draws randomness from the given unit-random source. */
  public GameSession(UnitRandom raiderRandom) {
    raiderEncounter = new RaiderEncounter(raiderRandom);
  }

  /** Starts the playable run; global navigation belongs to the coordinator. */
  public void start() {
    run.start();
  }

  /** Advances the playable run by the given delta in seconds. */
  public void advanceRun(float deltaSeconds) {
    run.advance(deltaSeconds);
  }

  /**
   * Advances the raider encounters by the given delta while the playable run is active. Time before
   * the run starts and after it finishes is ignored.
   */
  public void advanceEncounter(float deltaSeconds, PlayArea area) {
    if (!run.isStarted() || run.isFinished()) {
      return;
    }
    raiderEncounter.advance(deltaSeconds, area);
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

  public boolean isRaiderActive() {
    return raiderEncounter.isActive();
  }

  public float raiderX() {
    return raiderEncounter.raiderX();
  }

  public float raiderY() {
    return raiderEncounter.raiderY();
  }

  public int raiderBank() {
    return raiderEncounter.raiderBank();
  }
}