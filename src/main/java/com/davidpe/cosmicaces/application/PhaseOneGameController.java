package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.collision.CollisionDetector;
import com.davidpe.cosmicaces.domain.enemy.RaiderEncounter;
import com.davidpe.cosmicaces.domain.enemy.UnitRandom;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.PhaseScore;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.ship.Ship;
import com.davidpe.cosmicaces.domain.ship.ShipCombatState;
import com.davidpe.cosmicaces.domain.weapon.GunBurst;
import com.davidpe.cosmicaces.domain.weapon.GunProjectile;
import java.util.concurrent.ThreadLocalRandom;

/** Rules specific to phase one: timed run, autonomous Raider, scoring and the Astra cannon. */
public final class PhaseOneGameController extends GameController {

  public static final WorldBounds WORLD = new WorldBounds(8192f, 12000f);
  /**
   * Body hulls are approximated by the inscribed circle of each ship box; the exact rotated box can
   * be introduced later without changing the collision contract.
   */
  private static final float BODY_RADIUS_FACTOR = 0.5f;
  private static final float RAIDER_BODY_RADIUS = 0.5f * RaiderEncounter.RAIDER_WIDTH;

  private final RaiderEncounter raiderEncounter;
  private final PhaseScore score = new PhaseScore();
  private final GunBurst astraGun = new GunBurst();
  private final GunBurst raiderGun = new GunBurst();
  private final ShipCombatState astraCombat = ShipCombatState.astra();
  private final ShipCombatState raiderCombat = ShipCombatState.vesper();
  private AstraMotion astraMotion;
  private float astraDeathX;
  private float astraDeathY;
  private float astraDeathYaw;
  private int pendingCombatPoints;

  public PhaseOneGameController() {
    this(ThreadLocalRandom.current()::nextFloat);
  }

  public PhaseOneGameController(UnitRandom raiderRandom) {
    raiderEncounter = new RaiderEncounter(raiderRandom);
  }

  public void setRaiderVisuals(VesperRaider.Visuals visuals) {
    raiderEncounter.setVisuals(visuals);
  }

  /** Starts Astra in the world; placement is independent of viewport coordinates. */
  public void placeAstra(float x, float y, float yawDegrees) {
    astra().placeAt(x, y, yawDegrees, WORLD);
  }

  /**
   * Advances Astra and the run clock using only the portion of this frame remaining in the
   * 60-second run, and returns the points earned by this delta. Scoring shares the same clamped
   * {@code activeSeconds} as the clock, so points freeze with the run and are never double counted.
   * A destroyed Astra keeps the clock running but does not move or earn coincidence points.
   *
   * @param coincident whether Astra and the raider were both visible during this delta
   */
  public int advanceFlight(FlightControls controls, float deltaSeconds, boolean coincident) {
    if (!isRunStarted() || isRunFinished() || !Float.isFinite(deltaSeconds)
        || deltaSeconds <= 0f) {
      return 0;
    }
    float activeSeconds = Math.min(deltaSeconds, remainingRunSeconds());
    if (astraCombat.canAct()) {
      float beforeX = astra().x();
      float beforeY = astra().y();
      float beforeYaw = astra().yawDegrees();
      astra().fly(controls, activeSeconds, WORLD);
      astraMotion = new AstraMotion(beforeX, beforeY, beforeYaw,
          astra().x(), astra().y(), astra().yawDegrees(), activeSeconds);
    } else {
      astraMotion = null;
    }
    advanceRun(activeSeconds);
    return score.advance(activeSeconds, coincident && astraCombat.canAct());
  }

  /** Astra's combat energy, impacts and life timers. */
  public ShipCombatState astraCombat() {
    return astraCombat;
  }

  /** Vesper Raider's combat energy, impacts and life timers. */
  public ShipCombatState raiderCombat() {
    return raiderCombat;
  }

  /** True while Astra may fly and shoot (alive or inside her post-respawn protection). */
  public boolean isAstraActive() {
    return astraCombat.canAct();
  }

  /** The player's visual Astra cannon, exposed so the screen can query and draw it. */
  public GunBurst astraGun() {
    return astraGun;
  }

  /**
   * The Vesper Raider's autonomous cannon. It only emits while Astra and the raider coincide in the
   * real camera view; its projectiles outlive that coincidence and keep their frozen path.
   */
  public GunBurst raiderGun() {
    return raiderGun;
  }

