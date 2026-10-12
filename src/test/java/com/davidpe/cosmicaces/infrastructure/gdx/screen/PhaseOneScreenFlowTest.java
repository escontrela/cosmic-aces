package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
  void stageAccessorsReflectTheTransitions() {
    PhaseOneScreenFlow flow = new PhaseOneScreenFlow();
    assertEquals(PhaseOneScreenFlow.Stage.INTRO, flow.stage());
    flow.observeSpace(true);
    flow.consumeStartPress(true);
    assertEquals(PhaseOneScreenFlow.Stage.PLAYING, flow.stage());
    flow.showResult();
    assertEquals(PhaseOneScreenFlow.Stage.RESULT, flow.stage());
  }
}
