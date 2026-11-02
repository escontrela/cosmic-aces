package com.davidpe.cosmicaces.domain.weapon;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.CombustionStage;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Faction;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Heading;

/** One phase-owned missile texture and its prepared regions, shared by all missiles. */
public final class GuidedMissileVisuals implements Disposable {
  private final Texture texture;
  private final Color savedBatchColor = new Color();
  private final TextureRegion[][][] regions = new TextureRegion[Faction.values().length]
      [Heading.values().length][CombustionStage.values().length];
  private boolean disposed;

  public GuidedMissileVisuals() {
    Texture loaded = new Texture(Gdx.files.internal(GuidedMissileSheet.internalPath()));
    try {
      if (loaded.getWidth() != GuidedMissileSheet.WIDTH
          || loaded.getHeight() != GuidedMissileSheet.HEIGHT) {
        throw new IllegalStateException("Guided missile sheet dimensions differ from metadata");
      }
      loaded.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
      for (Faction faction : Faction.values()) {
        for (Heading heading : Heading.values()) {
          for (CombustionStage stage : CombustionStage.values()) {
            regions[faction.ordinal()][heading.ordinal()][stage.ordinal()] =
                GuidedMissileSheet.region(loaded, faction, heading, stage);
          }
        }
      }
      texture = loaded;
    } catch (RuntimeException | Error failure) {
      loaded.dispose();
      throw failure;
    }
  }

  /** Returns a cached, borrowed region; callers must not flip or otherwise mutate it. */
  public TextureRegion region(Faction faction, Heading heading, CombustionStage stage) {
    if (disposed) {
      throw new IllegalStateException("Guided missile visuals have been disposed");
    }
    if (faction == null || heading == null || stage == null) {
      throw new IllegalArgumentException("Faction, heading and combustion stage are required");
    }
    return regions[faction.ordinal()][heading.ordinal()][stage.ordinal()];
  }

  /** Draws one missile from cached regions with its body pivot fixed at its world coordinate. */
  public void draw(SpriteBatch batch, GuidedMissile missile, float astraFuselageLength) {
    GuidedMissileSpriteLayout.Layout layout =
        GuidedMissileSpriteLayout.forMissile(missile, astraFuselageLength);
    draw(batch, missile, layout);
  }

  /** Draws using one already calculated layout so culling and drawing share the same bounds. */
  public void draw(SpriteBatch batch, GuidedMissile missile,
      GuidedMissileSpriteLayout.Layout layout) {
    ensureOpen();
    if (batch == null || missile == null || layout == null) {
      throw new IllegalArgumentException("Batch, missile and sprite layout are required");
    }
    TextureRegion region = region(layout.faction(), layout.heading(), layout.combustionStage());
    savedBatchColor.set(batch.getColor());
    try {
      batch.setColor(Color.WHITE);
      batch.draw(region, missile.x() - layout.originX(), missile.y() - layout.originY(),
          layout.originX(), layout.originY(), layout.width(), layout.height(), 1f, 1f,
          layout.rotationDegrees());
    } finally {
      batch.setColor(savedBatchColor);
    }
  }

  private void ensureOpen() {
    if (disposed) throw new IllegalStateException("Guided missile visuals have been disposed");
  }

  @Override
  public void dispose() {
    if (!disposed) {
      disposed = true;
      texture.dispose();
    }
  }
}
