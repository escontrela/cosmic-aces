package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainCache.FrameResult;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainCatalog.Tile;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainPlanner.Camera;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainPlanner.Plan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/**
 * Tests for the terrain cache owner with a fake loader: no native window, no {@code Texture}. The
 * fake factory counts creations/disposals and can advance an injected clock so the per-frame budget
 * is deterministic.
 */
class PhaseOneTerrainCacheTest {

  private static final float REFERENCE_WIDTH = 1024f;
  private static final float REFERENCE_HEIGHT = 768f;
  private static final float LARGE_WIDTH = 1920f;
  private static final float LARGE_HEIGHT = 1080f;
  private static final long BYTES_PER_TILE = 3L * 1024L * 1024L;

  @Test
  void primeVisibleLoadsExactlyTheVisibleTilesInPlannerOrder() {
    FakeFactory factory = new FakeFactory();
    PhaseOneTerrainCache<FakeTexture> cache = new PhaseOneTerrainCache<>(factory);
    Plan plan = planAt(4096f, 6000f);

    FrameResult result = cache.primeVisible(plan);

    assertEquals(plan.visible().size(), result.loaded());
    assertTrue(result.visibleComplete());
    assertFalse(result.budgetExhausted());
    assertEquals(plan.visible().size(), cache.residentCount());
    for (Tile tile : plan.visible()) {
      assertTrue(cache.isResident(tile));
      assertNotNull(cache.resource(tile));
    }
    assertEquals(plan.prefetch().size(), cache.pendingCount(),
        "priming must not prefetch, so the ring stays pending");
    for (Tile tile : plan.prefetch()) {
      assertFalse(cache.isResident(tile));
      assertNull(cache.resource(tile));
    }
    assertEquals(0, result.evicted());
    assertEquals(plan.visible().size() * BYTES_PER_TILE, cache.estimatedBytes());
    assertEquals(0, cache.totalEvictions());
  }

  @Test
  void updateNeverLeavesAVisibleTileUnresidentEvenBeyondTheBudget() {
    FakeFactory factory = new FakeFactory();
    PhaseOneTerrainCache<FakeTexture> cache = new PhaseOneTerrainCache<>(factory);
    cache.primeVisible(planAt(4096f, 6000f));

    // Exceptional jump/teleport: the new viewport is far away and shares no tile with the old one.
    Plan far = planAt(4096f, 11400f);
    FrameResult result = cache.update(far, 0L);

    assertTrue(result.visibleComplete(), "a jump must show a valid background, never a gap");
    for (Tile tile : far.visible()) {
      assertTrue(cache.isResident(tile));
      assertNotNull(cache.resource(tile));
    }
    assertTrue(result.loaded() > 0, "the far visible tiles had to be loaded synchronously");
  }

  @Test
  void prefetchIsBoundedByTheBudgetAndLeavesDemandPending() {
    FakeFactory factory = new FakeFactory();
    MutableNanos clock = factory.clock;
    factory.nanosPerCreate = 5L * 1_000_000L;
    PhaseOneTerrainCache<FakeTexture> cache = new PhaseOneTerrainCache<>(factory, clock);
    Plan plan = planAt(4096f, 6000f);

    FrameResult result = cache.update(plan, 12L * 1_000_000L);

    assertTrue(result.visibleComplete());
    assertTrue(result.pending() > 0, "the ring cannot fit entirely in one frame budget");
    assertTrue(result.budgetExhausted());
    assertTrue(result.loaded() < plan.visible().size() + plan.prefetch().size(),
        "the budget must stop short of loading the whole demand");
    assertTrue(cache.residentCount() < PhaseOneTerrainCatalog.TILE_COUNT,
        "a normal frame never decodes the whole matrix");
  }

  @Test
  void prefetchRequestsTheForwardTileFirst() {
    FakeFactory factory = new FakeFactory();
    factory.nanosPerCreate = 5L * 1_000_000L;
    PhaseOneTerrainCache<FakeTexture> cache =
        new PhaseOneTerrainCache<>(factory, factory.clock);
    Plan plan = planAt(4096f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT, 0f, 1f);
    cache.primeVisible(plan);
    int visibleLoaded = factory.createsByPath.size();

    FrameResult result = cache.update(plan, 5L * 1_000_000L);

    assertEquals(1, result.loaded(), "a 5 ms budget with 5 ms loads admits exactly one prefetch");
    assertFalse(plan.prefetch().isEmpty());
    assertEquals(visibleLoaded + 1, factory.createdPaths.size());
    assertEquals(plan.prefetch().get(0).path(),
        factory.createdPaths.get(factory.createdPaths.size() - 1),
        "the prefetch must consume the planner's forward-first order");
  }

