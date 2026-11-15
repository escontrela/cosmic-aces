package com.davidpe.cosmicaces.infrastructure.gdx.input;

import java.util.HashSet;
import java.util.Set;

/** Simulated keyboard/pointer state for the action translator. */
final class FakeKeyState implements KeyboardInput.KeyState {

  private final Set<Integer> pressed = new HashSet<>();
  private final Set<Integer> justPressed = new HashSet<>();
  private boolean anyButtonJustPressed;

  void press(int key) {
    pressed.add(key);
    justPressed.add(key);
  }

  void hold(int key) {
    pressed.add(key);
  }

  void release(int key) {
    pressed.remove(key);
  }

  void releaseAll() {
    pressed.clear();
    justPressed.clear();
    anyButtonJustPressed = false;
  }

  void clearEdges() {
    justPressed.clear();
    anyButtonJustPressed = false;
  }

  void setAnyButtonJustPressed(boolean value) {
    anyButtonJustPressed = value;
  }

  @Override
  public boolean pressed(int key) {
    return pressed.contains(key);
  }

  @Override
  public boolean justPressed(int key) {
    return justPressed.contains(key);
  }

  @Override
  public boolean anyButtonJustPressed() {
    return anyButtonJustPressed;
  }
}
