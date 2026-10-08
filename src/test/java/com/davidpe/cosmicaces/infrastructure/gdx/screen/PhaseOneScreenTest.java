package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhaseOneScreenTest {

  @Test
  void astraStaysVisibleWithoutAProtectionWindow() {
    assertTrue(PhaseOneScreen.astraVisible(0f));
    assertTrue(PhaseOneScreen.astraVisible(-0.5f));
    assertTrue(PhaseOneScreen.astraVisible(Float.NaN));
    assertTrue(PhaseOneScreen.astraVisible(Float.POSITIVE_INFINITY));
  }

  @Test
  void astraAlternatesVisibilityWhileInvulnerable() {
    // Mid-interval samples avoid the float boundary between consecutive 0.1 s slots.
    assertTrue(PhaseOneScreen.astraVisible(1.85f));
    assertFalse(PhaseOneScreen.astraVisible(1.95f));
    assertNotEquals(PhaseOneScreen.astraVisible(1.85f), PhaseOneScreen.astraVisible(1.95f));

    boolean sawVisible = false;
    boolean sawHidden = false;
    for (float remaining = 0.05f; remaining < 2f; remaining += 0.1f) {
      if (PhaseOneScreen.astraVisible(remaining)) {
        sawVisible = true;
      } else {
        sawHidden = true;
      }
    }
    assertTrue(sawVisible, "Astra must be visible during part of the protection window");
    assertTrue(sawHidden, "Astra must blink off during the protection window");
  }
}
