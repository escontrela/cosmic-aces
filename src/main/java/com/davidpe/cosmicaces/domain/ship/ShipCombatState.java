package com.davidpe.cosmicaces.domain.ship;

/**
 * Deterministic combat energy for one ship. Every fixed number of counted projectile impacts applies
 * a fixed damage step, so the residual impact count survives between frames but is reset by a new
 * life. Death, the death timer and the post-respawn invulnerability window are explicit, idempotent
 * transitions; animation length never drives them.
 *
 * <p>The class owns no LibGDX, world, rendering or scoring state. The phase controller decides when a
 * ship is destroyed (energy reaches zero or a vulnerable contact happens) and when it respawns, so a
 * single destruction event can be observed exactly once for the kill bonus.
 */
public final class ShipCombatState {

  /** Full energy of both ships, expressed as an integer percentage. */
  public static final int MAX_ENERGY = 100;

  /** Player damage model: one point of energy every ten impacts. */
  private static final int ASTRA_IMPACTS_PER_DAMAGE = 10;
  private static final int ASTRA_DAMAGE_PER_STEP = 1;
  private static final float ASTRA_RESPAWN_SECONDS = 3f;
  private static final float ASTRA_INVULNERABILITY_SECONDS = 2f;

  /** Raider damage model: thirty-five points of energy every ten impacts. */
  private static final int VESPER_IMPACTS_PER_DAMAGE = 10;
  private static final int VESPER_DAMAGE_PER_STEP = 35;
  private static final float VESPER_RESPAWN_SECONDS = 5f;
  private static final float VESPER_INVULNERABILITY_SECONDS = 0f;

  public enum State {
    ACTIVE,
    DESTROYED,
    INVULNERABLE
  }

  private final int impactsPerDamage;
  private final int damagePerStep;
  private final float respawnSeconds;
  private final float invulnerabilitySeconds;

  private int energy = MAX_ENERGY;
  private int pendingImpacts;
  private int lifeGeneration;
  private State state = State.ACTIVE;
  private float timeInState;

  /**
   * @param impactsPerDamage impacts that trigger one damage step; must be positive
   * @param damagePerStep energy removed by each damage step; must be positive
   * @param respawnSeconds wait after death before {@link #readyToRespawn()}; must be non-negative
   * @param invulnerabilitySeconds protection window granted on respawn; must be non-negative
   */
  public ShipCombatState(int impactsPerDamage, int damagePerStep,
      float respawnSeconds, float invulnerabilitySeconds) {
    if (impactsPerDamage <= 0) {
      throw new IllegalArgumentException("Impacts per damage must be positive");
    }
    if (damagePerStep <= 0) {
      throw new IllegalArgumentException("Damage per step must be positive");
    }
    if (!Float.isFinite(respawnSeconds) || respawnSeconds < 0f) {
      throw new IllegalArgumentException("Respawn delay must be finite and non-negative");
    }
    if (!Float.isFinite(invulnerabilitySeconds) || invulnerabilitySeconds < 0f) {
      throw new IllegalArgumentException("Invulnerability window must be finite and non-negative");
    }
    this.impactsPerDamage = impactsPerDamage;
    this.damagePerStep = damagePerStep;
    this.respawnSeconds = respawnSeconds;
    this.invulnerabilitySeconds = invulnerabilitySeconds;
  }

  /** Player ship model: 1% per ten impacts, 3 s respawn and 2 s of invulnerability. */
  public static ShipCombatState astra() {
    return new ShipCombatState(ASTRA_IMPACTS_PER_DAMAGE, ASTRA_DAMAGE_PER_STEP,
        ASTRA_RESPAWN_SECONDS, ASTRA_INVULNERABILITY_SECONDS);
  }

  /** Raider model: 35% per ten impacts, 5 s respawn and no invulnerability. */
  public static ShipCombatState vesper() {
    return new ShipCombatState(VESPER_IMPACTS_PER_DAMAGE, VESPER_DAMAGE_PER_STEP,
        VESPER_RESPAWN_SECONDS, VESPER_INVULNERABILITY_SECONDS);
  }

  /**
   * Counts one projectile impact. Impacts are always accumulated (nine impacts do not damage, the
   * tenth does and keeps the residual zero); a damage step that empties the energy leaves it
   * clamped at zero but does not destroy the ship here, so the controller can attribute the single
   * destruction event. Impacts on a non-active ship are ignored.
   *
   * @return {@code true} when this impact completed a damage step
   */
  public boolean receiveProjectileHit() {
    if (state != State.ACTIVE) {
      return false;
    }
    pendingImpacts++;
    if (pendingImpacts < impactsPerDamage) {
      return false;
    }
    pendingImpacts -= impactsPerDamage;
    energy = Math.max(0, energy - damagePerStep);
    return true;
  }

  /** Marks the ship destroyed once, starting the death timer. Repeated calls are no-ops. */
  public void destroy() {
    if (state == State.DESTROYED) {
      return;
    }
    state = State.DESTROYED;
    energy = 0;
    timeInState = 0f;
  }

  /**
   * Advances the death or invulnerability timer. When the invulnerability window ends the ship
   * becomes active again; a dead ship stays dead until the controller respawns it.
   */
  public void advance(float deltaSeconds) {
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
      return;
    }
    if (state == State.INVULNERABLE) {
      timeInState += deltaSeconds;
      if (timeInState >= invulnerabilitySeconds) {
        state = State.ACTIVE;
        timeInState = 0f;
      }
    } else if (state == State.DESTROYED) {
      timeInState += deltaSeconds;
    }
  }

  /** True once a destroyed ship has waited the full respawn delay. */
  public boolean readyToRespawn() {
    return state == State.DESTROYED && timeInState >= respawnSeconds;
  }

  /**
   * Restores a destroyed ship to full energy with a fresh residual counter and a new life
   * generation. The controller places the ship in the world; this method only resets combat state.
   * Respawn grants no Ultra: it does not touch player flight state. Repeated calls are no-ops.
   */
  public void respawn() {
    if (state != State.DESTROYED) {
      return;
    }
    energy = MAX_ENERGY;
    pendingImpacts = 0;
    lifeGeneration++;
    state = invulnerabilitySeconds > 0f ? State.INVULNERABLE : State.ACTIVE;
    timeInState = 0f;
  }

  public State state() {
    return state;
  }

  public boolean isDestroyed() {
    return state == State.DESTROYED;
  }

  public boolean isInvulnerable() {
    return state == State.INVULNERABLE;
  }

  /** True while the ship may move and shoot: active or inside its post-respawn protection. */
  public boolean canAct() {
    return state != State.DESTROYED;
  }

  /** True while the ship may take damage: active but neither destroyed nor invulnerable. */
  public boolean canBeHit() {
    return state == State.ACTIVE;
  }

  public int energyPercent() {
    return energy;
  }

  /** Monotonic life counter, useful to discard stale events from a previous life. */
  public int lifeGeneration() {
    return lifeGeneration;
  }

  public float secondsSinceDeath() {
    return state == State.DESTROYED ? timeInState : 0f;
  }

  public float invulnerabilityRemaining() {
    return state == State.INVULNERABLE ? Math.max(0f, invulnerabilitySeconds - timeInState) : 0f;
  }
}
