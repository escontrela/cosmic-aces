package com.davidpe.cosmicaces.infrastructure.gdx.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/**
 * Keyboard and pointer state read through LibGDX. It is the only keyboard adapter that touches
 * {@link Gdx}; the action translation stays in {@link KeyboardInput} and is unit tested with a
 * simulated state.
 */
public final class GdxKeyState implements KeyboardInput.KeyState {

  private static final int POINTER_BUTTONS = 5;

  @Override
  public boolean pressed(int key) {
    return Gdx.input.isKeyPressed(key);
  }

  @Override
  public boolean justPressed(int key) {
    return Gdx.input.isKeyJustPressed(key);
  }

  @Override
  public boolean anyButtonJustPressed() {
    for (int key = 0; key < Input.Keys.MAX_KEYCODE; key++) {
      if (Gdx.input.isKeyJustPressed(key)) {
        return true;
      }
    }
    for (int button = 0; button < POINTER_BUTTONS; button++) {
      if (Gdx.input.isButtonJustPressed(button)) {
        return true;
      }
    }
    return false;
  }
}
