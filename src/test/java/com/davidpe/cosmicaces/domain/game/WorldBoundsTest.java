package com.davidpe.cosmicaces.domain.game;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorldBoundsTest {
  @Test void usableRangeAccountsForTheShipBox() {
    WorldBounds world = new WorldBounds(3200f, 12000f);
    assertEquals(3100f, world.maxX(100f));
    assertEquals(11800f, world.maxY(200f));
    assertEquals(3100f, world.clampX(5000f, 100f));
    assertEquals(0f, world.clampY(-3f, 200f));
  }

  @Test void rejectsInvalidDimensionsAndOversizedBoxes() {
    assertThrows(IllegalArgumentException.class, () -> new WorldBounds(Float.NaN, 1f));
    assertThrows(IllegalArgumentException.class, () -> new WorldBounds(1f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new WorldBounds(10f, 10f).maxX(10f));
  }
}
