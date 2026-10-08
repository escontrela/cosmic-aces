package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.enemy.RaiderEncounter;
import com.davidpe.cosmicaces.domain.enemy.UnitRandom;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.PhaseScore;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import java.util.concurrent.ThreadLocalRandom;

/** Rules specific to phase one: timed run, autonomous Raider and scoring. */
public final class PhaseOneGameController extends GameController {

  public static final WorldBounds WORLD = new WorldBounds(8192f, 12000f);
  private final RaiderEncounter raiderEncounter;
  private final PhaseScore score = new PhaseScore();

  public PhaseOneGameController() {
    this(ThreadLocalRandom.current()::nextFloat);
  }

  public PhaseOneGameController(UnitRandom raiderRandom) {
    raiderEncounter = new RaiderEncounter(raiderRandom);
  }

  public void setRaiderVisuals(VesperRaider.Visuals visuals) {
    raiderEncounter.setVisuals(visuals);
  }

  /** Starts Astra in the world; placement is independent of viewport coordinates. */
  public void placeAstra(float x, float y, float yawDegrees) {
    astra().placeAt(x, y, yawDegrees, WORLD);
  }

  /**
   * Advances Astra and the run clock using only the portion of this frame remaining in the
   * 60-second run, and returns the points earned by this delta. Scoring shares the same clamped
   * {@code activeSeconds} as the clock, so points freeze with the run and are never double counted.
   *
   * @param coincident whether Astra and the raider were both visible during this delta
   */
  public int advanceFlight(FlightControls controls, float deltaSeconds, boolean coincident) {
    if (!isRunStarted() || isRunFinished() || !Float.isFinite(deltaSeconds)
        || deltaSeconds <= 0f) {
      return 0;
    }
    float activeSeconds = Math.min(deltaSeconds, remainingRunSeconds());
    astra().fly(controls, activeSeconds, WORLD);
    advanceRun(activeSeconds);
    return score.advance(activeSeconds, coincident);
  }

  /** Total points this run has awarded so far. */
  public int scorePoints() {
    return score.totalPoints();
  }

  /** Advances the single raider toward Astra in world coordinates. */
  public void advanceEncounter(float deltaSeconds) {
    if (!isRunStarted() || isRunFinished()) {
      return;
    }
    raiderEncounter.advance(deltaSeconds, WORLD, astra().x(), astra().y());
  }

  public boolean isRaiderActive() {
    return raiderEncounter.isActive();
  }

  public VesperRaider activeRaider() {
    return raiderEncounter.raider();
  }

}