  @Test
  void borderOscillationDoesNotReloadOrEvictInsideTheRetentionZone() {
    FakeFactory factory = new FakeFactory();
    PhaseOneTerrainCache<FakeTexture> cache = new PhaseOneTerrainCache<>(factory);

    // x = 1530 shows columns 1-2; x = 1542 shows columns 2-3. Both retention zones include 1-4,
    // so every tile ever visible stays resident across the oscillation.
    Plan left = planAt(1530f, 6000f);
    Plan right = planAt(1542f, 6000f);
    assertEquals(3, unionColumns(left, right));

    for (int step = 0; step < 100; step++) {
      Plan plan = (step % 2 == 0) ? left : right;
      cache.update(plan, 0L);
    }

    assertEquals(6, cache.totalLoaded(),
        "rows 8-9 across columns 1-3 load once and must never reload");
    assertEquals(0, cache.totalEvictions(), "nothing leaves the retention zone");
    assertEquals(6, cache.residentCount());
    for (int count : factory.createsByPath.values()) {
      assertEquals(1, count, "a resident tile must never be created twice");
    }
  }

  @Test
  void distantTilesAreEvictedOnlyOutsideTheRetentionZone() {
    FakeFactory factory = new FakeFactory();
    PhaseOneTerrainCache<FakeTexture> cache = new PhaseOneTerrainCache<>(factory);
    Plan bottom = planAt(4096f, 300f);
    cache.primeVisible(bottom);
    cache.update(bottom, 0L);
    long loadedAtBottom = cache.totalLoaded();

    Plan top = planAt(4096f, 11700f);
    FrameResult result = cache.update(top, 0L);

    assertTrue(result.evicted() > 0, "the bottom rows must be released when far away");
    assertTrue(cache.totalEvictions() > 0);
    assertFalse(cache.isResident(bottom.visible().get(0)),
        "a tile outside the new retention zone must be evicted");
    for (Tile tile : top.visible()) {
      assertTrue(cache.isResident(tile));
    }
    assertEquals(top.visible().size(), result.resident(),
        "with a zero budget only the new visible tiles are resident");
    assertEquals(loadedAtBottom, result.evicted(),
        "every tile from the bottom viewport left the retention zone");
    assertEquals(result.resident() * BYTES_PER_TILE, cache.estimatedBytes());
    assertEquals(cache.totalLoaded() - cache.totalEvictions(), cache.residentCount());
  }

  @Test
  void resizeRotationWorldEdgesAndReturnKeepEveryVisibleTileReady() {
    FakeFactory factory = new FakeFactory();
    PhaseOneTerrainCache<FakeTexture> cache = new PhaseOneTerrainCache<>(factory);
    List<Plan> views = List.of(
        planAt(400f, 450f, 0.35f, 0.94f, 1f, LARGE_WIDTH, LARGE_HEIGHT),
        planAt(7800f, 11600f, -0.71f, 0.71f, 1.15f, LARGE_WIDTH, LARGE_HEIGHT),
        planAt(3500f, 6000f, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT),
        planAt(400f, 450f, 0.35f, 0.94f, 1f, LARGE_WIDTH, LARGE_HEIGHT));

    for (Plan plan : views) {
      FrameResult result = cache.update(plan, 0L);
      assertTrue(result.visibleComplete(), "resize, yaw or return left a visible tile missing");
      for (Tile tile : plan.visible()) {
        assertNotNull(cache.resource(tile), "missing visible tile " + tile.path());
      }
      assertTrue(cache.residentCount() <= plan.retained().size(),
          "residents must fit the current two-ring retention bound");
      assertTrue(cache.residentCount() < PhaseOneTerrainCatalog.TILE_COUNT,
          "the complete map must never be resident at once");
    }

    cache.dispose();
    assertEquals(factory.createdPaths.size(), factory.disposedPaths.size(),
        "all resources loaded across resize and return must be freed on exit");
  }

