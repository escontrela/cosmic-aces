package com.davidpe.cosmicaces.domain.enemy;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Schedules and flies Ghostwing drone groups across the whole PhaseOne world, independent of the
 * player and of the Vesper Raider encounter. A group contains between {@link #MIN_GROUP_SIZE} and
 * {@link #MAX_GROUP_SIZE} drones (both ends included), enters completely from outside one world edge
 * and crosses in a straight line at slow speed until every member passes the opposite edge; each
 * member keeps one fixed cardinal direction and never turns, chases or fires.
 *
 * <p>Spawn timing is driven only by active game time: the first group and each following one appear
 * after a random interval in [{@link #MIN_SPAWN_INTERVAL_SECONDS}, {@link #MAX_SPAWN_INTERVAL_SECONDS}],
 * with at most {@link #MAX_ACTIVE_GROUPS} groups alive. When the cap is full a due spawn is
 * postponed (the countdown holds at zero) so freeing a slot produces at most one new group, never a
 * burst. Formation centers are staggered along the axis perpendicular to travel with a spacing
 * larger than the collision box, plus a bounded longitudinal offset along the travel axis.
 *
 * <p>Randomness is injectable via {@link UnitRandom} for deterministic tests; production uses
 * {@link ThreadLocalRandom}. Destroyed drones are removed through {@link #markDestroyed(long)} and
 * are never respawned or recycled.
 */
public final class GhostwingEncounter {

  /** Inclusive bounds for the number of drones per group. */
  public static final int MIN_GROUP_SIZE = 2;
  public static final int MAX_GROUP_SIZE = 10;
  /** Maximum number of groups alive at the same time. */
  public static final int MAX_ACTIVE_GROUPS = 3;
  /** Random wait before the first group and between group spawns, in active seconds. */
  public static final float MIN_SPAWN_INTERVAL_SECONDS = 20f;
  public static final float MAX_SPAWN_INTERVAL_SECONDS = 30f;
  /** Slow fixed drone speed in world units per second, well below the 230 u/s Astra cruise. */
  public static final float DRONE_SPEED = 90f;
  /** World size of the drone box, matched by its square 724×724 sprite cell. */
  public static final float DRONE_WIDTH = 72f;
  public static final float DRONE_HEIGHT = 72f;
  /** Distance between consecutive formation centers along the perpendicular axis. */
  public static final float FORMATION_SPACING = 1.4f * DRONE_WIDTH;
  /** Maximum extra offset each drone may have along its travel axis at entry. */
  public static final float FORMATION_LONGITUDINAL_JITTER = 0.9f * DRONE_WIDTH;
  /** Perpendicular margin kept around the whole formation span so it stays inside the world. */
  private static final float FORMATION_EDGE_MARGIN = FORMATION_SPACING;

  private final UnitRandom random;
  private final List<GhostwingGroup> groups = new ArrayList<>();
  private float nextSpawnSeconds;
  private long nextGroupId;
  private long nextDroneId;

  public GhostwingEncounter() {
    this(ThreadLocalRandom.current()::nextFloat);
  }

  public GhostwingEncounter(UnitRandom random) {
    if (random == null) {
      throw new IllegalArgumentException("Random source must not be null");
    }
    this.random = random;
    nextSpawnSeconds = nextIntervalSeconds();
  }

  /** Active groups in insertion order, as a read-only view. */
  public List<GhostwingGroup> groups() {
    return Collections.unmodifiableList(groups);
  }

  /** Every live drone of every active group, as a read-only view. */
  public List<GhostwingDrone> activeDrones() {
    List<GhostwingDrone> drones = new ArrayList<>();
    for (GhostwingGroup group : groups) {
      drones.addAll(group.members());
    }
    return Collections.unmodifiableList(drones);
  }

  /**
   * Advances the encounter by {@code deltaSeconds} of active game time: schedules due groups and
   * moves every live drone in its fixed direction, retiring members once their whole box passes the
   * opposite world edge. Non-positive or non-finite deltas are ignored. Overall movement and the
   * spawn calendar are linear, so splitting a delta into smaller pieces never changes the result
   * materially. {@link WorldBounds#clampX/Y} are intentionally never applied: groups cross the whole
   * world and are allowed to leave it.
   */
  public void advance(float deltaSeconds, WorldBounds world) {
    if (world == null) {
      throw new IllegalArgumentException("World is required");
    }
    if (deltaSeconds <= 0f || !Float.isFinite(deltaSeconds)) {
      return;
    }
    nextSpawnSeconds -= deltaSeconds;
    if (nextSpawnSeconds <= 0f && groups.size() < MAX_ACTIVE_GROUPS) {
      spawnGroup(world);
    }
    if (nextSpawnSeconds <= 0f) {
      // At cap or just after a spawn: wait for a slot instead of queueing a burst of groups.
      nextSpawnSeconds = groups.size() < MAX_ACTIVE_GROUPS ? nextIntervalSeconds() : 0f;
    }
    advanceGroups(deltaSeconds, world);
  }

  /**
   * Removes the drone with the given id from its group; a group with no remaining members disappears
   * entirely. Idempotent for already-destroyed or unknown ids. Never respawns the drone.
   */
  public void markDestroyed(long droneId) {
    Iterator<GhostwingGroup> iterator = groups.iterator();
    while (iterator.hasNext()) {
      GhostwingGroup group = iterator.next();
      group.removeMember(droneId);
      if (!group.hasActiveMembers()) {
        iterator.remove();
      }
    }
  }

  /**
   * Centroid of the group's active member centres; used by the minimap as the single group marker.
   * Groups only exist while they keep live members, so a fully retired or destroyed group has no
   * representative point.
   */
  public static Point representativePoint(GhostwingGroup group) {
    if (group == null || !group.hasActiveMembers()) {
      throw new IllegalArgumentException("Only an active group has a representative point");
    }
    float sumX = 0f;
    float sumY = 0f;
    int count = 0;
    for (GhostwingDrone drone : group.members()) {
      sumX += drone.centerX();
      sumY += drone.centerY();
      count++;
    }
    return new Point(sumX / count, sumY / count);
  }

  private void spawnGroup(WorldBounds world) {
    GhostwingDrone.Direction direction = pickDirection();
    int size = MIN_GROUP_SIZE
        + (int) (random.nextUnit() * (MAX_GROUP_SIZE - MIN_GROUP_SIZE + 1));
    List<GhostwingDrone> members = new ArrayList<>(size);
    boolean crossesHorizontally = crossesHorizontally(direction);
    final float travelLimit;
    final float perpendicularLimit;
    if (crossesHorizontally) {
      travelLimit = world.width();
      perpendicularLimit = world.height();
    } else {
      travelLimit = world.height();
      perpendicularLimit = world.width();
    }
    float span = (size - 1) * FORMATION_SPACING;
    float lower = FORMATION_EDGE_MARGIN + span / 2f;
    float upper = perpendicularLimit - FORMATION_EDGE_MARGIN - span / 2f;
    float entryPerpendicular = upper > lower
        ? lower + random.nextUnit() * (upper - lower)
        : perpendicularLimit / 2f;
    for (int i = 0; i < size; i++) {
      float perpendicularOffset = (i - (size - 1) / 2f) * FORMATION_SPACING;
      float longitudinalJitter = random.nextUnit() * FORMATION_LONGITUDINAL_JITTER;
      GhostwingDrone drone = newDrone(direction, crossesHorizontally, world,
          entryPerpendicular + perpendicularOffset, longitudinalJitter, travelLimit);
      members.add(drone);
    }
    groups.add(new GhostwingGroup(nextGroupId++, direction, members));
  }

  /** Picks travel axis (horizontal/vertical) and entry sense uniformly. */
  private GhostwingDrone.Direction pickDirection() {
    boolean horizontal = random.nextUnit() < 0.5f;
    boolean positive = random.nextUnit() < 0.5f;
    if (horizontal) {
      return positive ? GhostwingDrone.Direction.EAST : GhostwingDrone.Direction.WEST;
    }
    return positive ? GhostwingDrone.Direction.NORTH : GhostwingDrone.Direction.SOUTH;
  }

  private static boolean crossesHorizontally(GhostwingDrone.Direction direction) {
    return direction == GhostwingDrone.Direction.EAST
        || direction == GhostwingDrone.Direction.WEST;
  }

  /**
   * Places one drone completely outside the world on its entry edge, staggered in the perpendicular
   * axis with a bounded offset along the travel axis. {@code perpendicular} is the box centre of the
   * formation line coordinate; the box top-left is derived for {@link Ship}'s coordinate convention.
   */
  private GhostwingDrone newDrone(GhostwingDrone.Direction direction, boolean crossesHorizontally,
      WorldBounds world, float perpendicular, float longitudinalJitter, float travelLimit) {
    float x;
    float y;
    if (crossesHorizontally) {
      float boxPerpendicular = perpendicular - DRONE_HEIGHT / 2f;
      float jitteredTravel = DRONE_WIDTH + longitudinalJitter;
      if (direction == GhostwingDrone.Direction.EAST) {
        x = -jitteredTravel;
      } else {
        x = travelLimit + jitteredTravel - DRONE_WIDTH;
      }
      y = boxPerpendicular;
    } else {
      float boxPerpendicular = perpendicular - DRONE_WIDTH / 2f;
      float jitteredTravel = DRONE_HEIGHT + longitudinalJitter;
      if (direction == GhostwingDrone.Direction.NORTH) {
        y = -jitteredTravel;
      } else {
        y = travelLimit + jitteredTravel - DRONE_HEIGHT;
      }
      x = boxPerpendicular;
    }
    return new GhostwingDrone(nextDroneId++, DRONE_SPEED, x, y, DRONE_WIDTH, DRONE_HEIGHT,
        direction);
  }

  private void advanceGroups(float deltaSeconds, WorldBounds world) {
    Iterator<GhostwingGroup> iterator = groups.iterator();
    while (iterator.hasNext()) {
      GhostwingGroup group = iterator.next();
      group.advance(deltaSeconds, world);
      if (!group.hasActiveMembers()) {
        iterator.remove();
      }
    }
  }

  private float nextIntervalSeconds() {
    return MIN_SPAWN_INTERVAL_SECONDS
        + random.nextUnit() * (MAX_SPAWN_INTERVAL_SECONDS - MIN_SPAWN_INTERVAL_SECONDS);
  }

  /** A world point in encounter coordinates, returned by value. */
  public record Point(float x, float y) {}

  /**
   * One identifiable group: its id, travel direction and live members. Members leave the group once
   * their whole box passes the opposite world edge or when the controller destroys them; a group
   * with no members is removed by the encounter.
   */
  public static final class GhostwingGroup {

    private final long id;
    private final GhostwingDrone.Direction direction;
    private final List<GhostwingDrone> members;

    GhostwingGroup(long id, GhostwingDrone.Direction direction, List<GhostwingDrone> members) {
      this.id = id;
      this.direction = direction;
      this.members = members;
    }

    /** Stable group identity, shared by the encounter and the minimap marker. */
    public long id() {
      return id;
    }

    /** The direction every member of this group travels in. */
    public GhostwingDrone.Direction direction() {
      return direction;
    }

    /** Live members as a read-only view. */
    public List<GhostwingDrone> members() {
      return Collections.unmodifiableList(members);
    }

    /** Number of live members still crossing the world. */
    public int memberCount() {
      return members.size();
    }

    public boolean hasActiveMembers() {
      return !members.isEmpty();
    }

    /** Moves every member one frame and drops those that fully passed the opposite world edge. */
    void advance(float deltaSeconds, WorldBounds world) {
      Iterator<GhostwingDrone> iterator = members.iterator();
      while (iterator.hasNext()) {
        GhostwingDrone drone = iterator.next();
        drone.advance(deltaSeconds);
        if (hasExitedWorld(drone, world)) {
          iterator.remove();
        }
      }
    }

    void removeMember(long droneId) {
      Iterator<GhostwingDrone> iterator = members.iterator();
      while (iterator.hasNext()) {
        if (iterator.next().id() == droneId) {
          iterator.remove();
          return;
        }
      }
    }

    /** Whole box past the edge opposite to the entry edge: the crossing is complete. */
    private static boolean hasExitedWorld(GhostwingDrone drone, WorldBounds world) {
      return switch (drone.direction()) {
        case EAST -> drone.x() >= world.width();
        case WEST -> drone.x() + drone.drawWidth() <= 0f;
        case NORTH -> drone.y() >= world.height();
        case SOUTH -> drone.y() + drone.drawHeight() <= 0f;
      };
    }
  }
}