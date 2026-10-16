package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
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
  void vulnerableContactDestroysOnlyAstraAndAwardsNoKillOrBonus() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    // Drop Astra's box exactly on the raider's box: both bodies overlap.
    controller.placeAstra(raider.x(), raider.y(), 0f);

    assertEquals(0, controller.advanceCombat(DELTA, 3));
    assertTrue(controller.astraCombat().isDestroyed());
    assertFalse(controller.raiderCombat().isDestroyed());
    assertFalse(controller.isAstraActive());
    assertTrue(controller.isRaiderActive());
    assertEquals(100, controller.raiderCombat().energyPercent());
    assertEquals(0, controller.vesperKills());
    assertEquals(1, controller.drainAstraLifeLosses());

    assertEquals(0, controller.advanceCombat(DELTA, 3), "a wreck earns nothing more");
    assertEquals(0, controller.scorePoints());
  }

  @Test
  void astraRespawnsAfterThreeSecondsWithInvulnerabilityAndVesperStaysAlive() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    controller.placeAstra(raider.x(), raider.y(), 0f);
    controller.advanceCombat(DELTA, 3); // only Astra is destroyed by contact

    /// Astra comes back after exactly 3 seconds, invulnerable and at full energy.
    assertEquals(0, controller.advanceCombat(3f, 3));
    assertTrue(controller.isAstraActive());
    assertTrue(controller.astraCombat().isInvulnerable());
    assertEquals(100, controller.astraCombat().energyPercent());
    assertTrue(controller.isRaiderActive(), "contact must not destroy Vesper");
    assertEquals(100, controller.raiderCombat().energyPercent());

    /// Astra's protection lasts exactly 2 seconds; Vesper remains alive throughout.
    assertEquals(0, controller.advanceCombat(2f, 3));
    assertTrue(controller.isRaiderActive(), "Vesper remains active after the collision");
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
    controller.advanceCombat(DELTA, 3);
    assertFalse(controller.isAstraActive());

    float frozenY = controller.astra().y();
    float remainingBefore = controller.remainingRunSeconds();
    int earned = controller.advanceFlight(FlightControls.neutral(), 1f, true);

    assertEquals(frozenY, controller.astra().y(), 0.001f, "a wreck does not fly");
    assertEquals(remainingBefore - 1f, controller.remainingRunSeconds(), 0.001f,
        "the 10-minute mission clock is not reset by death");
    assertEquals(1, earned, "a dead Astra earns the base second but no coincidence bonus");
    assertEquals(1, controller.scorePoints(), "Astra's collision adds no Vesper kill bonus");
  }

  @Test
  void astraContactRecordsOnlyAstrasDeathCenter() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    float astraCenterX = raider.x() + controller.astra().drawWidth() / 2f;
    float astraCenterY = raider.y() + controller.astra().drawHeight() / 2f;
    controller.placeAstra(raider.x(), raider.y(), 0f);

    controller.advanceCombat(DELTA, 3);

    assertEquals(astraCenterX, controller.astraDeathCenterX(), 0.01f);
    assertEquals(astraCenterY, controller.astraDeathCenterY(), 0.01f);
    assertFalse(controller.raiderCombat().isDestroyed());

    // Reappearing moves the ship but never rewrites the death centre used by its explosion.
    controller.advanceCombat(3f, 3);
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

  private static void alignAstraForBurst(PhaseOneGameController controller, VesperRaider raider) {
    float astraX = raider.centerX() - controller.astra().drawWidth() / 2f;
    float astraY = raider.centerY() - 200f - controller.astra().drawHeight() / 2f;
    controller.placeAstra(astraX, astraY, 0f);
  }

  private static int fireUntilRaiderDestroyed(PhaseOneGameController controller) {
    int points = 0;
    int guard = 0;
    while (!controller.raiderCombat().isDestroyed() && guard < 1200) {
      points += controller.advanceWeapons(DELTA, DELTA, true, false, 10_000f);
      guard++;
    }
    assertTrue(controller.raiderCombat().isDestroyed(), "Vesper must fall to Astra's burst");
    return points;
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
  void astraContactDoesNotClearVespersActiveHitPulse() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    float astraX = raider.centerX() - controller.astra().drawWidth() / 2f;
    float astraY = raider.centerY() - 200f - controller.astra().drawHeight() / 2f;
    controller.placeAstra(astraX, astraY, 0f);
    fireUntilFirstRaiderFlash(controller);
    assertEquals(1f, controller.raiderHitFlashIntensity(), 0.0001f);

    // Hull contact destroys Astra only; Vesper's hit pulse belongs to its own earlier hit.
    controller.placeAstra(raider.x(), raider.y(), 0f);
    controller.advanceCombat(DELTA, 3);

    assertTrue(controller.astraCombat().isDestroyed());
    assertFalse(controller.raiderCombat().isDestroyed());
    assertEquals(1f, controller.raiderHitFlashIntensity(), 0.0001f,
        "Astra's collision must not clear Vesper's hit pulse");
    assertEquals(0f, controller.astraHitFlashIntensity(), 0.0001f);

    // Astra's respawn clears her pulse; Vesper's independent pulse keeps decaying only when
    // hit-feedback time advances.
    controller.advanceCombat(3f, 3);
    controller.advanceCombat(2f, 3);
    assertEquals(0f, controller.astraHitFlashIntensity(), 0.0001f);
    assertEquals(1f, controller.raiderHitFlashIntensity(), 0.0001f);
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
    controller.advanceCombat(3f, 3);
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

  @Test
  void eachShotVesperDestructionCountsOnceAndTheThirdOneClosesRespawns() {
    PhaseOneGameController controller = spawnRaider();
    assertEquals(0, controller.vesperKills());

    for (int kill = 1; kill <= 3; kill++) {
      VesperRaider raider = controller.activeRaider();
      assertNotNull(raider, "Vesper must be present before kill " + kill);
      // Park Vesper at a known open spot so the firing geometry is deterministic.
      raider.placeAt(4000f - raider.drawWidth() / 2f, 6000f - raider.drawHeight() / 2f, 0f,
          PhaseOneGameController.WORLD);
      alignAstraForBurst(controller, raider);
      assertEquals(1000, fireUntilRaiderDestroyed(controller),
          "kill " + kill + " awards one bonus");
      assertEquals(kill, controller.vesperKills(), "exactly one kill per destruction");
      assertEquals(kill * 1000, controller.scorePoints(), "no double bonus");
      assertEquals(0, controller.raiderCombat().energyPercent());
      assertFalse(controller.isRaiderActive());

      if (kill < 3) {
        // Vesper returns after five seconds at full energy; Astra took no collision damage.
        controller.advanceCombat(5f, 3);
        assertTrue(controller.isRaiderActive(), "Vesper reappears after kill " + kill);
        assertEquals(100, controller.raiderCombat().energyPercent(),
            "a new Vesper life starts at full energy");
        VesperRaider next = controller.activeRaider();
        assertTrue(next.centerX() >= 0f
            && next.centerX() <= PhaseOneGameController.WORLD.width());
        assertTrue(next.centerY() >= 0f
            && next.centerY() <= PhaseOneGameController.WORLD.height());
      }
    }

    // Long past the 5 s respawn window, the third kill must keep Vesper away for good.
    controller.advanceCombat(30f, 3);
    assertFalse(controller.isRaiderActive(), "no fourth Vesper life after the third kill");
    assertEquals(3, controller.vesperKills());
    assertEquals(3000, controller.scorePoints());
  }

  @Test
  void aMissionLostByLivesDoesNotRespawnADefeatedVesper() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    alignAstraForBurst(controller, raider);
    assertEquals(1000, fireUntilRaiderDestroyed(controller));
    assertEquals(1, controller.vesperKills());

    // The lives child resolves terminal LOSE even if Vesper was previously destroyed.
    assertEquals(PhaseOutcome.LOSE, controller.resolveMissionOutcome(0));
    controller.advanceCombat(60f, 3);
    assertFalse(controller.isRaiderActive(), "a terminal mission never respawns Vesper");
    assertEquals(1, controller.vesperKills());
  }

  @Test
  void anExpiredRunNeverRespawnsAVesperKilledDuringTheRun() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    alignAstraForBurst(controller, raider);
    assertEquals(1000, fireUntilRaiderDestroyed(controller));
    assertEquals(1, controller.vesperKills());

    controller.advanceRun(600f);
    assertTrue(controller.isRunFinished());
    assertEquals(0, controller.advanceCombat(60f, 3), "the finished run advances no combat");
    assertFalse(controller.isRaiderActive(), "the expired clock creates no new Raider");
    assertEquals(1, controller.vesperKills());
  }

  @Test
  void astraLivesGoFromThreeToZeroAndRespawnsStopWhenExhausted() {
    PhaseOneGameController controller = spawnRaider();
    assertEquals(0, controller.drainAstraLifeLosses(), "no life is lost before any destruction");

    int livesRemaining = 3;
    for (int death = 1; death <= 3; death++) {
      VesperRaider raider = controller.activeRaider();
      assertNotNull(raider, "Vesper must be present before Astra death " + death);
      // Park Vesper at a known open spot so the collision geometry is deterministic.
      raider.placeAt(4000f - raider.drawWidth() / 2f, 6000f - raider.drawHeight() / 2f, 0f,
          PhaseOneGameController.WORLD);
      // A vulnerable hull contact destroys Astra only; Vesper remains in the encounter.
      controller.placeAstra(raider.x(), raider.y(), 0f);
      controller.advanceCombat(DELTA, livesRemaining);
      assertEquals(1, controller.drainAstraLifeLosses(),
          "each real destruction costs exactly one life");
      assertFalse(controller.isAstraActive());
      assertEquals(0, controller.vesperKills(), "collision must not count a Vesper kill");
      assertTrue(controller.isRaiderActive(), "Vesper survives Astra's collision");
      livesRemaining--;

      // Astra returns after 3 s with protection; move her away before that protection ends.
      controller.advanceCombat(3f, livesRemaining);
      if (livesRemaining > 0) {
        assertTrue(controller.isAstraActive(), "Astra reappears while lives remain");
        controller.placeAstra(1600f, 6000f, 0f);
        controller.advanceCombat(2f, livesRemaining); // the protection window ends
        assertFalse(controller.astraCombat().isInvulnerable());
      }
    }

    // With the pool exhausted Astra never returns, even long past the 3 s respawn delay, and no
    // further loss can be recorded from the wreck.
    controller.advanceCombat(60f, 0);
    assertFalse(controller.isAstraActive(), "an exhausted pool leaves Astra defeated");
    assertEquals(0, controller.drainAstraLifeLosses(), "a wreck never loses another life");
    assertEquals(PhaseOutcome.LOSE, controller.resolveMissionOutcome(0));
  }

  @Test
  void aVesperContactCostsOneAstraLifeAndDoesNotKillVesper() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    controller.placeAstra(raider.x(), raider.y(), 0f);

    assertEquals(0, controller.advanceCombat(DELTA, 3));
    assertEquals(1, controller.drainAstraLifeLosses(), "the collision costs Astra one life");
    assertFalse(controller.raiderCombat().isDestroyed(), "Vesper takes no collision damage");
    assertEquals(0, controller.vesperKills(), "collision does not count a Vesper kill");
    assertEquals(0, controller.scorePoints(), "collision awards no kill bonus");
    assertEquals(0, controller.drainAstraLifeLosses(), "the transition is drained exactly once");
  }

  @Test
  void astraProtectedByInvulnerabilityNeverLosesALifeOnContact() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    controller.placeAstra(raider.x(), raider.y(), 0f);
    controller.advanceCombat(DELTA, 3);      // first death
    controller.advanceCombat(5f, 2);         // Astra is back and invulnerable
    assertTrue(controller.astraCombat().isInvulnerable());
    controller.drainAstraLifeLosses();

    // Overlap the raider again inside the protection window: nobody is destroyed, no life is lost.
    VesperRaider respawned = controller.activeRaider();
    controller.placeAstra(respawned.x(), respawned.y(), 0f);
    controller.advanceCombat(DELTA, 2);

    assertFalse(controller.astraCombat().isDestroyed(), "protected Astra never collides");
    assertEquals(0, controller.drainAstraLifeLosses(), "a protected contact costs no life");
  }

  @Test
  void aSustainedBurstOnTheSameBodyCostsExactlyOneLife() {
    PhaseOneGameController controller = spawnRaider();
    VesperRaider raider = controller.activeRaider();
    // Park Astra directly under the raider's firing line so its burst destroys her.
    float astraX = raider.centerX() - controller.astra().drawWidth() / 2f;
    float astraY = raider.y() - 150f - controller.astra().drawHeight();
    controller.placeAstra(astraX, astraY, 0f);

    int guard = 0;
    while (!controller.astraCombat().isDestroyed() && guard < 400) {
      controller.advanceWeapons(1f, 1f, false, true, 10_000f);
      guard++;
    }
    assertTrue(controller.astraCombat().isDestroyed(), "the burst destroys Astra");
    assertEquals(1, controller.drainAstraLifeLosses(), "one destruction, one life");

    for (int frame = 0; frame < 30; frame++) {
      controller.advanceWeapons(1f / 60f, 1f / 60f, false, true, 10_000f);
    }
    assertEquals(0, controller.drainAstraLifeLosses(),
        "further impacts on the wreck never cost another life");
  }
}
