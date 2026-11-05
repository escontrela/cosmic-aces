package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionSnapshot;
import com.davidpe.cosmicaces.infrastructure.gdx.input.InputAction;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickDirection;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.HallOfFameEntryInput.Action;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.HallOfFameEntryInput.Keys;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pure edge/arming checks of the Hall of Fame initials input; no window is required. */
class HallOfFameEntryInputTest {

  private static final Keys NONE = new Keys(false, false, false, false, false, false);

  @Test
  void staysDisarmedWhileAKeyFromTheMissionIsStillHeld() {
    HallOfFameEntryInput input = new HallOfFameEntryInput();

    assertEquals(List.of(), input.poll(new Keys(false, false, false, false, false, true)));
    assertFalse(input.armed(), "SPACE from the ending shot must be released first");
    assertEquals(List.of(), input.poll(new Keys(true, false, false, false, false, true)));
    assertFalse(input.armed());

    assertEquals(List.of(), input.poll(NONE));
    assertTrue(input.armed());
    assertEquals(List.of(), input.poll(NONE), "releasing keys never produces an action");
  }

  @Test
  void oneEdgeProducesOneActionAndAHeldKeyDoesNotRepeat() {
    HallOfFameEntryInput input = armedInput();

    assertEquals(List.of(Action.NEXT_POSITION), input.poll(new Keys(false, true, false, false, false, false)));
    assertEquals(List.of(), input.poll(new Keys(false, true, false, false, false, false)));
    assertEquals(List.of(), input.poll(NONE));
    assertEquals(List.of(Action.NEXT_POSITION), input.poll(new Keys(false, true, false, false, false, false)));
  }

  @Test
  void mapsTheFourArrowsAndEnterToTheirActions() {
    HallOfFameEntryInput input = armedInput();

    assertEquals(List.of(Action.PREVIOUS_POSITION), input.poll(new Keys(true, false, false, false, false, false)));
    assertEquals(List.of(Action.NEXT_POSITION), input.poll(new Keys(false, true, false, false, false, false)));
    assertEquals(List.of(Action.PREVIOUS_LETTER), input.poll(new Keys(false, false, true, false, false, false)));
    assertEquals(List.of(Action.NEXT_LETTER), input.poll(new Keys(false, false, false, true, false, false)));
    assertEquals(List.of(Action.CONFIRM), input.poll(new Keys(false, false, false, false, true, false)));
  }

  @Test
  void confirmIsConsumedOnceEvenIfEnterStaysHeld() {
    HallOfFameEntryInput input = armedInput();
    Keys enter = new Keys(false, false, false, false, true, false);

    assertEquals(List.of(Action.CONFIRM), input.poll(enter));
    assertEquals(List.of(), input.poll(enter), "a held Enter must not confirm twice");
    assertEquals(List.of(), input.poll(NONE));
    assertEquals(List.of(Action.CONFIRM), input.poll(enter));
  }

  @Test
  void aFrameWithSeveralNewEdgesReturnsTheirStableOrder() {
    HallOfFameEntryInput input = armedInput();

    assertEquals(List.of(Action.NEXT_POSITION, Action.NEXT_LETTER),
        input.poll(new Keys(false, true, false, true, false, false)));
  }

  @Test
  void spaceAloneNeverConfirmsEvenAfterArming() {
    HallOfFameEntryInput input = armedInput();

    assertEquals(List.of(), input.poll(new Keys(false, false, false, false, false, true)));
    assertEquals(List.of(), input.poll(NONE));
    assertEquals(List.of(), input.poll(new Keys(false, false, false, false, false, true)));
  }

  @Test
  void mergedMissionInputsAndTheStickBlockArmingUntilReleased() {
    HallOfFameEntryInput input = new HallOfFameEntryInput();

    assertEquals(List.of(), input.poll(Keys.from(snapshot(Set.of(InputAction.FIRE),
        Set.of(), new StickDirection(true, false, false, false), StickDirection.NEUTRAL))));
    assertFalse(input.armed(), "the shot that ended the mission must be released first");

    assertEquals(List.of(), input.poll(Keys.from(snapshot(Set.of(), Set.of(),
        StickDirection.NEUTRAL, StickDirection.NEUTRAL))));
    assertTrue(input.armed());
  }

  @Test
  void mergedConfirmEdgeConfirmsOnceEvenWhenBothSourcesPressedIt() {
    HallOfFameEntryInput input = armedSnapshotInput();
    ActionSnapshot keyboard = snapshot(Set.of(InputAction.CONFIRM),
        Set.of(InputAction.CONFIRM), StickDirection.NEUTRAL, StickDirection.NEUTRAL);
    ActionSnapshot gamepad = snapshot(Set.of(InputAction.CONFIRM),
        Set.of(InputAction.CONFIRM), StickDirection.NEUTRAL, StickDirection.NEUTRAL);
    Keys confirm = Keys.from(ActionSnapshot.merge(keyboard, gamepad));

    assertEquals(List.of(Action.CONFIRM), input.poll(confirm));
    assertEquals(List.of(), input.poll(confirm), "a held merged confirm must not confirm twice");
    assertEquals(List.of(), input.poll(Keys.from(snapshot(Set.of(), Set.of(),
        StickDirection.NEUTRAL, StickDirection.NEUTRAL))));
    assertEquals(List.of(Action.CONFIRM), input.poll(confirm));
  }

  @Test
  void mergedFireAloneNeverConfirms() {
    HallOfFameEntryInput input = armedSnapshotInput();
    Keys fire = Keys.from(snapshot(Set.of(InputAction.FIRE), Set.of(InputAction.FIRE),
        StickDirection.NEUTRAL, StickDirection.NEUTRAL));

    assertEquals(List.of(), input.poll(fire));
    assertEquals(List.of(), input.poll(fire));
  }

  @Test
  void mergedStickEdgesMoveOnceAndOtherHeldActionsBlockArming() {
    HallOfFameEntryInput input = armedSnapshotInput();
    Keys right = Keys.from(snapshot(Set.of(), Set.of(),
        new StickDirection(false, true, false, false),
        new StickDirection(false, true, false, false)));

    assertEquals(List.of(Action.NEXT_POSITION), input.poll(right));
    assertEquals(List.of(), input.poll(right), "a held stick must not repeat the cursor move");

    HallOfFameEntryInput other = new HallOfFameEntryInput();
    assertEquals(List.of(), other.poll(Keys.from(snapshot(Set.of(InputAction.ULTRA),
        Set.of(), StickDirection.NEUTRAL, StickDirection.NEUTRAL))));
    assertFalse(other.armed(), "any mission action held from the flight must be released");
  }

  private static HallOfFameEntryInput armedSnapshotInput() {
    HallOfFameEntryInput input = new HallOfFameEntryInput();
    input.poll(Keys.from(snapshot(Set.of(), Set.of(), StickDirection.NEUTRAL,
        StickDirection.NEUTRAL)));
    assertTrue(input.armed());
    return input;
  }

  private static ActionSnapshot snapshot(Set<InputAction> held, Set<InputAction> justPressed,
      StickDirection stick, StickDirection stickEdges) {
    return new ActionSnapshot(held, justPressed, stick, stickEdges, false);
  }

  private static HallOfFameEntryInput armedInput() {
    HallOfFameEntryInput input = new HallOfFameEntryInput();
    input.poll(NONE);
    assertTrue(input.armed());
    return input;
  }
}
