package com.davidpe.cosmicaces.application;

import com.davidpe.cosmicaces.domain.collision.CollisionDetector;
import com.davidpe.cosmicaces.domain.effect.ShipHitFlash;
import com.davidpe.cosmicaces.domain.enemy.GhostwingDrone;
import com.davidpe.cosmicaces.domain.enemy.GhostwingEncounter;
import com.davidpe.cosmicaces.domain.enemy.RaiderEncounter;
import com.davidpe.cosmicaces.domain.enemy.UnitRandom;
import com.davidpe.cosmicaces.domain.enemy.VesperRaider;
import com.davidpe.cosmicaces.domain.game.GhostwingDeath;
import com.davidpe.cosmicaces.domain.game.PhaseOneMission;
import com.davidpe.cosmicaces.domain.game.PhaseOutcome;
import com.davidpe.cosmicaces.domain.game.PhaseScore;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.domain.player.FlightSpeeds;
import com.davidpe.cosmicaces.domain.ship.Ship;
import com.davidpe.cosmicaces.domain.ship.ShipCombatState;
import com.davidpe.cosmicaces.domain.weapon.GunBurst;
import com.davidpe.cosmicaces.domain.weapon.GunProjectile;
import com.davidpe.cosmicaces.domain.weapon.GunTuning;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rules specific to phase one: the 10-minute mission clock, autonomous Raider, scoring, mission
 * outcome and the Astra cannon.
 */
public final class PhaseOneGameController extends GameController {

  public static final WorldBounds WORLD = new WorldBounds(8192f, 12000f);
  /**
   * Body hulls are approximated by the inscribed circle of each ship box; the exact rotated box can
   * be introduced later without changing the collision contract.
   */
  private static final float BODY_RADIUS_FACTOR = 0.5f;
  private static final float RAIDER_BODY_RADIUS = 0.5f * RaiderEncounter.RAIDER_WIDTH;
  /** Stable ordering of projectile targets used when two hulls share the same entry fraction. */
  private static final int TARGET_GHOSTWING = 0;
  private static final int TARGET_RAIDER_SHIELD = 1;
  private static final int TARGET_RAIDER = 2;

  private final RaiderEncounter raiderEncounter;
  private final GhostwingEncounter ghostwingEncounter;
  private PhaseOneScenery phaseOneScenery;
  private final PhaseScore score = new PhaseScore();
  private final PhaseOneMission mission = new PhaseOneMission();
  private final GunBurst astraGun = new GunBurst();
  private final GunBurst raiderGun = new GunBurst(GunTuning.RAIDER_BURST_INTERVAL_SECONDS);
  private final ShipCombatState astraCombat = ShipCombatState.astra();
  private final ShipCombatState raiderCombat = ShipCombatState.vesper();
  /**
   * Independent hit-flash pulses for presentation: each valid projectile impact renews the pulse of
   * the ship actually hit; the screen converts {@link #astraHitFlashIntensity()} and {@link
   * #raiderHitFlashIntensity()} into the shared white shader. They own no damage rules.
   */
  private final ShipHitFlash astraHitFlash = new ShipHitFlash();
  private final ShipHitFlash raiderHitFlash = new ShipHitFlash();
  private AstraMotion astraMotion;
  private float astraDeathX;
  private float astraDeathY;
  private float raiderDeathCenterX;
  private float raiderDeathCenterY;
  private int pendingCombatPoints;
  /**
   * Astra destructions recorded since the last drain: exactly one per real ACTIVE→DESTROYED
   * transition. The screen publishes one {@code LifeLost} per entry so a frame with several impacts
   * on the same body still costs a single life.
   */
  private int pendingAstraLifeLosses;
  private final List<GhostwingDeath> pendingGhostwingDeaths = new ArrayList<>();
  /**
   * Sentinel for "no simulation frame is open": each direct call bounds itself with the run clock,
   * which is the contract the deterministic tests use.
   */
  private static final float NO_ACTIVE_FRAME = -1f;
  /**
   * Authorized active slice of the frame being simulated, in seconds, or {@link #NO_ACTIVE_FRAME}
   * outside a frame. The screen computes it once per frame from the mission clock and shares it with
   * every subsystem through {@link #beginActiveFrame(float)}, so a delta that reaches 00:00 while
   * flight advances cannot cut the same frame's encounter, Ghostwing, contact timers or collisions
   * short. While a frame is open this value is zero or positive.
   */
  private float activeFrameSeconds = NO_ACTIVE_FRAME;

