package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.weapon.GunProjectile;
import org.junit.jupiter.api.Test;

/**
 * Integration of the Astra M61 Vulcan with the phase-one controller: SPACE gating, the two cannon
 * origins, frozen straight paths, run-window emission and the no-ammunition/retire-by-distance
 * contract. Rendering and the HUD belong to the later child.
 */
class PhaseOneWeaponTest {

  private static final float RANGE = 1_000_000f;

  private static void frame(PhaseOneGameController controller, float delta, boolean trigger) {
    float emission = controller.isRunStarted() && !controller.isRunFinished()
        ? Math.min(delta, controller.remainingRunSeconds()) : 0f;
    controller.advanceFlight(FlightControls.neutral(), delta, false);
    controller.advanceWeapons(delta, emission, trigger, false, RANGE);
  }

  private static void vesperFrame(PhaseOneGameController controller, float delta,
      boolean coincident) {
    float emission = controller.isRunStarted() && !controller.isRunFinished()
        ? Math.min(delta, controller.remainingRunSeconds()) : 0f;
    controller.advanceWeapons(delta, emission, false, coincident, RANGE);
  }

  @Test
  void holdingSpaceFiresBothCannonsDuringTheRun() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();

    for (int frame = 0; frame < 60; frame++) {
      frame(controller, 1f / 60f, true);
    }

    var projectiles = controller.astraGun().projectiles();
    assertTrue(projectiles.size() >= 24, "expected ~26 projectiles, got " + projectiles.size());
    assertEquals(0, projectiles.size() % 2, "each event fires the two cannons");
    assertTrue(controller.astraGun().isFiring());

