package com.davidpe.cosmicaces.domain.weapon;

import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.CombustionStage;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Faction;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Heading;
import java.util.ArrayList;
import java.util.List;

/** One guided missile in world coordinates. It owns no graphics or input resources. */
public final class GuidedMissile {
  private static final float MAX_SUBSTEP_SECONDS = 1f / 120f;
  private static final float EPSILON_SECONDS = 0.000001f;

  public enum Owner { ASTRA, ENEMY }
  public enum FlightStage { SEPARATING, ACCELERATING, GUIDED, RETIRED }
  public enum Retirement { NONE, FUEL_EXHAUSTED, TARGET_INVALID, IMPACTED }

  /** One bounded piece of this frame's swept path, ready for downstream collision checks. */
  public record Segment(float fromX, float fromY, float toX, float toY,
      float startElapsedSeconds, float endElapsedSeconds) {
    public Segment {
      if (!Float.isFinite(fromX) || !Float.isFinite(fromY)
          || !Float.isFinite(toX) || !Float.isFinite(toY)
          || !Float.isFinite(startElapsedSeconds) || !Float.isFinite(endElapsedSeconds)
          || startElapsedSeconds < 0f || endElapsedSeconds < startElapsedSeconds) {
        throw new IllegalArgumentException("Missile segment values must be finite and ordered");
      }
    }
  }

  private final Owner owner;
  private final MissileTarget.Reference target;
  private final MissileConfig config;
  private final float launchSpeed;
  private float x;
  private float y;
  private float headingDegrees;
  private float speed;
  private float elapsedSeconds;
  private FlightStage stage = FlightStage.SEPARATING;
  private Retirement retirement = Retirement.NONE;

  /**
   * Launches at the caller-supplied world position (for Astra, below the hull). Heading uses
   * clockwise degrees from north, matching Astra and the missile atlas.
   */
  public GuidedMissile(Owner owner, MissileTarget.Reference target, MissileConfig config,
      float x, float y, float headingDegrees, float launcherVelocityX, float launcherVelocityY) {
    if (owner == null || target == null || config == null) {
      throw new IllegalArgumentException("Owner, exact target reference and config are required");
    }
    if ((owner == Owner.ASTRA && target.kind() == MissileTarget.Kind.ASTRA)
        || (owner == Owner.ENEMY && target.kind() != MissileTarget.Kind.ASTRA)
        || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(headingDegrees)
        || !Float.isFinite(launcherVelocityX) || !Float.isFinite(launcherVelocityY)) {
      throw new IllegalArgumentException("Launch state must be finite and have an assigned target");
    }
    this.owner = owner;
    this.target = target;
    this.config = config;
    this.x = x;
    this.y = y;
    this.headingDegrees = normalizeDegrees(headingDegrees);
    launchSpeed = (float) Math.min(config.maxSpeed(), Math.hypot(launcherVelocityX, launcherVelocityY));
    speed = launchSpeed;
  }

  public Owner owner() { return owner; }
  public MissileTarget.Reference targetReference() { return target; }
  public float x() { return x; }
  public float y() { return y; }
  public float headingDegrees() { return headingDegrees; }
  public Heading heading() { return Heading.values()[Math.floorMod(Math.round(headingDegrees / 45f), 8)]; }
  public float speed() { return speed; }
  public float elapsedSeconds() { return elapsedSeconds; }
  public float fuelRemainingSeconds() { return Math.max(0f, config.fuelSeconds() - elapsedSeconds); }
  public FlightStage stage() { return stage; }
  public Retirement retirement() { return retirement; }
  public boolean isActive() { return stage != FlightStage.RETIRED; }
  public Faction faction() { return owner == Owner.ASTRA ? Faction.ASTRA : Faction.ENEMY; }

  public CombustionStage combustionStage() {
    if (!isActive()) return CombustionStage.SPENT;
    if (stage == FlightStage.SEPARATING) return CombustionStage.IGNITION;
    if (fuelRemainingSeconds() <= config.fuelSeconds() * 0.25f) return CombustionStage.FADING;
    return CombustionStage.BURNING;
  }

