package com.davidpe.cosmicaces.domain.weapon;

/** Immutable per-run missile settings. Times are seconds; distances and speeds use world units. */
public record MissileConfig(int astraMissilesPerLife, int escortMissiles,
    float fuelSeconds, float maxSpeed, float maxTurnDegreesPerSecond,
    float separationSeconds, float accelerationSeconds, float aimCaptureRadius,
    float aimSpeed) {
  public static final int MIN_ASTRA_MISSILES = 0;
  public static final int MAX_ASTRA_MISSILES = 10;
  public static final String ASTRA_MISSILE_NAME = "AIM-120 AMRAAM";
  public static final MissileConfig DEFAULTS = new MissileConfig(
      2, 1, 6f, 1500f, 180f, 0.25f, 0.5f, 180f, 1200f);

  public MissileConfig {
    if (astraMissilesPerLife < MIN_ASTRA_MISSILES || astraMissilesPerLife > MAX_ASTRA_MISSILES) {
      throw new IllegalArgumentException("Astra missile capacity must be between 0 and 10");
    }
    if (escortMissiles < 0) {
      throw new IllegalArgumentException("Escort missile capacity must be non-negative");
    }
    positive(fuelSeconds, "Fuel duration");
    positive(maxSpeed, "Maximum speed");
    positive(maxTurnDegreesPerSecond, "Maximum turn rate");
    positive(separationSeconds, "Separation duration");
    positive(accelerationSeconds, "Acceleration duration");
    positive(aimCaptureRadius, "Aim capture radius");
    positive(aimSpeed, "Aim speed");
    if (separationSeconds + accelerationSeconds >= fuelSeconds) {
      throw new IllegalArgumentException("Separation and acceleration must fit within fuel duration");
    }
  }

  /** Returns a new configuration for a new run without changing any existing instance. */
  public MissileConfig withAstraMissilesPerLife(int count) {
    return new MissileConfig(count, escortMissiles, fuelSeconds, maxSpeed,
        maxTurnDegreesPerSecond, separationSeconds, accelerationSeconds, aimCaptureRadius, aimSpeed);
  }

  private static void positive(float value, String name) {
    if (!Float.isFinite(value) || value <= 0f) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }
}
