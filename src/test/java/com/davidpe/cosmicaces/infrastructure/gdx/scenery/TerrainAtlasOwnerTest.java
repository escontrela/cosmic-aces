package com.davidpe.cosmicaces.infrastructure.gdx.scenery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerrainAtlasOwnerTest {
    @Test
    void lazilyLoadsOneSharedResourceAndDisposesItExactlyOnce() {
        FakeFactory factory = new FakeFactory();
        TerrainAtlasOwner<String> owner = new TerrainAtlasOwner<>("atlas", factory);

        assertFalse(owner.isLoaded());
        assertEquals(0, owner.estimatedBytes());
        String first = owner.resource();
        assertSame(first, owner.resource());
        assertEquals(1, factory.loads);
        assertEquals(4096, owner.estimatedBytes());

        owner.dispose();
        owner.dispose();
        assertEquals(1, factory.disposals);
        assertEquals(0, owner.estimatedBytes());
        assertThrows(IllegalStateException.class, owner::resource);
    }

    @Test
    void failedSizeInspectionReleasesThePartiallyLoadedResource() {
        FakeFactory factory = new FakeFactory();
        factory.invalidSize = true;
        TerrainAtlasOwner<String> owner = new TerrainAtlasOwner<>("atlas", factory);

        assertThrows(IllegalStateException.class, owner::resource);
        assertFalse(owner.isLoaded());
        assertEquals(1, factory.loads);
        assertEquals(1, factory.disposals);
        owner.dispose();
        assertEquals(1, factory.disposals);
    }

    @Test
    void disposingBeforeFirstUseDoesNotLoadAnUnusedAtlas() {
        FakeFactory factory = new FakeFactory();
        TerrainAtlasOwner<String> owner = new TerrainAtlasOwner<>("atlas", factory);
        owner.dispose();

        assertEquals(0, factory.loads);
        assertEquals(0, factory.disposals);
        assertFalse(owner.isLoaded());
        assertThrows(IllegalStateException.class, owner::resource);
    }

    private static final class FakeFactory implements TerrainAtlasOwner.ResourceFactory<String> {
        private int loads;
        private int disposals;
        private boolean invalidSize;

        @Override
        public String load(String path) {
            loads++;
            return new String(path);
        }

        @Override
        public void dispose(String resource) {
            disposals++;
        }

        @Override
        public long estimatedBytes(String resource) {
            return invalidSize ? -1 : 4096;
        }
    }
}
