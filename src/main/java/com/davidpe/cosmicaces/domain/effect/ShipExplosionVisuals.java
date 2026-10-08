package com.davidpe.cosmicaces.domain.effect;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

/**
 * One shared texture and eight prepared regions, owned and disposed by the phase screen.
 * Animation instances only borrow these visuals; this class does not time deaths or respawns.
 */
public final class ShipExplosionVisuals implements Disposable {
  private final Texture texture;
  private final TextureRegion[] frames = new TextureRegion[ShipExplosionSheet.FRAME_COUNT];
  private boolean disposed;

  public ShipExplosionVisuals() {
    Texture loaded = new Texture(Gdx.files.internal(ShipExplosionSheet.internalPath()));
    try {
      if (loaded.getWidth() != ShipExplosionSheet.WIDTH
          || loaded.getHeight() != ShipExplosionSheet.HEIGHT) {
        throw new IllegalStateException("Explosion sheet dimensions differ from measured metadata");
      }
      loaded.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
      for (int i = 0; i < frames.length; i++) {
        ShipExplosionSheet.Slice slice = ShipExplosionSheet.frameSlice(i);
        frames[i] = new TextureRegion(loaded, slice.x(), slice.y(), slice.width(), slice.height());
      }
      texture = loaded;
    } catch (RuntimeException | Error failure) {
      loaded.dispose();
      throw failure;
    }
  }

  /** Prepared region; borrowed, so callers must not flip or otherwise mutate it. */
  public TextureRegion region(int frameIndex) {
    ensureOpen();
    return frames[frameIndex];
  }

  /**
   * Draws the chosen frame centered on a world position into an already open world-space batch.
   * peakSize is the largest frame dimension in world units, not the size of every frame.
   * Using the same peakSize preserves natural growth and decay instead of stretching each crop.
   * The caller owns begin/end, projection, animation time and any batch tint.
   */
  public void draw(SpriteBatch batch, int frameIndex, float centerX, float centerY,
      float peakSize) {
    ensureOpen();
    if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
        || !Float.isFinite(peakSize) || peakSize <= 0f) {
      throw new IllegalArgumentException("Explosion center and positive size must be finite");
    }
    ShipExplosionSheet.Slice slice = ShipExplosionSheet.frameSlice(frameIndex);
    float scale = peakSize / ShipExplosionSheet.REFERENCE_SIZE;
    batch.draw(region(frameIndex), centerX - slice.anchorX() * scale,
        centerY - slice.anchorY() * scale, slice.width() * scale, slice.height() * scale);
  }

  private void ensureOpen() {
    if (disposed) {
      throw new IllegalStateException("Explosion visuals have been disposed");
    }
  }

  @Override
  public void dispose() {
    if (!disposed) {
      disposed = true;
      texture.dispose();
    }
  }
}
