package com.davidpe.cosmicaces.infrastructure.gdx.input;

/**
 * Device-independent player actions understood by the screens.
 *
 * <p>The same action can be bound to different physical buttons per context (for example, a menu
 * confirm and the flight trigger can share a button because they are never active in the same
 * screen). Keeping the vocabulary here lets keyboard and gamepad samples be merged without any
 * screen reading a physical device directly.
 */
public enum InputAction {
  FIRE,
  ARM_MISSILE,
  ULTRA,
  MINIMAP,
  START_GAME,
  CONFIRM,
  BACK,
  SETTINGS
}