  @Test
  void fullFlightAcrossColumnsRowsTurnsAndViewportChangesStaysBounded() {
    FakeFactory factory = new FakeFactory();
    PhaseOneTerrainCache<FakeTexture> cache = new PhaseOneTerrainCache<>(factory);
    int maxResident = 0;
    long maxBytes = 0L;

    // A smooth S route sweeps from left to right and back while climbing through all 16 rows.
    for (int step = 0; step <= 120; step++) {
      float y = 100f + step * (11_800f / 120f);
      float phase = y / 2_000f;
      float x = 4096f + 3500f * (float) Math.sin(phase);
      float tangentX = 3500f / 2_000f * (float) Math.cos(phase);
      float travelLength = (float) Math.hypot(tangentX, 1f);
      float upX = tangentX / travelLength;
      float upY = 1f / travelLength;
      float width = step % 3 == 0 ? LARGE_WIDTH : REFERENCE_WIDTH;
      float height = step % 3 == 0 ? LARGE_HEIGHT : REFERENCE_HEIGHT;
      float zoom = step % 9 == 0 ? 1.25f : 1f;
      Plan plan = planAt(x, y, upX, upY, zoom, width, height, tangentX, 1f);

      FrameResult result = cache.update(plan, 8L * 1_000_000L);

      assertTrue(result.visibleComplete(), "flight left a hole at step " + step);
      assertTrue(cache.residentCount() <= plan.retained().size(),
          "cache exceeded the current two-ring bound at step " + step);
      assertTrue(cache.residentCount() < PhaseOneTerrainCatalog.TILE_COUNT,
          "full-flight cache must not load all 128 images");
      for (Tile tile : plan.visible()) {
        assertNotNull(cache.resource(tile), "visible tile not resident at step " + step);
      }
      maxResident = Math.max(maxResident, cache.residentCount());
      maxBytes = Math.max(maxBytes, cache.estimatedBytes());
      assertEquals(cache.residentCount() * BYTES_PER_TILE, cache.estimatedBytes());
    }

    assertTrue(maxResident > 0);
    assertTrue(maxResident <= 64,
        "S-flight peak should stay within 64 tiles (~192 MiB RGBA8888), got " + maxResident);
    assertEquals(maxResident * BYTES_PER_TILE, maxBytes);
    cache.dispose();
    assertEquals(factory.createdPaths.size(), factory.disposedPaths.size(),
        "leaving after a full flight releases every resident texture");
  }

  @Test
  void estimatedBytesTracksTheResidentSetAcrossMovement() {
    FakeFactory factory = new FakeFactory();
    PhaseOneTerrainCache<FakeTexture> cache = new PhaseOneTerrainCache<>(factory);

    for (float y = 400f; y <= 11600f; y += 400f) {
      Plan plan = planAt(4096f, y);
      cache.update(plan, 8L * 1_000_000L);
      assertEquals(cache.residentCount() * BYTES_PER_TILE, cache.estimatedBytes());
      assertTrue(cache.estimatedBytes() <= 128L * BYTES_PER_TILE);
    }
  }

  @Test
  void residentSetStaysBoundedDuringALongFlightAtReferenceAndLargeViewports() {
    Measurement reference = fly(REFERENCE_WIDTH, REFERENCE_HEIGHT);
    Measurement large = fly(LARGE_WIDTH, LARGE_HEIGHT);

    assertTrue(reference.maxResident >= reference.maxVisible);
    assertTrue(large.maxResident >= large.maxVisible);
    assertTrue(reference.maxResident <= reference.maxRetained,
        "resident can never exceed the current retention zone");
    assertTrue(large.maxResident <= large.maxRetained);
    assertTrue(reference.maxResident < PhaseOneTerrainCatalog.TILE_COUNT,
        "the cache never holds the whole 128-tile matrix");
    assertTrue(large.maxResident < PhaseOneTerrainCatalog.TILE_COUNT);
    assertTrue(large.maxResident >= reference.maxResident,
        "a bigger window exposes at least as many tiles");
    assertTrue(reference.maxResident <= 48, "bounded reference footprint, got " + reference.maxResident);
    assertTrue(large.maxResident <= 64, "bounded large footprint, got " + large.maxResident);
    assertEquals(reference.maxResident * BYTES_PER_TILE, reference.maxBytes,
        "estimated bytes must track the peak resident set");
    assertEquals(large.maxResident * BYTES_PER_TILE, large.maxBytes);
  }

  @Test
  void disposeFreesEverythingAndRepeatedEnterExitLeavesNoTrace() {
    FakeFactory factory = new FakeFactory();
    PhaseOneTerrainCache<FakeTexture> first = new PhaseOneTerrainCache<>(factory, factory.clock);
    Plan plan = planAt(4096f, 6000f);
    first.primeVisible(plan);
    first.update(plan, 50L * 1_000_000L);
    int created = factory.createdPaths.size();
    assertTrue(created > 0);

    first.dispose();

    assertEquals(created, factory.disposedPaths.size(),
        "every created resource must be freed exactly once");
    assertEquals(0, first.residentCount());
    assertEquals(0, first.pendingCount());
    assertEquals(0L, first.estimatedBytes());
    first.dispose(); // idempotent
    assertEquals(created, factory.disposedPaths.size());

    PhaseOneTerrainCache<FakeTexture> second = new PhaseOneTerrainCache<>(factory);
    second.primeVisible(plan);
    assertTrue(second.residentCount() > 0, "a new screen owns a fresh, working cache");
    second.dispose();
  }

