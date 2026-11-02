package com.davidpe.cosmicaces.domain.weapon;

/** Astra's selected weapon and per-life missile inventory; no input or rendering dependencies. */
public final class MissileLoadout {
  public enum Weapon { CANNON, MISSILE }

  private final int capacity;
  private int remaining;
  private Weapon weapon = Weapon.CANNON;

  public MissileLoadout(int capacity) {
    if (capacity < MissileConfig.MIN_ASTRA_MISSILES || capacity > MissileConfig.MAX_ASTRA_MISSILES) {
      throw new IllegalArgumentException("Missile capacity must be between 0 and 10");
    }
    this.capacity = capacity;
    remaining = capacity;
  }

  public int capacity() {
    return capacity;
  }

  public int remaining() {
    return remaining;
  }

  public Weapon weapon() {
    return weapon;
  }

  public void armMissile() {
    weapon = Weapon.MISSILE;
  }

  public void armCannon() {
    weapon = Weapon.CANNON;
  }

  /** Consume once for an accepted launch; a missing/invalid target never costs ammunition. */
  public boolean consumeForLaunch(boolean validTarget) {
    if (weapon != Weapon.MISSILE || !validTarget || remaining == 0) return false;
    remaining--;
    return true;
  }

  /** Call only for a real new life, never when switching weapons or on a render tick. */
  public void restoreForNewLife() {
    remaining = capacity;
    weapon = Weapon.CANNON;
  }
}
