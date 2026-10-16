package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainCatalog.Tile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure selection policy for the PhaseOne terrain map: which tiles are really visible for a rotated,
 * zoomed camera and which ones should be requested before the camera reaches them.
 *
 * <p>It consumes only {@link PhaseOneTerrainCatalog} metadata and holds no {@code Texture}, reads no
 * file and mutates no global state, so the whole policy is testable without starting LibGDX. The
 * screen (or its future loader/cache in later subtasks) turns the returned {@link Plan} into texture
 * requests; it never draws anything here.
 *
 * <p>The camera view is the rotated rectangle LibGDX builds from {@code position}, {@code up},
 * {@code zoom} and the current viewport world size, matching {@link CameraVisibility}: the camera's
 * right axis is {@code (upY, -upX)} and its half extents are {@code viewWidth/2 * zoom} and
 * {@code viewHeight/2 * zoom}. The planner takes the four corners of that rectangle, computes their
 * conservative axis-aligned bounding box and clips it to the world. Because the AABB contains every
 * corner, no visible corner can fall outside the selected set.
 *
 * <p>The plan exposes three nested zones that grow outwards, which is exactly the hysteresis a
 * bounded cache needs:
 *
 * <ul>
 *   <li>{@code visible} — tiles intersecting the clipped view AABB; always requested.
 *   <li>{@code prefetch} — one ring of tiles around the visible zone, ordered so tiles ahead of the
 *       recent travel direction come first, then lateral/rear tiles. Requested with lead time.
 *   <li>{@code retained} — two rings around the visible zone (visible ⊂ prefetch ⊂ retained). A
 *       loaded texture may stay cached while its tile is retained; only tiles outside it are
 *       eviction candidates, so an oscillation on a tile border does not reload continuously.
 * </ul>
 *
 * <p>Visible tiles always win over any fixed budget: the whole set is returned, so a bigger window
 * (or combat zoom) simply yields more visible tiles. The policy never asks for indices outside
 * 1..16/1..8 because every zone is clipped to the world first.
 */
public final class PhaseOneTerrainPlanner {

  /** Extra tile rings around the visible zone that form the prefetch demand. */
  public static final int PREFETCH_RING_TILES = 1;
  /** Extra tile rings around the visible zone kept for cache hysteresis (eviction boundary). */
  public static final int RETENTION_RING_TILES = 2;

  private static final Comparator<Tile> ROW_MAJOR =
      Comparator.comparingInt(Tile::row).thenComparingInt(Tile::column);

  /**
   * Upstream camera state after {@code FlightCamera.update}: world position, the {@code up} vector
   * that encodes yaw ({@code (sin yaw, cos yaw)}), zoom and the effective viewport world size.
   */
  public record Camera(
      float worldX,
      float worldY,
      float upX,
      float upY,
      float zoom,
      float viewWidth,
      float viewHeight) {

    public Camera {
      if (!Float.isFinite(worldX) || !Float.isFinite(worldY)) {
        throw new IllegalArgumentException("Camera position must be finite");
      }
      if (!Float.isFinite(upX) || !Float.isFinite(upY) || (upX == 0f && upY == 0f)) {
        throw new IllegalArgumentException("Camera up vector must be a finite, non-zero direction");
      }
      if (!Float.isFinite(zoom) || zoom <= 0f) {
        throw new IllegalArgumentException("Camera zoom must be positive");
      }
      if (!Float.isFinite(viewWidth) || viewWidth <= 0f
          || !Float.isFinite(viewHeight) || viewHeight <= 0f) {
        throw new IllegalArgumentException("View dimensions must be positive");
      }
    }
  }

  /** A world-space point. */
  public record Point(float x, float y) {}

  /** An axis-aligned world rectangle. {@code maxX/maxY} may be below {@code minX/minY} when empty. */
  public record Bounds(float minX, float minY, float maxX, float maxY) {

    /** Whether this rectangle intersects the rectangle {@code [left,right]×[bottom,top]}. */
    public boolean intersects(float left, float bottom, float right, float top) {
      return left <= maxX && right >= minX && bottom <= maxY && top >= minY;
    }