  public PhaseOneGameController() {
    this(ThreadLocalRandom.current()::nextFloat);
  }

  public PhaseOneGameController(UnitRandom raiderRandom) {
    this(raiderRandom, ThreadLocalRandom.current()::nextFloat);
  }

  public PhaseOneGameController(UnitRandom raiderRandom, UnitRandom ghostwingRandom) {
    this(raiderRandom, ghostwingRandom, FlightSpeeds.DEFAULTS);
  }

  public PhaseOneGameController(FlightSpeeds speeds) {
    this(ThreadLocalRandom.current()::nextFloat, ThreadLocalRandom.current()::nextFloat, speeds);
  }

  public PhaseOneGameController(UnitRandom raiderRandom, UnitRandom ghostwingRandom,
      FlightSpeeds speeds) {
    super(speeds);
    raiderEncounter = new RaiderEncounter(raiderRandom);
    ghostwingEncounter = new GhostwingEncounter(ghostwingRandom);
  }

  public void setRaiderVisuals(VesperRaider.Visuals visuals) {
    raiderEncounter.setVisuals(visuals);
  }

  /** Returns the landscape shared by this run, generating it once on first request. */
  public PhaseOneScenery phaseOneScenery() {
    if (phaseOneScenery == null) {
      phaseOneScenery = new PhaseOneScenery(PhaseOneScenery.DEFAULT_SEED,
          (int) WORLD.width(), (int) WORLD.height());
    }
    return phaseOneScenery;
  }

  /** Starts Astra in the world; placement is independent of viewport coordinates. */
  public void placeAstra(float x, float y, float yawDegrees) {
    astra().placeAt(x, y, yawDegrees, WORLD);
  }

  /** Places Astra on the operations-base runway for the first sortie. */
  public void placeAstraAtOperationsBase() {
    PhaseOneScenery scenery = phaseOneScenery();
    astra().respawnAt((float) scenery.operationsBaseSpawnCenterX(),
        (float) scenery.operationsBaseSpawnCenterY(), WORLD);
  }

  /**
   * Opens the simulation frame with the slice the screen already bounded by the mission clock. While
   * the frame is open, every subsystem advances exactly this slice instead of re-deriving it from
   * the run clock, so the clock reaching 00:00 during flight cannot stop the encounter, Ghostwing,
   * combat timers or collisions of the very same frame. A non-positive or non-finite slice opens an
   * inert frame in which no subsystem advances.
   */
  public void beginActiveFrame(float activeSeconds) {
    activeFrameSeconds = Float.isFinite(activeSeconds) && activeSeconds > 0f ? activeSeconds : 0f;
  }

  /**
   * Closes the current simulation frame, restoring the direct-call contract in which every call
   * bounds its delta with the run clock and an already finished run advances nothing.
   */
  public void endActiveFrame() {
    activeFrameSeconds = NO_ACTIVE_FRAME;
  }

  /** True while a frame opened by {@link #beginActiveFrame(float)} is being simulated. */
  private boolean isActiveFrameOpen() {
    return activeFrameSeconds != NO_ACTIVE_FRAME;
  }

