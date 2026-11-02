package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.enemy.GhostwingDrone;
import com.davidpe.cosmicaces.domain.enemy.Geran4Encounter;
import com.davidpe.cosmicaces.domain.weapon.MissileTarget;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhaseOneMissileTargetTest {
  @Test
  void selectsOneLiveDroneRatherThanItsGroupAndDropsRemovedIdentities() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f, () -> 0f);
    controller.start();
    controller.advanceGhostwing(20f);
    List<GhostwingDrone> drones = controller.ghostwingEncounter().activeDrones();
    assertEquals(2, drones.size());
    GhostwingDrone first = drones.get(0);
    controller.placeAstra(first.centerX() - controller.astra().drawWidth() / 2f,
        first.centerY() - controller.astra().drawHeight() / 2f, 0f);
    assertTrue(controller.armAstraMissile());
    MissileTarget selected = controller.selectedAstraMissileTarget().orElseThrow();
    assertEquals(MissileTarget.Kind.GHOSTWING, selected.reference().kind());
    assertEquals(first.id(), selected.reference().entityId());
    assertFalse(selected.isBoss());
    assertEquals(first.speed() * first.direction().horizontal(), selected.velocityX(), 0.001f);
    assertEquals(first.speed() * first.direction().vertical(), selected.velocityY(), 0.001f);
    controller.ghostwingEncounter().markDestroyed(first.id());
    assertTrue(controller.missileTarget(selected.reference()).isEmpty());
    assertNotEquals(selected.reference(),
        controller.selectedAstraMissileTarget().orElseThrow().reference());
  }

  @Test
  void respawnedVesperCannotResolveItsPreviousLifeReference() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.placeAstra(1600, 6000, 0);
    controller.start();
    controller.advanceEncounter(3f);
    MissileTarget original = controller.liveMissileTargets().stream()
        .filter(t -> t.reference().kind() == MissileTarget.Kind.VESPER).findFirst().orElseThrow();
    controller.raiderCombat().destroy();
    assertTrue(controller.missileTarget(original.reference()).isEmpty());
    controller.advanceCombat(5f, 3);
    MissileTarget respawned = controller.liveMissileTargets().stream()
        .filter(t -> t.reference().kind() == MissileTarget.Kind.VESPER).findFirst().orElseThrow();
    assertNotEquals(original.reference(), respawned.reference());
    assertTrue(controller.missileTarget(original.reference()).isEmpty());
    assertTrue(controller.missileTarget(respawned.reference()).isPresent());
  }

  @Test
  void aimStartsAtAstraIsNotResetByRearmingAndIsCancelledByCannonOrEnd() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.placeAstra(1000, 1000, 0);
    controller.start();
    assertTrue(controller.armAstraMissile());
    assertTrue(controller.selectedAstraMissileTarget().isEmpty());
    assertFalse(controller.consumeAstraMissileForLaunch(true), "no target cannot consume a missile");
    assertEquals(2, controller.astraMissilesRemaining());
    float startX = controller.astraMissileAimX();
    controller.moveAstraMissileAim(1, 0, 0.5f);
    assertEquals(startX + 600, controller.astraMissileAimX(), 0.01f);
    assertTrue(controller.armAstraMissile());
    assertEquals(startX + 600, controller.astraMissileAimX(), 0.01f);
    controller.armAstraCannon();
    assertFalse(controller.isAstraMissileAimActive());
    assertTrue(controller.selectedAstraMissileTarget().isEmpty());
    controller.armAstraMissile();
    controller.resolveMissionOutcome(0);
    assertFalse(controller.isAstraMissileAimActive());
  }

  @Test
  void geranTargetsAreIndividualAndRetireWhenTheDroneIsRemoved() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f, () -> 0f,
        com.davidpe.cosmicaces.domain.player.FlightSpeeds.DEFAULTS, () -> 0.5f);
    controller.placeAstra(1000, 1000, 0);
    controller.start();
    var view = new Geran4Encounter.ViewportFrame(4000, 1200, 0, 1, 1, 800, 800);
    controller.advanceGeran4(10f, view, false);
    var drone = controller.geran4Encounter().activeDrones().get(0);
    MissileTarget target = controller.liveMissileTargets().stream()
        .filter(t -> t.reference().kind() == MissileTarget.Kind.GERAN4).findFirst().orElseThrow();
    assertEquals(drone.id(), target.reference().entityId());
    assertEquals(drone.speed() * drone.directionX(), target.velocityX(), 0.001f);
    assertEquals(drone.speed() * drone.directionY(), target.velocityY(), 0.001f);
    controller.geran4Encounter().markDestroyed(drone.id());
    assertTrue(controller.missileTarget(target.reference()).isEmpty());
  }

  @Test
  void parkedBomberIsAStationaryBossAndEscortsAppearOnlyAfterDeployment() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f);
    controller.phaseOneScenery();
    controller.start();
    MissileTarget bomber = controller.liveMissileTargets().stream()
        .filter(t -> t.reference().kind() == MissileTarget.Kind.BASTION).findFirst().orElseThrow();
    assertTrue(bomber.isBoss());
    assertEquals(0, bomber.velocityX());
    assertEquals(0, bomber.velocityY());
    assertFalse(controller.liveMissileTargets().stream()
        .anyMatch(t -> t.reference().kind() == MissileTarget.Kind.ESCORT));
    controller.advanceEncounter(11f);
    assertEquals(2, controller.liveMissileTargets().stream()
        .filter(t -> t.reference().kind() == MissileTarget.Kind.ESCORT).count());
  }
}
