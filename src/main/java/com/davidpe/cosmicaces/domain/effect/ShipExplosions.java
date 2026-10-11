package com.davidpe.cosmicaces.domain.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A small pool of simultaneous world-anchored explosions, one per destruction. It advances every
 * running animation together and drops each entry as soon as its {@link ShipExplosion} finishes, so
 * the presentation layer never needs a per-ship flag for an unbounded set (several Ghostwing drones
 * can die in the same frame). It owns no LibGDX resource and never times damage or respawn: it only
 * keeps the frozen centres, the shared animation clocks and the captured peak sizes.
 */
public final class ShipExplosions {

  private final List<Entry> entries = new ArrayList<>();

  /**
   * Adds one explosion at a frozen world centre. {@code peakSize} is the largest frame dimension in
   * world units (the caller captures it at destruction and applies it to every frame).
   */
  public void spawn(float centerX, float centerY, float peakSize) {
    if (!Float.isFinite(peakSize) || peakSize <= 0f) {
      throw new IllegalArgumentException("Peak size must be positive and finite");
    }
    entries.add(new Entry(new ShipExplosion(centerX, centerY), peakSize));
  }

  /**
   * Advances every running animation by {@code deltaSeconds} and removes the ones that finished.
   * Non-positive or non-finite deltas are ignored, exactly like {@link ShipExplosion#advance(float)}.
   */
  public void advance(float deltaSeconds) {
    if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
      return;
    }
    entries.removeIf(entry -> {
      entry.animation().advance(deltaSeconds);
      return entry.animation().isFinished();
    });
  }

  /** Still-running animations in spawn order, as a read-only view; entries are owned here. */
  public List<Entry> active() {
    return Collections.unmodifiableList(entries);
  }

  /** Number of animations still running. */
  public int size() {
    return entries.size();
  }

  /** One running explosion: its world-anchored animation plus the peak size captured on death. */
  public record Entry(ShipExplosion animation, float peakSize) {}
}
