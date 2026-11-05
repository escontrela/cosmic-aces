package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionSnapshot;
import com.davidpe.cosmicaces.infrastructure.gdx.input.InputAction;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickDirection;

/**
 * Mission-specific reading of one merged keyboard/Joy-Con sample.
 *
 * <p>The shared {@link ActionSnapshot} already merges both sources and keeps one logical edge per
 * action. This immutable adapter only names the decisions PhaseOne needs, so the screen never reads
 * a physical device and the whole contract can be exercised with simulated snapshots. Held actions
 * are kept apart from edges: the cannon is sustained while aiming, missile arming, launching,
 * cancelling, Ultra and the minimap consume only a fresh press.
 */
record MissionInput(
    boolean fireHeld,
    boolean fireJustPressed,
    boolean armMissileJustPressed,
    boolean ultraJustPressed,
    boolean minimapJustPressed,
    boolean backJustPressed,
    boolean anyButtonJustPressed,
    StickDirection stick) {

  static final MissionInput EMPTY =
      new MissionInput(false, false, false, false, false, false, false,
          StickDirection.NEUTRAL);

  MissionInput {
    stick = stick == null ? StickDirection.NEUTRAL : stick;
  }

  /** Translates the shared sample; a missing sample is a safe, neutral frame. */
  static MissionInput from(ActionSnapshot snapshot) {
    if (snapshot == null) {
      return EMPTY;
    }
    return new MissionInput(
        snapshot.held(InputAction.FIRE),
        snapshot.justPressed(InputAction.FIRE),
        snapshot.justPressed(InputAction.ARM_MISSILE),
        snapshot.justPressed(InputAction.ULTRA),
        snapshot.justPressed(InputAction.MINIMAP),
        snapshot.justPressed(InputAction.BACK),
        snapshot.anyButtonJustPressed(),
        snapshot.stick());
  }

  boolean left() {
    return stick.left();
  }

  boolean right() {
    return stick.right();
  }

  boolean up() {
    return stick.up();
  }

  boolean down() {
    return stick.down();
  }
}
