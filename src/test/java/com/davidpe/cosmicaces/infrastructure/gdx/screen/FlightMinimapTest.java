package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlightMinimapTest {
  @Test void startsHiddenAndTogglesVisibilityOncePerCall() {
    FlightMinimap minimap = new FlightMinimap();

    assertFalse(minimap.isVisible());
    minimap.toggle();
    assertTrue(minimap.isVisible());
    minimap.toggle();
    assertFalse(minimap.isVisible());
  }
}
