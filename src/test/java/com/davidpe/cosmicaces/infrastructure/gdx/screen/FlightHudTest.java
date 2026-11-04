package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.*;

import com.davidpe.cosmicaces.domain.weapon.MissileLoadout;
import java.util.List;
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

  @Test void weaponLineAlwaysNamesTheSelectionAndMarksCannonFireSeparately() {
    assertEquals("M61 VULCAN", FlightHud.weaponLine(MissileLoadout.Weapon.CANNON, false),
        "the selected cannon stays visible without firing");
    assertEquals("M61 VULCAN · DISPARO", FlightHud.weaponLine(MissileLoadout.Weapon.CANNON, true),
        "effective fire is a separate marker, not the selection");
    assertEquals("AIM-120 AMRAAM", FlightHud.weaponLine(MissileLoadout.Weapon.MISSILE, false),
        "missile mode names the selected missile");
    assertEquals("AIM-120 AMRAAM", FlightHud.weaponLine(MissileLoadout.Weapon.MISSILE, true),
        "the missile is not a continuous-fire weapon");
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
    assertEquals("MISILES 1/2", FlightHud.missileAmmoLabel(1, 2));
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

  @Test void threatLineKeepsTheEnemyIdentityAndItsOwnEnergy() {
    assertEquals("VESPER RAIDER 65%", FlightHud.threatLine(new EnemyStatusHud.Readout(65)));
    assertEquals("BASTION B-52 100%",
        FlightHud.threatLine(new EnemyStatusHud.Readout("BASTION B-52", 100)));
    assertEquals("BASTION ESCORT 0%",
        FlightHud.threatLine(new EnemyStatusHud.Readout("BASTION ESCORT", 0)));
  }

  @Test void readoutClampsAmmoAndEnergyAndCopiesTheVisibleThreats() {
    EnemyStatusHud.Readout threat = new EnemyStatusHud.Readout(42);
    FlightHud.Readout readout = new FlightHud.Readout(12f, 150.4f, true, 125,
        MissileLoadout.Weapon.CANNON, 5, 2, null, List.of(threat));

    assertEquals(100, readout.energyPercent(), "energy never exceeds the displayable range");
    assertEquals(2, readout.missilesRemaining(), "remaining ammo never exceeds capacity");
    assertEquals(2, readout.missileCapacity());
    assertEquals(List.of(threat), readout.threats());
    assertThrows(UnsupportedOperationException.class,
        () -> readout.threats().add(new EnemyStatusHud.Readout(10)),
        "the frame snapshot is immutable");

    FlightHud.Readout empty = new FlightHud.Readout(0f, 0f, false, -5,
        MissileLoadout.Weapon.MISSILE, -3, -1, null, null);
    assertEquals(0, empty.energyPercent());
    assertEquals(0, empty.missilesRemaining());
    assertEquals(0, empty.missileCapacity());
    assertEquals(List.of(), empty.threats());
  }

  @Test void viewportAnchorsKeepHudReadingsDistributedAtTheCurrentEdges() {
    assertEquals(1760f, FlightHud.rightAlignedX(1920f, 136f));
    assertEquals(672f, FlightHud.centeredX(1440f, 96f));
    assertEquals(24f, FlightHud.rightAlignedX(180f, 200f),
        "narrow windows keep a safe left margin when content cannot fit");
  }
}
