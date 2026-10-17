package com.davidpe.cosmicaces.domain.scenery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Produces a stable, jittered candidate lattice for decorative scenery.
 * Generation is a whole-map operation, independent of render and query order.
 */
public final class TreePatternGenerator {
    private static final long VARIANT_CHANNEL = 0xA0761D6478BD642FL;
    private static final long JITTER_CHANNEL = 0xE7037ED1A0B428DBL;
    private static final long CHOICE_CHANNEL = 0x8EBC6AF09C88C6E3L;
    private static final long PUDDLE_CHANNEL = 0x589965CC75374CC3L;
    private static final double JITTER_FRACTION = 0.22;
    private static final double CLEARANCE = 7.0;
    private static final double INDEX_CELL_SIZE = 128.0;
    private static final Comparator<Candidate> BY_PRIORITY = (left, right) -> {
        int priority = Long.compareUnsigned(left.priority(), right.priority());
        return priority != 0 ? priority : left.instance().id().compareTo(right.instance().id());
    };

    public SceneryField generate(TerrainMap terrain) {
        return generate(terrain, List.of());
    }

    public SceneryField generate(TerrainMap terrain, List<SceneryPlacement> futurePlacements) {
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(futurePlacements, "futurePlacements");
        List<WorldRect> reservations = futurePlacements.stream()
                .map(placement -> Objects.requireNonNull(placement, "placement").reservation())
                .toList();
        TerrainConfig config = terrain.config();
        List<Candidate> candidates = new ArrayList<>();
        long baseSeed = mix(config.seed() ^ config.version());
        long puddleSeed = mix(baseSeed ^ PUDDLE_CHANNEL);
        for (SceneryType type : SceneryType.values()) {
            int columns = (int) Math.ceil(config.worldWidth() / type.candidateSpacing());
            int rows = (int) Math.ceil(config.worldHeight() / type.candidateSpacing());
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    Candidate candidate = candidate(type, column, row, terrain, baseSeed, puddleSeed);
                    if (candidate == null) continue;
                    WorldRect footprint = candidate.instance().footprint();
                    if (reservations.stream().anyMatch(footprint::intersects)) continue;
                    candidates.add(candidate);
                }
            }
        }

        candidates.sort(BY_PRIORITY);
        PlacementIndex occupied = new PlacementIndex();
        List<SceneryInstance> accepted = new ArrayList<>();
        for (Candidate candidate : candidates) {
            WorldRect footprint = candidate.instance().footprint();
            if (occupied.intersects(footprint.expanded(CLEARANCE))) continue;
            accepted.add(candidate.instance());
            occupied.add(footprint.expanded(CLEARANCE));
        }
        return new SceneryField(accepted);
    }

    private static Candidate candidate(SceneryType type, int column, int row, TerrainMap terrain,
                                       long seed, long puddleSeed) {
        double spacing = type.candidateSpacing();
        long coordinateKey = coordinateKey(type, column, row);
        double x = (column + 0.5 + (unit(mix(seed ^ coordinateKey ^ JITTER_CHANNEL)) - 0.5) * JITTER_FRACTION)
                * spacing;
        double y = (row + 0.5 + (unit(mix(seed ^ Long.rotateLeft(coordinateKey, 19) ^ JITTER_CHANNEL)) - 0.5)
                * JITTER_FRACTION) * spacing;
        if (x < 0 || x >= terrain.config().worldWidth() || y < 0 || y >= terrain.config().worldHeight()) return null;
        TerrainType region = terrain.terrainAtWorld(x, y);
        double forestDensity = terrain.forestDensityAtWorld(x, y);
        long choiceHash = mix(seed ^ coordinateKey ^ CHOICE_CHANNEL);
        double choice = unit(choiceHash);
        double regionalChance;
        switch (type) {
            case TREE -> {
                if (region != TerrainType.FOREST) return null;
                regionalChance = 0.48 + forestDensity * 0.5;
            }
            case BUSH -> regionalChance = region == TerrainType.FOREST
                    ? 0.26 + (1 - forestDensity) * 0.22
                    : region == TerrainType.MEADOW ? 0.035 : 0;
            case ROCK -> {
                if (region != TerrainType.ROCKY) return null;
                regionalChance = 0.62;
            }
            case PUDDLE -> {
                if (region != TerrainType.MEADOW) return null;
                float patchNoise = OpenSimplex2S.noise2(puddleSeed, x / 260.0, y / 260.0);
                regionalChance = patchNoise >= 0.12f ? 0.42 : 0;
            }
            default -> throw new IllegalStateException("unsupported scenery type: " + type);
        }
        if (choice >= regionalChance) return null;

        double sizeRoll = unit(mix(choiceHash ^ VARIANT_CHANNEL));
        int variant = (int) (unit(mix(choiceHash ^ Long.rotateLeft(VARIANT_CHANNEL, 17))) * type.variantCount());
        variant = Math.min(variant, type.variantCount() - 1);
        double nominalSize = (type.minSize() + type.maxSize()) / 2;
        double width = type.minSize() + sizeRoll * (type.maxSize() - type.minSize());
        double heightRoll = unit(mix(choiceHash ^ 0xD1B54A32D192ED03L));
        double height = type.minSize() + heightRoll * (type.maxSize() - type.minSize());
        double scale = width / nominalSize;
        double rotation = unit(mix(choiceHash ^ 0x94D049BB133111EBL)) * 360.0;
        String id = type.name().toLowerCase() + ":" + column + ":" + row;
        SceneryInstance instance = new SceneryInstance(
                id, type, type.assetId(variant), x, y, width, height, rotation, scale
        );
        WorldRect footprint = instance.footprint();
        if (footprint.x() < 0 || footprint.y() < 0
                || footprint.right() > terrain.config().worldWidth()
                || footprint.top() > terrain.config().worldHeight()) return null;
        return new Candidate(instance, choiceHash);
    }

    private static long coordinateKey(SceneryType type, int column, int row) {
        return ((long) type.ordinal() << 56) ^ ((long) column << 28) ^ row;
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record Candidate(SceneryInstance instance, long priority) {}

    /** Spatial hash keeps minimum-clearance resolution near linear for the full world. */
    private static final class PlacementIndex {
        private final Map<Long, List<WorldRect>> cells = new HashMap<>();

        boolean intersects(WorldRect area) {
            int minX = cell(area.x());
            int maxX = cell(area.right());
            int minY = cell(area.y());
            int maxY = cell(area.top());
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    List<WorldRect> indexed = cells.get(key(x, y));
                    if (indexed != null) {
                        for (WorldRect existing : indexed) {
                            if (existing.intersects(area)) return true;
                        }
                    }
                }
            }
            return false;
        }

        void add(WorldRect area) {
            forEachCell(area, key -> cells.computeIfAbsent(key, ignored -> new ArrayList<>()).add(area));
        }

        private static void forEachCell(WorldRect area, java.util.function.LongConsumer action) {
            int minX = cell(area.x());
            int maxX = cell(area.right());
            int minY = cell(area.y());
            int maxY = cell(area.top());
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) action.accept(key(x, y));
            }
        }

        private static int cell(double coordinate) { return (int) Math.floor(coordinate / INDEX_CELL_SIZE); }
        private static long key(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }
    }
}
