package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainCatalog.Tile;
import com.davidpe.cosmicaces.infrastructure.gdx.screen.PhaseOneTerrainPlanner.Plan;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Single owner of the PhaseOne terrain tile resources: it turns the {@link Plan} selection into
 * prioritized creations, reuses an already loaded tile, discards the far ones and keeps a bounded
 * set bounded by the viewport, not by a fixed tile count.
 *
 * <p>It is generic over the resource type {@code T} and takes a {@link ResourceFactory} that
 * creates, disposes and sizes a resource for a tile path. That keeps the whole policy testable with
 * a fake loader and no LibGDX context; {@link PhaseOneTerrainTextureFactory} is the concrete GL
 * factory the screen wires in the render subtask. This class never draws and never touches a {@code
 * Texture} itself.
 *
 * <p><b>Threading and budget.</b> Resources are created on the calling thread, which is the LibGDX
 * render thread when the screen integrates it; the same thread must dispose them. {@link
 * #update(Plan, long)} spends a per-frame time budget on the prefetch ring so a normal frame never
 * decodes the whole matrix, while the tiles that are actually visible are always served even if
 * they exceed that budget.
 *
 * <p><b>Retention.</b> A loaded tile stays resident while the plan's {@link Plan#retains(Tile)}
 * says so (visible plus two rings). Only tiles outside that zone are disposed, which is the
 * hysteresis that stops a border oscillation from reloading. A visible tile is always inside its
 * own retention zone, so it can never be evicted on the frame it is drawn.
 *
 * <p><b>Lifecycle.</b> {@link #dispose()} frees every resident resource and drops the pending
 * demand, so leaving or recreating the screen leaves no texture and no outstanding request.
 */
public final class PhaseOneTerrainCache<T> implements Disposable {

  /** Creates, frees and sizes the terrain resource a tile path maps to. */
  public interface ResourceFactory<T> {

    /** Creates the resource for a catalog {@code path}; called once per tile at a time. */
    T create(String path);

    /** Frees a resource previously returned by {@link #create(String)}. */
    void dispose(T resource);

    /** Estimated native bytes of a resource, for diagnostics and memory accounting. */
    long estimatedBytes(T resource);
  }

  /**
   * The outcome of one {@link #primeVisible(Plan)} or {@link #update(Plan, long)} call.
   *
   * @param loaded resources created by this call (visible plus prefetch)
   * @param evicted resources disposed by this call because they left the retention zone
   * @param resident resources currently held
   * @param pending tiles requested by the plan but still not resident (prefetch beyond budget)
   * @param estimatedBytes estimated native bytes of the resident resources
   * @param visibleComplete whether every visible tile is resident after the call (always true once
   *     a call returns, including the exceptional jump/resize case)
   * @param budgetExhausted whether the prefetch budget stopped this call with demand left pending
   */
  public record FrameResult(
      int loaded,
      int evicted,
      int resident,
      int pending,
      long estimatedBytes,
      boolean visibleComplete,
      boolean budgetExhausted) {}

  private final ResourceFactory<T> factory;
  private final LongSupplier nanoClock;
  private final Map<Tile, T> resident = new HashMap<>();
  private final Set<Tile> pending = new HashSet<>();
  private long totalLoaded;
  private long totalEvictions;
  private long estimatedBytes;
  private boolean disposed;

  /** Builds the owner with the real monotonic clock. */
  public PhaseOneTerrainCache(ResourceFactory<T> factory) {
    this(factory, System::nanoTime);
  }

  /** Builds the owner with an injected clock so tests can drive the prefetch budget. */
  public PhaseOneTerrainCache(ResourceFactory<T> factory, LongSupplier nanoClock) {
    if (factory == null) {
      throw new IllegalArgumentException("factory must not be null");
    }
    if (nanoClock == null) {
      throw new IllegalArgumentException("nanoClock must not be null");
    }
    this.factory = factory;
    this.nanoClock = nanoClock;
  }

  /**
   * Blocking load of every visible tile before the first world draw, in the planner's nearest-first
   * order. No prefetch is requested here, so entering the phase never decodes the whole matrix.
   * Returns once every visible tile is resident, so the first frame cannot show a gap.
   */
  public FrameResult primeVisible(Plan plan) {
    requireActive();
    int loaded = 0;
    for (Tile tile : plan.visible()) {
      loaded += load(tile);
    }
    int evicted = evictOutsideRetention(plan);
    recomputePending(plan);
    return new FrameResult(
        loaded, evicted, resident.size(), pending.size(), estimatedBytes, visibleResident(plan),
        false);
  }

  /**
   * Advances one frame for {@code plan}, spending at most {@code budgetNanos} on the prefetch ring.
   *
   * <p>Order is exactly the planner's: every visible tile is created first and without budget, so a
   * sudden camera jump or a resize that exposes an unprepared region still has a valid background
   * before the frame is drawn. Then the prefetch tiles are created while the elapsed time since the
   * start of the call stays under the budget, so a normal frame spends a bounded amount of work and
   * the rest stays pending for later frames. Finally, resident tiles outside the retention zone are
   * disposed.
   *
   * @param plan the current selection
   * @param budgetNanos non-negative prefetch budget; {@code 0} loads only the visible zone
   */
  public FrameResult update(Plan plan, long budgetNanos) {
    requireActive();
    if (budgetNanos < 0L) {
      throw new IllegalArgumentException("budgetNanos must not be negative: " + budgetNanos);
    }
    int loaded = 0;
    for (Tile tile : plan.visible()) {
      loaded += load(tile);
    }

    long startNanos = nanoClock.getAsLong();
    boolean budgetExhausted = false;
    for (Tile tile : plan.prefetch()) {
      if (resident.containsKey(tile)) {
        continue;
      }
      if (budgetNanos == 0L || nanoClock.getAsLong() - startNanos >= budgetNanos) {
        budgetExhausted = true;
        break;
      }
      loaded += load(tile);
    }

    int evicted = evictOutsideRetention(plan);
    recomputePending(plan);
    return new FrameResult(
        loaded, evicted, resident.size(), pending.size(), estimatedBytes, visibleResident(plan),
        budgetExhausted);
  }

  /** The resident resource for {@code tile}, or {@code null} when it is not loaded. */
  public T resource(Tile tile) {
    return resident.get(tile);
  }

  /** Whether {@code tile} currently holds a resource. */
  public boolean isResident(Tile tile) {
    return resident.containsKey(tile);
  }

  /** Number of resources held right now. */
  public int residentCount() {
    return resident.size();
  }

  /** Number of tiles requested by the last plan and still not resident. */
  public int pendingCount() {
    return pending.size();
  }

  /** Total resources created since construction (monotonic diagnostic counter). */
  public long totalLoaded() {
    return totalLoaded;
  }

  /** Total resources disposed as evictions since construction (excludes {@link #dispose()}). */
  public long totalEvictions() {
    return totalEvictions;
  }

  /** Estimated native bytes of the resident resources. */
  public long estimatedBytes() {
    return estimatedBytes;
  }

  /**
   * Frees every resident resource and drops the pending demand. Idempotent, so a repeated screen
   * exit never disposes the same resource twice.
   */
  @Override
  public void dispose() {
    if (disposed) {
      return;
    }
    disposed = true;
    for (T resource : resident.values()) {
      factory.dispose(resource);
    }
    resident.clear();
    pending.clear();
    estimatedBytes = 0L;
  }

  private int load(Tile tile) {
    if (resident.containsKey(tile)) {
      return 0;
    }
    T resource = factory.create(tile.path());
    resident.put(tile, resource);
    estimatedBytes += factory.estimatedBytes(resource);
    totalLoaded++;
    return 1;
  }

  private int evictOutsideRetention(Plan plan) {
    int evicted = 0;
    Iterator<Map.Entry<Tile, T>> iterator = resident.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Tile, T> entry = iterator.next();
      if (plan.retains(entry.getKey())) {
        continue;
      }
      estimatedBytes -= factory.estimatedBytes(entry.getValue());
      factory.dispose(entry.getValue());
      iterator.remove();
      totalEvictions++;
      evicted++;
    }
    return evicted;
  }

  private void recomputePending(Plan plan) {
    pending.clear();
    for (Tile tile : plan.visible()) {
      if (!resident.containsKey(tile)) {
        pending.add(tile);
      }
    }
    for (Tile tile : plan.prefetch()) {
      if (!resident.containsKey(tile)) {
        pending.add(tile);
      }
    }
  }

  private boolean visibleResident(Plan plan) {
    for (Tile tile : plan.visible()) {
      if (!resident.containsKey(tile)) {
        return false;
      }
    }
    return true;
  }

  private void requireActive() {
    if (disposed) {
      throw new IllegalStateException("Terrain cache was disposed");
    }
  }
}
