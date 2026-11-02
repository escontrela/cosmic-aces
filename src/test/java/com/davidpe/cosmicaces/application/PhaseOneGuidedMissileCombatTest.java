package com.davidpe.cosmicaces.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissile;
import com.davidpe.cosmicaces.domain.weapon.MissileTarget;
import org.junit.jupiter.api.Test;

class PhaseOneGuidedMissileCombatTest {
  private static final float FRAME = 1f / 60f;
  private static final float RANGE = 10_000f;

  @Test
  void twoMissilesDestroyBastionWithSixtyPointsEachAndOneMissionTransition() {
    PhaseOneGameController controller = startedController();
    controller.bastionBomber();
    MissileTarget target = target(controller, MissileTarget.Kind.BASTION);
    GuidedMissile first = missile(controller, GuidedMissile.Owner.ASTRA, target,
        target.x(), target.y() - target.radius() - 100f, 0f, 1500f);
    GuidedMissile second = missile(controller, GuidedMissile.Owner.ASTRA, target,
        target.x(), target.y() - target.radius() - 100f, 0f, 1500f);
    GuidedMissile stale = missile(controller, GuidedMissile.Owner.ASTRA, target,
        target.x(), target.y(), 0f, 1500f);
    assertTrue(controller.trackGuidedMissile(first));
    assertTrue(controller.trackGuidedMissile(second));
    assertTrue(controller.trackGuidedMissile(stale));

    controller.advanceWeapons(0.2f, 0f, false, false, false, RANGE);

    assertEquals(0, controller.bastionBomberCombat().energyPercent(),
        "the first two 60-point impacts must take Bastion from 100 to 40 to zero");
    assertTrue(controller.bastionBomberCombat().isDestroyed());
    assertFalse(controller.recordBastionBomberDestroyed(),
        "the bomber destruction transition must already have been recorded exactly once");
    assertEquals(GuidedMissile.Retirement.IMPACTED, first.retirement());
    assertEquals(GuidedMissile.Retirement.IMPACTED, second.retirement());
    assertEquals(GuidedMissile.Retirement.TARGET_INVALID, stale.retirement());
    assertEquals(0, controller.drainCombatPoints(), "Bastion has no Vesper kill bonus");
  }

  @Test
  void twoEnemyMissilesRemoveFiftyPointsEachAndCostOneLifeOnly() {
    PhaseOneGameController controller = startedController();
    MissileTarget target = astraTarget(controller);
    GuidedMissile first = missile(controller, GuidedMissile.Owner.ENEMY, target,
        target.x(), target.y(), 180f, 1500f);
    GuidedMissile second = missile(controller, GuidedMissile.Owner.ENEMY, target,
        target.x(), target.y(), 180f, 1500f);
    GuidedMissile third = missile(controller, GuidedMissile.Owner.ENEMY, target,
        target.x(), target.y(), 180f, 1500f);
    assertTrue(controller.trackGuidedMissile(first));
    assertTrue(controller.trackGuidedMissile(second));
    assertTrue(controller.trackGuidedMissile(third));

    controller.advanceWeapons(FRAME, 0f, false, false, false, RANGE);

    assertEquals(0, controller.astraCombat().energyPercent());
    assertTrue(controller.astraCombat().isDestroyed());
    assertEquals(1, controller.drainAstraLifeLosses(),
        "two lethal impacts on the same life still produce one life-loss event");
    assertEquals(GuidedMissile.Retirement.IMPACTED, first.retirement());
    assertEquals(GuidedMissile.Retirement.IMPACTED, second.retirement());
    assertEquals(GuidedMissile.Retirement.TARGET_INVALID, third.retirement());
    assertEquals(0, controller.drainAstraLifeLosses());
  }

  @Test
  void staleAstraReferenceCannotDamageHerRespawnedLife() {
    PhaseOneGameController controller = startedController();
    MissileTarget oldTarget = astraTarget(controller);
    GuidedMissile oldLifeMissile = missile(controller, GuidedMissile.Owner.ENEMY, oldTarget,
        oldTarget.x(), oldTarget.y(), 180f, 1500f);
    assertTrue(controller.trackGuidedMissile(oldLifeMissile));

    controller.astraCombat().destroy();
    controller.advanceCombat(3f, 3);
    assertEquals(1, controller.astraCombat().lifeGeneration());
    assertEquals(100, controller.astraCombat().energyPercent());

    controller.advanceWeapons(FRAME, 0f, false, false, false, RANGE);

    assertEquals(100, controller.astraCombat().energyPercent());
    assertEquals(GuidedMissile.Retirement.TARGET_INVALID, oldLifeMissile.retirement());
  }

