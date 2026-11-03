package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.davidpe.cosmicaces.domain.scenery.SceneryPlacement;
import com.davidpe.cosmicaces.infrastructure.gdx.scenery.LegacyMapMilestoneCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Pure geometry for the five village labels of the extended PhaseOne minimap. The map panel is very
 * narrow for a world this tall, so labels are stacked in a collision-free ladder next to their
 * markers instead of being drawn on top of each other. The screen measures the font and draws the
 * text; this class only resolves names by asset id and places label boxes inside the map rectangle.
 */
final class VillageLabelLayout {
  /** Same clamp margin used by {@link FlightMinimap#villageMarkers}. */
  static final float MARKER_MARGIN = 4f;
  /** Horizontal gap between a marker and the start of its label. */
  static final float LABEL_GAP = 6f;
  /** Keeps every label box clear of the map border. */
  static final float EDGE_INSET = 2f;
  /** Minimum vertical gap between two stacked labels. */
  static final float LINE_GAP = 2f;

  private VillageLabelLayout() {}

  /** One projected village marker and the measured width of its name at the current font scale. */
  record Seed(String name, float markerX, float markerY, float width) {
    Seed {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Village label name is required");
      }
      requireFinite(markerX, "marker x");
      requireFinite(markerY, "marker y");
      if (!Float.isFinite(width) || width < 0f) {
        throw new IllegalArgumentException("Village label width must be finite and non-negative");
      }
    }
  }

  /** A label box whose {@code topY} is the top edge of the first text line. */
  record Label(String name, float x, float topY, float width, float textHeight,
      float markerX, float markerY) {
    float bottomY() {
      return topY - textHeight;
    }

    float right() {
      return x + width;
    }

    float centerY() {
      return topY - textHeight / 2f;
    }

    /** A leader is only needed when the label had to move away from its marker. */
    boolean needsLeader() {
      return Math.abs(centerY() - markerY) > 2f;
    }
  }

  /** Resolves each placement's approved name by asset id and projects its marker. */
  static List<Seed> seeds(MinimapProjection projection, List<SceneryPlacement> villages,
      Function<String, Float> widthOf) {
    if (projection == null || villages == null || widthOf == null) {
      throw new IllegalArgumentException("Projection, village placements and width lookup are required");
    }
    List<Seed> seeds = new ArrayList<>(villages.size());
    for (SceneryPlacement placement : villages) {
      if (placement == null) {
        throw new IllegalArgumentException("Village placement is required");
      }
      String name = LegacyMapMilestoneCatalog.nameFor(placement.assetId());
      MinimapProjection.Point marker = projection.projectMarker(
          (float) placement.bounds().centerX(), (float) placement.bounds().centerY(), MARKER_MARGIN);
      Float width = widthOf.apply(name);
      if (width == null) {
        throw new IllegalArgumentException("No measured width for village label: " + name);
      }
      seeds.add(new Seed(name, marker.x(), marker.y(), width));
    }
    return List.copyOf(seeds);
  }

  /**
   * Places every label inside {@code map}, ordered south to north, with the closest fit to its
   * marker's height. A label never starts before the previous label's top plus its text height and
   * gap, which makes same-height villages readable instead of overlapping.
   */
  static List<Label> place(MinimapProjection.Rectangle map, List<Seed> seeds, float textHeight) {
    if (map == null || seeds == null) {
      throw new IllegalArgumentException("Map rectangle and label seeds are required");
    }
    if (!Float.isFinite(textHeight) || textHeight <= 0f) {
      throw new IllegalArgumentException("Label text height must be finite and positive");
    }
    if (!Float.isFinite(map.width()) || !Float.isFinite(map.height())
        || map.width() <= 0f || map.height() <= 0f) {
      throw new IllegalArgumentException("Map rectangle must have a positive finite size");
    }
    float minTop = map.y() + EDGE_INSET + textHeight;
    float maxTop = map.y() + map.height() - EDGE_INSET;
    if (minTop > maxTop) {
      throw new IllegalArgumentException("Map rectangle is too short for a label");
    }

    List<Seed> ordered = new ArrayList<>(seeds);
    ordered.sort(Comparator.comparingDouble(Seed::markerY)
        .thenComparingDouble(Seed::markerX));

    float cursor = minTop;
    List<Label> labels = new ArrayList<>(ordered.size());
    for (Seed seed : ordered) {
      float desiredTop = seed.markerY() - textHeight / 2f;
      float top = Math.min(Math.max(Math.max(desiredTop, cursor), minTop), maxTop);
      float minX = map.x() + EDGE_INSET;
      float maxX = Math.max(minX, map.x() + map.width() - EDGE_INSET - seed.width());
      float x = Math.min(Math.max(seed.markerX() + LABEL_GAP, minX), maxX);
      labels.add(new Label(seed.name(), x, top, seed.width(), textHeight,
          seed.markerX(), seed.markerY()));
      cursor = top + textHeight + LINE_GAP;
    }
    return List.copyOf(labels);
  }

  private static void requireFinite(float value, String label) {
    if (!Float.isFinite(value)) {
      throw new IllegalArgumentException(label + " must be finite");
    }
  }
}
