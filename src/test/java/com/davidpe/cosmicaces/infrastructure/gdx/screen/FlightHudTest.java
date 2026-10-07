package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FlightHudTest {
  @Test void compassUsesSpanishCardinalAndIntercardinalLetters() {
    assertEquals("N", FlightHud.headingLabel(0f));
    assertEquals("NE", FlightHud.headingLabel(45f));
    assertEquals("E", FlightHud.headingLabel(90f));
    assertEquals("SE", FlightHud.headingLabel(135f));
    assertEquals("S", FlightHud.headingLabel(180f));
    assertEquals("SO", FlightHud.headingLabel(-135f));
    assertEquals("O", FlightHud.headingLabel(-90f));
    assertEquals("NO", FlightHud.headingLabel(-45f));
    assertEquals(359, FlightHud.headingDegrees(-1f));
  }
}
