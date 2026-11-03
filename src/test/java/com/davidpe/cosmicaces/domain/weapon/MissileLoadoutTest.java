package com.davidpe.cosmicaces.domain.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MissileLoadoutTest {
  @Test
  void defaultsAndConfigurationAreValidatedAndImmutable() {
    MissileConfig defaults = MissileConfig.DEFAULTS;
    assertEquals(2, defaults.astraMissilesPerLife());
    assertEquals(1, defaults.escortMissiles());
    assertEquals(6f, defaults.fuelSeconds());
    assertEquals(1500f, defaults.maxSpeed());
    assertEquals(180f, defaults.maxTurnDegreesPerSecond());
    assertEquals(0, defaults.withAstraMissilesPerLife(0).astraMissilesPerLife());
    assertEquals(10, defaults.withAstraMissilesPerLife(10).astraMissilesPerLife());
    assertEquals(2, defaults.astraMissilesPerLife());
    assertThrows(IllegalArgumentException.class, () -> defaults.withAstraMissilesPerLife(-1));
    assertThrows(IllegalArgumentException.class, () -> defaults.withAstraMissilesPerLife(11));
    assertThrows(IllegalArgumentException.class,
        () -> new MissileConfig(2, 1, Float.NaN, 1500, 180, 0.25f, 0.5f, 180, 1200));
    assertThrows(IllegalArgumentException.class,
        () -> new MissileConfig(2, 1, 6, Float.POSITIVE_INFINITY, 180, 0.25f, 0.5f, 180, 1200));
    assertThrows(IllegalArgumentException.class,
        () -> new MissileConfig(2, 1, 1, 1500, 180, 0.5f, 0.5f, 180, 1200));
  }

  @Test
  void onlyAnArmedLaunchWithAValidTargetConsumesOneMissile() {
    MissileLoadout loadout = new MissileLoadout(2);
    assertEquals(MissileLoadout.Weapon.CANNON, loadout.weapon());
    assertFalse(loadout.consumeForLaunch(true));
    loadout.armMissile();
    assertFalse(loadout.consumeForLaunch(false));
    assertEquals(2, loadout.remaining());
    assertTrue(loadout.consumeForLaunch(true));
    loadout.armCannon();
    loadout.armMissile();
    assertEquals(1, loadout.remaining());
    assertTrue(loadout.consumeForLaunch(true));
    assertFalse(loadout.consumeForLaunch(true));
    assertEquals(0, loadout.remaining());
    loadout.restoreForNewLife();
    assertEquals(2, loadout.remaining());
    assertEquals(MissileLoadout.Weapon.CANNON, loadout.weapon());
  }

  @Test
  void zeroAndTenAreSupportedWithoutUnderflow() {
    MissileLoadout empty = new MissileLoadout(0);
    empty.armMissile();
    assertFalse(empty.consumeForLaunch(true));
    assertEquals(0, empty.remaining());
    MissileLoadout full = new MissileLoadout(10);
    full.armMissile();
    for (int i = 0; i < 10; i++) assertTrue(full.consumeForLaunch(true));
    assertFalse(full.consumeForLaunch(true));
    assertEquals(0, full.remaining());
    assertThrows(IllegalArgumentException.class, () -> new MissileLoadout(-1));
    assertThrows(IllegalArgumentException.class, () -> new MissileLoadout(11));
  }
}
