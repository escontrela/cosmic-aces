package com.davidpe.cosmicaces.infrastructure.gdx.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;

/** Existing keyboard controls translated to logical actions and digital directions. */
class KeyboardInputTest {

  @Test
  void mapsTheCurrentKeysToActions() {
    FakeKeyState keys = new FakeKeyState();
    keys.press(Input.Keys.SPACE);
    keys.press(Input.Keys.F);
    keys.press(Input.Keys.P);
    keys.press(Input.Keys.M);
    keys.press(Input.Keys.Y);
    keys.press(Input.Keys.ENTER);
    keys.setAnyButtonJustPressed(true);
    KeyboardInput input = new KeyboardInput(keys);

    ActionSnapshot snapshot = input.poll();

    assertTrue(snapshot.held(InputAction.FIRE));
    assertTrue(snapshot.held(InputAction.ARM_MISSILE));
    assertTrue(snapshot.held(InputAction.ULTRA));
    assertTrue(snapshot.held(InputAction.MINIMAP));
    assertTrue(snapshot.held(InputAction.START_GAME));
    assertTrue(snapshot.held(InputAction.CONFIRM));
    assertTrue(snapshot.justPressed(InputAction.FIRE));
    assertTrue(snapshot.anyButtonJustPressed());
  }

  @Test
  void escapeProducesBothBackAndSettingsForContextualUse() {
    FakeKeyState keys = new FakeKeyState();
    keys.press(Input.Keys.ESCAPE);
    ActionSnapshot snapshot = new KeyboardInput(keys).poll();

    assertTrue(snapshot.held(InputAction.BACK));
    assertTrue(snapshot.held(InputAction.SETTINGS));
    assertTrue(snapshot.justPressed(InputAction.BACK));
    assertTrue(snapshot.justPressed(InputAction.SETTINGS));
  }

  @Test
  void arrowsBecomeDigitalStickDirections() {
    FakeKeyState keys = new FakeKeyState();
    keys.press(Input.Keys.LEFT);
    keys.press(Input.Keys.UP);
    ActionSnapshot snapshot = new KeyboardInput(keys).poll();

    assertEquals(new StickDirection(true, false, true, false), snapshot.stick());
    assertEquals(new StickDirection(true, false, true, false), snapshot.stickJustPressed());
  }

  @Test
  void anUnmappedKeyStillCountsAsActivity() {
    FakeKeyState keys = new FakeKeyState();
    keys.press(Input.Keys.Q);
    keys.setAnyButtonJustPressed(true);
    ActionSnapshot snapshot = new KeyboardInput(keys).poll();

    assertTrue(snapshot.anyButtonJustPressed());
    assertTrue(snapshot.held().isEmpty());
    assertTrue(snapshot.justPressed().isEmpty());
  }

  @Test
  void releasingAKeyClearsHeldAndEdge() {
    FakeKeyState keys = new FakeKeyState();
    keys.press(Input.Keys.SPACE);
    KeyboardInput input = new KeyboardInput(keys);
    assertTrue(input.poll().held(InputAction.FIRE));

    keys.release(Input.Keys.SPACE);
    keys.clearEdges();
    ActionSnapshot released = input.poll();
    assertFalse(released.held(InputAction.FIRE));
    assertFalse(released.justPressed(InputAction.FIRE));
  }

  @Test
  void resetRequiresReleasingAKeyHeldAcrossAContextChange() {
    FakeKeyState keys = new FakeKeyState();
    keys.press(Input.Keys.SPACE);
    KeyboardInput input = new KeyboardInput(keys);
    assertTrue(input.poll().held(InputAction.FIRE));

    input.reset();
    assertTrue(input.neutralRequired());
    assertTrue(input.poll().held().isEmpty());
    assertTrue(input.poll().held().isEmpty(), "a held key is ignored while waiting for neutral");

    keys.release(Input.Keys.SPACE);
    keys.clearEdges();
    assertTrue(input.poll().held().isEmpty());
    assertFalse(input.neutralRequired());

    keys.press(Input.Keys.SPACE);
    assertTrue(input.poll().justPressed(InputAction.FIRE));
  }
}
