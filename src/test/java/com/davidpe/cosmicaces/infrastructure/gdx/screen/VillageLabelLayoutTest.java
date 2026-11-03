package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.scenery.SceneryPlacement;
import com.davidpe.cosmicaces.domain.scenery.WorldRect;
import java.util.List;
import org.junit.jupiter.api.Test;

class VillageLabelLayoutTest {
  private static final WorldBounds EXTENDED = new WorldBounds(8192f, 36000f);
  private static final float TEXT_HEIGHT = 10f;

  private static final SceneryPlacement NOIA =
      village("map-row-02-col-01", 0, 2304);
  private static final SceneryPlacement MUROS =
      village("map-row-02-col-02", 1024, 2304);
  private static final SceneryPlacement BETANZOS =
      village("map-row-03-col-01", 0, 4608);
  private static final SceneryPlacement FISTERRA =
      village("map-row-03-col-02", 1024, 4608);
  private static final SceneryPlacement RIBADEO =
      village("map-row-16-col-02", 1024, 34560);

  private static final List<SceneryPlacement> VILLAGES =
      List.of(NOIA, MUROS, BETANZOS, FISTERRA, RIBADEO);

  @Test
  void namesAreResolvedByAssetIdEvenWhenPlacementsAreReordered() {
    MinimapProjection projection = MinimapProjection.fit(EXTENDED, 800f, 600f);
    List<SceneryPlacement> reordered =
        List.of(RIBADEO, BETANZOS, NOIA, FISTERRA, MUROS);

    List<VillageLabelLayout.Seed> seeds =
        VillageLabelLayout.seeds(projection, reordered, name -> name.length() * 6f);

    assertEquals(List.of("Ribadeo", "Betanzos", "Noia", "Fisterra", "Muros"),
        seeds.stream().map(VillageLabelLayout.Seed::name).toList());
    for (int index = 0; index < reordered.size(); index++) {
      SceneryPlacement placement = reordered.get(index);
      MinimapProjection.Point marker = projection.projectMarker(
          (float) placement.bounds().centerX(), (float) placement.bounds().centerY(),
          VillageLabelLayout.MARKER_MARGIN);
      assertEquals(marker.x(), seeds.get(index).markerX(), 0.0001f);
      assertEquals(marker.y(), seeds.get(index).markerY(), 0.0001f);
    }
  }

  @Test
  void everyLabelStaysInsideTheMapAndNoTwoLabelsOverlapAtEightHundredBySixHundred() {
    MinimapProjection projection = MinimapProjection.fit(EXTENDED, 800f, 600f);
    MinimapProjection.Rectangle map = projection.map();
    List<VillageLabelLayout.Label> labels = VillageLabelLayout.place(
        map, seeds(projection), TEXT_HEIGHT);

    assertEquals(5, labels.size());
    for (VillageLabelLayout.Label label : labels) {
      assertTrue(label.x() >= map.x() + VillageLabelLayout.EDGE_INSET - 0.0001f,
          label.name() + " starts inside the map");
      assertTrue(label.right() <= map.x() + map.width() - VillageLabelLayout.EDGE_INSET + 0.0001f,
          label.name() + " ends inside the map");
      assertTrue(label.bottomY() >= map.y() + VillageLabelLayout.EDGE_INSET - 0.0001f,
          label.name() + " stays above the map bottom");
      assertTrue(label.topY() <= map.y() + map.height() - VillageLabelLayout.EDGE_INSET + 0.0001f,
          label.name() + " stays below the map top");
    }
    for (int first = 0; first < labels.size(); first++) {
      for (int second = first + 1; second < labels.size(); second++) {
        assertFalse(overlaps(labels.get(first), labels.get(second)),
            labels.get(first).name() + " must not overlap " + labels.get(second).name());
      }
    }
  }

  @Test
  void villagesAtTheSameHeightGetDistinctVerticalBands() {
    MinimapProjection projection = MinimapProjection.fit(EXTENDED, 800f, 600f);
    List<VillageLabelLayout.Label> labels = VillageLabelLayout.place(
        projection.map(), seeds(projection), TEXT_HEIGHT);

    VillageLabelLayout.Label noia = byName(labels, "Noia");
    VillageLabelLayout.Label muros = byName(labels, "Muros");
    VillageLabelLayout.Label betanzos = byName(labels, "Betanzos");
    VillageLabelLayout.Label fisterra = byName(labels, "Fisterra");

    assertEquals(noia.markerY(), muros.markerY(), 0.0001f);
    assertEquals(betanzos.markerY(), fisterra.markerY(), 0.0001f);
    assertNotEquals(noia.topY(), muros.topY());
    assertNotEquals(betanzos.topY(), fisterra.topY());
    assertTrue(muros.topY() - noia.topY() >= TEXT_HEIGHT + VillageLabelLayout.LINE_GAP - 0.0001f);
    assertTrue(fisterra.topY() - betanzos.topY()
        >= TEXT_HEIGHT + VillageLabelLayout.LINE_GAP - 0.0001f);
    assertEquals(List.of("Noia", "Muros", "Betanzos", "Fisterra", "Ribadeo"),
        labels.stream().map(VillageLabelLayout.Label::name).toList(),
        "labels keep the south-to-north order of the map");
  }