  @Test
  void invulnerableAstraConsumesIncomingMissileWithoutDamage() {
    PhaseOneGameController controller = startedController();
    controller.astraCombat().destroy();
    controller.advanceCombat(3f, 3);
    MissileTarget target = astraTarget(controller);
    GuidedMissile missile = missile(controller, GuidedMissile.Owner.ENEMY, target,
        target.x(), target.y(), 180f, 1500f);
    assertTrue(controller.trackGuidedMissile(missile));

    controller.advanceWeapons(FRAME, 0f, false, false, false, RANGE);

    assertTrue(controller.astraCombat().isInvulnerable());
    assertEquals(100, controller.astraCombat().energyPercent());
    assertEquals(GuidedMissile.Retirement.IMPACTED, missile.retirement());
    assertEquals(0, controller.drainAstraLifeLosses());
  }

  @Test
  void missileDamageAfterMissionOutcomeCannotAwardKillsOrPoints() {
    PhaseOneGameController controller = startedController();
    controller.advanceEncounter(3f);
    MissileTarget target = target(controller, MissileTarget.Kind.VESPER);
    GuidedMissile missile = missile(controller, GuidedMissile.Owner.ASTRA, target,
        target.x(), target.y(), 0f, 1500f);
    assertTrue(controller.trackGuidedMissile(missile));
    assertEquals(PhaseOutcome.LOSE, controller.resolveMissionOutcome(0));

    controller.advanceWeapons(FRAME, 0f, false, false, false, RANGE);

    assertFalse(controller.raiderCombat().isDestroyed());
    assertEquals(0, controller.vesperKills());
    assertEquals(0, controller.scorePoints());
    assertEquals(GuidedMissile.Retirement.MISSION_FINISHED, missile.retirement());
  }

  @Test
  void astraLaunchConsumesAmmunitionOnlyForASelectedLiveTarget() {
    PhaseOneGameController controller = startedController();
    controller.armAstraMissile();
    assertFalse(controller.launchAstraMissile());
    assertEquals(2, controller.astraMissilesRemaining());

    controller.advanceEncounter(3f);
    var raider = controller.activeRaider();
    float centerX = controller.astra().x() + controller.astra().drawWidth() / 2f;
    float centerY = controller.astra().y() + controller.astra().drawHeight() / 2f;
    raider.placeAt(centerX - raider.drawWidth() / 2f,
        centerY + 70f - raider.drawHeight() / 2f, 0f, PhaseOneGameController.WORLD);

    assertTrue(controller.launchAstraMissile());
    assertEquals(1, controller.astraMissilesRemaining());
    assertEquals(1, controller.guidedMissiles().size());
  }

  private static PhaseOneGameController startedController() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f, () -> 0f);
    controller.placeAstra(4000f, 6000f, 0f);
    controller.start();
    return controller;
  }

  private static MissileTarget target(PhaseOneGameController controller,
      MissileTarget.Kind kind) {
    return controller.liveMissileTargets().stream()
        .filter(candidate -> candidate.reference().kind() == kind).findFirst().orElseThrow();
  }

  private static MissileTarget astraTarget(PhaseOneGameController controller) {
    return new MissileTarget(new MissileTarget.Reference(MissileTarget.Kind.ASTRA, 0,
        controller.astraCombat().lifeGeneration()),
        controller.astra().x() + controller.astra().drawWidth() / 2f,
        controller.astra().y() + controller.astra().drawHeight() / 2f, 0f, 0f, 20f);
  }

  private static GuidedMissile missile(PhaseOneGameController controller,
      GuidedMissile.Owner owner, MissileTarget target, float x, float y,
      float heading, float launchSpeed) {
    return new GuidedMissile(owner, target.reference(), controller.missileConfig(),
        x, y, heading, (float) Math.sin(Math.toRadians(heading)) * launchSpeed,
        (float) Math.cos(Math.toRadians(heading)) * launchSpeed);
  }
}
