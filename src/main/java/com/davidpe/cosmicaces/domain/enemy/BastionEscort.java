package com.davidpe.cosmicaces.domain.enemy;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.effect.ShipHitFlash;
import com.davidpe.cosmicaces.domain.ship.ShipCombatState;
import com.davidpe.cosmicaces.domain.weapon.GunBurst;
import com.davidpe.cosmicaces.domain.weapon.GunTuning;
import com.davidpe.cosmicaces.domain.weapon.MissileConfig;
import com.davidpe.cosmicaces.domain.weapon.MissileTarget;

/** One Vesper wingman that follows the Bastion bomber until Astra enters its detection range. */
public final class BastionEscort {
  public static final float FORMATION_OFFSET = BastionBomber.DRAW_WIDTH / 2f
      + RaiderEncounter.RAIDER_WIDTH / 2f + 24f;
  public static final float DETECTION_RADIUS = 1800f;
  public static final float MISSILE_LOCK_SECONDS = 2f;

  private final int side;
  private final RaiderEncounter encounter;
  private final ShipCombatState combat = ShipCombatState.vesper();
  private final GunBurst gun = new GunBurst(GunTuning.RAIDER_BURST_INTERVAL_SECONDS);
  private final ShipHitFlash hitFlash = new ShipHitFlash();
  private final int missileCapacity;
  private int missilesRemaining;
  private float missileLockSeconds;
  private MissileTarget.Reference missileLockTarget;
  private boolean deployed;
  private boolean attacking;
  private float deathCenterX;
  private float deathCenterY;

  public BastionEscort(int side, UnitRandom random) {
    this(side, random, MissileConfig.DEFAULTS.escortMissiles());
  }

  public BastionEscort(int side, UnitRandom random, int missileCapacity) {
    if (side != -1 && side != 1) {
      throw new IllegalArgumentException("Escort side must be -1 or 1");
    }
    if (random == null) {
      throw new IllegalArgumentException("Random source must not be null");
    }
    if (missileCapacity < 0) {
      throw new IllegalArgumentException("Escort missile capacity must be non-negative");
    }
    this.side = side;
    this.missileCapacity = missileCapacity;
    missilesRemaining = missileCapacity;
    encounter = new RaiderEncounter(random);
  }

  public void setVisuals(VesperRaider.Visuals visuals) {
    encounter.setVisuals(visuals);
  }

  public void deploy(BastionBomber bomber, WorldBounds world) {
    if (deployed) return;
    if (bomber == null || world == null) {
      throw new IllegalArgumentException("Bomber and world are required to deploy escorts");
    }
    encounter.spawnAt(world, formationX(bomber), bomber.centerY(), bomber.headingDegrees());
    deployed = true;
  }

  public void advance(float deltaSeconds, BastionBomber bomber, WorldBounds world,
      float targetX, float targetY, float targetVelocityX, float targetVelocityY,
      float targetRadius) {
    if (!deployed || combat.isDestroyed() || !Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
      return;
    }
    VesperRaider raider = encounter.raider();
    if (!attacking && Math.hypot(targetX - raider.centerX(), targetY - raider.centerY())
        <= DETECTION_RADIUS) {
      attacking = true;
    }
    if (!attacking) {
      raider.placeAt(formationX(bomber) - raider.drawWidth() / 2f,
          bomber.centerY() - raider.drawHeight() / 2f, bomber.headingDegrees(), world);
      return;
    }
    encounter.advance(deltaSeconds, world, targetX, targetY,
        targetVelocityX, targetVelocityY, targetRadius,
        0.5f * RaiderEncounter.RAIDER_WIDTH);
  }

  private float formationX(BastionBomber bomber) {
    return bomber.centerX() + side * FORMATION_OFFSET;
  }

  public boolean isDeployed() {
    return deployed;
  }

  public boolean isAttacking() {
    return attacking;
  }

  public boolean isActive() {
    return deployed && !combat.isDestroyed() && encounter.isActive();
  }

  public int side() {
    return side;
  }

  public VesperRaider raider() {
    return deployed ? encounter.raider() : null;
  }

  public RaiderEncounter encounter() {
    return encounter;
  }

  public ShipCombatState combat() {
    return combat;
  }

  public GunBurst gun() {
    return gun;
  }

  public ShipHitFlash hitFlash() {
    return hitFlash;
  }

  public void destroy() {
    if (!isActive()) return;
    VesperRaider raider = encounter.raider();
    deathCenterX = raider.centerX();
    deathCenterY = raider.centerY();
    combat.destroy();
    encounter.destroyActive();
    hitFlash.clear();
    gun.clear();
    resetMissileLock();
  }

  /** Advances this escort's independent Astra lock; invalid eligibility breaks the lock. */
  public boolean advanceMissileLock(float deltaSeconds, MissileTarget target) {
    if (!isActive() || !combat.canAct() || missilesRemaining == 0 || target == null
        || target.reference().kind() != MissileTarget.Kind.ASTRA) {
      resetMissileLock();
      return false;
    }
    if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) return false;
    VesperRaider ship = encounter.raider();
    if (Math.hypot(target.x() - ship.centerX(), target.y() - ship.centerY()) > DETECTION_RADIUS) {
      resetMissileLock();
      return false;
    }
    if (missileLockTarget != null && !missileLockTarget.equals(target.reference())) {
      missileLockSeconds = 0f;
    }
    missileLockTarget = target.reference();
    missileLockSeconds += deltaSeconds;
    return missileLockSeconds >= MISSILE_LOCK_SECONDS;
  }

  /** Consumes the single per-escort stock only for the exact Astra life held for two seconds. */
  public boolean consumeLockedMissile(MissileTarget.Reference target) {
    if (!isActive() || !combat.canAct() || missilesRemaining == 0 || target == null
        || !target.equals(missileLockTarget) || missileLockSeconds < MISSILE_LOCK_SECONDS) {
      return false;
    }
    missilesRemaining--;
    resetMissileLock();
    return true;
  }

  private void resetMissileLock() {
    missileLockSeconds = 0f;
    missileLockTarget = null;
  }

  public int missileCapacity() { return missileCapacity; }
  public int missilesRemaining() { return missilesRemaining; }
  public float missileLockSeconds() { return missileLockSeconds; }
  public MissileTarget.Reference missileLockTarget() { return missileLockTarget; }

  public float deathCenterX() {
    return deathCenterX;
  }

  public float deathCenterY() {
    return deathCenterY;
  }
}
