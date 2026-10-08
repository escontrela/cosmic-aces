package com.davidpe.cosmicaces.domain.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShipCombatStateTest {

  private static final float EPS = 0.0001f;

  @Test
  void astraLosesOnePointEveryTenImpacts() {
    ShipCombatState astra = ShipCombatState.astra();

    for (int impact = 0; impact < 9; impact++) {
      assertFalse(astra.receiveProjectileHit());
    }
    assertEquals(100, astra.energyPercent());

    assertTrue(astra.receiveProjectileHit(), "the tenth impact must damage");
    assertEquals(99, astra.energyPercent());
    for (int impact = 0; impact < 9; impact++) {
      assertFalse(astra.receiveProjectileHit());
    }
    assertEquals(99, astra.energyPercent());
    assertTrue(astra.receiveProjectileHit());
    assertEquals(98, astra.energyPercent());
  }

  @Test
  void astraReachesZeroAfterAThousandImpactsAndClamps() {
    ShipCombatState astra = ShipCombatState.astra();

    for (int impact = 0; impact < 1000; impact++) {
      astra.receiveProjectileHit();
    }

    assertEquals(0, astra.energyPercent());
    for (int impact = 0; impact < 30; impact++) {
      astra.receiveProjectileHit();
    }
    assertEquals(0, astra.energyPercent(), "energy must clamp at zero");
  }

  @Test
  void vesperLosesThirtyFivePointsEveryTenImpacts() {
    ShipCombatState vesper = ShipCombatState.vesper();

    for (int impact = 0; impact < 9; impact++) {
      vesper.receiveProjectileHit();
    }
    assertEquals(100, vesper.energyPercent());

    vesper.receiveProjectileHit();
    assertEquals(65, vesper.energyPercent());
    for (int impact = 0; impact < 10; impact++) {
      vesper.receiveProjectileHit();
    }
    assertEquals(30, vesper.energyPercent());
    for (int impact = 0; impact < 10; impact++) {
      vesper.receiveProjectileHit();
    }
    assertEquals(0, vesper.energyPercent());
  }

  @Test
  void residualImpactsSurviveFramesButResetOnRespawn() {
    ShipCombatState vesper = ShipCombatState.vesper();
    for (int impact = 0; impact < 9; impact++) {
      vesper.receiveProjectileHit();
    }
    assertEquals(100, vesper.energyPercent(), "nine impacts do not damage yet");
    vesper.receiveProjectileHit();
    assertEquals(65, vesper.energyPercent(), "the residual counter was kept across frames");
    for (int impact = 0; impact < 9; impact++) {
      vesper.receiveProjectileHit();
    }

    vesper.destroy();
    assertFalse(vesper.readyToRespawn());
    vesper.advance(5f);
    assertTrue(vesper.readyToRespawn());
    vesper.respawn();
    assertEquals(100, vesper.energyPercent());
    for (int impact = 0; impact < 9; impact++) {
      assertFalse(vesper.receiveProjectileHit(),
          "respawn must clear the residual impacts");
    }
  }

  @Test
  void destroyIsIdempotentAndStartsTheDeathTimer() {
    ShipCombatState astra = ShipCombatState.astra();
    astra.destroy();
    astra.destroy();

    assertTrue(astra.isDestroyed());
    assertEquals(0f, astra.secondsSinceDeath(), EPS);
    assertFalse(astra.canAct());
    assertFalse(astra.canBeHit());
    assertFalse(astra.receiveProjectileHit(), "a wreck takes no damage");

    astra.advance(1.5f);
    assertEquals(1.5f, astra.secondsSinceDeath(), EPS);
    assertFalse(astra.readyToRespawn(), "3 s have not elapsed");
    astra.advance(1.5f);
    assertTrue(astra.readyToRespawn());
  }

  @Test
  void respawnGrantsInvulnerabilityWithIndependentTimer() {
    ShipCombatState astra = ShipCombatState.astra();
    astra.destroy();
    astra.advance(3f);
    int generationBefore = astra.lifeGeneration();
    astra.respawn();

    assertEquals(generationBefore + 1, astra.lifeGeneration());
    assertTrue(astra.isInvulnerable());
    assertEquals(100, astra.energyPercent());
    assertTrue(astra.canAct(), "an invulnerable ship may still fly");
    assertFalse(astra.canBeHit(), "an invulnerable ship takes no damage");
    assertFalse(astra.receiveProjectileHit());

    astra.advance(1.9f);
    assertTrue(astra.isInvulnerable());
    astra.advance(0.2f);
    assertFalse(astra.isInvulnerable());
    assertTrue(astra.canBeHit());
    assertEquals(0f, astra.invulnerabilityRemaining(), EPS);
  }

  @Test
  void vesperRespawnsActiveWithoutInvulnerabilityAfterFiveSeconds() {
    ShipCombatState vesper = ShipCombatState.vesper();
    vesper.destroy();
    vesper.advance(4.99f);
    assertFalse(vesper.readyToRespawn());
    vesper.advance(0.01f);
    assertTrue(vesper.readyToRespawn());

    vesper.respawn();
    assertFalse(vesper.isInvulnerable());
    assertTrue(vesper.canBeHit());
    assertEquals(100, vesper.energyPercent());
  }

  @Test
  void invalidDeltasDoNotAdvanceTimers() {
    ShipCombatState astra = ShipCombatState.astra();
    astra.destroy();
    astra.advance(0f);
    astra.advance(-1f);
    astra.advance(Float.NaN);
    astra.advance(Float.POSITIVE_INFINITY);
    assertEquals(0f, astra.secondsSinceDeath(), EPS);
  }

  @Test
  void aNewLifeCannotBeDestroyedByStaleEnergy() {
    ShipCombatState astra = ShipCombatState.astra();
    astra.destroy();
    astra.advance(3f);
    astra.respawn();
    assertTrue(astra.isInvulnerable(), "Astra comes back with two seconds of protection");
    assertEquals(100, astra.energyPercent());
    assertFalse(astra.isDestroyed());
    for (int impact = 0; impact < 999; impact++) {
      astra.receiveProjectileHit(); // ignored while invulnerable
    }
    assertEquals(100, astra.energyPercent(), "the protection window absorbs the impacts");

    astra.advance(2f); // protection ends
    for (int impact = 0; impact < 999; impact++) {
      astra.receiveProjectileHit();
    }
    assertEquals(1, astra.energyPercent(), "the fresh life needs its own thousand impacts");
  }

  @Test
  void rejectsInvalidConfiguration() {
    assertThrows(IllegalArgumentException.class,
        () -> new ShipCombatState(0, 1, 3f, 2f));
    assertThrows(IllegalArgumentException.class,
        () -> new ShipCombatState(10, 0, 3f, 2f));
    assertThrows(IllegalArgumentException.class,
        () -> new ShipCombatState(10, 1, -1f, 2f));
    assertThrows(IllegalArgumentException.class,
        () -> new ShipCombatState(10, 1, 3f, Float.NaN));
  }
}
