package com.davidpe.cosmicaces.domain.scenery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerrainGeneratorTest {
    private static final int WIDTH = 1003;
    private static final int HEIGHT = 777;
    private static final TerrainGenerator GENERATOR = new TerrainGenerator();

    @Test
    void sameSeedAndConfigurationProduceIdenticalTerrainAndDensity() {
        TerrainConfig config = TerrainConfig.defaults(81234L, WIDTH, HEIGHT);
        TerrainMap first = GENERATOR.generate(config);
        TerrainMap second = GENERATOR.generate(config);

        assertEquals(first.columns(), second.columns());
        assertEquals(first.rows(), second.rows());
        for (int row = 0; row < first.rows(); row++) {
            for (int column = 0; column < first.columns(); column++) {
                assertEquals(first.terrainAtCell(column, row), second.terrainAtCell(column, row));
                assertEquals(first.forestDensityAtCell(column, row), second.forestDensityAtCell(column, row));
            }
        }
    }

    @Test
    void queryOrderAndRepeatedQueriesDoNotChangeResults() {
        TerrainMap map = GENERATOR.generate(TerrainConfig.defaults(77L, WIDTH, HEIGHT));
        TerrainType[][] expected = new TerrainType[map.rows()][map.columns()];
        float[][] density = new float[map.rows()][map.columns()];
        for (int row = 0; row < map.rows(); row++) {
            for (int column = 0; column < map.columns(); column++) {
                expected[row][column] = map.terrainAtCell(column, row);
                density[row][column] = map.forestDensityAtCell(column, row);
            }
        }
        for (int row = map.rows() - 1; row >= 0; row--) {
            for (int column = map.columns() - 1; column >= 0; column--) {
                assertEquals(expected[row][column], map.terrainAtCell(column, row));
                assertEquals(density[row][column], map.forestDensityAtCell(column, row));
                assertEquals(expected[row][column], map.terrainAtCell(column, row));
            }
        }
    }

    @Test
    void changingSeedChangesRepresentativeMapAndIncludesAllTerrainClasses() {
        TerrainMap first = GENERATOR.generate(TerrainConfig.defaults(1L, 8192, 12000));
        TerrainMap second = GENERATOR.generate(TerrainConfig.defaults(2L, 8192, 12000));
        int differences = 0;
        boolean meadow = false;
        boolean forest = false;
        boolean rocky = false;
        for (int row = 0; row < first.rows(); row++) {
            for (int column = 0; column < first.columns(); column++) {
                TerrainType type = first.terrainAtCell(column, row);
                meadow |= type == TerrainType.MEADOW;
                forest |= type == TerrainType.FOREST;
                rocky |= type == TerrainType.ROCKY;
                if (type != second.terrainAtCell(column, row)) differences++;
                float density = first.forestDensityAtCell(column, row);
                assertTrue(density >= 0 && density <= 1);
                if (type != TerrainType.FOREST) assertEquals(0, density);
            }
        }
        assertTrue(differences > 0);
        assertTrue(meadow);
        assertTrue(forest);
        assertTrue(rocky);
    }

    @Test
    void partialEdgeCellsAreAddressableOnlyInsideWorldBounds() {
        TerrainMap map = GENERATOR.generate(TerrainConfig.defaults(8L, WIDTH, HEIGHT));

        assertEquals(16, map.columns());
        assertEquals(13, map.rows());
        assertEquals(map.terrainAtCell(15, 12), map.terrainAtWorld(WIDTH - 0.01, HEIGHT - 0.01));
        assertThrows(IndexOutOfBoundsException.class, () -> map.terrainAtWorld(WIDTH, HEIGHT - 1));
        assertThrows(IndexOutOfBoundsException.class, () -> map.terrainAtWorld(Double.NaN, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> map.terrainAtCell(map.columns(), 0));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TerrainConfig(1, 0, WIDTH, HEIGHT, 64, 100, 100, 0.1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new TerrainConfig(1, 1, 0, HEIGHT, 64, 100, 100, 0.1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new TerrainConfig(1, 1, WIDTH, HEIGHT, 0, 100, 100, 0.1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new TerrainConfig(1, 1, WIDTH, HEIGHT, 64, Double.POSITIVE_INFINITY, 100, 0.1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new TerrainConfig(1, 1, WIDTH, HEIGHT, 64, 100, 100, 1.01, 0.5));
        assertThrows(NullPointerException.class, () -> GENERATOR.generate(null));
    }

    @Test
    void worldQueriesUseTheSameGlobalCellsAsCellQueries() {
        TerrainMap map = GENERATOR.generate(TerrainConfig.defaults(918L, WIDTH, HEIGHT));
        for (int row = 0; row < map.rows(); row++) {
            for (int column = 0; column < map.columns(); column++) {
                double x = Math.min(column * 64.0 + 1, WIDTH - 0.01);
                double y = Math.min(row * 64.0 + 1, HEIGHT - 0.01);
                assertEquals(map.terrainAtCell(column, row), map.terrainAtWorld(x, y));
            }
        }
    }
}
