package com.davidpe.cosmicaces.domain.enemy;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Schedules one Geran-4 UAV every {@link #SPAWN_INTERVAL_SECONDS} seconds of active game time and
 * places it around the real camera viewport, independently of the Vesper Raider and Ghostwing
 * encounters. The first UAV appears exactly at 30 active seconds; pausing the run, the objective
 * screen or the result screen never advances the calendar because callers only pass active time.
 *
 * <p>Placement works in the camera's rotated frame: a side and a lateral coordinate are drawn from
 * {@link UnitRandom}, and the UAV is born near that visible edge when there is no dogfight or fully
 * outside the real viewport when Astra is fighting. Its fixed heading points straight across the
 * view, so splitting the frame delta never bends the trajectory. A candidate that would leave the
 * world or overlap Astra is rejected and the next camera side is tried, keeping the spawn valid
 * even when the camera sits next to a world edge.
 *
 * <p>The encounter only schedules and flies UAVs. Weapons, projectile impacts, hull contact,
 * explosions and drawing belong to later children; {@link #markDestroyed(long)} is the idempotent
 * removal seam they will use.
 */
public final class Geran4Encounter {

  /** Active seconds between two scheduled UAVs; the first pulse also happens at this instant. */
  public static final float SPAWN_INTERVAL_SECONDS = 10f;
  /** Slow fixed UAV speed in world units per second, well below Astra's 230 u/s cruise. */
  public static final float DRONE_SPEED = 110f;
  /**
   * Separation kept beyond the UAV box when it must be born completely outside the viewport during
   * a dogfight, so it starts off-camera and enters after a short straight flight.
   */
  public static final float DOGFIGHT_CLEARANCE = 140f;
  /** Extra separation inside the visible edge, so the whole sprite is born complete and unclipped. */
  public static final float ENTRY_INSET = 10f;
  /** Extra separation from Astra, so a UAV never appears overlapping the player's ship. */
  public static final float ASTRA_CLEARANCE = 16f;

  private static final int SIDE_COUNT = 4;
  private static final float HALF_BOX = 0.5f * Math.max(Geran4Uav.BOX_WIDTH,
      Geran4Uav.BOX_HEIGHT);
  /** Circumscribed half diagonal: a rotated square never projects farther than this on any axis. */
  private static final float HALF_DIAGONAL = HALF_BOX * (float) Math.sqrt(2d);

  private final UnitRandom random;
  private final List<Geran4Uav> drones = new ArrayList<>();
  private float secondsUntilNextSpawn = SPAWN_INTERVAL_SECONDS;
  private long nextDroneId;

  public Geran4Encounter() {
    this(ThreadLocalRandom.current()::nextFloat);
  }

  public Geran4Encounter(UnitRandom random) {
    if (random == null) {
      throw new IllegalArgumentException("Random source must not be null");
    }
    this.random = random;
  }

  /** Live UAVs in spawn order, as a read-only view. */
  public List<Geran4Uav> activeDrones() {
    return Collections.unmodifiableList(drones);
  }

  /** Active seconds left until the next scheduled pulse, exposed for deterministic tests. */
  public float secondsUntilNextSpawn() {
    return secondsUntilNextSpawn;
  }

  /**
   * Advances the calendar and every live UAV by {@code activeSeconds} of active game time. A large
   * delta resolves each due pulse (never skipped) and moves the new UAV by the time left after its
   * own pulse, so positions stay staggered instead of piling up at one point. Non-positive or
   * non-finite deltas are ignored. UAVs whose whole box leaves the world are retired without any
   * event or reward.
   *
   * @param world finite world the UAV must be born inside and leave to retire
   * @param viewport the camera geometry this frame actually draws
   * @param astraCenterX world X of Astra's box centre
   * @param astraCenterY world Y of Astra's box centre
   * @param astraHalfBox half of Astra's larger box side, used as keep-out radius
   * @param dogfight whether Astra is fighting a raider or an attacking fighter escort this frame
   */
  public void advance(float activeSeconds, WorldBounds world, ViewportFrame viewport,
      float astraCenterX, float astraCenterY, float astraHalfBox, boolean dogfight) {
    if (world == null) {
      throw new IllegalArgumentException("World is required");
    }
    if (viewport == null) {
      throw new IllegalArgumentException("Viewport is required");
    }
    if (activeSeconds <= 0f || !Float.isFinite(activeSeconds)) {
      return;
    }
    advanceExistingDrones(activeSeconds, world);
    float remaining = activeSeconds;
    while (secondsUntilNextSpawn <= remaining) {
      remaining -= secondsUntilNextSpawn;
      secondsUntilNextSpawn = SPAWN_INTERVAL_SECONDS;
      Geran4Uav drone = spawn(world, viewport, astraCenterX, astraCenterY, astraHalfBox,
          dogfight);
      if (remaining > 0f) {
        drone.advance(remaining);
      }
      if (!hasLeftWorld(drone, world)) {
        drones.add(drone);
      }
    }
    secondsUntilNextSpawn -= remaining;
  }

  /**
   * Removes the UAV with the given id from the encounter. Idempotent for already-destroyed or
   * unknown ids; never respawns or recycles the UAV.
   */
  public void markDestroyed(long droneId) {
    Iterator<Geran4Uav> iterator = drones.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().id() == droneId) {
        iterator.remove();
        return;
      }
    }
  }

  private void advanceExistingDrones(float activeSeconds, WorldBounds world) {
    Iterator<Geran4Uav> iterator = drones.iterator();
    while (iterator.hasNext()) {
      Geran4Uav drone = iterator.next();
      drone.advance(activeSeconds);
      if (hasLeftWorld(drone, world)) {
        iterator.remove();
      }
    }
  }

  private Geran4Uav spawn(WorldBounds world, ViewportFrame viewport, float astraCenterX,
      float astraCenterY, float astraHalfBox, boolean dogfight) {
    int firstSide = sideIndex();
    for (int offset = 0; offset < SIDE_COUNT; offset++) {
      int side = (firstSide + offset) % SIDE_COUNT;
      Geran4Uav candidate = candidate(side, world, viewport, astraCenterX, astraCenterY,
          astraHalfBox, dogfight);
      if (candidate != null) {
        return candidate;
      }
    }
    // Degenerate world smaller than the viewport plus the keep-out margins: fly the UAV straight
    // toward the camera from the farthest world corner instead of dropping the scheduled pulse.
    return fallback(world, viewport);
  }

  /**
   * Builds one candidate for a camera side, or returns {@code null} when it would leave the world,
   * overlap Astra or break the requested inside/outside relation.
   *
   * <p>Sides 0 and 1 are the camera's right and left edges; sides 2 and 3 are its top and bottom
   * edges. The lateral coordinate is drawn uniformly inside the visible strip, so the whole sprite
   * fits on that axis and the straight flight crosses the player's visible zone.
   */
  private Geran4Uav candidate(int side, WorldBounds world, ViewportFrame viewport,
      float astraCenterX, float astraCenterY, float astraHalfBox, boolean dogfight) {
    boolean horizontal = side < 2;
    float halfAlong = horizontal ? viewport.halfWidth() : viewport.halfHeight();
    float halfLateral = horizontal ? viewport.halfHeight() : viewport.halfWidth();
    float lateralLimit = Math.max(0f, halfLateral - HALF_DIAGONAL - ENTRY_INSET);
    float lateral = (2f * random.nextUnit() - 1f) * lateralLimit;
    float sign = side % 2 == 0 ? 1f : -1f;
    float along = dogfight
        ? halfAlong + HALF_DIAGONAL + DOGFIGHT_CLEARANCE
        : Math.max(0f, halfAlong - HALF_DIAGONAL - ENTRY_INSET);
    float signedAlong = sign * along;

    float astraAlong = horizontal
        ? viewport.localRight(astraCenterX, astraCenterY)
        : viewport.localUp(astraCenterX, astraCenterY);
    float astraLateral = horizontal
        ? viewport.localUp(astraCenterX, astraCenterY)
        : viewport.localRight(astraCenterX, astraCenterY);
    float minimumDistance = astraHalfBox + HALF_DIAGONAL + ASTRA_CLEARANCE;
    float alongGap = signedAlong - astraAlong;
    if (alongGap * alongGap < minimumDistance * minimumDistance) {
      float requiredLateral = (float) Math.sqrt(
          minimumDistance * minimumDistance - alongGap * alongGap);
      float away = lateral >= astraLateral ? 1f : -1f;
      float shifted = astraLateral + away * requiredLateral;
      float mirrored = astraLateral - away * requiredLateral;
      if (Math.abs(shifted) <= lateralLimit) {
        lateral = shifted;
      } else if (Math.abs(mirrored) <= lateralLimit) {
        lateral = mirrored;
      } else {
        return null;
      }
    }

    float right = horizontal ? signedAlong : lateral;
    float up = horizontal ? lateral : signedAlong;
    float centerX = viewport.worldX(right, up);
    float centerY = viewport.worldY(right, up);
    if (!insideWorld(centerX, centerY, world)) {
      return null;
    }
    if (dogfight != fullyOutside(centerX, centerY, viewport)) {
      return null;
    }
    if (!dogfight) {
      if (!fullyInside(centerX, centerY, viewport)) {
        return null;
      }
    }
    float directionX = horizontal ? -sign * viewport.upY() : -sign * viewport.upX();
    float directionY = horizontal ? sign * viewport.upX() : -sign * viewport.upY();
    return new Geran4Uav(nextDroneId++, DRONE_SPEED, centerX - Geran4Uav.BOX_WIDTH / 2f,
        centerY - Geran4Uav.BOX_HEIGHT / 2f, directionX, directionY);
  }

  private Geran4Uav fallback(WorldBounds world, ViewportFrame viewport) {
    float bestX = 0f;
    float bestY = 0f;
    float bestDistance = -1f;
    float[] xs = {0f, world.maxX(Geran4Uav.BOX_WIDTH)};
    float[] ys = {0f, world.maxY(Geran4Uav.BOX_HEIGHT)};
    for (float x : xs) {
      for (float y : ys) {
        float centerX = x + Geran4Uav.BOX_WIDTH / 2f;
        float centerY = y + Geran4Uav.BOX_HEIGHT / 2f;
        float distance = (float) Math.hypot(centerX - viewport.centerX(),
            centerY - viewport.centerY());
        if (distance > bestDistance) {
          bestDistance = distance;
          bestX = x;
          bestY = y;
        }
      }
    }
    float centerX = bestX + Geran4Uav.BOX_WIDTH / 2f;
    float centerY = bestY + Geran4Uav.BOX_HEIGHT / 2f;
    float directionX = viewport.centerX() - centerX;
    float directionY = viewport.centerY() - centerY;
    if (Math.hypot(directionX, directionY) < 1e-4f) {
      directionX = 1f;
      directionY = 0f;
    }
    return new Geran4Uav(nextDroneId++, DRONE_SPEED, bestX, bestY, directionX, directionY);
  }

  private int sideIndex() {
    return Math.min(SIDE_COUNT - 1, (int) (random.nextUnit() * SIDE_COUNT));
  }

  private static boolean insideWorld(float centerX, float centerY, WorldBounds world) {
    return centerX - Geran4Uav.BOX_WIDTH / 2f >= 0f
        && centerY - Geran4Uav.BOX_HEIGHT / 2f >= 0f
        && centerX + Geran4Uav.BOX_WIDTH / 2f <= world.width()
        && centerY + Geran4Uav.BOX_HEIGHT / 2f <= world.height();
  }

  /** Whole rotated box beyond the visible polygon, using the circumscribed half diagonal. */
  private static boolean fullyOutside(float centerX, float centerY, ViewportFrame viewport) {
    float right = Math.abs(viewport.localRight(centerX, centerY));
    float up = Math.abs(viewport.localUp(centerX, centerY));
    return right > viewport.halfWidth() + HALF_DIAGONAL
        || up > viewport.halfHeight() + HALF_DIAGONAL;
  }

  /** Whole rotated box inside the visible polygon, using the circumscribed half diagonal. */
  private static boolean fullyInside(float centerX, float centerY, ViewportFrame viewport) {
    float right = Math.abs(viewport.localRight(centerX, centerY));
    float up = Math.abs(viewport.localUp(centerX, centerY));
    return right + HALF_DIAGONAL <= viewport.halfWidth()
        && up + HALF_DIAGONAL <= viewport.halfHeight();
  }

  private static boolean hasLeftWorld(Geran4Uav drone, WorldBounds world) {
    return drone.x() + drone.drawWidth() < 0f
        || drone.x() > world.width()
        || drone.y() + drone.drawHeight() < 0f
        || drone.y() > world.height();
  }

  /**
   * Immutable geometry of the camera view actually drawn this frame: centre, {@code up} vector
   * (which encodes yaw), zoom and logical viewport size. Mirrors {@code CameraVisibility}: the
   * visible half extents are {@code view/2 * zoom}, and {@code right = (upY, -upX)}.
   */
  public record ViewportFrame(float centerX, float centerY, float upX, float upY, float zoom,
      float viewWidth, float viewHeight) {

    public ViewportFrame {
      if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
          || !Float.isFinite(upX) || !Float.isFinite(upY) || !Float.isFinite(zoom)
          || !Float.isFinite(viewWidth) || !Float.isFinite(viewHeight)) {
        throw new IllegalArgumentException("Viewport geometry must be finite");
      }
      double magnitude = Math.hypot(upX, upY);
      if (magnitude < 1e-6d) {
        throw new IllegalArgumentException("Camera up vector must not be the zero vector");
      }
      if (zoom <= 0f || viewWidth <= 0f || viewHeight <= 0f) {
        throw new IllegalArgumentException("Viewport extents and zoom must be positive");
      }
      upX = (float) (upX / magnitude);
      upY = (float) (upY / magnitude);
    }

    public float halfWidth() {
      return viewWidth * zoom / 2f;
    }

    public float halfHeight() {
      return viewHeight * zoom / 2f;
    }

    /** Signed distance of a world point along the camera's right axis. */
    public float localRight(float worldX, float worldY) {
      return (worldX - centerX) * upY - (worldY - centerY) * upX;
    }

    /** Signed distance of a world point along the camera's up axis. */
    public float localUp(float worldX, float worldY) {
      return (worldX - centerX) * upX + (worldY - centerY) * upY;
    }

    /** World X of a point given its coordinates in the camera frame. */
    public float worldX(float right, float up) {
      return centerX + right * upY + up * upX;
    }

    /** World Y of a point given its coordinates in the camera frame. */
    public float worldY(float right, float up) {
      return centerY - right * upX + up * upY;
    }
  }
}