    GunProjectile first = projectiles.get(0);
    GunProjectile second = projectiles.get(1);
    assertNotEquals(first.originX(), second.originX(), 1e-3f,
        "the two cannons must have distinct lateral origins");
    assertEquals(first.originY(), second.originY(), 1.5f);
  }

  @Test
  void noShotsBeforeStartOrAfterTheRun() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);

    frame(controller, 1f / 60f, true);
    assertTrue(controller.astraGun().projectiles().isEmpty(), "no shots before the run");

    controller.start();
    controller.advanceRun(60f);
    assertTrue(controller.isRunFinished());

    frame(controller, 1f / 60f, true);
    assertTrue(controller.astraGun().projectiles().isEmpty(), "no shots after the run");
  }

  @Test
  void releasingSpaceStopsNewShotsButKeepsExistingOnes() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();

    for (int frame = 0; frame < 30; frame++) {
      frame(controller, 1f / 60f, true);
    }
    int fired = controller.astraGun().projectiles().size();
    assertTrue(fired >= 2);

    for (int frame = 0; frame < 60; frame++) {
      frame(controller, 1f / 60f, false);
    }

    assertEquals(fired, controller.astraGun().projectiles().size(),
        "releasing must not remove in-flight projectiles");
    assertFalse(controller.astraGun().isFiring());
  }

  @Test
  void reEnablingDoesNotAccumulateBursts() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();

    frame(controller, 1f / 60f, true);
    int afterFirst = controller.astraGun().projectiles().size();
    assertEquals(2, afterFirst);

    for (int frame = 0; frame < 60; frame++) {
      frame(controller, 1f / 60f, false);
    }
    frame(controller, 1f / 60f, true);

    assertEquals(afterFirst + 2, controller.astraGun().projectiles().size(),
        "a long release must not bank extra bursts");
  }

  @Test
  void firedProjectilesKeepTheirStraightPathWhenAstraTurns() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();

    controller.advanceFlight(FlightControls.neutral(), 1f / 60f, false);
    controller.advanceWeapons(1f / 60f, 1f / 60f, true, false, RANGE);
    GunProjectile projectile = controller.astraGun().projectiles().get(0);
    float forwardX = projectile.forwardX();
    float forwardY = projectile.forwardY();
    float startY = projectile.y();

    for (int frame = 0; frame < 30; frame++) {
      controller.advanceFlight(new FlightControls(false, true, true, false, false),
          1f / 60f, false);
      controller.advanceWeapons(1f / 60f, 0f, false, false, RANGE);
    }

    assertEquals(forwardX, projectile.forwardX(), 1e-6f);
    assertEquals(forwardY, projectile.forwardY(), 1e-6f);
    assertTrue(projectile.y() > startY, "the frozen shot keeps travelling along its own path");
  }

  @Test
  void deltaCrossingTheFinishEmitsOnlyInsideTheActiveFraction() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();
    controller.advanceRun(59.95f);

    float remaining = controller.remainingRunSeconds();
    assertTrue(remaining > 0f && remaining < 0.08f, "next cadence step must fall past the run end");

    controller.advanceFlight(FlightControls.neutral(), 1f, false);
    assertTrue(controller.isRunFinished());
    controller.advanceWeapons(1f, remaining, true, false, RANGE);

    assertEquals(2, controller.astraGun().projectiles().size(),
        "only the immediate event fits inside the still-active fraction");
  }

  @Test
  void vesperDoesNotFireWithoutARaiderAndFiresOnceItCoincides() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f); // spawn after 3 s

    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    vesperFrame(controller, 1f / 60f, true);
    assertTrue(controller.raiderGun().projectiles().isEmpty(),
        "no raider yet, so no autonomous shots");

    controller.advanceEncounter(3f);
    assertTrue(controller.isRaiderActive());

    vesperFrame(controller, 1f / 60f, true);
    assertEquals(2, controller.raiderGun().projectiles().size(),
        "the raider's two side cannons fire one event each");
  }

  @Test
  void vesperDoesNotFireWhenNotCoincident() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    controller.advanceEncounter(3f);

    for (int frame = 0; frame < 30; frame++) {
      vesperFrame(controller, 1f / 60f, false);
    }

    assertTrue(controller.raiderGun().projectiles().isEmpty());
  }

  @Test
  void vesperStopsNewShotsWhenCoincidenceEndsButKeepsExistingOnes() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    controller.advanceEncounter(3f);
    vesperFrame(controller, 1f / 60f, true);
    int fired = controller.raiderGun().projectiles().size();
    assertTrue(fired >= 2);

    for (int frame = 0; frame < 60; frame++) {
      vesperFrame(controller, 1f / 60f, false);
    }

    assertEquals(fired, controller.raiderGun().projectiles().size(),
        "leaving the camera must not remove in-flight projectiles");
    assertFalse(controller.raiderGun().isFiring());
  }

  @Test
  void vesperReenablingDoesNotAccumulateBursts() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    controller.advanceEncounter(3f);
    vesperFrame(controller, 1f / 60f, true);
    int afterFirst = controller.raiderGun().projectiles().size();
    assertEquals(2, afterFirst);

    for (int frame = 0; frame < 60; frame++) {
      vesperFrame(controller, 1f / 60f, false);
    }
    vesperFrame(controller, 1f / 60f, true);

    assertEquals(afterFirst + 2, controller.raiderGun().projectiles().size(),
        "a long loss of coincidence must not bank extra bursts");
  }

  @Test
  void vesperShotsKeepTheirFrozenDirectionWhenTheRaiderMoves() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    controller.advanceEncounter(3f);
    vesperFrame(controller, 1f / 60f, true);

    GunProjectile projectile = controller.raiderGun().projectiles().get(0);
    float forwardX = projectile.forwardX();
    float forwardY = projectile.forwardY();

    for (int frame = 0; frame < 30; frame++) {
      controller.advanceEncounter(1f / 60f);
      vesperFrame(controller, 1f / 60f, false);
    }

    assertEquals(forwardX, projectile.forwardX(), 1e-6f);
    assertEquals(forwardY, projectile.forwardY(), 1e-6f);
  }

  @Test
  void astraEmissionCountTracksOneEventPerRealBurstNotPerProjectile() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();

    for (int frame = 0; frame < 60; frame++) {
      frame(controller, 1f / 60f, true); // 1.0 s of sustained fire
    }

    long bursts = controller.astraGun().emissionCount();
    assertTrue(bursts >= 12 && bursts <= 14,
        "cadence of 0.08 s over one second, got " + bursts);
    assertEquals(bursts * 2, controller.astraGun().projectiles().size(),
        "one emission event per burst, two cannons per event");
  }

  @Test
  void astraEmissionCountFreezesWhenTheTriggerIsReleasedOrTheRunEnds() {
    PhaseOneGameController controller = new PhaseOneGameController();
    controller.placeAstra(1600f, 100f, 0f);
    controller.start();
    frame(controller, 1f / 60f, true);
    long afterBurst = controller.astraGun().emissionCount();
    assertTrue(afterBurst >= 1);

    for (int frame = 0; frame < 60; frame++) {
      frame(controller, 1f / 60f, false); // SPACE released
    }
    assertEquals(afterBurst, controller.astraGun().emissionCount(),
        "a held key without a real emission must not produce cues");

    controller.advanceRun(60f);
    assertTrue(controller.isRunFinished());
    for (int frame = 0; frame < 30; frame++) {
      frame(controller, 1f / 60f, true); // trigger still held after the end
    }
    assertEquals(afterBurst, controller.astraGun().emissionCount(),
        "the run end disables the weapon, so no new bursts");
  }

  @Test
  void vesperEmissionCountOnlyAdvancesWhenTheControllerAuthorizes() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    controller.advanceEncounter(3f);

    for (int frame = 0; frame < 60; frame++) {
      vesperFrame(controller, 1f / 60f, false); // not coincident
    }
    assertEquals(0, controller.raiderGun().emissionCount(),
        "no authorized burst, no event and therefore no audio cue");
    assertTrue(controller.raiderGun().projectiles().isEmpty());

    for (int frame = 0; frame < 72; frame++) {
      vesperFrame(controller, 1f / 60f, true); // 1.2 s coincident
    }
    long bursts = controller.raiderGun().emissionCount();
    assertTrue(bursts >= 2 && bursts <= 4, "Vesper cadence 0.4 s over 1.2 s, got " + bursts);
    assertEquals(bursts * 2, controller.raiderGun().projectiles().size(),
        "one event per burst, two side cannons per event");
  }

  @Test
  void vesperDoesNotFireAfterTheRunEnds() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    controller.advanceEncounter(3f);
    controller.advanceRun(60f);
    assertTrue(controller.isRunFinished());

    controller.advanceWeapons(1f / 60f, 0f, false, true, RANGE);
    assertTrue(controller.raiderGun().projectiles().isEmpty(),
        "the phase end cuts new autonomous shots");
  }

  @Test
  void vesperFastTurnFrameEmitsFromDifferentPointsAndHeadings() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f); // spawn after 3 s
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    controller.advanceEncounter(3f);
    VesperRaider raider = controller.activeRaider();
    float beforeHeading = raider.headingDegrees();

    // Move Astra far aside so the raider must turn hard and fly a long way within one frame.
    controller.placeAstra(6000f, 3000f, 0f);
    controller.advanceEncounter(0.85f);
    assertNotEquals(beforeHeading, raider.headingDegrees(), 1f, "the raider must turn this frame");

    controller.advanceWeapons(0.85f, 0.85f, false, true, RANGE);

    var shots = controller.raiderGun().projectiles();
    assertEquals(6, shots.size(), "0.85 s at the 0.4 s cadence: events at 0, 0.4 and 0.8 s");
    GunProjectile first = shots.get(0);   // event at 0.0 s, both side cannons
    GunProjectile second = shots.get(2);  // event at 0.4 s
    GunProjectile third = shots.get(4);   // event at 0.8 s
    assertTrue(originDistance(first, second) > 5f,
        "the second event must advance along the trajectory, not repeat the first origin");
    assertTrue(originDistance(second, third) > 5f,
        "the third event must advance along the trajectory");
    assertTrue(directionGap(first, third) > 1f,
        "the frozen heading must follow the fast turn, not freeze on the final frame state");
  }

  @Test
  void vesperShotsAfterRespawnComeFromTheNewLife() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    controller.advanceEncounter(3f);
    VesperRaider raider = controller.activeRaider();

    // Record a moving trajectory for the old life, then destroy and respawn the raider.
    controller.advanceEncounter(0.5f);
    controller.placeAstra(raider.x(), raider.y(), 0f);
    controller.advanceCombat(1f / 60f); // hull contact destroys both ships
    assertFalse(controller.isRaiderActive());
    controller.advanceCombat(3f); // Astra returns
    controller.advanceCombat(2f); // Vesper returns at the 5-second mark
    assertTrue(controller.isRaiderActive());
    VesperRaider respawned = controller.activeRaider();
    float newCenterX = respawned.centerX();
    float newCenterY = respawned.centerY();

    controller.advanceWeapons(0.85f, 0.85f, false, true, RANGE);

    var shots = controller.raiderGun().projectiles();
    assertFalse(shots.isEmpty(), "the respawned raider fires while coincident");
    for (GunProjectile projectile : shots) {
      float distance = (float) Math.hypot(projectile.originX() - newCenterX,
          projectile.originY() - newCenterY);
      assertTrue(distance < 200f,
          "every shot must originate at the new life, not the pre-death trajectory");
    }
  }

  private static float originDistance(GunProjectile a, GunProjectile b) {
    return (float) Math.hypot(a.originX() - b.originX(), a.originY() - b.originY());
  }

  private static float directionGap(GunProjectile a, GunProjectile b) {
    float dot = a.forwardX() * b.forwardX() + a.forwardY() * b.forwardY();
    if (dot >= 1f) {
      return 0f;
    }
    if (dot <= -1f) {
      return 180f;
    }
    return (float) Math.toDegrees(Math.acos(dot));
  }
}
