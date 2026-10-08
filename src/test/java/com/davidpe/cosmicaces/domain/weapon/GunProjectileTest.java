package com.davidpe.cosmicaces.domain.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GunProjectileTest {

  private static final float EPS = 0.001f;

  @Test
  void advancesAlongTheNormalizedFrozenDirection() {
    GunProjectile projectile = new GunProjectile(0f, 0f, 3f, 4f, 100f, 1000f);
    assertEquals(0.6f, projectile.forwardX(), EPS);
    assertEquals(0.8f, projectile.forwardY(), EPS);

    projectile.advance(1f);

    assertEquals(60f, projectile.x(), EPS);
    assertEquals(80f, projectile.y(), EPS);
    assertEquals(100f, projectile.travelled(), EPS);
    assertFalse(projectile.expired());
  }

  @Test
  void coversCardinalAndDiagonalDirections() {
    GunProjectile east = new GunProjectile(10f, 20f, 1f, 0f, 10f, 100f);
    east.advance(1f);
    assertEquals(20f, east.x(), EPS);
    assertEquals(20f, east.y(), EPS);

    GunProjectile south = new GunProjectile(10f, 20f, 0f, -1f, 10f, 100f);
    south.advance(2f);
    assertEquals(10f, south.x(), EPS);
    assertEquals(0f, south.y(), EPS);

    GunProjectile diagonal = new GunProjectile(0f, 0f, 1f, 1f, 10f, 100f);
    diagonal.advance(1f);
    assertEquals(7.0711f, diagonal.x(), EPS);
    assertEquals(7.0711f, diagonal.y(), EPS);
  }

  @Test
  void clampsFinalAdvanceToRangeAndExpiresExactlyAtTheLimit() {
    GunProjectile projectile = new GunProjectile(0f, 0f, 1f, 0f, 100f, 50f);
    projectile.advance(1f); // wants 100 units, clamped to the 50-unit range

    assertEquals(50f, projectile.x(), EPS);
    assertEquals(50f, projectile.travelled(), EPS);
    assertTrue(projectile.expired());

    projectile.advance(1f);
    assertEquals(50f, projectile.x(), EPS);
  }

  @Test
  void expiresWhenTheRangeIsReachedWithinOneFrame() {
    GunProjectile projectile = new GunProjectile(0f, 0f, 1f, 0f, 10f, 5f);
    projectile.advance(0.5f);
    assertEquals(5f, projectile.travelled(), EPS);
    assertTrue(projectile.expired());
  }

  @Test
  void ignoresNonPositiveAndNonFiniteDelta() {
    GunProjectile projectile = new GunProjectile(0f, 0f, 1f, 0f, 10f, 100f);
    projectile.advance(0f);
    projectile.advance(-1f);
    projectile.advance(Float.NaN);
    projectile.advance(Float.POSITIVE_INFINITY);

    assertEquals(0f, projectile.x(), EPS);
    assertEquals(0f, projectile.travelled(), EPS);
    assertFalse(projectile.expired());
  }

  @Test
  void copiesOriginAndDirectionAtConstruction() {
    GunProjectile projectile = new GunProjectile(5f, 7f, 0f, -5f, 10f, 100f);

    assertEquals(5f, projectile.originX(), EPS);
    assertEquals(7f, projectile.originY(), EPS);
    assertEquals(5f, projectile.x(), EPS);
    assertEquals(7f, projectile.y(), EPS);
    assertEquals(0f, projectile.forwardX(), EPS);
    assertEquals(-1f, projectile.forwardY(), EPS);
  }

  @Test
  void rejectsInvalidConstructorArguments() {
    assertThrows(IllegalArgumentException.class,
        () -> new GunProjectile(0f, 0f, 0f, 0f, 10f, 100f));
    assertThrows(IllegalArgumentException.class,
        () -> new GunProjectile(0f, 0f, 1f, 0f, 0f, 100f));
    assertThrows(IllegalArgumentException.class,
        () -> new GunProjectile(0f, 0f, 1f, 0f, -1f, 100f));
    assertThrows(IllegalArgumentException.class,
        () -> new GunProjectile(0f, 0f, 1f, 0f, 10f, 0f));
    assertThrows(IllegalArgumentException.class,
        () -> new GunProjectile(Float.NaN, 0f, 1f, 0f, 10f, 100f));
    assertThrows(IllegalArgumentException.class,
        () -> new GunProjectile(0f, 0f, Float.NaN, 0f, 10f, 100f));
    assertThrows(IllegalArgumentException.class,
        () -> new GunProjectile(0f, 0f, 1f, 0f, 10f, Float.POSITIVE_INFINITY));
  }
}