    /** Whether the tile's world rectangle intersects this rectangle (shared edges count). */
    public boolean contains(Tile tile) {
      return intersects(tile.worldX(), tile.worldY(),
          tile.worldX() + tile.worldWidth(), tile.worldY() + tile.worldHeight());
    }

    /** A rectangle grown by {@code marginX}/{@code marginY} on every side. */
    public Bounds expanded(float marginX, float marginY) {
      return new Bounds(minX - marginX, minY - marginY, maxX + marginX, maxY + marginY);
    }

    /** This rectangle clipped to the PhaseOne world {@code [0,8192]×[0,12000]}. */
    public Bounds clippedToWorld() {
      return new Bounds(
          Math.max(minX, 0f),
          Math.max(minY, 0f),
          Math.min(maxX, PhaseOneTerrainCatalog.WORLD_WIDTH),
          Math.min(maxY, PhaseOneTerrainCatalog.WORLD_HEIGHT));
    }
  }

  /**
   * The selection for one camera state.
   *
   * @param visibleCorners the four world-space corners of the rotated camera view
   * @param visibleBounds the conservative view AABB, clipped to the world
   * @param prefetchBounds the visible zone plus {@value #PREFETCH_RING_TILES} ring, clipped
   * @param retentionBounds the visible zone plus {@value #RETENTION_RING_TILES} rings, clipped
   * @param visible tiles intersecting {@code visibleBounds}, nearest camera first
   * @param prefetch tiles in {@code prefetchBounds} beyond {@code visible}, forward first
   * @param retained tiles intersecting {@code retentionBounds} in row-major order
   */
  public record Plan(
      List<Point> visibleCorners,
      Bounds visibleBounds,
      Bounds prefetchBounds,
      Bounds retentionBounds,
      List<Tile> visible,
      List<Tile> prefetch,
      List<Tile> retained) {

    /**
     * Cache-retention rule: a loaded tile stays valid while it intersects the retention zone. Tiles
     * outside it are the only eviction candidates, which is the hysteresis that prevents thrashing
     * on a border.
     */
    public boolean retains(Tile tile) {
      return retentionBounds.contains(tile);
    }
  }

  /**
   * Plans for the camera using its forward ({@code up}) direction as the travel direction.
   *
   * @param camera the updated camera state
   */
  public static Plan plan(Camera camera) {
    return plan(camera, camera.upX(), camera.upY());
  }

  /**
   * Plans for the camera and a recent travel direction used only to order the prefetch demand.
   * Visible tiles are never filtered; the direction only decides which surrounding tiles are
   * requested first. A zero-length, non-finite or absent direction falls back to the camera's
   * forward axis.
   *
   * @param camera the updated camera state
   * @param travelX x of the recent camera/ship displacement direction
   * @param travelY y of the recent camera/ship displacement direction
   */
  public static Plan plan(Camera camera, float travelX, float travelY) {
    List<Point> corners = visibleCorners(camera);
    Bounds rawVisible = boundsOf(corners);
    float marginX = PhaseOneTerrainCatalog.TILE_PIXEL_WIDTH;
    float marginY = PhaseOneTerrainCatalog.TILE_PIXEL_HEIGHT;
    Bounds rawPrefetch = rawVisible.expanded(
        PREFETCH_RING_TILES * marginX, PREFETCH_RING_TILES * marginY);
    Bounds rawRetention = rawVisible.expanded(
        RETENTION_RING_TILES * marginX, RETENTION_RING_TILES * marginY);

    Bounds visibleBounds = rawVisible.clippedToWorld();
    Bounds prefetchBounds = rawPrefetch.clippedToWorld();
    Bounds retentionBounds = rawRetention.clippedToWorld();

    List<Tile> visible = new ArrayList<>(
        PhaseOneTerrainCatalog.tilesIn(
            visibleBounds.minX(), visibleBounds.minY(), visibleBounds.maxX(), visibleBounds.maxY()));
    TileDistance distance = new TileDistance(camera);
    visible.sort(distance.thenRowMajor());

    Set<Tile> visibleSet = new HashSet<>(visible);
    List<Tile> prefetch = new ArrayList<>();
    for (Tile tile : PhaseOneTerrainCatalog.tilesIn(
        prefetchBounds.minX(), prefetchBounds.minY(), prefetchBounds.maxX(), prefetchBounds.maxY())) {
      if (!visibleSet.contains(tile)) {
        prefetch.add(tile);
      }
    }
    float[] travel = normalizedTravel(camera, travelX, travelY);
    prefetch.sort(prefetchOrder(camera, distance, travel[0], travel[1]));

    List<Tile> retained = PhaseOneTerrainCatalog.tilesIn(
        retentionBounds.minX(), retentionBounds.minY(),
        retentionBounds.maxX(), retentionBounds.maxY());

    return new Plan(
        corners, visibleBounds, prefetchBounds, retentionBounds,
        List.copyOf(visible), List.copyOf(prefetch), retained);
  }

