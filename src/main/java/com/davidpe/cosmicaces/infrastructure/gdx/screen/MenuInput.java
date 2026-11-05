package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionSnapshot;
import com.davidpe.cosmicaces.infrastructure.gdx.input.InputAction;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickDirection;

/**
 * Menu-specific reading of one merged keyboard/Joy-Con sample.
 *
 * <p>The shared {@link ActionSnapshot} already merges both sources and keeps one logical edge per
 * action. This immutable adapter only names the decisions the welcome lobby, the settings pages and
 * the Hall of Fame need, so those screens never read a physical device and the navigation sequences
 * can be exercised with simulated snapshots. It reports only fresh edges: a button or stick held
 * across a transition never repeats an action.
 *
 * <p>The video settings page keeps its existing F/W/R keyboard behavior and gives the same functions
 * a gamepad route through the mission actions, reinterpreted only in that page: arming changes the
 * destination, Ultra restores the window and the minimap refreshes the mode list. Those actions are
 * never consumed as mission actions while a menu is open.
 */
record MenuInput(
    boolean startGame,
    boolean confirm,
    boolean back,
    boolean settings,
    boolean up,
    boolean down,
    boolean left,
    boolean right,
    boolean videoToggleDestination,
    boolean videoRestoreWindow,
    boolean videoRefreshModes) {

  static final MenuInput EMPTY = new MenuInput(false, false, false, false, false, false, false,
      false, false, false, false);

  /** Translates the shared sample; a missing sample is a safe, neutral frame. */
  static MenuInput from(ActionSnapshot snapshot) {
    if (snapshot == null) {
      return EMPTY;
    }
    StickDirection edges = snapshot.stickJustPressed();
    return new MenuInput(
        snapshot.justPressed(InputAction.START_GAME),
        snapshot.justPressed(InputAction.CONFIRM),
        snapshot.justPressed(InputAction.BACK),
        snapshot.justPressed(InputAction.SETTINGS),
        edges.up(),
        edges.down(),
        edges.left(),
        edges.right(),
        snapshot.justPressed(InputAction.ARM_MISSILE),
        snapshot.justPressed(InputAction.ULTRA),
        snapshot.justPressed(InputAction.MINIMAP));
  }
}
