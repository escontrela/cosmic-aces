package com.davidpe.cosmicaces.infrastructure.gdx.input;

import com.badlogic.gdx.Input;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Translates the existing keyboard controls into the same logical actions as the gamepad.
 *
 * <p>The key assignments are the current ones and are not editable here: SPACE fires or starts the
 * mission, F arms a missile, P activates Ultra, M toggles the minimap, Y starts a game, Enter
 * confirms, Escape goes back and opens settings, and the arrows drive movement or menu navigation.
 * A later integration consumes the merged sample without changing these keys.
 */
public final class KeyboardInput {

  /** Minimal keyboard/pointer state needed by the translator; simulated in tests. */
  public interface KeyState {

    boolean pressed(int key);

    boolean justPressed(int key);

    /** True when any key or pointer button produced an edge this frame. */
    boolean anyButtonJustPressed();
  }

  private final KeyState keys;
  private boolean neutralRequired;

  public KeyboardInput(KeyState keys) {
    this.keys = Objects.requireNonNull(keys, "keys");
  }

  public KeyboardInput() {
    this(new GdxKeyState());
  }

  /**
   * Clears the logical state and requires every relevant key to be released before another action
   * is reported. Used on focus changes and screen transitions so a held key does not leak into the
   * new context.
   */
  public void reset() {
    neutralRequired = true;
  }

  public boolean neutralRequired() {
    return neutralRequired;
  }

  public ActionSnapshot poll() {
    if (neutralRequired) {
      if (!anyRelevantKeyPressed()) {
        neutralRequired = false;
      }
      return new ActionSnapshot(Set.of(), Set.of(), StickDirection.NEUTRAL,
          StickDirection.NEUTRAL, false);
    }

    EnumSet<InputAction> held = EnumSet.noneOf(InputAction.class);
    EnumSet<InputAction> justPressed = EnumSet.noneOf(InputAction.class);
    bind(held, justPressed, InputAction.FIRE, Input.Keys.SPACE);
    bind(held, justPressed, InputAction.ARM_MISSILE, Input.Keys.F);
    bind(held, justPressed, InputAction.ULTRA, Input.Keys.P);
    bind(held, justPressed, InputAction.MINIMAP, Input.Keys.M);
    bind(held, justPressed, InputAction.START_GAME, Input.Keys.Y);
    bind(held, justPressed, InputAction.CONFIRM, Input.Keys.ENTER);
    bind(held, justPressed, InputAction.CONFIRM, Input.Keys.NUMPAD_ENTER);
    bind(held, justPressed, InputAction.BACK, Input.Keys.ESCAPE);
    bind(held, justPressed, InputAction.SETTINGS, Input.Keys.ESCAPE);

    StickDirection stick = new StickDirection(keys.pressed(Input.Keys.LEFT),
        keys.pressed(Input.Keys.RIGHT), keys.pressed(Input.Keys.UP),
        keys.pressed(Input.Keys.DOWN));
    StickDirection stickJustPressed = new StickDirection(keys.justPressed(Input.Keys.LEFT),
        keys.justPressed(Input.Keys.RIGHT), keys.justPressed(Input.Keys.UP),
        keys.justPressed(Input.Keys.DOWN));
    return new ActionSnapshot(held, justPressed, stick, stickJustPressed,
        keys.anyButtonJustPressed());
  }

  private boolean anyRelevantKeyPressed() {
    return keys.pressed(Input.Keys.SPACE) || keys.pressed(Input.Keys.F)
        || keys.pressed(Input.Keys.P) || keys.pressed(Input.Keys.M)
        || keys.pressed(Input.Keys.Y) || keys.pressed(Input.Keys.ENTER)
        || keys.pressed(Input.Keys.NUMPAD_ENTER)
        || keys.pressed(Input.Keys.ESCAPE) || keys.pressed(Input.Keys.LEFT)
        || keys.pressed(Input.Keys.RIGHT) || keys.pressed(Input.Keys.UP)
        || keys.pressed(Input.Keys.DOWN);
  }

  private void bind(EnumSet<InputAction> held, EnumSet<InputAction> justPressed,
      InputAction action, int key) {
    if (keys.pressed(key)) {
      held.add(action);
    }
    if (keys.justPressed(key)) {
      justPressed.add(action);
    }
  }
}
