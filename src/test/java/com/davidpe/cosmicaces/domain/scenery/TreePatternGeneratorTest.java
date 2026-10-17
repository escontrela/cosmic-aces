package com.davidpe.cosmicaces.domain.scenery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TreePatternGeneratorTest {
    private static final TerrainConfig CONFIG = TerrainConfig.defaults(81234L, 8192, 12000);
    private static final TerrainMap TERRAIN = new TerrainGenerator().generate(CONFIG);
    private static final TreePatternGenerator GENERATOR = new TreePatternGenerator();

    @Test
    void sameSeedAndConfigurationProduceIdenticalInstancesAndStableQueries() {
        SceneryField first = GENERATOR.generate(TERRAIN);
        SceneryField second = GENERATOR.generate(TERRAIN);
        assertEquals(first.instances(), second.instances());

        List<WorldRect> areas = List.of(
                new WorldRect(0, 0, 1024, 768),
                new WorldRect(2048, 3072, 1300, 900),
                new WorldRect(7000, 11000, 900, 900)
        );
        List<List<SceneryInstance>> expected = areas.stream().map(first::query).toList();
        for (int i = areas.size() - 1; i >= 0; i--) {
            assertEquals(expected.get(i), first.query(areas.get(i)));
            assertEquals(expected.get(i), second.query(areas.get(i)));
        }
        assertEquals(first.instances(), GENERATOR.generate(TERRAIN, List.of()).instances());
    }

    @Test
    void seedControlsResourceVariantOrientationSizeAndPosition() {
        SceneryField first = GENERATOR.generate(TERRAIN);
        TerrainMap otherTerrain = new TerrainGenerator().generate(TerrainConfig.defaults(98765L, 8192, 12000));
        SceneryField second = GENERATOR.generate(otherTerrain);

        assertNotEquals(first.instances(), second.instances());
        assertTrue(first.instances().stream().anyMatch(item -> item.type() == SceneryType.TREE));
        assertTrue(first.instances().stream().anyMatch(item -> item.type() == SceneryType.BUSH));
        assertTrue(first.instances().stream().anyMatch(item -> item.type() == SceneryType.PUDDLE));
        assertTrue(first.instances().stream().anyMatch(item -> item.type() == SceneryType.ROCK));
    }

    @Test
    void instancesUseKnownAssetsFitTheirSizeLimitsAndMatchTheirTerrainRegion() {
        SceneryField field = GENERATOR.generate(TERRAIN);
        Map<SceneryType, List<SceneryInstance>> byType = field.instances().stream()
                .collect(Collectors.groupingBy(SceneryInstance::type));

        assertEquals(List.of("tree-01"), byType.get(SceneryType.TREE).stream()
                .map(SceneryInstance::assetId).distinct().toList());
        assertEquals(List.of("bush-01"), byType.get(SceneryType.BUSH).stream()
                .map(SceneryInstance::assetId).distinct().toList());
        assertTrue(byType.get(SceneryType.PUDDLE).stream()
                .allMatch(item -> List.of("puddle-01", "puddle-02", "puddle-03").contains(item.assetId())));
        assertEquals(List.of("rock-cluster-01"), byType.get(SceneryType.ROCK).stream()
                .map(SceneryInstance::assetId).distinct().toList());

        for (SceneryInstance item : field.instances()) {
            assertTrue(item.width() >= item.type().minSize() && item.width() <= item.type().maxSize());
            assertTrue(item.height() >= item.type().minSize() && item.height() <= item.type().maxSize());
            assertEquals(item.width(), 256 * item.scale(), 1.0e-9);
            TerrainType region = TERRAIN.terrainAtWorld(item.x(), item.y());
            if (item.type() == SceneryType.TREE) assertEquals(TerrainType.FOREST, region);
            if (item.type() == SceneryType.ROCK) assertEquals(TerrainType.ROCKY, region);
            if (item.type() == SceneryType.PUDDLE) assertEquals(TerrainType.MEADOW, region);
        }
    }

    @Test
    void minimumClearanceIsMaintainedAcrossNearbyTypes() {
        SceneryField field = GENERATOR.generate(TERRAIN);
        List<SceneryInstance> instances = field.instances();
        assertTrue(instances.size() > 100);
        for (int i = 0; i < instances.size(); i += 29) {
            SceneryInstance current = instances.get(i);
            for (SceneryInstance nearby : field.query(current.footprint().expanded(7.01))) {
                if (current.id().equals(nearby.id())) continue;
                assertFalse(current.footprint().expanded(7).intersects(nearby.footprint().expanded(7)));
            }
        }
    }

    @Test
    void sceneryQueryKeepsSpritesWhoseFootprintCrossesTheVisibleEdge() {
        SceneryInstance tree = new SceneryInstance(
                "tree:edge", SceneryType.TREE, "tree-01", 100, 100, 80, 70, 0, 80.0 / 256
        );
        SceneryField field = new SceneryField(List.of(tree));

        assertTrue(field.query(new WorldRect(135, 90, 20, 20)).contains(tree));
        assertTrue(field.query(new WorldRect(135, 90, 20, 20)).stream()
                .allMatch(instance -> instance.x() < 135));
    }

    @Test
    void reservationsExcludeWholeFootprintsIncludingWhenTheCenterIsOutside() {
        SceneryField unreserved = GENERATOR.generate(TERRAIN);
        SceneryInstance target = unreserved.instances().get(unreserved.instances().size() / 3);
        WorldRect smallBuilding = new WorldRect(target.x() + 0.25, target.y() + 0.25, 1, 1);
        assertFalse(smallBuilding.x() <= target.x() && smallBuilding.right() >= target.x()
                && smallBuilding.y() <= target.y() && smallBuilding.top() >= target.y());
        SceneryPlacement placement = new SceneryPlacement(
                "future-outpost", "outpost", SceneryPlacement.Anchor.BOTTOM_LEFT,
                smallBuilding.x(), smallBuilding.y(), smallBuilding, 10, 18
        );

        SceneryField reserved = GENERATOR.generate(TERRAIN, List.of(placement));
        WorldRect reservation = placement.reservation();
        assertTrue(unreserved.instances().stream().anyMatch(item -> item.footprint().intersects(reservation)));
        assertTrue(reserved.instances().stream().noneMatch(item -> item.footprint().intersects(reservation)));
    }

    @Test
    void emptyPlacementListAddsNoArtificialClearingsAndPlacementContractIsValidated() {
        SceneryField field = GENERATOR.generate(TERRAIN, List.of());
        assertFalse(field.instances().isEmpty());
        assertEquals(field.instances(), GENERATOR.generate(TERRAIN).instances());
        WorldRect bounds = new WorldRect(100, 200, 300, 400);
        SceneryPlacement centered = new SceneryPlacement(
                "base", "military-base", SceneryPlacement.Anchor.CENTER,
                bounds.centerX(), bounds.centerY(), bounds, 2, 32
        );
        assertEquals(new WorldRect(68, 168, 364, 464), centered.reservation());
        assertThrows(IllegalArgumentException.class, () -> new WorldRect(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SceneryPlacement(
                "base", "military-base", SceneryPlacement.Anchor.BOTTOM_CENTER,
                100, 200, bounds, 2, 0
        ));
        assertThrows(IllegalArgumentException.class, () -> new SceneryPlacement(
                "base", "military-base", SceneryPlacement.Anchor.CENTER,
                250, 400, bounds, 2, -1
        ));
        assertThrows(NullPointerException.class, () -> GENERATOR.generate(TERRAIN, null));
        assertThrows(NullPointerException.class, () -> GENERATOR.generate(TERRAIN, List.of((SceneryPlacement) null)));
    }
}
