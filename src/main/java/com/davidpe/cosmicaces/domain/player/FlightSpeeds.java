package com.davidpe.cosmicaces.domain.player;

/** Player-selected target speeds for Astra, in world units per second. */
public record FlightSpeeds(int normal, int turbo, int ultra) {

  public static final int MIN_SPEED = 50;
  public static final int MAX_SPEED = 1000;
  public static final int ADJUSTMENT_STEP = 10;
  /**
   * Factory targets, taken from {@link FlightTuning} so the approved cruise, Turbo and Ultra
   * references cannot drift apart from the stored defaults.
   */
  public static final FlightSpeeds DEFAULTS = new FlightSpeeds((int) FlightTuning.NORMAL_SPEED,
      (int) FlightTuning.TURBO_SPEED, (int) FlightTuning.ULTRA_SPEED);

  public FlightSpeeds {
    validate(normal);
    validate(turbo);
    validate(ultra);
  }

  public FlightSpeeds withNormal(int value) {
    return new FlightSpeeds(clamp(value), turbo, ultra);
  }

  public FlightSpeeds withTurbo(int value) {
    return new FlightSpeeds(normal, clamp(value), ultra);
  }

  public FlightSpeeds withUltra(int value) {
    return new FlightSpeeds(normal, turbo, clamp(value));
  }

  private static int clamp(int value) {
    return Math.max(MIN_SPEED, Math.min(MAX_SPEED, value));
  }

  private static void validate(int value) {
    if (value < MIN_SPEED || value > MAX_SPEED) {
      throw new IllegalArgumentException("Astra speeds must be between 50 and 1000 u/s");
    }
  }
}
