package com.davidpe.cosmicaces.domain.weapon;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.CombustionStage;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Faction;
import com.davidpe.cosmicaces.domain.weapon.GuidedMissileSheet.Heading;

/** One phase-owned missile texture and its prepared regions, shared by all missiles. */
public final class GuidedMissileVisuals implements Disposable {
  private final Texture texture;
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

  @Override
  public void dispose() {
    if (!disposed) {
      disposed = true;
      texture.dispose();
    }
  }
}