  /**
   * Advances no farther than available fuel, splitting long frames and phase boundaries into
   * bounded segments. A missing or replaced target retires the missile without reacquisition.
   */
  public List<Segment> advance(float deltaSeconds, MissileTarget currentTarget) {
    if (!isActive() || !Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) return List.of();
    if (currentTarget == null || !target.equals(currentTarget.reference())) {
      retire(Retirement.TARGET_INVALID);
      return List.of();
    }

    float remaining = Math.min(deltaSeconds, fuelRemainingSeconds());
    float elapsedThisCall = 0f;
    List<Segment> segments = new ArrayList<>();
    while (remaining > EPSILON_SECONDS && isActive()) {
      updateStage();
      float phaseRemaining = switch (stage) {
        case SEPARATING -> config.separationSeconds() - elapsedSeconds;
        case ACCELERATING -> config.separationSeconds() + config.accelerationSeconds()
            - elapsedSeconds;
        case GUIDED, RETIRED -> Float.POSITIVE_INFINITY;
      };
      float step = Math.min(remaining, Math.min(MAX_SUBSTEP_SECONDS, phaseRemaining));
      if (step <= EPSILON_SECONDS) {
        updateStage();
        continue;
      }
      float startX = x;
      float startY = y;
      float startTime = elapsedSeconds;
      MissileTarget targetAtStep = targetAt(currentTarget, elapsedThisCall);
      if (stage == FlightStage.GUIDED) steerTowardIntercept(targetAtStep, step);
      float nextSpeed = speedAfter(step);
      float distance = (speed + nextSpeed) * 0.5f * step;
      double radians = Math.toRadians(headingDegrees);
      x += (float) (Math.sin(radians) * distance);
      y += (float) (Math.cos(radians) * distance);
      speed = nextSpeed;
      elapsedSeconds += step;
      remaining -= step;
      elapsedThisCall += step;
      segments.add(new Segment(startX, startY, x, y, startTime, elapsedSeconds));
    }
    if (isActive()) updateStage();
    if (fuelRemainingSeconds() <= EPSILON_SECONDS) retire(Retirement.FUEL_EXHAUSTED);
    return List.copyOf(segments);
  }

  /** Marks a collision as the terminal event; damage is resolved by the combat coordinator. */
  public void consumeOnImpact() {
    if (isActive()) retire(Retirement.IMPACTED);
  }

  private float speedAfter(float deltaSeconds) {
    if (stage != FlightStage.ACCELERATING) return speed;
    float accelerationElapsed = elapsedSeconds + deltaSeconds - config.separationSeconds();
    float fraction = Math.min(1f, accelerationElapsed / config.accelerationSeconds());
    return launchSpeed + (config.maxSpeed() - launchSpeed) * fraction;
  }

  private void steerTowardIntercept(MissileTarget liveTarget, float deltaSeconds) {
    float aimTime = interceptTime(liveTarget);
    double desired = Math.toDegrees(Math.atan2(
        liveTarget.x() + liveTarget.velocityX() * aimTime - x,
        liveTarget.y() + liveTarget.velocityY() * aimTime - y));
    float difference = shortestTurn(headingDegrees, (float) desired);
    float maxTurn = config.maxTurnDegreesPerSecond() * deltaSeconds;
    headingDegrees = normalizeDegrees(headingDegrees + clamp(difference, -maxTurn, maxTurn));
  }

  private static MissileTarget targetAt(MissileTarget target, float seconds) {
    return new MissileTarget(target.reference(), target.x() + target.velocityX() * seconds,
        target.y() + target.velocityY() * seconds, target.velocityX(), target.velocityY(),
        target.radius());
  }

  /** Solves constant-velocity interception; falls back to pursuit when no positive solution exists. */
  private float interceptTime(MissileTarget liveTarget) {
    double rx = liveTarget.x() - x;
    double ry = liveTarget.y() - y;
    double vx = liveTarget.velocityX();
    double vy = liveTarget.velocityY();
    double a = vx * vx + vy * vy - speed * speed;
    double b = 2d * (rx * vx + ry * vy);
    double c = rx * rx + ry * ry;
    double time = Double.NaN;
    if (Math.abs(a) < 0.000001d) {
      if (Math.abs(b) > 0.000001d) time = -c / b;
    } else {
      double discriminant = b * b - 4d * a * c;
      if (discriminant >= 0d) {
        double root = Math.sqrt(discriminant);
        double first = (-b - root) / (2d * a);
        double second = (-b + root) / (2d * a);
        if (first > 0d && second > 0d) time = Math.min(first, second);
        else if (first > 0d) time = first;
        else if (second > 0d) time = second;
      }
    }
    if (!Double.isFinite(time) || time <= 0d) {
      time = Math.hypot(rx, ry) / Math.max(speed, 1f);
    }
    return (float) Math.min(fuelRemainingSeconds(), time);
  }

  private void updateStage() {
    if (elapsedSeconds < config.separationSeconds() - EPSILON_SECONDS) {
      stage = FlightStage.SEPARATING;
    } else if (elapsedSeconds < config.separationSeconds() + config.accelerationSeconds()
        - EPSILON_SECONDS) {
      stage = FlightStage.ACCELERATING;
    } else {
      stage = FlightStage.GUIDED;
    }
  }

  private void retire(Retirement reason) {
    stage = FlightStage.RETIRED;
    retirement = reason;
  }

  private static float shortestTurn(float from, float to) {
    return normalizeDegrees(to - from + 180f) - 180f;
  }

  private static float normalizeDegrees(float degrees) {
    float normalized = degrees % 360f;
    return normalized < 0f ? normalized + 360f : normalized;
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }
}