  /**
   * Advances both visual cannons once per frame and resolves their impacts against the opposing
   * ship. Existing projectiles always travel the full {@code frameDelta}; new emissions only happen
   * inside {@code emissionSeconds}, so passing zero stops new shots while the already-fired ones
   * keep flying. Astra fires on its own trigger only while alive; the raider fires only when it is
   * present and {@code shipsCoincidentVisible} is true for this frame. Each projectile impacts at
   * most once; a wreck is not a target and an invulnerable hull consumes the projectile without
   * damage. The returned points are the single destruction bonus earned inside this call.
   *
   * @param frameDelta elapsed frame seconds, also used to advance existing projectiles
   * @param emissionSeconds part of the frame during which the run is still active
   * @param astraTrigger whether SPACE is held
   * @param shipsCoincidentVisible whether Astra and the raider are both visible in the drawn frame
   * @param shotRange travel distance frozen into each new projectile
   */
  public int advanceWeapons(float frameDelta, float emissionSeconds, boolean astraTrigger,
      boolean shipsCoincidentVisible, float shotRange) {
    boolean astraEnabled = astraTrigger && astraCombat.canAct();
    astraGun.advance(frameDelta, emissionSeconds, astraEnabled,
        astraEnabled ? astraShotSource(shotRange) : null, this::astraProjectileStep);
    VesperRaider raider = activeRaider();
    boolean raiderEnabled = raider != null && shipsCoincidentVisible && raiderCombat.canAct();
    raiderGun.advance(frameDelta, emissionSeconds, raiderEnabled,
        raiderEnabled ? raiderShotSource(raider, shotRange) : null, this::raiderProjectileStep);
    return drainCombatPoints();
  }

  /**
   * Advances both ships' combat timers, resolves a vulnerable hull-to-hull contact and places any
   * ship whose respawn delay has elapsed. Called once per frame inside the run; it can award the
   * single destruction bonus if the contact destroys the raider. No {@code LifeLost} is emitted.
   *
   * @return points earned by this call (the raider destruction bonus, if any)
   */
  public int advanceCombat(float deltaSeconds) {
    if (!isRunStarted() || isRunFinished() || !Float.isFinite(deltaSeconds)
        || deltaSeconds <= 0f) {
      return 0;
    }
    astraCombat.advance(deltaSeconds);
    raiderCombat.advance(deltaSeconds);
    handleRespawns();
    resolveShipContact();
    return drainCombatPoints();
  }

  /** Returns and clears the combat points earned since the last drain, so they publish once. */
  public int drainCombatPoints() {
    int points = pendingCombatPoints;
    pendingCombatPoints = 0;
    return points;
  }

  private void resolveShipContact() {
    VesperRaider raider = activeRaider();
    if (raider == null || astraCombat.isDestroyed() || raiderCombat.isDestroyed()) {
      return;
    }
    // An invulnerable Astra also protects against contact, so neither ship is destroyed.
    if (!astraCombat.canBeHit() || !raiderCombat.canBeHit()) {
      return;
    }
    if (CollisionDetector.overlaps(bodyCircle(astra()), bodyCircle(raider))) {
      destroyAstra();
      destroyRaider();
    }
  }

  private void handleRespawns() {
    if (astraCombat.readyToRespawn()) {
      astraCombat.respawn();
      astra().placeAt(astraDeathX, astraDeathY, astraDeathYaw, WORLD);
      astraMotion = null;
    }
    if (raiderCombat.readyToRespawn() && raiderEncounter.raider() != null) {
      raiderCombat.respawn();
      raiderEncounter.respawnAt(WORLD, astra().x() + astra().drawWidth() / 2f,
          astra().y() + astra().drawHeight() / 2f, bodyRadius(astra()));
    }
  }

  private void destroyAstra() {
    if (astraCombat.isDestroyed()) {
      return;
    }
    astraCombat.destroy();
    astraDeathX = astra().x();
    astraDeathY = astra().y();
    astraDeathYaw = astra().yawDegrees();
  }

  private void destroyRaider() {
    if (raiderCombat.isDestroyed()) {
      return;
    }
    raiderCombat.destroy();
    raiderEncounter.destroyActive();
    pendingCombatPoints += score.awardRaiderDestroyed();
  }

  private void astraProjectileStep(GunProjectile projectile, float fromX, float fromY,
      float toX, float toY) {
    if (projectile.isConsumed()) {
      return;
    }
    VesperRaider raider = activeRaider();
    if (raider == null || raiderCombat.isDestroyed()) {
      return;
    }
    if (!raiderCombat.canBeHit()) {
      // Protected hull: the projectile is spent on contact but deals no damage.
      if (raiderCombat.isInvulnerable() && hitsShip(fromX, fromY, toX, toY, raider)) {
        projectile.consume();
      }
      return;
    }
    if (!hitsShip(fromX, fromY, toX, toY, raider)) {
      return;
    }
    projectile.consume();
    raiderCombat.receiveProjectileHit();
    if (raiderCombat.energyPercent() <= 0) {
      destroyRaider();
    }
  }

