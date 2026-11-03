package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissile;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import org.junit.jupiter.api.Test;

/**
 * Pure lifecycle and SPACE-arming contract of {@link PhaseOneScreenFlow}, verified without a window:
 * the objective press starts the mission and never fires, held SPACE cannot fire until released, and
 * the ending shot cannot immediately confirm the result panel.
 */
class PhaseOneScreenFlowTest {

  @Test
  void startsInIntroAndOnlyAFreshPressStartsTheMission() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    assertTrue(flow.isIntro());

    // A released key with no press does not start anything.
    flow.observeSpace(false);
    assertFalse(flow.consumeStartPress(false));
    assertTrue(flow.isIntro());

    // The fresh press starts the mission exactly once.
    flow.observeSpace(true);
    assertTrue(flow.consumeStartPress(true));
    assertTrue(flow.isPlaying());
    assertFalse(flow.consumeStartPress(true), "INTRO is consumed and cannot start twice");
  }

  @Test
  void theStartingPressNeverFiresAndHeldSpaceWaitsForRelease() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    flow.observeSpace(true);
    assertTrue(flow.consumeStartPress(true));

    // Same frame: the key is physically held and the start press was consumed, so no shot.
    assertFalse(flow.fireTrigger(true));

    // The key stays held across later frames: still no shot until it is released.
    flow.observeSpace(true);
    assertFalse(flow.fireTrigger(true));

    // Release re-arms; the next press fires.
    flow.observeSpace(false);
    assertFalse(flow.fireTrigger(false));
    flow.observeSpace(true);
    assertTrue(flow.fireTrigger(true));
    flow.observeSpace(false);
  }

  @Test
  void holdFromTheObjectiveUntilReachingPlayingNeverFires() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    // The player presses and keeps SPACE from the objective panel through the start frame.
    flow.observeSpace(true);
    assertTrue(flow.consumeStartPress(true));
    for (int frame = 0; frame < 30; frame++) {
      flow.observeSpace(true);
      assertFalse(flow.fireTrigger(true), "a held trigger must not fire before a release");
    }
    flow.observeSpace(false);
    flow.observeSpace(true);
    assertTrue(flow.fireTrigger(true));
  }

  @Test
  void theEndingShotCannotConfirmTheResultPanel() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    flow.observeSpace(true);
    flow.consumeStartPress(true);

    // The mission ends while SPACE is still held from the firing burst.
    flow.showResult();
    assertTrue(flow.isResult());
    assertFalse(flow.consumeResultConfirm(false));

    // The release does not confirm by itself...
    flow.observeSpace(false);
    assertFalse(flow.consumeResultConfirm(false));
    assertTrue(flow.isResult());

    // ...but the next fresh press does.
    flow.observeSpace(true);
    assertTrue(flow.consumeResultConfirm(true));
    assertFalse(flow.consumeResultConfirm(true), "one press confirms the result only once");
  }

  @Test
  void theEndingPressOfTheSameFrameDoesNotConfirm() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    flow.observeSpace(true);
    flow.consumeStartPress(true);
    flow.observeSpace(false);
    flow.observeSpace(true);

    // The same physical press that fired the ending shot is still down when RESULT appears.
    flow.showResult();
    assertFalse(flow.consumeResultConfirm(true), "the ending press must be released first");
  }

  @Test
  void successfulMissileLaunchReturnsToCannonAndConsumesTriggerUntilRelease() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    assertFalse(flow.armMissile(), "intro cannot arm a weapon");
    flow.observeSpace(true);
    assertTrue(flow.consumeStartPress(true));
    flow.observeSpace(false);

    assertTrue(flow.armMissile());
    assertFalse(flow.armMissile(), "repeated F must not restart missile mode");
    assertTrue(flow.isMissileArmed());
    assertFalse(flow.fireTrigger(true), "missile mode suppresses cannon bursts");
    assertEquals(new PhaseOneScreenFlow.AimDirection(1f, 1f),
        flow.aimDirection(false, true, true, false));
    assertEquals(FlightControls.neutral(),
        flow.flightControls(true, true, true, true, true));

    flow.observeSpace(true);
    assertTrue(flow.consumeMissileLaunch(true));
    assertFalse(flow.consumeMissileLaunch(true), "one press requests at most one missile");
    flow.missileLaunched();
    assertFalse(flow.isMissileArmed(), "successful launch returns to cannon mode");
    assertFalse(flow.fireTrigger(true));

    assertTrue(flow.armMissile(), "missile mode can be selected again without ESC");
    assertFalse(flow.fireTrigger(true), "the launch press cannot become a cannon burst");
    flow.observeSpace(false);
    flow.observeSpace(true);
    assertTrue(flow.consumeMissileLaunch(true), "a later fresh press launches the next missile");
    assertTrue(flow.consumeMissileEscape(), "ESC still cancels an armed missile mode");
    assertFalse(flow.isMissileArmed());
    assertFalse(flow.consumeMissileEscape(), "ESC in cannon mode belongs to phase abandonment");
    assertFalse(flow.fireTrigger(true), "the missile trigger must be released before cannon fire");
    assertEquals(new PhaseOneScreenFlow.AimDirection(0f, 0f),
        flow.aimDirection(false, true, true, false));
    flow.observeSpace(false);
    flow.observeSpace(true);
    assertTrue(flow.fireTrigger(true));
    assertEquals(new FlightControls(true, false, true, false, true),
        flow.flightControls(true, false, true, false, true));
  }

  @Test
  void deathOrResultClearsMissileModeAndRequiresSpaceRelease() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    flow.observeSpace(true);
    flow.consumeStartPress(true);
    flow.observeSpace(false);
    assertTrue(flow.armMissile());

    flow.resetWeaponMode();
    assertFalse(flow.isMissileArmed());
    assertFalse(flow.fireTrigger(true), "a held key must not fire after death/respawn");
    flow.observeSpace(false);
    flow.observeSpace(true);
    assertTrue(flow.fireTrigger(true), "a new press works after release");

    assertTrue(flow.armMissile());
    flow.showResult();
    assertFalse(flow.isMissileArmed());
    assertFalse(flow.consumeMissileLaunch(true));
    assertFalse(flow.consumeMissileEscape(), "ESC on a result remains phase abandonment");
  }

  @Test
  void stageAccessorsReflectTheTransitions() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    assertEquals(PhaseOneScreenFlow.Stage.INTRO, flow.stage());
    flow.observeSpace(true);
    flow.consumeStartPress(true);
    assertEquals(PhaseOneScreenFlow.Stage.PLAYING, flow.stage());
    flow.showResult();
    assertEquals(PhaseOneScreenFlow.Stage.RESULT, flow.stage());
  }

  @Test
  void guidedMissileJourneySelectsLaunchesImpactsAndReturnsToCannon() {
    PhaseOneGameController controller = new PhaseOneGameController(() -> 0f, () -> 0f);
    controller.placeAstra(4000f, 6000f, 0f);
    controller.start();
    controller.advanceEncounter(3f);
    var raider = controller.activeRaider();
    float centerX = controller.astra().x() + controller.astra().drawWidth() / 2f;
    float centerY = controller.astra().y() + controller.astra().drawHeight() / 2f;
    raider.placeAt(centerX - raider.drawWidth() / 2f,
        centerY + 70f - raider.drawHeight() / 2f, 0f, PhaseOneGameController.WORLD);

    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    flow.observeSpace(true);
    assertTrue(flow.consumeStartPress(true));
    flow.observeSpace(false);
    assertTrue(flow.armMissile());
    assertTrue(controller.armAstraMissile());
    PhaseOneScreenFlow.AimDirection aim = flow.aimDirection(false, false, true, false);
    controller.moveAstraMissileAim(aim.horizontal(), aim.vertical(), 0.01f);
    assertTrue(controller.selectedAstraMissileTarget().isPresent());

    flow.observeSpace(true);
    assertTrue(flow.consumeMissileLaunch(true));
    assertTrue(controller.launchAstraMissile());
    flow.missileLaunched();
    controller.armAstraCannon();
    assertFalse(flow.isMissileArmed(), "successful launch returns to cannon without ESC");
    assertFalse(flow.consumeMissileLaunch(true), "holding SPACE cannot launch twice");
    assertFalse(flow.fireTrigger(true), "the missile trigger is not reused as a cannon burst");
    assertEquals(1, controller.astraMissilesRemaining());
    GuidedMissile launched = controller.guidedMissiles().get(0);
    int combatPoints = 0;
    for (int frame = 0; frame < 120 && launched.retirement() == GuidedMissile.Retirement.NONE;
        frame++) {
      combatPoints += controller.advanceWeapons(1f / 60f, 1f / 60f, false, false,
          10_000f);
    }

    assertEquals(GuidedMissile.Retirement.IMPACTED, launched.retirement());
    assertTrue(controller.raiderCombat().isDestroyed());
    assertEquals(1, controller.vesperKills());
    assertTrue(combatPoints > 0);

    assertFalse(flow.isMissileArmed(), "impact does not require ESC to restore cannon mode");
    assertEquals(1, controller.astraMissilesRemaining(), "ESC does not replenish ammunition");
  }
}
