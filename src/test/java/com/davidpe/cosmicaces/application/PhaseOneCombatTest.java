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

  /** Fires Astra's sustained burst at the raider until the raider's first hit-flash pulse shows. */
  private static void fireUntilFirstRaiderFlash(PhaseOneGameController controller) {
    int guard = 0;
    while (controller.raiderHitFlashIntensity() <= 0f && guard < 600) {
      controller.advanceWeapons(DELTA, DELTA, true, false, 10_000f);
      guard++;
    }
  }

  @Test
  void eachValidImpactFlashesTheRaiderEvenBeforeTheTenthTouchesEnergy() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    float astraX = raider.centerX() - controller.astra().drawWidth() / 2f;
    float astraY = raider.centerY() - 200f - controller.astra().drawHeight() / 2f;
    controller.placeAstra(astraX, astraY, 0f);

    fireUntilFirstRaiderFlash(controller);

    assertEquals(1f, controller.raiderHitFlashIntensity(), 0.0001f,
        "the first counted impact renews a full-strength pulse");
    assertEquals(100, controller.raiderCombat().energyPercent(),
        "the first nine impacts flash without completing a damage step");
    assertEquals(0f, controller.astraHitFlashIntensity(), 0.0001f,
        "the un-hit Astra never flashes from her own burst");
  }

  @Test
  void theTenthImpactAlsoFlashesWhileReducingEnergyByTheApprovedStep() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    float astraX = raider.centerX() - controller.astra().drawWidth() / 2f;
    float astraY = raider.centerY() - 200f - controller.astra().drawHeight() / 2f;
    controller.placeAstra(astraX, astraY, 0f);

    int guard = 0;
    while (controller.raiderCombat().energyPercent() == 100 && guard < 900) {
      controller.advanceWeapons(DELTA, DELTA, true, false, 10_000f);
      guard++;
    }

    assertEquals(65, controller.raiderCombat().energyPercent(),
        "the tenth impact applies the first approved 35-point step");
    assertEquals(1f, controller.raiderHitFlashIntensity(), 0.0001f,
        "the damaging impact flashes exactly like the first nine");
  }

  @Test
  void consumedProjectilesNeverRetriggerThePulseAfterItDecays() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    // Park Astra far below so the single burst needs several frames to arrive.
    float astraX = raider.centerX() - controller.astra().drawWidth() / 2f;
    float astraY = raider.centerY() - 1000f - controller.astra().drawHeight() / 2f;
    controller.placeAstra(astraX, astraY, 0f);

    controller.advanceWeapons(DELTA, DELTA, true, false, 10_000f);

    boolean sawFlash = false;
    for (int frame = 0; frame < 240; frame++) {
      controller.advanceWeapons(DELTA, 0f, false, false, 10_000f);
      controller.advanceHitFeedback(DELTA);
      if (controller.raiderHitFlashIntensity() > 0f) {
        sawFlash = true;
      }
    }

    assertTrue(sawFlash, "the two in-flight projectiles must hit and flash once");
    assertEquals(0f, controller.raiderHitFlashIntensity(), 0.0001f,
        "a consumed projectile never repeats its impact");
    assertEquals(100, controller.raiderCombat().energyPercent(),
        "two impacts are still far below the damage threshold");
  }

  @Test
  void destructionClearsAnActivePulseSoTheExplosionStaysUncluttered() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    float astraX = raider.centerX() - controller.astra().drawWidth() / 2f;
    float astraY = raider.centerY() - 200f - controller.astra().drawHeight() / 2f;
    controller.placeAstra(astraX, astraY, 0f);
    fireUntilFirstRaiderFlash(controller);
    assertEquals(1f, controller.raiderHitFlashIntensity(), 0.0001f);

    // A vulnerable hull-to-hull contact destroys both ships: death outranks the flash.
    controller.placeAstra(raider.x(), raider.y(), 0f);
    controller.advanceCombat(DELTA);

    assertTrue(controller.astraCombat().isDestroyed());
    assertTrue(controller.raiderCombat().isDestroyed());
    assertEquals(0f, controller.raiderHitFlashIntensity(), 0.0001f,
        "death clears the raider pulse before the explosion");
    assertEquals(0f, controller.astraHitFlashIntensity(), 0.0001f);

    // Respawn restarts neither pulse.
    controller.advanceCombat(3f);
    controller.advanceCombat(2f);
    assertEquals(0f, controller.astraHitFlashIntensity(), 0.0001f);
    assertEquals(0f, controller.raiderHitFlashIntensity(), 0.0001f);
  }

  @Test
  void hitsAbsorbedDuringAstraInvulnerabilityNeverFlash() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    // Park Astra directly under the raider's spawn point, aligned with its firing line. With the
    // scripted random the raider keeps heading 0 (straight down) and never moves while the
    // encounter is not advanced, so both side cannons sweep straight down through her body.
    float astraX = raider.centerX() - controller.astra().drawWidth() / 2f;
    float astraY = raider.y() - 150f - controller.astra().drawHeight();
    controller.placeAstra(astraX, astraY, 0f);

    // The raider pounds her until she falls while it survives: many impacts, then death clears it.
    int guard = 0;
    while (!controller.astraCombat().isDestroyed() && guard < 400) {
      controller.advanceWeapons(1f, 1f, false, true, 10_000f);
      guard++;
    }
    assertTrue(controller.astraCombat().isDestroyed(),
        "Astra must fall to the raider's sustained burst drawn by this setup");
    assertFalse(controller.raiderCombat().isDestroyed(), "the raider survives direct fire");
    assertEquals(0f, controller.astraHitFlashIntensity(), 0.0001f, "death clears the pulse");

    // Three seconds later Astra respawns invulnerable, still in the same spot under the fire.
    controller.advanceCombat(3f);
    assertTrue(controller.astraCombat().isInvulnerable());
    assertEquals(100, controller.astraCombat().energyPercent());

    // Contacts during the protection window are absorbed without a damage flash (CA22).
    for (int frame = 0; frame < 15; frame++) {
      controller.advanceWeapons(1f / 60f, 1f / 60f, false, true, 10_000f);
      controller.advanceHitFeedback(1f / 60f);
      assertEquals(0f, controller.astraHitFlashIntensity(), 0.0001f,
          "immune contacts must not trigger the hit flash");
    }
    assertTrue(controller.astraCombat().isInvulnerable(),
        "the protection window still runs during these contacts");
  }
}