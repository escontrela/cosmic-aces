package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.application.PhaseOneGameController;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissile;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionSnapshot;
import com.davidpe.cosmicaces.infrastructure.gdx.input.InputAction;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickDirection;
import java.util.Set;
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
    flow.observeTrigger(false);
    assertFalse(flow.consumeStartPress(false));
    assertTrue(flow.isIntro());

    // The fresh press starts the mission exactly once.
    flow.observeTrigger(true);
    assertTrue(flow.consumeStartPress(true));
    assertTrue(flow.isPlaying());
    assertFalse(flow.consumeStartPress(true), "INTRO is consumed and cannot start twice");
  }

  @Test
  void theStartingPressNeverFiresAndHeldSpaceWaitsForRelease() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    flow.observeTrigger(true);
    assertTrue(flow.consumeStartPress(true));

    // Same frame: the key is physically held and the start press was consumed, so no shot.
    assertFalse(flow.fireTrigger(true));

    // The key stays held across later frames: still no shot until it is released.
    flow.observeTrigger(true);
    assertFalse(flow.fireTrigger(true));

    // Release re-arms; the next press fires.
    flow.observeTrigger(false);
    assertFalse(flow.fireTrigger(false));
    flow.observeTrigger(true);
    assertTrue(flow.fireTrigger(true));
    flow.observeTrigger(false);
  }

  @Test
  void holdFromTheObjectiveUntilReachingPlayingNeverFires() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    // The player presses and keeps SPACE from the objective panel through the start frame.
    flow.observeTrigger(true);
    assertTrue(flow.consumeStartPress(true));
    for (int frame = 0; frame < 30; frame++) {
      flow.observeTrigger(true);
      assertFalse(flow.fireTrigger(true), "a held trigger must not fire before a release");
    }
    flow.observeTrigger(false);
    flow.observeTrigger(true);
    assertTrue(flow.fireTrigger(true));
  }

  @Test
  void theResultStageIsTerminalAndConsumesTheEndingPress() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    flow.observeTrigger(true);
    flow.consumeStartPress(true);

    // The mission ends while SPACE is still held from the firing burst. The frozen frame accepts no
    // firing, no missile mode and no launch; the Hall of Fame takes over at the end of the frame.
    flow.showResult();
    assertTrue(flow.isResult());
    assertFalse(flow.fireTrigger(true));
    assertFalse(flow.armMissile());
    assertFalse(flow.consumeMissileLaunch(true));

    flow.observeTrigger(false);
    flow.observeTrigger(true);
    assertFalse(flow.fireTrigger(true), "the result frame never fires again");
    assertTrue(flow.isResult());
  }

  @Test
  void successfulMissileLaunchReturnsToCannonAndConsumesTriggerUntilRelease() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    assertFalse(flow.armMissile(), "intro cannot arm a weapon");
    flow.observeTrigger(true);
    assertTrue(flow.consumeStartPress(true));
    flow.observeTrigger(false);

    assertTrue(flow.armMissile());
    assertFalse(flow.armMissile(), "repeated F must not restart missile mode");
    assertTrue(flow.isMissileArmed());
    assertFalse(flow.fireTrigger(true), "missile mode suppresses cannon bursts");
    assertEquals(new PhaseOneScreenFlow.AimDirection(1f, 1f),
        flow.aimDirection(false, true, true, false));
    assertEquals(FlightControls.neutral(),
        flow.flightControls(true, true, true, true, true));

    flow.observeTrigger(true);
    assertTrue(flow.consumeMissileLaunch(true));
    assertFalse(flow.consumeMissileLaunch(true), "one press requests at most one missile");
    flow.missileLaunched();
    assertFalse(flow.isMissileArmed(), "successful launch returns to cannon mode");
    assertFalse(flow.fireTrigger(true));

    assertTrue(flow.armMissile(), "missile mode can be selected again without ESC");
    assertFalse(flow.fireTrigger(true), "the launch press cannot become a cannon burst");
    flow.observeTrigger(false);
    flow.observeTrigger(true);
    assertTrue(flow.consumeMissileLaunch(true), "a later fresh press launches the next missile");
    assertTrue(flow.consumeMissileCancel(), "ESC still cancels an armed missile mode");
    assertFalse(flow.isMissileArmed());
    assertFalse(flow.consumeMissileCancel(), "ESC in cannon mode belongs to phase abandonment");
    assertFalse(flow.fireTrigger(true), "the missile trigger must be released before cannon fire");
    assertEquals(new PhaseOneScreenFlow.AimDirection(0f, 0f),
        flow.aimDirection(false, true, true, false));
    flow.observeTrigger(false);
    flow.observeTrigger(true);
    assertTrue(flow.fireTrigger(true));
    assertEquals(new FlightControls(true, false, true, false, true),
        flow.flightControls(true, false, true, false, true));
  }

  @Test
  void deathOrResultClearsMissileModeAndRequiresSpaceRelease() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    flow.observeTrigger(true);
    flow.consumeStartPress(true);
    flow.observeTrigger(false);
    assertTrue(flow.armMissile());

    flow.resetWeaponMode();
    assertFalse(flow.isMissileArmed());
    assertFalse(flow.fireTrigger(true), "a held key must not fire after death/respawn");
    flow.observeTrigger(false);
    flow.observeTrigger(true);
    assertTrue(flow.fireTrigger(true), "a new press works after release");

    assertTrue(flow.armMissile());
    flow.showResult();
    assertFalse(flow.isMissileArmed());
    assertFalse(flow.consumeMissileLaunch(true));
    assertFalse(flow.consumeMissileCancel(), "ESC on a result belongs to the Hall takeover");
  }

  @Test
  void stageAccessorsReflectTheTransitions() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    assertEquals(PhaseOneScreenFlow.Stage.INTRO, flow.stage());
    flow.observeTrigger(true);
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
    flow.observeTrigger(true);
    assertTrue(flow.consumeStartPress(true));
    flow.observeTrigger(false);
    assertTrue(flow.armMissile());
    assertTrue(controller.armAstraMissile());
    PhaseOneScreenFlow.AimDirection aim = flow.aimDirection(false, false, true, false);
    controller.moveAstraMissileAim(aim.horizontal(), aim.vertical(), 0.01f);
    assertTrue(controller.selectedAstraMissileTarget().isPresent());

    flow.observeTrigger(true);
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

  @Test
  void aMergedKeyboardAndJoyConFireEdgeStartsTheMissionAndHoldsTheCannon() {
    ActionSnapshot merged = ActionSnapshot.merge(
        missionSnapshot(Set.of(InputAction.FIRE), Set.of(InputAction.FIRE),
            StickDirection.NEUTRAL),
        missionSnapshot(Set.of(InputAction.FIRE), Set.of(InputAction.FIRE),
            StickDirection.NEUTRAL));
    MissionInput input = MissionInput.from(merged);

    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    flow.observeTrigger(input.fireHeld());
    assertTrue(flow.consumeStartPress(input.fireJustPressed()));
    assertFalse(flow.fireTrigger(input.fireHeld()), "the start press never fires Astra");

    // The merged trigger stays held across later frames without repeating an edge.
    flow.observeTrigger(input.fireHeld());
    assertFalse(flow.fireTrigger(input.fireHeld()));

    // Releasing both sources re-arms; the next press fires sustained.
    flow.observeTrigger(false);
    assertFalse(flow.fireTrigger(false));
    flow.observeTrigger(true);
    assertTrue(flow.fireTrigger(true));
  }

  @Test
  void joyConArmingAimingLaunchAndCancelUseFreshEdgesOnly() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    startRunning(flow);

    MissionInput arm = MissionInput.from(missionSnapshot(Set.of(), Set.of(InputAction.ARM_MISSILE),
        StickDirection.NEUTRAL));
    assertTrue(flow.armMissile());
    assertTrue(arm.armMissileJustPressed());
    assertFalse(flow.armMissile(), "a held arm action does not restart missile mode");

    MissionInput aim = MissionInput.from(missionSnapshot(Set.of(), Set.of(),
        new StickDirection(false, true, true, false)));
    PhaseOneScreenFlow.AimDirection direction = flow.aimDirection(aim.left(), aim.right(),
        aim.up(), aim.down());
    assertEquals(new PhaseOneScreenFlow.AimDirection(1f, 1f), direction,
        "the digital stick feeds the same world-axis aim as the arrows");

    MissionInput opposed = MissionInput.from(missionSnapshot(Set.of(), Set.of(),
        new StickDirection(false, true, true, true)));
    PhaseOneScreenFlow.AimDirection noVertical = flow.aimDirection(opposed.left(), opposed.right(),
        opposed.up(), opposed.down());
    assertEquals(new PhaseOneScreenFlow.AimDirection(1f, 0f), noVertical,
        "opposite vertical directions cancel without inventing a diagonal rule");

    MissionInput launch = MissionInput.from(missionSnapshot(Set.of(InputAction.FIRE),
        Set.of(InputAction.FIRE), StickDirection.NEUTRAL));
    assertTrue(flow.consumeMissileLaunch(launch.fireJustPressed()));
    assertFalse(flow.consumeMissileLaunch(launch.fireJustPressed()),
        "one press requests at most one missile");
    flow.missileLaunched();

    assertFalse(flow.fireTrigger(launch.fireHeld()),
        "the launch press cannot become a cannon burst while still held");
    flow.observeTrigger(false);
    assertTrue(flow.armMissile());

    MissionInput cancel = MissionInput.from(missionSnapshot(Set.of(),
        Set.of(InputAction.BACK), StickDirection.NEUTRAL));
    assertTrue(cancel.backJustPressed());
    assertTrue(flow.consumeMissileCancel(), "the back action cancels missile mode");
    assertFalse(flow.isMissileArmed());
    assertFalse(flow.consumeMissileCancel(), "back in cannon mode abandons the phase");
  }

  @Test
  void ultraIsAOneFrameEdgeAndTheStickStaysDigital() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    startRunning(flow);

    MissionInput press = MissionInput.from(missionSnapshot(Set.of(InputAction.ULTRA),
        Set.of(InputAction.ULTRA), new StickDirection(false, true, true, false)));
    FlightControls controls = flow.flightControls(press.left(), press.right(), press.up(),
        press.down(), press.ultraJustPressed());
    assertTrue(controls.activateUltra());
    assertTrue(controls.right());
    assertTrue(controls.up());
    assertEquals(1, controls.lateral(), "the stick keeps the existing digital lateral sign");

    MissionInput held = MissionInput.from(missionSnapshot(Set.of(InputAction.ULTRA), Set.of(),
        StickDirection.NEUTRAL));
    assertFalse(held.ultraJustPressed(), "holding Ultra never re-triggers it");
    assertFalse(flow.flightControls(false, false, false, false, held.ultraJustPressed())
        .activateUltra());
  }

  @Test
  void aMissionDoesNotConsumeMenuConfirmOrStartActions() {
    MissionInput menu = MissionInput.from(missionSnapshot(Set.of(),
        Set.of(InputAction.START_GAME, InputAction.CONFIRM), StickDirection.NEUTRAL));

    assertFalse(menu.fireJustPressed());
    assertFalse(menu.backJustPressed());
    assertFalse(menu.minimapJustPressed());
    assertFalse(menu.armMissileJustPressed());
    assertFalse(menu.ultraJustPressed());

    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    flow.observeTrigger(menu.fireHeld());
    assertFalse(flow.consumeStartPress(menu.fireJustPressed()),
        "a menu action cannot start the mission");
    assertTrue(flow.isIntro());
  }

  private static void startRunning(PhaseOneScreenFlow flow) {
    flow.observeTrigger(true);
    assertTrue(flow.consumeStartPress(true));
    flow.observeTrigger(false);
  }

  private static ActionSnapshot missionSnapshot(Set<InputAction> held,
      Set<InputAction> justPressed, StickDirection stick) {
    return new ActionSnapshot(held, justPressed, stick,
        new StickDirection(stick.left(), stick.right(), stick.up(), stick.down()), true);
  }
}
