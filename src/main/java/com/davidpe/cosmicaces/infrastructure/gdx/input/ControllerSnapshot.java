package com.davidpe.cosmicaces.infrastructure.gdx.input;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One frame of gamepad state after edge detection and neutral gating.
 *
 * <p>{@code justPressedButtons} contains physical button edges only. Stick edges are kept
 * separately so a noisy axis can never be mistaken for a button press by "any button" checks.
 */
public record ControllerSnapshot(
    List<DeviceView> devices,
    DeviceView selected,
    Set<Integer> heldButtons,
    Set<Integer> justPressedButtons,
    StickDirection stick,
    StickDirection stickJustPressed,
    boolean anyButtonJustPressed,
    boolean neutralRequired) {

  public ControllerSnapshot {
    devices = devices == null ? List.of() : List.copyOf(devices);
    heldButtons = immutableButtons(heldButtons);
    justPressedButtons = immutableButtons(justPressedButtons);
    stick = stick == null ? StickDirection.NEUTRAL : stick;
    stickJustPressed =
        stickJustPressed == null ? StickDirection.NEUTRAL : stickJustPressed;
  }

  public static ControllerSnapshot empty() {
    return new ControllerSnapshot(List.of(), null, Set.of(), Set.of(), StickDirection.NEUTRAL,
        StickDirection.NEUTRAL, false, true);
  }

  public boolean heldButton(int button) {
    return heldButtons.contains(button);
  }

  public boolean justPressedButton(int button) {
    return justPressedButtons.contains(button);
  }

  /** Resolves the physical snapshot into logical actions using the given bindings. */
  public ActionSnapshot actions(ActionBindings bindings) {
    Objects.requireNonNull(bindings, "bindings");
    EnumSet<InputAction> held = EnumSet.noneOf(InputAction.class);
    EnumSet<InputAction> justPressed = EnumSet.noneOf(InputAction.class);
    bindings.asMap().forEach((action, button) -> {
      if (heldButtons.contains(button)) {
        held.add(action);
      }
      if (justPressedButtons.contains(button)) {
        justPressed.add(action);
      }
    });
    return new ActionSnapshot(held, justPressed, stick, stickJustPressed,
        anyButtonJustPressed);
  }

  private static Set<Integer> immutableButtons(Set<Integer> buttons) {
    if (buttons == null || buttons.isEmpty()) {
      return Set.of();
    }
    LinkedHashSet<Integer> copy = new LinkedHashSet<>();
    for (Integer button : buttons) {
      if (button != null && button >= 0) {
        copy.add(button);
      }
    }
    return Collections.unmodifiableSet(copy);
  }
}
