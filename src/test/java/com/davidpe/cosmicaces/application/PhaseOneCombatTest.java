package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import org.junit.jupiter.api.Test;

/**
 * Combat integration of PhaseOne: hull-to-hull contact, energy depletion by projectiles, the single
 * 1000-point Vesper destruction bonus, respawn timings and the invulnerability window. All rules
 * live in {@link PhaseOneGameController}; the screen only feeds frame deltas and input.
 */
class PhaseOneCombatTest {

  private static final float DELTA = 1f / 60f;

  private PhaseOneGameController spawnRaider() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    controller.advanceEncounter(3f); // spawn wait = 3 s with the scripted random
    assertTrue(controller.isRaiderActive());
    return controller;
  }

  @Test
  void vulnerableContactDestroysBothShipsAndAwardsTheBonusOnce() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    // Drop Astra's box exactly on the raider's box: both bodies overlap.
    controller.placeAstra(raider.x(), raider.y(), 0f);

    assertEquals(1000, controller.advanceCombat(DELTA));
    assertTrue(controller.astraCombat().isDestroyed());
    assertTrue(controller.raiderCombat().isDestroyed());
    assertFalse(controller.isAstraActive());
    assertFalse(controller.isRaiderActive());

    assertEquals(0, controller.advanceCombat(DELTA), "a wreck earns nothing more");
    assertEquals(1000, controller.scorePoints());
  }

  @Test
  void resurrectionsFollowThreeAndFiveSecondsWithAstraInvulnerability() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    controller.placeAstra(raider.x(), raider.y(), 0f);
    controller.advanceCombat(DELTA); // both destroyed

    /// Astra comes back after exactly 3 seconds, invulnerable and at full energy.
    assertEquals(0, controller.advanceCombat(3f));
    assertTrue(controller.isAstraActive());
    assertTrue(controller.astraCombat().isInvulnerable());
    assertEquals(100, controller.astraCombat().energyPercent());
    assertFalse(controller.isRaiderActive(), "Vesper still needs 5 seconds");

    /// Astra's protection lasts exactly 2 seconds and Vesper respawns at exactly 5.
    assertEquals(0, controller.advanceCombat(2f));
    assertTrue(controller.isRaiderActive(), "Vesper must respawn at the 5-second mark");
    assertFalse(controller.astraCombat().isInvulnerable(), "Astra protection ends at 2 seconds");
    assertEquals(100, controller.raiderCombat().energyPercent());
  }

  @Test
  void burstThatDestroysVesperAwardsThousandPointsOnlyOnce() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    // Align Astra's centre with the raider's and open fire 200 units below, nose pointing north.
    float astraX = raider.centerX() - controller.astra().drawWidth() / 2f;
    float astraY = raider.centerY() - 200f - controller.astra().drawHeight() / 2f;
    controller.placeAstra(astraX, astraY, 0f);

    int guard = 0;
    while (!controller.raiderCombat().isDestroyed() && guard < 1200) {
      controller.advanceWeapons(DELTA, DELTA, true, false, 10_000f);
      guard++;
    }

    assertTrue(controller.raiderCombat().isDestroyed(), "Vesper must fall to Astra's burst");
    assertFalse(controller.isRaiderActive());
    assertEquals(0, controller.raiderCombat().energyPercent());
    assertEquals(1000, controller.scorePoints());

    for (int frame = 0; frame < 30; frame++) {
      assertEquals(0, controller.advanceWeapons(DELTA, DELTA, true, false, 10_000f));
    }
    assertEquals(1000, controller.scorePoints(), "no respawn or wreck can earn another bonus");
  }

  @Test
  void destroyedAstraStopsFlyingWhileTheRunKeepsCounting() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    controller.placeAstra(raider.x(), raider.y(), 0f);
    controller.advanceCombat(DELTA);
    assertFalse(controller.isAstraActive());

    float frozenY = controller.astra().y();
    float remainingBefore = controller.remainingRunSeconds();
    int earned = controller.advanceFlight(FlightControls.neutral(), 1f, true);

    assertEquals(frozenY, controller.astra().y(), 0.001f, "a wreck does not fly");
    assertEquals(remainingBefore - 1f, controller.remainingRunSeconds(), 0.001f,
        "the 60-second run is not reset by death");
    assertEquals(1, earned, "a dead Astra earns the base second but no coincidence bonus");
    assertEquals(1001, controller.scorePoints());
  }

  @Test
  void deathCentersExposeTheFrozenWorldPositionOfEachShip() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    float astraCenterX = raider.x() + controller.astra().drawWidth() / 2f;
    float astraCenterY = raider.y() + controller.astra().drawHeight() / 2f;
    float raiderCenterX = raider.centerX();
    float raiderCenterY = raider.centerY();
    controller.placeAstra(raider.x(), raider.y(), 0f);

    controller.advanceCombat(DELTA);

    assertEquals(astraCenterX, controller.astraDeathCenterX(), 0.01f);
    assertEquals(astraCenterY, controller.astraDeathCenterY(), 0.01f);
    assertEquals(raiderCenterX, controller.raiderDeathCenterX(), 0.01f);
    assertEquals(raiderCenterY, controller.raiderDeathCenterY(), 0.01f);

    // Reappearing moves the ship but never rewrites the death centre used by its explosion.
    controller.advanceCombat(3f);
    assertTrue(controller.isAstraActive());
    assertEquals(astraCenterX, controller.astraDeathCenterX(), 0.01f);
  }
}