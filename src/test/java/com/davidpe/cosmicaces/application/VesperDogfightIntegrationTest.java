package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.weapon.GunProjectile;
import org.junit.jupiter.api.Test;

/**
 * Integrated dogfight checks for COS-65 that need the real phase-one controller frame order
 * ({@code advanceFlight} → {@code advanceEncounter} → {@code advanceCombat} → {@code advanceWeapons}).
 * They close the whole-loop acceptance items that cannot be observed from the encounter alone:
 * Vesper keeps a single instance across a death/respawn cycle and never fires while defeated or
 * without the visible-coincidence permission.
 */
class VesperDogfightIntegrationTest {

  private static final float DT = 1f / 60f;
  private static final float RANGE = 1_000_000f;
  /** Holds the target still so the geometry is a clean joint fixture. */
  private static final FlightControls BRAKE = new FlightControls(false, false, false, true, false);

  @Test
  void raiderKeepsOneInstanceAndDoesNotFireWhileDefeated() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f); // spawn after 3 s
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    controller.advanceEncounter(3f);
    assertTrue(controller.isRaiderActive());
    VesperRaider first = controller.activeRaider();

    // Force a hull-to-hull contact by placing Astra on the raider: both ships are destroyed once.
    controller.placeAstra(first.x(), first.y(), 0f);
    controller.advanceCombat(DT);
    assertFalse(controller.isRaiderActive(), "the hull contact destroys the raider");
    assertTrue(controller.raiderCombat().isDestroyed());

    // While the raider is defeated, its cannon must not emit even with the coincidence permission.
    for (int frame = 0; frame < 60; frame++) {
      controller.advanceWeapons(DT, DT, false, true, RANGE);
    }
    assertTrue(controller.raiderGun().projectiles().isEmpty(),
        "a defeated raider must not fire, coincident or not");
    assertTrue(controller.raiderGun().emissionCount() == 0, "no defeated-life burst events");

    // Astra returns after 3 s and Vesper after 5 s; the respawn must reuse the same instance.
    controller.advanceCombat(3f);
    controller.advanceCombat(2f);
    assertTrue(controller.isRaiderActive());
    assertSame(first, controller.activeRaider(),
        "respawn must keep the single persistent instance, never recreate it");

    // The new life fires from its own position, never from the pre-death trajectory.
    controller.advanceEncounter(DT);
    float newCenterX = controller.activeRaider().centerX();
    float newCenterY = controller.activeRaider().centerY();
    controller.advanceWeapons(0.85f, 0.85f, false, true, RANGE);
    assertFalse(controller.raiderGun().projectiles().isEmpty(),
        "the respawned raider fires while coincident");
    for (GunProjectile projectile : controller.raiderGun().projectiles()) {
      float distance = (float) Math.hypot(projectile.originX() - newCenterX,
          projectile.originY() - newCenterY);
      assertTrue(distance < 200f,
          "every shot must originate at the new life, not the pre-death path");
    }
  }

  @Test
  void raiderCannonStaysSilentWhenCoincidenceIsWithdrawn() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    controller.advanceEncounter(3f);

    for (int frame = 0; frame < 120; frame++) {
      controller.advanceFlight(BRAKE, DT, false);
      controller.advanceEncounter(DT);
      controller.advanceCombat(DT);
      controller.advanceWeapons(DT, DT, false, false, RANGE); // never coincident
    }
    assertTrue(controller.raiderGun().projectiles().isEmpty(),
        "no visible coincidence means no new raider shots");
    assertTrue(controller.raiderGun().emissionCount() == 0,
        "no authorized burst event without coincidence, so no audio cue either");
  }
}
