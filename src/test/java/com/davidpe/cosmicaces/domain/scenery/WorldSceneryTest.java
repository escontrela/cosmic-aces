package com.davidpe.cosmicaces.domain.scenery;

import static org.junit.jupiter.api.Assertions.*;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import org.junit.jupiter.api.Test;

class WorldSceneryTest {
  private static final WorldBounds WORLD = new WorldBounds(3200f, 12000f);

  @Test void sameSeedKeepsAllStarsAndIslandsAtTheSameCoordinates() {
    WorldScenery first = new WorldScenery(WORLD, 12345L);
    WorldScenery same = new WorldScenery(WORLD, 12345L);
    WorldScenery other = new WorldScenery(WORLD, 12346L);
    assertEquals(first.stars(), same.stars());
    assertEquals(first.islands(), same.islands());
    assertNotEquals(first.stars(), other.stars());
    assertEquals(12000, first.stars().size());
    assertTrue(first.islands().size() > 100);
    assertThrows(UnsupportedOperationException.class, () -> first.stars().clear());
  }

  @Test void starsCoverDistantRegionsAndCullingPreservesLocations() {
    WorldScenery scenery = new WorldScenery(WORLD, 12345L);
    assertTrue(scenery.visibleStarCount(1200f, 600f, 2000f, 1200f) > 30);
    assertTrue(scenery.visibleStarCount(1200f, 10500f, 2000f, 11100f) > 30);
    assertEquals(0, scenery.visibleStarCount(-1000f, -1000f, -500f, -500f));
    assertTrue(scenery.islands().stream().anyMatch(island ->
        island.x() > 1000f && island.x() < 1900f && island.y() < 1200f));
    for (WorldScenery.Star star : scenery.stars()) {
      assertTrue(star.x() >= 0f && star.x() <= WORLD.width());
      assertTrue(star.y() >= 0f && star.y() <= WORLD.height());
    }
    for (WorldScenery.Island island : scenery.islands()) {
      assertTrue(island.x() >= 0f && island.x() + 7f * island.tileSize() < WORLD.width());
      assertTrue(island.y() >= 0f && island.y() + 6f * island.tileSize() < WORLD.height());
    }
  }
}
