package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.enemy.BastionBomber;
import com.davidpe.cosmicaces.domain.enemy.BastionEscort;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissile;
import com.davidpe.cosmicaces.domain.weapon.MissileTarget;
import org.junit.jupiter.api.Test;

class PhaseOneBastionEscortMissileTest {
  private static final float RANGE = 10_000f;
  private static final float FRAME = 1f / 60f;

  @Test
  void escortsKeepIndependentLocksAndFireExactlyOneMissileEachAfterTwoSeconds() {
    PhaseOneGameController controller = controllerWithDeployedEscorts();
    placeAstraBetweenEscorts(controller);

    controller.advanceWeapons(1.99f, 1.99f, false, false, false, RANGE);
    assertEquals(1.99f, controller.bastionEscorts().get(0).missileLockSeconds(), 0.0001f);
    assertEquals(1.99f, controller.bastionEscorts().get(1).missileLockSeconds(), 0.0001f);
    assertTrue(controller.guidedMissiles().isEmpty(), "1.99 seconds is not a complete lock");

    controller.advanceWeapons(0.02f, 0.02f, false, false, false, RANGE);

    assertEquals(2, controller.guidedMissiles().size(),
        "the Bastion escorts launch independently; the unescorted Raider does not gain a missile");
    assertTrue(controller.guidedMissiles().stream()
        .allMatch(missile -> missile.owner() == GuidedMissile.Owner.ENEMY));
    assertTrue(controller.guidedMissiles().stream().allMatch(missile ->
        missile.targetReference().kind() == MissileTarget.Kind.ASTRA
            && missile.targetReference().lifeGeneration() == controller.astraCombat().lifeGeneration()));
    assertTrue(controller.bastionEscorts().stream()
        .allMatch(escort -> escort.missileCapacity() == 1 && escort.missilesRemaining() == 0));

    for (int frame = 0; frame < 240 && controller.astraCombat().energyPercent() > 0; frame++) {
      controller.advanceWeapons(FRAME, FRAME, false, false, false, RANGE);
    }
    assertEquals(0, controller.astraCombat().energyPercent(), "two valid hits deal 50 points each");
    assertEquals(1, controller.drainAstraLifeLosses(), "the lethal missile pair costs one life");
    assertFalse(controller.bastionEscorts().stream().anyMatch(escort -> escort.missilesRemaining() > 0),
        "spent escorts never replenish or launch again");
  }

  @Test
  void leavingRangeResetsTheContinuousLockAndNoMissileLaunchesEarlyAfterReentry() {
    PhaseOneGameController controller = controllerWithDeployedEscorts();
    placeAstraBetweenEscorts(controller);
    controller.advanceWeapons(1f, 1f, false, false, false, RANGE);
    assertEquals(1f, controller.bastionEscorts().get(0).missileLockSeconds(), 0.0001f);

    controller.placeAstra(7000f, 1000f, 0f);
    controller.advanceWeapons(0.1f, 0.1f, false, false, false, RANGE);
    assertEquals(0f, controller.bastionEscorts().get(0).missileLockSeconds(), 0.0001f);
    assertTrue(controller.guidedMissiles().isEmpty());

    placeAstraBetweenEscorts(controller);
    controller.advanceWeapons(1.99f, 1.99f, false, false, false, RANGE);
    assertTrue(controller.guidedMissiles().isEmpty());
    controller.advanceWeapons(0.02f, 0.02f, false, false, false, RANGE);
    assertEquals(2, controller.guidedMissiles().size());
  }

  @Test
  void deathAndRespawnDiscardThePreviousLifeLockAndCapturedTargetGeneration() {
    PhaseOneGameController controller = controllerWithDeployedEscorts();
    placeAstraBetweenEscorts(controller);
    controller.advanceWeapons(1.5f, 1.5f, false, false, false, RANGE);

    controller.astraCombat().destroy();
    controller.advanceWeapons(FRAME, FRAME, false, false, false, RANGE);
    assertEquals(0f, controller.bastionEscorts().get(0).missileLockSeconds(), 0.0001f);
    assertTrue(controller.guidedMissiles().isEmpty());

    controller.advanceCombat(3f, 3);
    int newLife = controller.astraCombat().lifeGeneration();
    assertEquals(1, newLife);
    placeAstraBetweenEscorts(controller);
    controller.advanceWeapons(1.99f, 1.99f, false, false, false, RANGE);
    assertTrue(controller.guidedMissiles().isEmpty(), "respawn starts a fresh two-second lock");
    controller.advanceWeapons(0.02f, 0.02f, false, false, false, RANGE);

    assertEquals(2, controller.guidedMissiles().size());
    assertTrue(controller.guidedMissiles().stream().allMatch(missile ->
        missile.targetReference().lifeGeneration() == newLife));
  }

  private static PhaseOneGameController controllerWithDeployedEscorts() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f, () -> 0f);
    controller.placeAstra(4000f, 6000f, 0f);
    controller.phaseOneScenery();
    controller.start();
    controller.advanceEncounter(BastionBomber.PARKED_SECONDS + 0.1f);
    assertEquals(2, controller.bastionEscorts().stream().filter(BastionEscort::isActive).count());
    return controller;
  }

  private static void placeAstraBetweenEscorts(PhaseOneGameController controller) {
    BastionEscort left = controller.bastionEscorts().get(0);
    BastionEscort right = controller.bastionEscorts().get(1);
    float centerX = (left.raider().centerX() + right.raider().centerX()) / 2f;
    float centerY = (left.raider().centerY() + right.raider().centerY()) / 2f;
    controller.placeAstra(centerX - controller.astra().drawWidth() / 2f,
        centerY - controller.astra().drawHeight() / 2f, 0f);
  }
}
