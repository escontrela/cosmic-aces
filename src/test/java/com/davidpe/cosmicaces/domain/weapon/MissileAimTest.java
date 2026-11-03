package com.davidpe.cosmicaces.domain.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import java.util.List;
import org.junit.jupiter.api.Test;

class MissileAimTest {
  private static MissileTarget target(MissileTarget.Kind kind, long id, float x, float y) {
    return new MissileTarget(new MissileTarget.Reference(kind, id, 0), x, y, 0, 0, 10);
  }

  @Test
  void cursorMovesInWorldAxesWithNormalizedDiagonalAndClampedBoundaries() {
    MissileAim aim = new MissileAim(new WorldBounds(1000, 2000), 100, 50);
    aim.begin(500, 1000);
    aim.move(1, 1, 1);
    assertEquals(100d, Math.hypot(aim.x() - 500, aim.y() - 1000), 0.001);
    float x = aim.x(), y = aim.y();
    aim.move(0, 0, 1);
    aim.move(1, 0, Float.NaN);
    assertEquals(x, aim.x());
    assertEquals(y, aim.y());
    aim.move(-1, 1, 100);
    assertEquals(0, aim.x());
    assertEquals(2000, aim.y());
    aim.cancel();
    aim.move(1, -1, 1);
    assertEquals(0, aim.x());
    assertEquals(2000, aim.y());
    assertThrows(IllegalArgumentException.class, () -> aim.begin(Float.NaN, 1));
  }

  @Test
  void selectsNearestIndividualAndBreaksTiesWithoutDependingOnListOrder() {
    MissileAim aim = new MissileAim(new WorldBounds(1000, 1000), 100, 30);
    aim.begin(500, 500);
    MissileTarget first = target(MissileTarget.Kind.GHOSTWING, 4, 510, 500);
    MissileTarget second = target(MissileTarget.Kind.GHOSTWING, 2, 490, 500);
    MissileTarget far = target(MissileTarget.Kind.BASTION, 0, 900, 900);
    assertEquals(second, aim.select(List.of(first, far, second)).orElseThrow());
    assertEquals(second, aim.select(List.of(second, first, far)).orElseThrow());
    assertEquals(first, aim.select(List.of(first, far)).orElseThrow());
    assertFalse(aim.select(List.of(far)).isPresent());
    aim.cancel();
    assertTrue(aim.select(List.of(first)).isEmpty());
    assertThrows(IllegalArgumentException.class, () -> aim.select(null));
  }
}
