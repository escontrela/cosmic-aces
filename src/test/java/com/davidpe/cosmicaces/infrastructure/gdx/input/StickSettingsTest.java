package com.davidpe.cosmicaces.infrastructure.gdx.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure stick orientation and dead-zone checks; no window or controller is required. */
class StickSettingsTest {

  @Test
  void valuesInsideOrAtTheDeadZoneAreNeutral() {
    StickSettings settings = new StickSettings(false, false, false, 0.25f);

    assertTrue(settings.translate(0f, 0f).neutral());
    assertTrue(settings.translate(0.1f, 0.1f).neutral());
    assertTrue(settings.translate(0.25f, 0f).neutral(), "the boundary is neutral");
    assertEquals(new StickDirection(false, true, false, false),
        settings.translate(0.26f, 0f));
  }

  @Test
  void diagonalMovementKeepsBothDigitalDirections() {
    StickSettings settings = new StickSettings(false, false, false, 0.2f);

    assertEquals(new StickDirection(false, true, true, false),
        settings.translate(0.8f, 0.8f));
    assertEquals(new StickDirection(true, false, false, true),
        settings.translate(-0.8f, -0.8f));
  }

  @Test
  void swapsAxesAndInvertsThemIndependently() {
    StickSettings swapped = new StickSettings(true, false, false, 0.2f);
    assertEquals(new StickDirection(false, false, true, false),
        swapped.translate(0.8f, 0f), "raw X becomes vertical up after the swap");

    StickSettings inverted = new StickSettings(false, true, true, 0.2f);
    assertEquals(new StickDirection(true, false, false, true),
        inverted.translate(0.8f, 0.8f));
  }

  @Test
  void nonFiniteAxesAreNeutral() {
    StickSettings settings = StickSettings.DEFAULTS;

    assertTrue(settings.translate(Float.NaN, 0f).neutral());
    assertTrue(settings.translate(0f, Float.POSITIVE_INFINITY).neutral());
  }

  @Test
  void deadZoneIsBoundedAndFinite() {
    assertThrows(IllegalArgumentException.class,
        () -> new StickSettings(false, false, false, -0.01f));
    assertThrows(IllegalArgumentException.class,
        () -> new StickSettings(false, false, false, 0.91f));
    assertThrows(IllegalArgumentException.class,
        () -> new StickSettings(false, false, false, Float.NaN));
  }

  @Test
  void directionHelpersExposeStableSignsForMenusAndAim() {
    StickDirection direction = new StickDirection(true, false, true, false);

    assertEquals(-1, direction.horizontal());
    assertEquals(1, direction.vertical());
    assertTrue(direction.any());
    assertFalse(StickDirection.NEUTRAL.any());
  }
}
