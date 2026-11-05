package com.davidpe.cosmicaces.infrastructure.gdx.input;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Screen context that decides which actions must stay on distinct physical buttons.
 *
 * <p>A button can be reused by actions that never act in the same context (for example the flight
 * trigger and the menu confirm). Inside one context, two incompatible actions sharing a button would
 * execute together from a single press, so settings rejects that combination. {@code BACK} and
 * {@code SETTINGS} may share a button in menus because the keyboard already uses ESC for both and
 * they perform the same navigation gesture. The video page reuses three mission actions for its own
 * auxiliary controls, so it declares them together with confirm to keep that page unambiguous.
 */
public enum ActionContext {

  /** Mission controls consumed together while flying. */
  MISSION(EnumSet.of(InputAction.FIRE, InputAction.ARM_MISSILE, InputAction.ULTRA,
      InputAction.MINIMAP, InputAction.BACK)),

  /** Welcome, settings and Hall of Fame navigation. */
  MENU(EnumSet.of(InputAction.CONFIRM, InputAction.START_GAME, InputAction.BACK,
      InputAction.SETTINGS)),

  /**
   * Video settings page, which reinterprets the mission buttons as its own auxiliary controls.
   * Confirm and those three actions must stay distinct there even though they belong to different
   * contexts elsewhere.
   */
  VIDEO(EnumSet.of(InputAction.CONFIRM, InputAction.ARM_MISSILE, InputAction.ULTRA,
      InputAction.MINIMAP));

  private final Set<InputAction> actions;

  ActionContext(Set<InputAction> actions) {
    this.actions = Collections.unmodifiableSet(actions);
  }

  public Set<InputAction> actions() {
    return actions;
  }

  public boolean contains(InputAction action) {
    return actions.contains(action);
  }

  /** True for the one shared navigation gesture the existing keyboard already uses. */
  boolean allowsSharedGesture(InputAction first, InputAction second) {
    return this == MENU
        && ((first == InputAction.BACK && second == InputAction.SETTINGS)
            || (first == InputAction.SETTINGS && second == InputAction.BACK));
  }
}
