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

  @Test void consoleWeaponReadingKeepsTheSelectedMissileVisibleWithoutFiring() {
    assertEquals("AIM-120 AMRAAM", FlightHud.weaponNameLabel(true, false));
    assertEquals("AIM-120 AMRAAM", FlightHud.weaponNameLabel(true, true));
    assertEquals("M61 VULCAN", FlightHud.weaponNameLabel(false, true));
    assertEquals("", FlightHud.weaponNameLabel(false, false));
  }

  @Test void consoleReadingsKeepTheExistingHeadingAndSpeedFormats() {
    assertEquals("RUMBO N 000", FlightHud.headingLine(0f));
    assertEquals("RUMBO NE 045", FlightHud.headingLine(45f));
    assertEquals("RUMBO O 270", FlightHud.headingLine(-90f));
    assertEquals("RUMBO N 359", FlightHud.headingLine(-1f));
    assertEquals("VELOCIDAD 150 KM/H", FlightHud.velocityLine(150.4f));
    assertEquals("VELOCIDAD 0 KM/H", FlightHud.velocityLine(0.2f));
  }

  @Test void missileReadoutExplainsAmmoAndWhetherAnEnemyIsLocked() {
    assertEquals("MISILES 2/2", FlightHud.missileAmmoLabel(2, 2));
    assertEquals("Sin misiles", FlightHud.missileAmmoLabel(0, 2));
    assertEquals("Sin objetivo", FlightHud.missileTargetLabel(null));
    assertEquals("Sin objetivo", FlightHud.missileTargetLabel(" "));
    assertEquals("OBJETIVO Ghostwing #17", FlightHud.missileTargetLabel("Ghostwing #17"));
  }

  @Test void energyLabelRendersThePercentageClampedToZeroHundred() {
    assertEquals("ENERGÍA 100%", FlightHud.energyLabel(100));
    assertEquals("ENERGÍA 0%", FlightHud.energyLabel(0));
    assertEquals("ENERGÍA 73%", FlightHud.energyLabel(73));
    assertEquals("ENERGÍA 0%", FlightHud.energyLabel(-5), "negative energy clamps to zero");
    assertEquals("ENERGÍA 100%", FlightHud.energyLabel(125), "overflow clamps to one hundred");
  }

  @Test void missileHelpLineStaysAvailableForTheConsoleFooter() {
    assertEquals("ESPACIO LANZAR · ESC RÁFAGAS", FlightHud.missileHelpLabel());
  }

  @Test void viewportAnchorsKeepHudReadingsDistributedAtTheCurrentEdges() {
    assertEquals(1760f, FlightHud.rightAlignedX(1920f, 136f));
    assertEquals(672f, FlightHud.centeredX(1440f, 96f));
    assertEquals(24f, FlightHud.rightAlignedX(180f, 200f),
        "narrow windows keep a safe left margin when content cannot fit");
  }
}