  @Test
  void aDisposedCacheRejectsFurtherUse() {
    FakeFactory factory = new FakeFactory();
    PhaseOneTerrainCache<FakeTexture> cache = new PhaseOneTerrainCache<>(factory);
    cache.dispose();

    assertThrows(IllegalStateException.class, () -> cache.primeVisible(planAt(4096f, 6000f)));
    assertThrows(IllegalStateException.class, () -> cache.update(planAt(4096f, 6000f), 0L));
  }

  @Test
  void aNegativeBudgetIsRejected() {
    PhaseOneTerrainCache<FakeTexture> cache = new PhaseOneTerrainCache<>(new FakeFactory());
    assertThrows(IllegalArgumentException.class, () -> cache.update(planAt(4096f, 6000f), -1L));
  }

  private static Measurement fly(float viewWidth, float viewHeight) {
    FakeFactory factory = new FakeFactory();
    PhaseOneTerrainCache<FakeTexture> cache = new PhaseOneTerrainCache<>(factory);
    int maxResident = 0;
    int maxRetained = 0;
    int maxVisible = 0;
    long maxBytes = 0L;
    for (float y = 200f; y <= 11800f; y += 200f) {
      Plan plan = planAt(3500f, y, 0f, 1f, 1f, viewWidth, viewHeight);
      cache.update(plan, 8L * 1_000_000L);
      maxResident = Math.max(maxResident, cache.residentCount());
      maxRetained = Math.max(maxRetained, plan.retained().size());
      maxVisible = Math.max(maxVisible, plan.visible().size());
      maxBytes = Math.max(maxBytes, cache.estimatedBytes());
    }
    cache.dispose();
    return new Measurement(maxResident, maxRetained, maxVisible, maxBytes);
  }

  /** The number of distinct columns covered by the visible sets of both plans. */
  private static int unionColumns(Plan first, Plan second) {
    List<Tile> union = new ArrayList<>(first.visible());
    union.addAll(second.visible());
    return (int) union.stream().map(Tile::column).distinct().count();
  }

  private static Plan planAt(float x, float y) {
    return planAt(x, y, 0f, 1f, 1f, REFERENCE_WIDTH, REFERENCE_HEIGHT);
  }

  private static Plan planAt(float x, float y, float upX, float upY, float zoom,
      float viewWidth, float viewHeight) {
    return PhaseOneTerrainPlanner.plan(
        new Camera(x, y, upX, upY, zoom, viewWidth, viewHeight));
  }

  private static Plan planAt(float x, float y, float upX, float upY, float zoom,
      float viewWidth, float viewHeight, float travelX, float travelY) {
    return PhaseOneTerrainPlanner.plan(
        new Camera(x, y, upX, upY, zoom, viewWidth, viewHeight), travelX, travelY);
  }

  private record Measurement(int maxResident, int maxRetained, int maxVisible, long maxBytes) {}

  /** A resource handle that only carries its path, so tests need no GL context. */
  private record FakeTexture(String path) {}

  /** Mutable monotonic clock advanced by the fake factory on every creation. */
  private static final class MutableNanos implements LongSupplier {
    long nanos;

    @Override
    public long getAsLong() {
      return nanos;
    }
  }

  private static final class FakeFactory
      implements PhaseOneTerrainCache.ResourceFactory<FakeTexture> {

    final List<String> createdPaths = new ArrayList<>();
    final Map<String, Integer> createsByPath = new HashMap<>();
    final List<String> disposedPaths = new ArrayList<>();
    final MutableNanos clock = new MutableNanos();
    long nanosPerCreate;

    @Override
    public FakeTexture create(String path) {
      createdPaths.add(path);
      createsByPath.merge(path, 1, Integer::sum);
      clock.nanos += nanosPerCreate;
      return new FakeTexture(path);
    }

    @Override
    public void dispose(FakeTexture resource) {
      disposedPaths.add(resource.path());
    }

    @Override
    public long estimatedBytes(FakeTexture resource) {
      return BYTES_PER_TILE;
    }
  }
}
