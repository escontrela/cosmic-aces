package com.davidpe.cosmicaces.domain.weapon;

/**
 * Initial tuning shared by the M61 Vulcan style visual bursts. Cadence, projectile speed and muzzle
 * flash are gameplay/visual values; the shot range is frozen per shot by the adapter.
 */
public final class GunTuning {

  /** Seconds between two consecutive emission events of the same weapon. */
  public static final float BURST_INTERVAL_SECONDS = 0.08f;

  /** World units travelled by a projectile per second. */
  public static final float PROJECTILE_SPEED = 1800f;

  /** Seconds the muzzle flash stays visible after each emission event. */
  public static final float FLASH_SECONDS = 0.035f;

  private GunTuning() {}
}
