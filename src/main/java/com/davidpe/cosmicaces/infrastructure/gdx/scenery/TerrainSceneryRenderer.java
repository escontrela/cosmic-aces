package com.davidpe.cosmicaces.infrastructure.gdx.scenery;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.scenery.SceneryField;
import com.davidpe.cosmicaces.domain.scenery.SceneryInstance;
import com.davidpe.cosmicaces.domain.scenery.SceneryType;
import com.davidpe.cosmicaces.domain.scenery.TerrainConfig;
import com.davidpe.cosmicaces.domain.scenery.WorldRect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Screen-owned atlas renderer for repeated meadow ground and deterministic decorations. */
public final class TerrainSceneryRenderer implements Disposable {
    private static final Comparator<SceneryInstance> DRAW_ORDER =
            Comparator.comparingDouble(SceneryInstance::y).reversed()
                    .thenComparingInt(item -> layer(item.type()))
                    .thenComparing(SceneryInstance::id);

    private final TerrainAtlasOwner<TextureAtlas> atlasOwner;
    private final TextureAtlas atlas;
    private final List<GroundRegions> groundVariants;
    private final Map<String, TextureRegion> sceneryRegions;
    private final long groundSeed;
    private final int worldWidth;
    private final int worldHeight;
    private final TerrainGroundPlanner groundPlanner;
    private boolean disposed;

    public TerrainSceneryRenderer(TerrainConfig config) {
        Objects.requireNonNull(config, "config");
        worldWidth = config.worldWidth();
        worldHeight = config.worldHeight();
        groundSeed = config.seed();
        groundPlanner = new TerrainGroundPlanner(worldWidth, worldHeight);
        int tileSize = TerrainAtlasCatalog.GRASS_TILE_SIZE;

        TerrainAtlasOwner<TextureAtlas> owner = new TerrainAtlasOwner<>(
                TerrainAtlasCatalog.ATLAS_PATH,
                new TerrainAtlasOwner.ResourceFactory<>() {
                    @Override
                    public TextureAtlas load(String path) {
                        return new TextureAtlas(Gdx.files.internal(path));
                    }

                    @Override
                    public void dispose(TextureAtlas resource) {
                        resource.dispose();
                    }

                    @Override
                    public long estimatedBytes(TextureAtlas resource) {
                        long bytes = 0;
                        for (Texture texture : resource.getTextures()) {
                            texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
                            bytes += (long) texture.getWidth() * texture.getHeight() * 4;
                        }
                        return bytes;
                    }
                });
        TextureAtlas loaded = owner.resource();
        try {
            if (loaded.getTextures().size != TerrainAtlasCatalog.PAGE_COUNT) {
                throw new IllegalStateException("terrain atlas must contain exactly one texture page");
            }
            Map<String, TextureRegion> regions = new HashMap<>();
            for (TerrainAtlasCatalog.Region expected : TerrainAtlasCatalog.regions()) {
                TextureRegion region = loaded.findRegion(expected.id());
                if (region == null || region.getRegionWidth() != expected.width()
                        || region.getRegionHeight() != expected.height()) {
                    throw new IllegalStateException("missing or invalid terrain atlas region: " + expected.id());
                }
                regions.put(expected.id(), region);
            }
            groundVariants = List.of(
                    groundRegions(regions.get(TerrainAtlasCatalog.GRASS_ID), partialWidth(worldWidth, tileSize),
                            partialHeight(worldHeight, tileSize), tileSize),
                    groundRegions(regions.get(TerrainAtlasCatalog.GRASS_V2_ID), partialWidth(worldWidth, tileSize),
                            partialHeight(worldHeight, tileSize), tileSize));
            sceneryRegions = Map.copyOf(regions);
        } catch (RuntimeException | Error failure) {
            owner.dispose();
            throw failure;
        }
        atlasOwner = owner;
        atlas = loaded;
    }

    /** Draws only the repeated grass tiles intersecting a conservative visible world rectangle. */
    public int drawGround(SpriteBatch batch, WorldRect visibleArea) {
        requireActive();
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(visibleArea, "visibleArea");
        int drawn = 0;
        for (TerrainGroundPlanner.Tile tile : groundPlanner.plan(visibleArea)) {
            boolean rightEdge = tile.width() != TerrainAtlasCatalog.GRASS_TILE_SIZE;
            boolean topEdge = tile.height() != TerrainAtlasCatalog.GRASS_TILE_SIZE;
            GroundRegions ground = groundVariants.get(selectGroundVariant(tile));
            TextureRegion region = rightEdge && topEdge ? ground.partialCorner()
                    : rightEdge ? ground.partialRight() : topEdge ? ground.partialTop() : ground.full();
            batch.draw(region, (float) tile.x(), (float) tile.y(), tile.width(), tile.height());
            drawn++;
        }
        return drawn;
    }

    /** Draws visible footprints in a stable top-down order; sprites crossing the view edge are kept. */
    public int drawScenery(SpriteBatch batch, SceneryField field, WorldRect visibleArea) {
        requireActive();
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(visibleArea, "visibleArea");
        List<SceneryInstance> visible = new ArrayList<>(field.query(visibleArea));
        visible.sort(DRAW_ORDER);
        for (SceneryInstance item : visible) {
            TextureRegion region = sceneryRegions.get(item.assetId());
            if (region == null || item.type() == null) {
                throw new IllegalArgumentException("no atlas region for scenery asset: " + item.assetId());
            }
            float width = region.getRegionWidth() * (float) item.scale();
            float height = region.getRegionHeight() * (float) item.scale()
                    * (float) (item.height() / item.width());
            batch.draw(region, (float) item.x() - width / 2, (float) item.y() - height / 2,
                    width / 2, height / 2, width, height, 1, 1, (float) item.rotationDegrees());
        }
        return visible.size();
    }

    public int pageCount() { return atlas.getTextures().size; }
    public long estimatedBytes() { return atlasOwner.estimatedBytes(); }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        atlasOwner.dispose();
    }

    private void requireActive() {
        if (disposed) throw new IllegalStateException("terrain renderer is disposed");
    }

    private static TextureRegion subregion(TextureRegion parent, int x, int y, int width, int height) {
        return new TextureRegion(parent, x, y, width, height);
    }

    private static GroundRegions groundRegions(TextureRegion grass, int partialWidth, int partialHeight,
                                                int tileSize) {
        return new GroundRegions(grass,
                partialWidth == 0 ? null : subregion(grass, 0, 0, partialWidth, tileSize),
                partialHeight == 0 ? null
                        : subregion(grass, 0, tileSize - partialHeight, tileSize, partialHeight),
                partialWidth == 0 || partialHeight == 0 ? null
                        : subregion(grass, 0, tileSize - partialHeight, partialWidth, partialHeight));
    }

    private static int partialWidth(int worldWidth, int tileSize) { return worldWidth % tileSize; }

    private static int partialHeight(int worldHeight, int tileSize) { return worldHeight % tileSize; }

    private int selectGroundVariant(TerrainGroundPlanner.Tile tile) {
        long value = groundSeed ^ ((long) tile.column() << 32) ^ (tile.row() & 0xffffffffL);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value & 3L) == 0L ? 1 : 0;
    }

    private static int layer(SceneryType type) {
        return switch (type) {
            case PUDDLE -> 0;
            case ROCK -> 1;
            case BUSH -> 2;
            case TREE -> 3;
        };
    }

    private record GroundRegions(TextureRegion full, TextureRegion partialRight,
                                 TextureRegion partialTop, TextureRegion partialCorner) {}

}