  /**
   * Seconds a subsystem may simulate for this call. Inside an open frame it is exactly the
   * authorized slice, never re-derived from a clock that may already read 00:00; outside a frame the
   * run clock bounds the delta, so a direct call keeps the existing behavior.
   */
  private float boundedActiveSeconds(float deltaSeconds) {
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
      return 0f;
    }
    float limit = isActiveFrameOpen() ? activeFrameSeconds : remainingRunSeconds();
    return Math.min(deltaSeconds, limit);
  }

  /**
   * Advances Astra and the run clock using only the portion of this frame remaining in the
   * 10-minute mission limit, and returns the points earned by this delta. Scoring shares the same
   * clamped {@code activeSeconds} as the clock, so points freeze with the run and are never double
   * counted. A destroyed Astra keeps the clock running but does not move or earn coincidence points.
   *
   * @param coincident whether Astra and the raider were both visible during this delta
   */
  public int advanceFlight(FlightControls controls, float deltaSeconds, boolean coincident) {
    if (!isRunStarted() || (!isActiveFrameOpen() && isRunFinished())) {
      return 0;
    }
    float activeSeconds = boundedActiveSeconds(deltaSeconds);
    if (activeSeconds <= 0f) {
      return 0;
    }
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
        raiderEnabled ? raiderShotSource(shotRange) : null, this::raiderProjectileStep);
    return drainCombatPoints();
  }

  /**
   * Advances both ships' combat timers, resolves a vulnerable hull-to-hull contact and places any
   * ship whose respawn delay has elapsed. Hull contact only destroys Astra; Vesper takes no damage
   * from the collision and cannot award a kill or points this way. The authoritative lives remaining
   * are supplied by the caller from the coordinator snapshot: Astra only reappears while the pool is
   * above zero, so an exhausted pool leaves her defeated with no extra damage.
   *
   * @param astraLivesRemaining lives Astra has left, as held by the coordinator
   * @return points earned by this call (the raider destruction bonus, if any)
   */
  public int advanceCombat(float deltaSeconds, int astraLivesRemaining) {
    if (!isRunStarted() || (!isActiveFrameOpen() && isRunFinished())) {
      return 0;
    }
    float activeSeconds = boundedActiveSeconds(deltaSeconds);
    if (activeSeconds <= 0f) {
      return 0;
    }
    astraCombat.advance(activeSeconds);
    raiderCombat.advance(activeSeconds);
    handleRespawns(astraLivesRemaining);
    resolveShipContact();
    resolveGhostwingContact(activeSeconds);
    return drainCombatPoints();
  }

  /** Returns and clears the combat points earned since the last drain, so they publish once. */
  public int drainCombatPoints() {
    int points = pendingCombatPoints;
    pendingCombatPoints = 0;
    return points;
  }

  /**
   * Returns and clears the Astra life losses recorded since the last drain. Each real destruction
   * (projectile, hull contact with the Raider or a Ghostwing drone, or energy depletion) adds exactly
   * one entry; repeated impacts on an already destroyed body add none. The screen publishes one
   * {@code LifeLost} event per entry, so the coordinator always decrements the pool exactly once.
   */
  public int drainAstraLifeLosses() {
    int losses = pendingAstraLifeLosses;
    pendingAstraLifeLosses = 0;
    return losses;
  }

  /**
   * Returns and clears the Ghostwing drone destructions recorded since the last drain, so the
   * presentation can convert each one into a world-anchored explosion exactly once. Each drone is
   * reported at most once; drones that finish their world crossing produce no event.
   */
  public List<GhostwingDeath> drainGhostwingDeaths() {
    List<GhostwingDeath> deaths = List.copyOf(pendingGhostwingDeaths);
    pendingGhostwingDeaths.clear();
    return deaths;
  }

  /**
   * Advances both hit-flash decay timers once per frame. It runs unconditionally (also after the
   * phase has finished) so the last pulse of an in-flight projectile keeps extinguishing, and the
   * screen calls it before {@link #advanceWeapons} so an impact accepted this frame is drawn at its
   * full first intensity instead of losing one whole frame to the decay.
   */
  public void advanceHitFeedback(float deltaSeconds) {
    astraHitFlash.advance(deltaSeconds);
    raiderHitFlash.advance(deltaSeconds);
  }

  /** Presentation white intensity (0–1) of Astra's current hit-flash pulse. */
  public float astraHitFlashIntensity() {
    return astraHitFlash.intensity();
  }

  /** Presentation white intensity (0–1) of the Vesper Raider's current hit-flash pulse. */
  public float raiderHitFlashIntensity() {
    return raiderHitFlash.intensity();
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
    }
  }

  /**
   * Resolves hull-to-hull contact between Astra and every active Ghostwing drone, using the exact
   * relative sweep of both translating circles over the same frame so fast ships and long frames do
   * not pass through each other. Astra sweeps the motion captured this frame by {@code advanceFlight}
   * ({@link AstraMotion}); each drone sweeps its fixed cardinal travel for the same active-seconds
   * slice passed by {@code advanceCombat} (the run clock does not advance between both calls, so the
   * reconstruction is exact). A destroyed or protected Astra never collides; on a
   * valid contact only the involved drone is removed, {@code destroyAstra()} runs once (idempotent)
   * and {@link #handleRespawns()} keeps the existing 3 s respawn with its 2 s invulnerability. Other
   * drones are not dragged by the explosion: each contacting drone is destroyed on its own and
   * nothing else receives area damage.
   */
  private void resolveGhostwingContact(float activeSeconds) {
    if (astraCombat.isDestroyed() || !astraCombat.canBeHit()) {
      return;
    }
    CollisionDetector.Circle astraFrom;
    CollisionDetector.Circle astraTo;
    AstraMotion motion = astraMotion;
    if (motion == null) {
      astraFrom = bodyCircle(astra());
      astraTo = astraFrom;
    } else {
      astraFrom = new CollisionDetector.Circle(
          motion.beforeX() + astra().drawWidth() / 2f,
          motion.beforeY() + astra().drawHeight() / 2f,
          bodyRadius(astra()));
      astraTo = bodyCircle(astra());
    }
    float droneSpeed = GhostwingEncounter.DRONE_SPEED;
    for (GhostwingDrone drone : List.copyOf(ghostwingEncounter.activeDrones())) {
      float radius = bodyRadius(drone);
      CollisionDetector.Circle droneTo =
          new CollisionDetector.Circle(drone.centerX(), drone.centerY(), radius);
      CollisionDetector.Circle droneFrom = new CollisionDetector.Circle(
          drone.centerX() - drone.direction().horizontal() * droneSpeed * activeSeconds,
          drone.centerY() - drone.direction().vertical() * droneSpeed * activeSeconds,
          radius);
      if (CollisionDetector.sweep(astraFrom, astraTo, droneFrom, droneTo).isPresent()) {
        destroyGhostwingDrone(drone.id(), GhostwingDeath.Cause.COLLISION);
        destroyAstra();
      }
    }
  }

  private void handleRespawns(int astraLivesRemaining) {
    if (astraCombat.readyToRespawn() && astraLivesRemaining > 0) {
      astraCombat.respawn();
      astraHitFlash.clear();
      placeAstraAtOperationsBase();
      astraMotion = null;
    }
    if (raiderCombat.readyToRespawn() && raiderEncounter.raider() != null
        && !isRaiderRespawnClosed()) {
      raiderCombat.respawn();
      raiderHitFlash.clear();
      raiderEncounter.respawnAt(WORLD, astra().x() + astra().drawWidth() / 2f,
          astra().y() + astra().drawHeight() / 2f, bodyRadius(astra()));
    }
  }

  /**
   * True once the raider must never reappear: the mission already has a terminal outcome or the
   * approved three-kill goal has been reached. The single raider instance is kept either way, so no
   * third enemy is created and no texture is loaded; only the respawn is withheld.
   */
  private boolean isRaiderRespawnClosed() {
    return mission.isFinished() || mission.vesperKills() >= mission.vesperKillGoal();
  }

  private void destroyAstra() {
    if (astraCombat.isDestroyed()) {
      return;
    }
    astraCombat.destroy();
    astraHitFlash.clear();
    astraDeathX = astra().x();
    astraDeathY = astra().y();
    // The idempotent guard above makes this the single ACTIVE→DESTROYED transition, so exactly one
    // life loss is queued even when a projectile and a contact resolve in the same frame.
    pendingAstraLifeLosses++;
  }

  private void destroyRaider() {
    if (raiderCombat.isDestroyed()) {
      return;
    }
    VesperRaider raider = raiderEncounter.raider();
    if (raider != null) {
      raiderDeathCenterX = raider.centerX();
      raiderDeathCenterY = raider.centerY();
    }
    raiderCombat.destroy();
    raiderHitFlash.clear();
    raiderEncounter.destroyActive();
    // One real destruction advances the mission counter exactly once, in the same transition that
    // clears the encounter. The 1000-point bonus is tied to an accepted kill so a destruction after
    // a terminal outcome neither counts nor scores, while kills 1/2/3 still award 1000 each.
    if (mission.recordVesperKill()) {
      pendingCombatPoints += score.awardRaiderDestroyed();
    }
  }

  /**
   * Destroys exactly the live drone with the given id and records its death exactly once. The drone
   * leaves {@code activeDrones()}, movement, collision and the group marker immediately, never
   * respawns and never re-emits. Only a shot destruction awards the 100-point bonus; collisions
   * award nothing. A destroyed or already-retired id is a no-op, so duplicate projectiles of one
   * frame or a contact after a shot can never double-reward.
   */
  private void destroyGhostwingDrone(long droneId, GhostwingDeath.Cause cause) {
    GhostwingDrone drone = activeDrone(droneId);
    if (drone == null) {
      return;
    }
    ghostwingEncounter.markDestroyed(droneId);
    pendingGhostwingDeaths.add(
        new GhostwingDeath(droneId, drone.centerX(), drone.centerY(), cause));
    if (cause == GhostwingDeath.Cause.SHOT) {
      pendingCombatPoints += score.awardGhostwingDestroyed();
    }
  }

  /** The live drone with the given stable id, or {@code null} once it is destroyed or retired. */
  private GhostwingDrone activeDrone(long droneId) {
    for (GhostwingDrone drone : ghostwingEncounter.activeDrones()) {
      if (drone.id() == droneId) {
        return drone;
      }
    }
    return null;
  }

  /**
   * Resolves one Astra projectile segment against every live target it crosses. All candidate hulls
   * (the Vesper Raider and each active Ghostwing drone) compete on the first entry fraction of the
   * swept segment; ties break by target kind and then drone id, so the winner is stable and
   * independent of iteration order. One projectile impacts at most one target. A Ghostwing drone is
   * destroyed by the single impact (weak one-shot target), a hit Raider keeps its existing
   * ten-impact damage step, and a protected (invulnerable) hull absorbs the projectile without
   * damage. A drone already removed by {@link GhostwingEncounter#markDestroyed} is never a target.
   */
  private void astraProjectileStep(GunProjectile projectile, float fromX, float fromY,
      float toX, float toY) {
    if (projectile.isConsumed()) {
      return;
    }
    CollisionDetector.Segment segment = new CollisionDetector.Segment(fromX, fromY, toX, toY);
    ProjectileTarget best = null;
    GhostwingDrone bestDrone = null;
    VesperRaider raider = activeRaider();
    if (raider != null && !raiderCombat.isDestroyed()) {
      double fraction = CollisionDetector.firstHit(segment, bodyCircle(raider))
          .orElse(Double.POSITIVE_INFINITY);
      if (Double.isFinite(fraction)) {
        ProjectileTarget candidate = raiderCombat.canBeHit()
            ? ProjectileTarget.raider(fraction)
            : ProjectileTarget.raiderShield(fraction);
        if (candidate.precedes(best)) {
          best = candidate;
        }
      }
    }
    for (GhostwingDrone drone : ghostwingEncounter.activeDrones()) {
      double fraction = CollisionDetector.firstHit(segment, bodyCircle(drone))
          .orElse(Double.POSITIVE_INFINITY);
      if (!Double.isFinite(fraction)) {
        continue;
      }
      ProjectileTarget candidate = ProjectileTarget.drone(fraction, drone.id());
      if (candidate.precedes(best)) {
        best = candidate;
        bestDrone = drone;
      }
    }
    if (best == null) {
      return;
    }
    projectile.consume();
    if (bestDrone != null) {
      // The winner is a drone that was live when chosen; destroying it removes it from
      // activeDrones() immediately, so a second projectile of the same frame never re-hits it.
      destroyGhostwingDrone(bestDrone.id(), GhostwingDeath.Cause.SHOT);
      return;
    }
    if (best.kind() == TARGET_RAIDER_SHIELD) {
      // Protected hull: the projectile is spent on contact but deals no damage.
      return;
    }
    // Every counted impact flashes the ship actually hit, even the first nine that do not yet
    // complete a damage step; the pulse is presentation only and never decides damage (CA20-21).
    raiderHitFlash.trigger();
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
    astraHitFlash.trigger();
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
   * Emission-time snapshot provider for the raider cannon. It samples the trajectory the encounter
   * recorded while flying this frame at each frame-local {@code offsetSeconds}, so a fast pass or
   * turn emits from distinct points, headings and muzzle poses instead of repeating the final frame
   * state. The encounter owns the kinematics and returns {@code null} when no raider exists; each
   * {@code Shot} still freezes origin and direction, and no projectile retains a ship reference.
   */
  private GunBurst.ShotSource raiderShotSource(float shotRange) {
    return offsetSeconds -> raiderEncounter.shotAt(offsetSeconds, shotRange);
  }

  /** Astra's box position and heading before and after this frame's bounded flight. */
  private record AstraMotion(float beforeX, float beforeY, float beforeYaw,
      float afterX, float afterY, float afterYaw, float duration) {}

  /**
   * One candidate hull struck by an Astra projectile segment, ordered by first-entry fraction and
   * then by a stable priority so ties never depend on iteration order: a Ghostwing drone wins a tie
   * against a protected or vulnerable Raider, and the smaller drone id wins between two drones.
   */
  private record ProjectileTarget(double fraction, int kind, long droneId) {

    static ProjectileTarget drone(double fraction, long droneId) {
      return new ProjectileTarget(fraction, TARGET_GHOSTWING, droneId);
    }

    static ProjectileTarget raider(double fraction) {
      return new ProjectileTarget(fraction, TARGET_RAIDER, 0L);
    }

    static ProjectileTarget raiderShield(double fraction) {
      return new ProjectileTarget(fraction, TARGET_RAIDER_SHIELD, 0L);
    }

    boolean precedes(ProjectileTarget other) {
      if (other == null) {
        return true;
      }
      if (fraction != other.fraction) {
        return fraction < other.fraction;
      }
      if (kind != other.kind) {
        return kind < other.kind;
      }
      return droneId < other.droneId;
    }
  }

  /** Total points this run has awarded so far, including destruction bonuses. */
  public int scorePoints() {
    return score.totalPoints();
  }

  /** Vesper Raider destructions recorded by the mission, capped at the kill goal. */
  public int vesperKills() {
    return mission.vesperKills();
  }

  /** Vesper Raider destructions needed to win the mission. */
  public int vesperKillGoal() {
    return mission.vesperKillGoal();
  }

  /** Total game time the mission is allowed to last, in seconds (10:00). */
  public float missionTimeLimitSeconds() {
    return mission.timeLimitSeconds();
  }

  /** The mission outcome decided so far: in progress, or the single terminal WIN/LOSE result. */
  public PhaseOutcome missionOutcome() {
    return mission.outcome();
  }

  /**
   * Resolves the mission for this frame from the authoritative lives and the time left in the run
   * clock. Applies the approved priority (lives exhausted loses first, then the kill goal wins,
   * then the expired clock loses) and is idempotent once an outcome is terminal. The screen and the
   * lives child will call it once per active frame; this child only exposes the state and the rule.
   */
  public PhaseOutcome resolveMissionOutcome(int astraLivesRemaining) {
    return mission.resolve(astraLivesRemaining, remainingRunSeconds());
  }

  /**
   * Records one valid Vesper Raider destruction in the mission, counting each real kill exactly once
   * up to the kill goal. Package-private seam used by the kill-counting and lives children and by
   * the deterministic tests: the presentation must never mutate mission state on its own.
   *
   * @return {@code true} when this call advanced the kill counter
   */
  boolean recordVesperKill() {
    return mission.recordVesperKill();
  }

  /**
   * Advances the single raider toward Astra in world coordinates, supplying the player's centre,
   * velocity and body radius so the encounter can avoid a predicted ram. Projectile paths already
   * in flight are untouched by ship movement.
   */
  public void advanceEncounter(float deltaSeconds) {
    if (!isRunStarted() || (!isActiveFrameOpen() && isRunFinished())) {
      return;
    }
    float activeSeconds = boundedActiveSeconds(deltaSeconds);
    if (activeSeconds <= 0f) {
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
    raiderEncounter.advance(activeSeconds, WORLD, targetCenterX, targetCenterY,
        velocityX, velocityY, bodyRadius(astra()), RAIDER_BODY_RADIUS);
  }

  /**
   * Advances the Ghostwing drone groups using only the portion of this frame that still belongs to
   * the active run, independently of the Raider encounter. Groups are scheduled and flown entirely
   * in world coordinates; weapons, scoring, camera and the Vesper behaviour stay untouched. Non-
   * positive or non-finite deltas are ignored, like every other phase-advance method.
   */
  public void advanceGhostwing(float deltaSeconds) {
    if (!isRunStarted() || (!isActiveFrameOpen() && isRunFinished())) {
      return;
    }
    float activeSeconds = boundedActiveSeconds(deltaSeconds);
    if (activeSeconds <= 0f) {
      return;
    }
    ghostwingEncounter.advance(activeSeconds, WORLD);
  }

  /** The Ghostwing group encounter owned by this controller, for presentation and tests. */
  public GhostwingEncounter ghostwingEncounter() {
    return ghostwingEncounter;
  }

  public boolean isRaiderActive() {
    return raiderEncounter.isActive();
  }

  /** The live raider, or {@code null} while it has not appeared or is destroyed awaiting respawn. */
  public VesperRaider activeRaider() {
    return raiderEncounter.isActive() ? raiderEncounter.raider() : null;
  }

  /** World centre captured at Astra's last destruction, so the screen can anchor her explosion. */
  public float astraDeathCenterX() {
    return astraDeathX + astra().drawWidth() / 2f;
  }

  public float astraDeathCenterY() {
    return astraDeathY + astra().drawHeight() / 2f;
  }

  /** World centre captured at the raider's last destruction, for its explosion. */
  public float raiderDeathCenterX() {
    return raiderDeathCenterX;
  }

  public float raiderDeathCenterY() {
    return raiderDeathCenterY;
  }

}
