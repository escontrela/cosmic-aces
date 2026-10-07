package com.davidpe.cosmicaces.domain.player;

/** Approved phase-one flight parameters, in world units and seconds. */
public final class FlightTuning {
  public static final float NORMAL_SPEED = 300f;
  public static final float TURBO_SPEED = 550f;
  public static final float ULTRA_SPEED = 750f;
  public static final float BRAKE_RATE = 450f;
  public static final float RECOVERY_RATE = 300f;
  public static final float YAW_RATE_DEGREES = 90f;
  public static final float ULTRA_SECONDS = 8f;
  public static final float FIXED_PITCH_DEGREES = 0f;
  public static final float FIXED_HEIGHT = 0f;
  public static final float MAX_STEP_SECONDS = 1f / 120f;

  private FlightTuning() {}
}
