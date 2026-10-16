package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic catalog of the {@value #TILE_COUNT} PhaseOne terrain tiles (8 columns × 16 rows).
 *
 * <p>It is pure metadata: asset paths, world-space rectangles and the matching source rectangle
 * inside each 1024×768 PNG. It owns no {@code Texture}, reads no file and holds no mutable state, so
 * it can be built and queried without starting LibGDX. The screen (or its loader/selector in later
 * subtasks) turns these rectangles into drawing and caching decisions.
 *
 * <p>The mapping fixed by COS-81/COS-82: {@code row-01} is the start of the run (south, world
 * y = 0) and {@code row-16} is the end (north). Each tile covers 1024 world units per column and
 * 768 per row, matching the 8192×12000 PhaseOne world. The last row would reach y = 12288, so its
 * visible rectangle is clipped to the world height; the clip is expressed as a shorter destination
 * rectangle plus the corresponding sub-rectangle of the PNG, so the image is never stretched.
 *
 * <p>Orientation: each PNG's top edge is the map's north edge. LibGDX draws a default {@code
 * TextureRegion} with its top row at the highest world Y (v = 0 at the top of the destination), so
 * no vertical flip is required; see {@link #requiresVerticalFlip()}.
 */
public final class PhaseOneTerrainCatalog {

  /** Number of tile columns, each 1024 world units wide. */
  public static final int COLUMNS = 8;
  /** Number of tile rows, each 768 world units tall. */
  public static final int ROWS = 16;
  /** Total tiles in the matrix: {@value #COLUMNS} × {@value #ROWS}. */
  public static final int TILE_COUNT = COLUMNS * ROWS;
  /** Source PNG width in pixels; one pixel maps to one world unit. */
  public static final int TILE_PIXEL_WIDTH = 1024;
  /** Source PNG height in pixels; one pixel maps to one world unit. */
  public static final int TILE_PIXEL_HEIGHT = 768;
  /** PhaseOne world width in world units: {@value #COLUMNS} × {@value #TILE_PIXEL_WIDTH}. */
  public static final float WORLD_WIDTH = COLUMNS * (float) TILE_PIXEL_WIDTH;
  /** PhaseOne world height in world units, matching {@code PhaseOneGameController.WORLD}. */
  public static final float WORLD_HEIGHT = 12000f;
  /** Vertical overflow of the last row beyond the world height: 16 × 768 − 12000. */
  public static final float WORLD_HEIGHT_OVERFLOW = ROWS * (float) TILE_PIXEL_HEIGHT - WORLD_HEIGHT;
  /** Height in world units actually visible for the last row: 768 − 288. */
  public static final float LAST_ROW_VISIBLE_HEIGHT = TILE_PIXEL_HEIGHT - WORLD_HEIGHT_OVERFLOW;

  private static final String PATH_FORMAT = "assets/images/map-phase-one/row-%02d-col-%02d.png";

  /**
   * A single terrain tile.
   *
   * <p>{@code worldX/worldY} is the bottom-left corner in world units and {@code worldWidth/
   * worldHeight} the size already clipped to the world (the last row is {@value
   * #LAST_ROW_VISIBLE_HEIGHT} units tall). {@code sourceX/sourceY/sourceWidth/sourceHeight} is the
   * sub-rectangle of the 1024×768 PNG, in top-left pixel coordinates, that maps one pixel to one
   * world unit into the destination; {@code sourceX} is always 0 and the widths/heights match, so
   * drawing the source into the destination never stretches the image.
   */
  public record Tile(
      int row,
      int column,
      String path,
      float worldX,
      float worldY,
      float worldWidth,
      float worldHeight,
      int sourceX,
      int sourceY,
      int sourceWidth,
      int sourceHeight) {}

  private static final List<Tile> TILES = buildTiles();

  /**
   * All tiles in row-major order: row 1 column 1 first, row 16 column 8 last. Immutable.
   */
  public static List<Tile> tiles() {
    return TILES;
  }

  /** The tile for 1-based {@code row} (1..16) and {@code column} (1..8). */
  public static Tile tile(int row, int column) {
    requireIndices(row, column);
    return TILES.get((row - 1) * COLUMNS + (column - 1));
  }

  /** The classpath path of the PNG for 1-based {@code row} and {@code column}. */
  public static String pathFor(int row, int column) {
    requireIndices(row, column);
    return PATH_FORMAT.formatted(row, column);
  }

  /**
   * The tile containing the world point, or empty when the point lies outside the world.
   *
   * <p>Interior borders are half-open: a point exactly on a shared edge belongs to the tile to its
   * right/north ({@code x = 1024} is column 2, {@code y = 768} is row 2). A point on the world's far
   * edge ({@code x = 8192} or {@code y = 12000}) is outside and returns empty, so no resource is
   * requested beyond the world.
   */
  public static Optional<Tile> tileAt(float worldX, float worldY) {
    if (worldX < 0f || worldX >= WORLD_WIDTH || worldY < 0f || worldY >= WORLD_HEIGHT) {
      return Optional.empty();
    }
    int column = (int) (worldX / TILE_PIXEL_WIDTH) + 1;
    int row = (int) (worldY / TILE_PIXEL_HEIGHT) + 1;
    return Optional.of(tile(row, column));
  }

  /**
   * Tiles whose world rectangle intersects the world-space rectangle {@code [minX,maxX]×[minY,maxY]},
   * in row-major order, clipped to the world. The query rectangle may be given in any corner order.
   * Shared edges count as intersection, so a rectangle that merely touches a tile border still
   * includes that neighbour: prefetching must never miss an adjacent tile. When the query lies
   * completely outside {@code [0,8192]×[0,12000]} the result is empty.
   */
  public static List<Tile> tilesIn(float minX, float minY, float maxX, float maxY) {
    float left = Math.max(Math.min(minX, maxX), 0f);
    float bottom = Math.max(Math.min(minY, maxY), 0f);
    float right = Math.min(Math.max(minX, maxX), WORLD_WIDTH);
    float top = Math.min(Math.max(minY, maxY), WORLD_HEIGHT);
    if (left >= WORLD_WIDTH || bottom >= WORLD_HEIGHT || right <= 0f || top <= 0f) {
      return List.of();
    }
    List<Tile> overlapping = new ArrayList<>();
    for (Tile tile : TILES) {
      if (tile.worldX() <= right
          && tile.worldX() + tile.worldWidth() >= left
          && tile.worldY() <= top
          && tile.worldY() + tile.worldHeight() >= bottom) {
        overlapping.add(tile);
      }
    }
    return List.copyOf(overlapping);
  }

  /**
   * Whether the PNG must be flipped vertically before drawing.
   *
   * <p>Always {@code false}: the image top edge is the map north edge and LibGDX already draws a
   * default {@code TextureRegion} with its top row at the top of the destination rectangle, which
   * corresponds to the highest world Y in the y-up world.
   */
  public static boolean requiresVerticalFlip() {
    return false;
  }

  private static List<Tile> buildTiles() {
    if (WORLD_HEIGHT_OVERFLOW < 0f || WORLD_HEIGHT_OVERFLOW >= TILE_PIXEL_HEIGHT) {
      throw new IllegalStateException(
          "World height must land inside the last tile row, overflow=" + WORLD_HEIGHT_OVERFLOW);
    }
    int lastRowVisibleHeight = Math.round(LAST_ROW_VISIBLE_HEIGHT);
    List<Tile> tiles = new ArrayList<>(TILE_COUNT);
    for (int row = 1; row <= ROWS; row++) {
      int sourceY = 0;
      int visibleHeight = TILE_PIXEL_HEIGHT;
      if (row == ROWS) {
        visibleHeight = lastRowVisibleHeight;
        sourceY = TILE_PIXEL_HEIGHT - visibleHeight;
      }
      for (int column = 1; column <= COLUMNS; column++) {
        tiles.add(new Tile(
            row,
            column,
            PATH_FORMAT.formatted(row, column),
            (column - 1) * (float) TILE_PIXEL_WIDTH,
            (row - 1) * (float) TILE_PIXEL_HEIGHT,
            TILE_PIXEL_WIDTH,
            visibleHeight,
            0,
            sourceY,
            TILE_PIXEL_WIDTH,
            visibleHeight));
      }
    }
    List<Tile> immutable = List.copyOf(tiles);
    validate(immutable);
    return immutable;
  }

  private static void validate(List<Tile> tiles) {
    if (tiles.size() != TILE_COUNT) {
      throw new IllegalStateException("Expected " + TILE_COUNT + " tiles, got " + tiles.size());
    }
    for (Tile tile : tiles) {
      if (tile.sourceX() != 0
          || tile.sourceWidth() != tile.worldWidth()
          || tile.sourceHeight() != tile.worldHeight()) {
        throw new IllegalStateException("Tile source and destination must map 1:1: " + tile);
      }
      if (tile.sourceY() < 0
          || tile.sourceY() + tile.sourceHeight() > TILE_PIXEL_HEIGHT) {
        throw new IllegalStateException("Tile source lies outside the PNG: " + tile);
      }
      if (tile.worldX() < 0f
          || tile.worldY() < 0f
          || tile.worldX() + tile.worldWidth() > WORLD_WIDTH
          || tile.worldY() + tile.worldHeight() > WORLD_HEIGHT) {
        throw new IllegalStateException("Tile lies outside the world: " + tile);
      }
    }
  }

  private static void requireIndices(int row, int column) {
    if (row < 1 || row > ROWS) {
      throw new IndexOutOfBoundsException("row must be within 1.." + ROWS + ", got " + row);
    }
    if (column < 1 || column > COLUMNS) {
      throw new IndexOutOfBoundsException(
          "column must be within 1.." + COLUMNS + ", got " + column);
    }
  }

  private PhaseOneTerrainCatalog() {}
}
