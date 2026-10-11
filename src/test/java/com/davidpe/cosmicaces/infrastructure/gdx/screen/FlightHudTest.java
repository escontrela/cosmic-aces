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

  @Test void weaponLabelShowsTheActiveGunOnlyWhileFiring() {
    assertEquals("M61 VULCAN", FlightHud.weaponLabel(true));
    assertEquals("", FlightHud.weaponLabel(false));
  }

  @Test void energyLabelRendersThePercentageClampedToZeroHundred() {
    assertEquals("ENERGIA 100%", FlightHud.energyLabel(100));
    assertEquals("ENERGIA 0%", FlightHud.energyLabel(0));
    assertEquals("ENERGIA 73%", FlightHud.energyLabel(73));
    assertEquals("ENERGIA 0%", FlightHud.energyLabel(-5), "negative energy clamps to zero");
    assertEquals("ENERGIA 100%", FlightHud.energyLabel(125), "overflow clamps to one hundred");
  }

  @Test void viewportAnchorsKeepHudReadingsDistributedAtTheCurrentEdges() {
    assertEquals(1760f, FlightHud.rightAlignedX(1920f, 136f));
    assertEquals(672f, FlightHud.centeredX(1440f, 96f));
    assertEquals(24f, FlightHud.rightAlignedX(180f, 200f),
        "narrow windows keep a safe left margin when content cannot fit");
  }
}
