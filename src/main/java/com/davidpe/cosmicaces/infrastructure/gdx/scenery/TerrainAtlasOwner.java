package com.davidpe.cosmicaces.infrastructure.gdx.scenery;

import com.badlogic.gdx.utils.Disposable;
import java.util.Objects;

/** Lazy, idempotent owner of the shared atlas page resource. */
public final class TerrainAtlasOwner<T> implements Disposable {
    public interface ResourceFactory<T> {
        T load(String internalPath);
        void dispose(T resource);
        long estimatedBytes(T resource);
    }

    private final String internalPath;
    private final ResourceFactory<T> factory;
    private T resource;
    private long estimatedBytes;
    private boolean disposed;

    public TerrainAtlasOwner(String internalPath, ResourceFactory<T> factory) {
        if (internalPath == null || internalPath.isBlank()) throw new IllegalArgumentException("atlas path must not be blank");
        this.internalPath = internalPath;
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public synchronized T resource() {
        if (disposed) throw new IllegalStateException("atlas owner is disposed");
        if (resource == null) {
            T loaded = Objects.requireNonNull(factory.load(internalPath), "atlas factory returned null");
            try {
                long bytes = factory.estimatedBytes(loaded);
                if (bytes < 0) throw new IllegalStateException("atlas byte estimate must not be negative");
                resource = loaded;
                estimatedBytes = bytes;
            } catch (RuntimeException | Error failure) {
                factory.dispose(loaded);
                throw failure;
            }
        }
        return resource;
    }

    public synchronized boolean isLoaded() { return resource != null; }
    public synchronized long estimatedBytes() { return estimatedBytes; }

    @Override
    public synchronized void dispose() {
        if (disposed) return;
        disposed = true;
        if (resource != null) {
            T owned = resource;
            resource = null;
            estimatedBytes = 0;
            factory.dispose(owned);
        }
    }
}