  private void raiderProjectileStep(GunProjectile projectile, float fromX, float fromY,
      float toX, float toY) {
    if (projectile.isConsumed()) {
      return;
    }
    if (astraCombat.isDestroyed()) {
      return;
    }
    if (!astraCombat.canBeHit()) {
      if (astraCombat.isInvulnerable() && hitsShip(fromX, fromY, toX, toY, astra())) {
        projectile.consume();
      }
      return;
    }
    if (!hitsShip(fromX, fromY, toX, toY, astra())) {
      return;
    }
    projectile.consume();
    astraCombat.receiveProjectileHit();
    if (astraCombat.energyPercent() <= 0) {
      destroyAstra();
    }
  }

  private static boolean hitsShip(float fromX, float fromY, float toX, float toY, Ship ship) {
    return CollisionDetector.firstHit(
        new CollisionDetector.Segment(fromX, fromY, toX, toY), bodyCircle(ship)).isPresent();
  }

  private static CollisionDetector.Circle bodyCircle(Ship ship) {
    return new CollisionDetector.Circle(ship.x() + ship.drawWidth() / 2f,
        ship.y() + ship.drawHeight() / 2f, bodyRadius(ship));
  }

  private static float bodyRadius(Ship ship) {
    return BODY_RADIUS_FACTOR * Math.min(ship.drawWidth(), ship.drawHeight());
  }

  private GunBurst.ShotSource astraShotSource(float shotRange) {
    AstraMotion motion = astraMotion;
    if (motion == null) {
      return offsetSeconds -> astra().shotAt(
          astra().x() + astra().drawWidth() / 2f,
          astra().y() + astra().drawHeight() / 2f,
          astra().yawDegrees(), shotRange);
    }
    return offsetSeconds -> {
      float fraction = motion.duration() <= 0f ? 1f
          : (float) Math.max(0d, Math.min(1d, offsetSeconds / motion.duration()));
      float centerX = lerp(motion.beforeX(), motion.afterX(), fraction)
          + astra().drawWidth() / 2f;
      float centerY = lerp(motion.beforeY(), motion.afterY(), fraction)
          + astra().drawHeight() / 2f;
      float yaw = motion.beforeYaw()
          + normalizeYaw(motion.afterYaw() - motion.beforeYaw()) * fraction;
      return astra().shotAt(centerX, centerY, yaw, shotRange);
    };
  }

  private static float lerp(float from, float to, float fraction) {
    return from + (to - from) * fraction;
  }

  private static float normalizeYaw(float yaw) {
    float normalized = yaw % 360f;
    if (normalized > 180f) normalized -= 360f;
    if (normalized <= -180f) normalized += 360f;
    return normalized;
  }

  /**
   * Snapshots the raider's box center, heading and measured side cannon mouths at the emission
   * instant. Unlike Astra, the raider moves only a few units per frame, so no intra-frame
   * interpolation is needed; each {@code Shot} still freezes origin and direction, and the
   * resulting projectiles retain no ship reference.
   */
  private GunBurst.ShotSource raiderShotSource(VesperRaider raider, float shotRange) {
    return offsetSeconds -> raider.shotAt(
        raider.x() + raider.drawWidth() / 2f,
        raider.y() + raider.drawHeight() / 2f,
        raider.headingDegrees(), shotRange);
  }

  /** Astra's box position and heading before and after this frame's bounded flight. */
  private record AstraMotion(float beforeX, float beforeY, float beforeYaw,
      float afterX, float afterY, float afterYaw, float duration) {}

  /** Total points this run has awarded so far, including destruction bonuses. */
  public int scorePoints() {
    return score.totalPoints();
  }

  /**
   * Advances the single raider toward Astra in world coordinates, supplying the player's centre,
   * velocity and body radius so the encounter can avoid a predicted ram. Projectile paths already
   * in flight are untouched by ship movement.
   */
  public void advanceEncounter(float deltaSeconds) {
    if (!isRunStarted() || isRunFinished()) {
      return;
    }
    float targetCenterX = astra().x() + astra().drawWidth() / 2f;
    float targetCenterY = astra().y() + astra().drawHeight() / 2f;
    float velocityX = 0f;
    float velocityY = 0f;
    AstraMotion motion = astraMotion;
    if (motion != null && motion.duration() > 0f) {
      velocityX = (motion.afterX() - motion.beforeX()) / motion.duration();
      velocityY = (motion.afterY() - motion.beforeY()) / motion.duration();
    }
    raiderEncounter.advance(deltaSeconds, WORLD, targetCenterX, targetCenterY,
        velocityX, velocityY, bodyRadius(astra()), RAIDER_BODY_RADIUS);
  }

  public boolean isRaiderActive() {
    return raiderEncounter.isActive();
  }

  /** The live raider, or {@code null} while it has not appeared or is destroyed awaiting respawn. */
  public VesperRaider activeRaider() {
    return raiderEncounter.isActive() ? raiderEncounter.raider() : null;
  }

}
