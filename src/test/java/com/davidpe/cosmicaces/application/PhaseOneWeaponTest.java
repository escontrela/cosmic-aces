package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    controller.advanceWeapons(delta, emission, trigger, RANGE);
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
    controller.advanceWeapons(1f / 60f, 1f / 60f, true, RANGE);
    GunProjectile projectile = controller.astraGun().projectiles().get(0);
    float forwardX = projectile.forwardX();
    float forwardY = projectile.forwardY();
    float startY = projectile.y();

    for (int frame = 0; frame < 30; frame++) {
      controller.advanceFlight(new FlightControls(false, true, true, false, false),
          1f / 60f, false);
      controller.advanceWeapons(1f / 60f, 0f, false, RANGE);
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
    controller.advanceWeapons(1f, remaining, true, RANGE);

    assertEquals(2, controller.astraGun().projectiles().size(),
        "only the immediate event fits inside the still-active fraction");
  }
}