  @Test
  void labelsNearTheTopAndBottomEdgesAreClampedInsideTheMap() {
    MinimapProjection.Rectangle map = new MinimapProjection.Rectangle(100f, 50f, 78f, 344f);
    List<VillageLabelLayout.Seed> seeds = List.of(
        new VillageLabelLayout.Seed("Abajo", map.x() + 4f, map.y() + 4f, 30f),
        new VillageLabelLayout.Seed("Arriba", map.x() + 4f,
            map.y() + map.height() - 4f, 30f));

    List<VillageLabelLayout.Label> labels = VillageLabelLayout.place(map, seeds, TEXT_HEIGHT);

    for (VillageLabelLayout.Label label : labels) {
      assertTrue(label.x() >= map.x() + VillageLabelLayout.EDGE_INSET - 0.0001f);
      assertTrue(label.right() <= map.x() + map.width() - VillageLabelLayout.EDGE_INSET + 0.0001f);
      assertTrue(label.bottomY() >= map.y() + VillageLabelLayout.EDGE_INSET - 0.0001f);
      assertTrue(label.topY() <= map.y() + map.height() - VillageLabelLayout.EDGE_INSET + 0.0001f);
    }
    assertEquals("Abajo", labels.get(0).name());
    assertEquals("Arriba", labels.get(1).name());
  }

  @Test
  void unknownVillageAssetsAreRejected() {
    MinimapProjection projection = MinimapProjection.fit(EXTENDED, 800f, 600f);
    SceneryPlacement unknown = village("map-row-01-col-01", 0, 0);

    assertThrows(IllegalArgumentException.class,
        () -> VillageLabelLayout.seeds(projection, List.of(unknown), name -> 10f));
  }

  @Test
  void invalidInputsAreRejected() {
    MinimapProjection projection = MinimapProjection.fit(EXTENDED, 800f, 600f);
    List<VillageLabelLayout.Seed> seeds = seeds(projection);

    assertThrows(IllegalArgumentException.class,
        () -> VillageLabelLayout.place(null, seeds, TEXT_HEIGHT));
    assertThrows(IllegalArgumentException.class,
        () -> VillageLabelLayout.place(projection.map(), null, TEXT_HEIGHT));
    assertThrows(IllegalArgumentException.class,
        () -> VillageLabelLayout.place(projection.map(), seeds, 0f));
    assertThrows(IllegalArgumentException.class,
        () -> VillageLabelLayout.place(
            new MinimapProjection.Rectangle(0f, 0f, 40f, 5f), seeds, TEXT_HEIGHT));
    assertThrows(IllegalArgumentException.class,
        () -> new VillageLabelLayout.Seed(" ", 0f, 0f, 10f));
    assertThrows(IllegalArgumentException.class,
        () -> new VillageLabelLayout.Seed("Noia", Float.NaN, 0f, 10f));
    assertThrows(IllegalArgumentException.class,
        () -> new VillageLabelLayout.Seed("Noia", 0f, 0f, -1f));
    assertThrows(IllegalArgumentException.class,
        () -> VillageLabelLayout.seeds(projection, VILLAGES, name -> null));
  }

  private static List<VillageLabelLayout.Seed> seeds(MinimapProjection projection) {
    return VillageLabelLayout.seeds(projection, VILLAGES, name -> name.length() * 6f);
  }

  private static VillageLabelLayout.Label byName(List<VillageLabelLayout.Label> labels,
      String name) {
    return labels.stream().filter(label -> label.name().equals(name)).findFirst().orElseThrow();
  }

  private static boolean overlaps(VillageLabelLayout.Label first,
      VillageLabelLayout.Label second) {
    return first.x() < second.right() && second.x() < first.right()
        && first.bottomY() < second.topY() && second.bottomY() < first.topY();
  }

  private static SceneryPlacement village(String assetId, double x, double y) {
    WorldRect bounds = new WorldRect(x, y, 1024, 768);
    return new SceneryPlacement("legacy-village-" + assetId, assetId,
        SceneryPlacement.Anchor.BOTTOM_LEFT, bounds.x(), bounds.y(), bounds, 50, 48);
  }
}
