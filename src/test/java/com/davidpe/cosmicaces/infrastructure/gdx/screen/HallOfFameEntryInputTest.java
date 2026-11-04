package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.infrastructure.gdx.screen.HallOfFameEntryInput.Action;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.HallOfFameEntryInput.Keys;
import java.util.List;
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

  private static HallOfFameEntryInput armedInput() {
    HallOfFameEntryInput input = new HallOfFameEntryInput();
    input.poll(NONE);
    assertTrue(input.armed());
    return input;
  }
}
