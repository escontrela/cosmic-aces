package com.davidpe.cosmicaces.infrastructure.gdx.input;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Logical actions of one frame, already independent of the physical source.
 *
 * <p>Merging ORs held and edge states per action: if both keyboard and gamepad hold the same
 * action, releasing one does not release the action; if both produce an edge in the same frame,
 * the merged sample still reports a single logical edge.
 */
public record ActionSnapshot(
    Set<InputAction> held,
    Set<InputAction> justPressed,
    StickDirection stick,
    StickDirection stickJustPressed,
    boolean anyButtonJustPressed) {

  public static final ActionSnapshot EMPTY = new ActionSnapshot(Set.of(), Set.of(),
      StickDirection.NEUTRAL, StickDirection.NEUTRAL, false);

  public ActionSnapshot {
    held = immutableActions(held);
    justPressed = immutableActions(justPressed);
    stick = stick == null ? StickDirection.NEUTRAL : stick;
    stickJustPressed =
        stickJustPressed == null ? StickDirection.NEUTRAL : stickJustPressed;
  }

  public boolean held(InputAction action) {
    return held.contains(action);
  }

  public boolean justPressed(InputAction action) {
    return justPressed.contains(action);
  }

  /** Combines any number of samples without duplicating edges. */
  public static ActionSnapshot merge(ActionSnapshot... samples) {
    EnumSet<InputAction> held = EnumSet.noneOf(InputAction.class);
    EnumSet<InputAction> justPressed = EnumSet.noneOf(InputAction.class);
    StickDirection stick = StickDirection.NEUTRAL;
    StickDirection stickJustPressed = StickDirection.NEUTRAL;
    boolean anyButton = false;
    for (ActionSnapshot sample : samples) {
      if (sample == null) {
        continue;
      }
      held.addAll(sample.held);
      justPressed.addAll(sample.justPressed);
      stick = stick.or(sample.stick);
      stickJustPressed = stickJustPressed.or(sample.stickJustPressed);
      anyButton |= sample.anyButtonJustPressed;
    }
    return new ActionSnapshot(held, justPressed, stick, stickJustPressed, anyButton);
  }

  private static Set<InputAction> immutableActions(Set<InputAction> actions) {
    if (actions == null || actions.isEmpty()) {
      return Set.of();
    }
    EnumSet<InputAction> copy = EnumSet.noneOf(InputAction.class);
    copy.addAll(actions);
    return Collections.unmodifiableSet(copy);
  }
}
