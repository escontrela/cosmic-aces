package com.davidpe.cosmicaces.domain.weapon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A deterministic visual cannon. It owns a cadence clock, a muzzle-flash pulse and the live list of
 * projectiles. It neither knows about ships, cameras, input nor the world: the caller passes the
 * frame delta, the portion of the frame during which emission is allowed, the trigger state and a
 * {@link ShotSource} that snapshots the firing ship at each emission instant.
 *
 * <p>Projectiles always advance by the full frame exactly once; projectiles created by an event
 * only advance the time that remains after their emission instant. Retirement is purely by distance.
 */
public final class GunBurst {

  private static final double TIME_EPSILON = 1e-6;
  private final List<GunProjectile> projectiles = new ArrayList<>();
  private double sinceLastEmission = GunTuning.BURST_INTERVAL_SECONDS;
  private boolean firing;

  /** A muzzle position in world coordinates, frozen when the shot is taken. */
  public record Muzzle(float x, float y) {
    public Muzzle {
      if (!Float.isFinite(x) || !Float.isFinite(y)) {
        throw new IllegalArgumentException("Muzzle position must be finite");
      }
    }
  }

  /**
   * A snapshot of one firing event: one or more muzzle origins plus the direction and range frozen
   * at the instant of emission. Astra provides two muzzles; Vesper Raider provides one.
   */
  public record Shot(List<Muzzle> muzzles, float forwardX, float forwardY, float range) {
    public Shot {
      if (muzzles == null || muzzles.isEmpty()) {
        throw new IllegalArgumentException("A shot needs at least one muzzle");
      }
      muzzles = List.copyOf(muzzles);
      if (!Float.isFinite(forwardX) || !Float.isFinite(forwardY)
          || (forwardX == 0f && forwardY == 0f)) {
        throw new IllegalArgumentException("Shot direction must be finite and non-zero");
      }
      if (!Float.isFinite(range) || range <= 0f) {
        throw new IllegalArgumentException("Shot range must be positive and finite");
      }
    }
  }

  /** Supplies the emission-time snapshot for a frame-local offset in seconds. */
  @FunctionalInterface
  public interface ShotSource {
    Shot shotAt(double offsetSeconds);
  }

  /**
   * Advances every existing projectile by the full frame and then emits new events inside the
   * allowed window.
   *
   * @param frameDelta elapsed seconds of this frame; non-positive or non-finite means no-op
   * @param emissionSeconds portion of the frame during which emission is allowed, clamped to
   *     {@code [0, frameDelta]}
   * @param enabled whether the trigger is held; disabled discards cadence debt and stops the pulse
   * @param source emission-time snapshot provider; {@code null} disables emission
   */
  public void advance(float frameDelta, float emissionSeconds, boolean enabled, ShotSource source) {
    if (!Float.isFinite(frameDelta) || frameDelta <= 0f) {
      return;
    }
    for (GunProjectile projectile : projectiles) {
      projectile.advance(frameDelta);
    }
    projectiles.removeIf(GunProjectile::expired);

    float window = Float.isFinite(emissionSeconds)
        ? Math.max(0f, Math.min(frameDelta, emissionSeconds)) : 0f;
    boolean canFire = enabled && window > 0f && source != null;
    if (!canFire) {
      firing = false;
      sinceLastEmission = GunTuning.BURST_INTERVAL_SECONDS;
      return;
    }

    firing = true;
    double nextGap = GunTuning.BURST_INTERVAL_SECONDS - sinceLastEmission;
    if (nextGap < 0d) {
      nextGap = 0d;
    }
    double eventLocal = nextGap;
    double lastEventLocal = -1d;
    while (eventLocal <= window + TIME_EPSILON) {
      emit(source, eventLocal, frameDelta);
      lastEventLocal = eventLocal;
      eventLocal += GunTuning.BURST_INTERVAL_SECONDS;
    }
    sinceLastEmission = lastEventLocal >= 0d
        ? frameDelta - lastEventLocal
        : sinceLastEmission + frameDelta;
  }

  private void emit(ShotSource source, double eventLocal, float frameDelta) {
    Shot shot = source.shotAt(eventLocal);
    if (shot == null) {
      return;
    }
    float remainingLife = (float) Math.max(0d, frameDelta - eventLocal);
    for (Muzzle muzzle : shot.muzzles()) {
      GunProjectile projectile = new GunProjectile(muzzle.x(), muzzle.y(),
          shot.forwardX(), shot.forwardY(), GunTuning.PROJECTILE_SPEED, shot.range());
      if (remainingLife > 0f) {
        projectile.advance(remainingLife);
      }
      projectiles.add(projectile);
    }
  }

  /** Live projectiles as a read-only view; the weapon owns all mutation. */
  public List<GunProjectile> projectiles() {
    return Collections.unmodifiableList(projectiles);
  }

  /** True while the trigger is held inside its allowed emission window, including between pulses. */
  public boolean isFiring() {
    return firing;
  }

  /** True during the short muzzle-flash pulse that follows the last emission event. */
  public boolean flashVisible() {
    return sinceLastEmission < GunTuning.FLASH_SECONDS;
  }

  /** Removes all projectiles and resets the cadence clock and pulse. */
  public void clear() {
    projectiles.clear();
    firing = false;
    sinceLastEmission = GunTuning.BURST_INTERVAL_SECONDS;
  }
}
