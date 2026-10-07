package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.enemy.RaiderEncounter;
import com.davidpe.cosmicaces.domain.enemy.UnitRandom;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.PlayArea;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import java.util.concurrent.ThreadLocalRandom;

/** Rules specific to phase one: timed run and autonomous Raider encounters. */
public final class PhaseOneGameController extends GameController {

  public static final WorldBounds WORLD = new WorldBounds(8192f, 12000f);
  private final RaiderEncounter raiderEncounter;

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

  /** Uses only the portion of this frame remaining in the 60-second run. */
  public void advanceFlight(FlightControls controls, float deltaSeconds) {
    if (!isRunStarted() || isRunFinished() || !Float.isFinite(deltaSeconds)
        || deltaSeconds <= 0f) {
      return;
    }
    float activeSeconds = Math.min(deltaSeconds, remainingRunSeconds());
    astra().fly(controls, activeSeconds, WORLD);
    advanceRun(activeSeconds);
  }

  public void advanceEncounter(float deltaSeconds, PlayArea area) {
    if (!isRunStarted() || isRunFinished()) {
      return;
    }
    raiderEncounter.advance(deltaSeconds, area);
  }

  public boolean isRaiderActive() {
    return raiderEncounter.isActive();
  }

  public VesperRaider activeRaider() {
    return raiderEncounter.raider();
  }

}
