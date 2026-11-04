package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.*;

import com.davidpe.cosmicaces.domain.weapon.GuidedMissile;
import com.davidpe.cosmicaces.domain.weapon.MissileConfig;
import com.davidpe.cosmicaces.domain.weapon.MissileLoadout;
import com.davidpe.cosmicaces.domain.weapon.MissileTarget;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlightHudTest {
  private static final MissileTarget.Reference RAIDER_REFERENCE =
      new MissileTarget.Reference(MissileTarget.Kind.VESPER, 1, 0);

  private static GuidedMissile astraMissile() {
    return new GuidedMissile(GuidedMissile.Owner.ASTRA, RAIDER_REFERENCE, MissileConfig.DEFAULTS,
        100f, 100f, 0f, 0f, 0f);
  }

  private static GuidedMissile enemyMissile(MissileTarget.Reference astraReference) {
    return new GuidedMissile(GuidedMissile.Owner.ENEMY, astraReference, MissileConfig.DEFAULTS,
        400f, 100f, 180f, 0f, 0f);
  }
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

  @Test void footerReadsOwnMissilesInFlightAndKeepsTheEmergencyLabel() {
    assertEquals("MISILES EN VUELO 0", FlightHud.missilesInFlightLabel(0));
    assertEquals("MISILES EN VUELO 3", FlightHud.missilesInFlightLabel(3));
    assertEquals("MISILES EN VUELO 0", FlightHud.missilesInFlightLabel(-2),
        "a negative count can never paint an impossible reading");
    assertEquals("!! ENERGÍA 20%", FlightHud.energyAlarmLabel(20));
    assertEquals("!! ENERGÍA 0%", FlightHud.energyAlarmLabel(-8));
    assertEquals("!! MISIL ENEMIGO A ASTRA", FlightHud.missileAlarmLabel());
  }

  @Test void countsOnlyAstraMissilesStillInFlight() {
    GuidedMissile first = astraMissile();
    GuidedMissile second = astraMissile();
    GuidedMissile retired = astraMissile();
    retired.retireAfterMission();
    GuidedMissile enemy = enemyMissile(new MissileTarget.Reference(MissileTarget.Kind.ASTRA, 0, 0));

    assertEquals(2, FlightHud.ownMissilesInFlight(List.of(first, second, retired, enemy)),
        "only the player's own active missiles count, whatever the selected weapon is");
    assertEquals(0, FlightHud.ownMissilesInFlight(List.of()));
    assertEquals(0, FlightHud.ownMissilesInFlight(null));
  }

  @Test void enemyMissileAlarmRequiresAnActiveMissileAimedAtTheCurrentAstraLife() {
    MissileTarget.Reference currentLife =
        new MissileTarget.Reference(MissileTarget.Kind.ASTRA, 0, 2);
    GuidedMissile incoming = enemyMissile(currentLife);

    assertTrue(FlightHud.enemyMissileTargetsAstra(List.of(incoming), 2));
    GuidedMissile staleLife =
        enemyMissile(new MissileTarget.Reference(MissileTarget.Kind.ASTRA, 0, 1));
    assertTrue(FlightHud.enemyMissileTargetsAstra(List.of(staleLife, incoming), 2),
        "several missiles raise the alarm if at least one targets the current life");
    assertFalse(FlightHud.enemyMissileTargetsAstra(List.of(staleLife), 2));
    assertFalse(FlightHud.enemyMissileTargetsAstra(List.of(incoming), 1),
        "a missile locked to a previous life generation cannot raise the alarm");
    assertFalse(FlightHud.enemyMissileTargetsAstra(List.of(), 2));
    assertFalse(FlightHud.enemyMissileTargetsAstra(List.of(astraMissile()), 2),
        "an Astra-owned missile is never an enemy threat");

    incoming.retireAfterMission();
    assertFalse(FlightHud.enemyMissileTargetsAstra(List.of(incoming), 2),
        "a retired missile leaves no alarm behind");

    GuidedMissile currentTarget = enemyMissile(currentLife);
    assertTrue(FlightHud.missileThreat(List.of(currentTarget), 2, true));
    assertFalse(FlightHud.missileThreat(List.of(currentTarget), 2, false),
        "the alarm clears while Astra waits for respawn");
  }

  @Test void bothEmergenciesExistOnlyDuringPlayingAndAtTheApprovedThreshold() {
    assertTrue(FlightHud.energyAlarm(25, true));
    assertTrue(FlightHud.energyAlarm(0, true));
    assertFalse(FlightHud.energyAlarm(26, true), "the threshold is inclusive at 25 %");
    assertFalse(FlightHud.energyAlarm(5, false), "no emergency outside PLAYING");

    assertTrue(FlightHud.missileAlarm(true, true));
    assertFalse(FlightHud.missileAlarm(true, false));
    assertFalse(FlightHud.missileAlarm(false, true));
  }

  @Test void blinkAlternatesIntensityAndKeepsTheClockBounded() {
    assertTrue(FlightHud.blinkBright(0f));
    assertTrue(FlightHud.blinkBright(0.49f));
    assertFalse(FlightHud.blinkBright(0.5f));
    assertTrue(FlightHud.blinkBright(1f));
    assertTrue(FlightHud.blinkBright(-1f), "an invalid phase stays on the visible bright phase");
    assertTrue(FlightHud.blinkBright(Float.NaN));

    FlightHud hud = new FlightHud();
    hud.advanceBlink(10f);
    assertEquals(0f, hud.blinkSeconds(), 0.0001f, "a long frame wraps inside one period");
    hud.advanceBlink(Float.NaN);
    hud.advanceBlink(-1f);
    hud.advanceBlink(0.5f);
    assertEquals(0.5f, hud.blinkSeconds(), 0.0001f, "invalid deltas never move the clock");
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
        MissileLoadout.Weapon.CANNON, 5, 2, -4, null, true, false, List.of(threat));

    assertEquals(100, readout.energyPercent(), "energy never exceeds the displayable range");
    assertEquals(2, readout.missilesRemaining(), "remaining ammo never exceeds capacity");
    assertEquals(2, readout.missileCapacity());
    assertEquals(0, readout.missilesInFlight(), "an impossible count is clamped");
    assertTrue(readout.astraThreatened());
    assertFalse(readout.alertsEnabled());
    assertEquals(List.of(threat), readout.threats());
    assertThrows(UnsupportedOperationException.class,
        () -> readout.threats().add(new EnemyStatusHud.Readout(10)),
        "the frame snapshot is immutable");

    FlightHud.Readout empty = new FlightHud.Readout(0f, 0f, false, -5,
        MissileLoadout.Weapon.MISSILE, -3, -1, 0, null, false, true, null);
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