  /**
   * The four world-space corners of the rotated camera view. Order is bottom-left, bottom-right,
   * top-right, top-left in the camera's local frame.
   */
  public static List<Point> visibleCorners(Camera camera) {
    float length = (float) Math.hypot(camera.upX(), camera.upY());
    float upX = camera.upX() / length;
    float upY = camera.upY() / length;
    float rightX = upY;
    float rightY = -upX;
    float halfWidth = camera.viewWidth() * camera.zoom() / 2f;
    float halfHeight = camera.viewHeight() * camera.zoom() / 2f;
    float centerX = camera.worldX();
    float centerY = camera.worldY();
    List<Point> corners = new ArrayList<>(4);
    for (int vertical : new int[] {-1, 1}) {
      for (int horizontal : new int[] {-1, 1}) {
        corners.add(new Point(
            centerX + horizontal * rightX * halfWidth + vertical * upX * halfHeight,
            centerY + horizontal * rightY * halfWidth + vertical * upY * halfHeight));
      }
    }
    // Reorder to bottom-left, bottom-right, top-right, top-left for a stable, readable result.
    return List.of(corners.get(0), corners.get(1), corners.get(3), corners.get(2));
  }

  private static Bounds boundsOf(List<Point> corners) {
    float minX = Float.POSITIVE_INFINITY;
    float minY = Float.POSITIVE_INFINITY;
    float maxX = Float.NEGATIVE_INFINITY;
    float maxY = Float.NEGATIVE_INFINITY;
    for (Point corner : corners) {
      minX = Math.min(minX, corner.x());
      minY = Math.min(minY, corner.y());
      maxX = Math.max(maxX, corner.x());
      maxY = Math.max(maxY, corner.y());
    }
    return new Bounds(minX, minY, maxX, maxY);
  }

  private static Comparator<Tile> prefetchOrder(
      Camera camera, TileDistance distance, float travelX, float travelY) {
    return Comparator
        .comparingInt((Tile tile) -> aheadOfCamera(tile, camera, travelX, travelY) ? 0 : 1)
        .thenComparing(distance.thenRowMajor());
  }

  private static boolean aheadOfCamera(
      Tile tile, Camera camera, float travelX, float travelY) {
    float centerX = tile.worldX() + tile.worldWidth() / 2f;
    float centerY = tile.worldY() + tile.worldHeight() / 2f;
    return (centerX - camera.worldX()) * travelX + (centerY - camera.worldY()) * travelY > 0f;
  }

  private static float[] normalizedTravel(Camera camera, float travelX, float travelY) {
    float length = (float) Math.hypot(travelX, travelY);
    if (Float.isFinite(length) && length > 0f) {
      return new float[] {travelX / length, travelY / length};
    }
    float upLength = (float) Math.hypot(camera.upX(), camera.upY());
    return new float[] {camera.upX() / upLength, camera.upY() / upLength};
  }

  /** Orders tiles by squared distance from the camera position, nearest first. */
  private record TileDistance(Camera camera) {
    Comparator<Tile> thenRowMajor() {
      return Comparator
          .comparingDouble((Tile tile) -> {
            float centerX = tile.worldX() + tile.worldWidth() / 2f;
            float centerY = tile.worldY() + tile.worldHeight() / 2f;
            float dx = centerX - camera.worldX();
            float dy = centerY - camera.worldY();
            return dx * dx + dy * dy;
          })
          .thenComparing(ROW_MAJOR);
    }
  }

  private PhaseOneTerrainPlanner() {}
}
