package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.enemy.RaiderEncounter;
import com.davidpe.cosmicaces.domain.enemy.UnitRandom;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.PlayArea;
import java.util.concurrent.ThreadLocalRandom;

/** Rules specific to phase one: timed run and autonomous Raider encounters. */
public final class PhaseOneGameController extends GameController {

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
