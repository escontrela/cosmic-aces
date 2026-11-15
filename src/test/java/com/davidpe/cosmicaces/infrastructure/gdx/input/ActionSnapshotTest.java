package com.davidpe.cosmicaces.infrastructure.gdx.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Merging rules shared by keyboard and gamepad samples. */
class ActionSnapshotTest {

  @Test
  void aHeldActionSurvivesWhileEitherSourceKeepsItHeld() {
    ActionSnapshot keyboard = snapshot(Set.of(InputAction.FIRE), Set.of(), StickDirection.NEUTRAL);
    ActionSnapshot gamepad = snapshot(Set.of(InputAction.FIRE), Set.of(), StickDirection.NEUTRAL);

    assertTrue(ActionSnapshot.merge(keyboard, gamepad).held(InputAction.FIRE));
    assertTrue(ActionSnapshot.merge(keyboard, ActionSnapshot.EMPTY).held(InputAction.FIRE));
    assertFalse(ActionSnapshot.merge(ActionSnapshot.EMPTY, ActionSnapshot.EMPTY)
        .held(InputAction.FIRE));
  }

  @Test
  void twoEdgesForTheSameActionStayOneLogicalEdge() {
    ActionSnapshot keyboard =
        snapshot(Set.of(InputAction.FIRE), Set.of(InputAction.FIRE), StickDirection.NEUTRAL);
    ActionSnapshot gamepad =
        snapshot(Set.of(InputAction.FIRE), Set.of(InputAction.FIRE), StickDirection.NEUTRAL);

    ActionSnapshot merged = ActionSnapshot.merge(keyboard, gamepad);

    assertEquals(Set.of(InputAction.FIRE), merged.justPressed());
    assertTrue(merged.justPressed(InputAction.FIRE));
  }

  @Test
  void directionsAreOrCombined() {
    ActionSnapshot left =
        snapshot(Set.of(), Set.of(), new StickDirection(true, false, false, false));
    ActionSnapshot up =
        snapshot(Set.of(), Set.of(), new StickDirection(false, false, true, false));

    ActionSnapshot merged = ActionSnapshot.merge(left, up);

    assertEquals(new StickDirection(true, false, true, false), merged.stick());
  }

  @Test
  void activityFlagsAreOrCombined() {
    ActionSnapshot quiet = new ActionSnapshot(Set.of(), Set.of(), StickDirection.NEUTRAL,
        StickDirection.NEUTRAL, false);
    ActionSnapshot active = new ActionSnapshot(Set.of(), Set.of(), StickDirection.NEUTRAL,
        StickDirection.NEUTRAL, true);

    assertTrue(ActionSnapshot.merge(quiet, active).anyButtonJustPressed());
    assertFalse(ActionSnapshot.merge(quiet, quiet).anyButtonJustPressed());
  }

  private static ActionSnapshot snapshot(Set<InputAction> held, Set<InputAction> justPressed,
      StickDirection stick) {
    return new ActionSnapshot(held, justPressed, stick, StickDirection.NEUTRAL, false);
  }
}
