package com.davidpe.cosmicaces.domain.enemy;

/**
 * Source of unit-uniform randomness used to schedule raider encounters. It is injectable so tests
 * can drive the encounter deterministically; production uses {@link java.util.concurrent.ThreadLocalRandom}.
 */
@FunctionalInterface
public interface UnitRandom {

  /** Returns a pseudo-random value in [0, 1). */
  float nextUnit();
}