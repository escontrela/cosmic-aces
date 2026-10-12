package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

/**
 * LibGDX {@link PhaseOneTerrainCache.ResourceFactory} for the PhaseOne terrain map: the only place
 * that creates and frees the map's GL {@link Texture}s.
 *
 * <p>It must be used from the LibGDX lifecycle: {@link #create(String)} uploads the texture on the
 * render thread, and the owning {@code PhaseOneTerrainCache} calls {@link #dispose(Texture)} from
 * its own {@code dispose()} when the screen leaves. The integration of the drawing belongs to the
 * render subtask; this adapter is not wired into {@code PhaseOneScreen} yet.
 *
 * <p>Memory is estimated as RGBA8888 (four bytes per pixel), the default texture format, so the
 * counters report the real per-tile cost: 1024 × 768 × 4 ≈ 3 MiB per tile.
 */
public final class PhaseOneTerrainTextureFactory
    implements PhaseOneTerrainCache.ResourceFactory<Texture> {

  private static final int BYTES_PER_PIXEL = 4;

  @Override
  public Texture create(String path) {
    return new Texture(Gdx.files.internal(path));
  }

  @Override
  public void dispose(Texture texture) {
    texture.dispose();
  }

  @Override
  public long estimatedBytes(Texture texture) {
    return (long) texture.getWidth() * texture.getHeight() * BYTES_PER_PIXEL;
  }
}
