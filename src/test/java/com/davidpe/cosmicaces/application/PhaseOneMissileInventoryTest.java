package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.player.FlightSpeeds;
import com.davidpe.cosmicaces.domain.weapon.MissileConfig;
import com.davidpe.cosmicaces.domain.weapon.MissileLoadout.Weapon;
import org.junit.jupiter.api.Test;

class PhaseOneMissileInventoryTest {
  private PhaseOneGameController controller(int capacity) {
    return new PhaseOneGameController(() -> 0f, () -> 0f, FlightSpeeds.DEFAULTS, () -> 0f,
        MissileConfig.DEFAULTS.withAstraMissilesPerLife(capacity));
  }

  @Test
  void eachRunCapturesItsConfigurationAndOnlyValidLaunchesConsume() {
    PhaseOneGameController first = controller(2);
    PhaseOneGameController next = controller(10);
    assertEquals(2, first.astraMissileCapacity());
    assertEquals(10, next.astraMissileCapacity());
    assertFalse(first.armAstraMissile(), "intro cannot arm weapons");
    first.start();
    assertTrue(first.armAstraMissile());
    assertFalse(first.consumeAstraMissileForLaunch(false));
    assertTrue(first.consumeAstraMissileForLaunch(true));
    first.armAstraCannon();
    first.armAstraMissile();
    assertEquals(1, first.astraMissilesRemaining());
    assertTrue(first.consumeAstraMissileForLaunch(true));
    assertFalse(first.consumeAstraMissileForLaunch(true));
    assertEquals(0, first.astraMissilesRemaining());
    assertEquals(10, next.astraMissilesRemaining());

    PhaseOneGameController empty = controller(0);
    empty.start();
    assertTrue(empty.armAstraMissile());
    assertFalse(empty.consumeAstraMissileForLaunch(true));
    assertEquals(0, empty.astraMissilesRemaining());
  }

  @Test
  void realRespawnRestoresOnceAndDeathCancelsMissileMode() {
    PhaseOneGameController controller = controller(2);
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    controller.armAstraMissile();
    assertTrue(controller.consumeAstraMissileForLaunch(true));
    controller.advanceEncounter(3f);
    var raider = controller.activeRaider();
    assertNotNull(raider);
    controller.placeAstra(raider.x(), raider.y(), 0f);
    controller.advanceCombat(1f / 60f, 3);
    assertTrue(controller.astraCombat().isDestroyed());
    assertEquals(Weapon.CANNON, controller.astraWeapon());
    assertEquals(1, controller.astraMissilesRemaining(), "death alone does not replenish");
    assertFalse(controller.armAstraMissile());
    controller.advanceCombat(3f, 2);
    assertEquals(2, controller.astraMissilesRemaining());
    assertEquals(Weapon.CANNON, controller.astraWeapon());
    assertTrue(controller.armAstraMissile());
    assertTrue(controller.consumeAstraMissileForLaunch(true));
    controller.advanceCombat(0.1f, 2);
    assertEquals(1, controller.astraMissilesRemaining(), "later ticks must not refill again");
  }

  @Test
  void terminalOutcomeCancelsArmamentAndPreventsFurtherConsumption() {
    PhaseOneGameController controller = controller(2);
    controller.start();
    assertTrue(controller.armAstraMissile());
    assertEquals(PhaseOutcome.LOSE, controller.resolveMissionOutcome(0));
    assertEquals(Weapon.CANNON, controller.astraWeapon());
    assertFalse(controller.armAstraMissile());
    assertFalse(controller.consumeAstraMissileForLaunch(true));
    assertEquals(2, controller.astraMissilesRemaining());
  }

  @Test
  void exhaustedLivesDoNotTriggerAFalseReplenishment() {
    PhaseOneGameController controller = controller(2);
    controller.placeAstra(1600f, 6000f, 0f);
    controller.start();
    controller.armAstraMissile();
    assertTrue(controller.consumeAstraMissileForLaunch(true));
    controller.advanceEncounter(3f);
    var raider = controller.activeRaider();
    assertNotNull(raider);
    controller.placeAstra(raider.x(), raider.y(), 0f);
    controller.advanceCombat(1f / 60f, 1);
    controller.advanceCombat(3f, 0);
    assertTrue(controller.astraCombat().isDestroyed());
    assertEquals(1, controller.astraMissilesRemaining());
  }
}
