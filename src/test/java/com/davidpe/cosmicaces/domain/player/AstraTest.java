package com.davidpe.cosmicaces.domain.player;

import static org.junit.jupiter.api.Assertions.*;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.weapon.GunBurst;
import org.junit.jupiter.api.Test;

class AstraTest {
  private static final WorldBounds WORLD = new WorldBounds(3200f, 12000f);
  private static final float EPS = 0.2f;

  @Test void fliesForwardAndBackAtFixedAltitude() {
    Astra ship = new Astra();
    ship.placeAt(1600f, 6000f, 0f, WORLD);
    ship.fly(FlightControls.neutral(), 1f, WORLD);
    assertEquals(6300f, ship.y(), EPS);
    ship.placeAt(1600f, 6000f, 180f, WORLD);
    ship.fly(FlightControls.neutral(), 1f, WORLD);
    assertEquals(5700f, ship.y(), EPS);
    assertEquals(0f, ship.height());
    assertEquals(0f, ship.pitchDegrees());
  }

  @Test void fourDiagonalsYawAndLateralAloneStrafes() {
    for (boolean up : new boolean[] {true, false}) {
      for (boolean right : new boolean[] {true, false}) {
        Astra ship = new Astra();
        ship.placeAt(1600f, 6000f, 0f, WORLD);
        ship.fly(new FlightControls(!right, right, up, !up, false), 0.5f, WORLD);
        assertEquals(right ? 45f : -45f, ship.yawDegrees(), 0.01f);
        assertEquals(right ? HeroShipSheet.Pose.YAW_RIGHT : HeroShipSheet.Pose.YAW_LEFT,
            ship.pose());
      }
    }
    Astra ship = new Astra();
    ship.placeAt(1600f, 6000f, 0f, WORLD);
    ship.fly(new FlightControls(false, true, false, false, false), 1f, WORLD);
    assertEquals(0f, ship.yawDegrees(), 0.01f);
    assertEquals(1900f, ship.x(), EPS);
    assertEquals(6300f, ship.y(), EPS);
    assertEquals(HeroShipSheet.Pose.RIGHT, ship.pose());
  }

  @Test void sustainedTurnCompletesFullHeadingRotation() {
    Astra ship = new Astra();
    ship.placeAt(1600f, 6000f, 0f, WORLD);
    FlightControls turn = new FlightControls(false, true, true, false, false);
    ship.fly(turn, 4f, WORLD);
    assertEquals(0f, ship.yawDegrees(), 0.02f);
    assertTrue(ship.x() > 1000f && ship.x() < 2200f);
    assertTrue(ship.y() > 5400f && ship.y() < 6600f);
  }

  @Test void oppositeLateralKeysCancelAndTurboAccelerates() {
    Astra ship = new Astra();
    ship.placeAt(1600f, 6000f, 0f, WORLD);
    ship.fly(new FlightControls(true,true,true,false,false), 1f, WORLD);
    assertEquals(0f, ship.yawDegrees(), 0.01f);
    assertEquals(1600f, ship.x(), EPS);
    assertEquals(550f, ship.flightSpeed(), EPS);
  }

  @Test void allEdgesAndCornersSteerInward() {
    Astra sample = new Astra();
    float maxX = WORLD.maxX(sample.drawWidth());
    float maxY = WORLD.maxY(sample.drawHeight());
    float[][] cases = {{0f,6000f,-90f},{maxX,6000f,90f},
        {1600f,0f,180f},{1600f,maxY,0f},{0f,0f,-135f},{maxX,maxY,45f}};
    for (float[] edge : cases) {
      Astra ship = new Astra();
      ship.placeAt(edge[0], edge[1], edge[2], WORLD);
      ship.fly(FlightControls.neutral(), 3f, WORLD);
      assertTrue(ship.x() > 0f && ship.x() < maxX, "x for edge " + edge[2]);
      assertTrue(ship.y() > 0f && ship.y() < maxY, "y for edge " + edge[2]);
    }
  }

  @Test void largeDeltaAtUltraSpeedCannotEscapeOrRemainPinned() {
    Astra ship = new Astra();
    float maxX = WORLD.maxX(ship.drawWidth());
    ship.placeAt(maxX - 40f, 6000f, 90f, WORLD);
    ship.fly(new FlightControls(false,false,false,false,true), 5f, WORLD);
    assertTrue(ship.x() >= 0f && ship.x() < maxX);
    assertTrue(ship.y() > 0f && ship.y() < WORLD.maxY(ship.drawHeight()));
    float x = ship.x();
    float y = ship.y();
    ship.fly(FlightControls.neutral(), 1f, WORLD);
    assertTrue(Math.hypot(ship.x() - x, ship.y() - y) > 100d);
  }

  @Test void ultraIsSingleUseAndBrakeWins() {
    Astra ship = new Astra();
    ship.placeAt(1600f, 6000f, 0f, WORLD);
    ship.fly(new FlightControls(false,false,false,false,true), 2f, WORLD);
    assertEquals(750f, ship.flightSpeed(), EPS);
    assertEquals(6f, ship.ultraRemainingSeconds(), 0.02f);
    ship.fly(new FlightControls(false,false,true,true,true), 2f, WORLD);
    assertEquals(0f, ship.flightSpeed(), EPS);
    assertEquals(4f, ship.ultraRemainingSeconds(), 0.02f);
    ship.fly(FlightControls.neutral(), 1f, WORLD);
    assertEquals(300f, ship.flightSpeed(), EPS);
    ship.fly(new FlightControls(false,false,true,false,false), 4f, WORLD);
    assertEquals(0f, ship.ultraRemainingSeconds(), EPS);
    assertEquals(550f, ship.flightSpeed(), EPS);
    ship.fly(new FlightControls(false,false,false,false,true), 1f, WORLD);
    assertEquals(0f, ship.ultraRemainingSeconds(), EPS);
    assertEquals(300f, ship.flightSpeed(), EPS);
  }

  @Test void shotAtFiresFromTwoDistinctMeasuredCannonMouths() {
    Astra ship = new Astra();
    float centerX = 1600f;
    float centerY = 6000f;

    GunBurst.Shot north = ship.shotAt(centerX, centerY, 0f, 1200f);
    assertEquals(2, north.muzzles().size());
    assertEquals(0f, north.forwardX(), 1e-5f);
    assertEquals(1f, north.forwardY(), 1e-5f);
    GunBurst.Muzzle left = north.muzzles().get(0);
    GunBurst.Muzzle right = north.muzzles().get(1);
    assertTrue(left.x() < centerX && right.x() > centerX, "mouths must sit on both sides");
    assertEquals(centerX, (left.x() + right.x()) / 2f, 1f);
    assertEquals(left.y(), right.y(), 1.5f);

    GunBurst.Shot east = ship.shotAt(centerX, centerY, 90f, 1200f);
    assertEquals(1f, east.forwardX(), 1e-5f);
    assertEquals(0f, east.forwardY(), 1e-5f);
    assertNotEquals(east.muzzles().get(0).y(), east.muzzles().get(1).y(), 1e-3f);

    GunBurst.Shot south = ship.shotAt(centerX, centerY, 180f, 1200f);
    assertEquals(0f, south.forwardX(), 1e-5f);
    assertEquals(-1f, south.forwardY(), 1e-5f);
  }

  @Test void shotAtRejectsInvalidInput() {
    Astra ship = new Astra();
    assertThrows(IllegalArgumentException.class, () -> ship.shotAt(Float.NaN, 0f, 0f, 1200f));
    assertThrows(IllegalArgumentException.class, () -> ship.shotAt(0f, Float.NaN, 0f, 1200f));
    assertThrows(IllegalArgumentException.class, () -> ship.shotAt(0f, 0f, 0f, 0f));
  }

  @Test void framePartitionAndInvalidDelta() {
    Astra one = new Astra();
    Astra many = new Astra();
    one.placeAt(1600f, 6000f, 0f, WORLD);
    many.placeAt(1600f, 6000f, 0f, WORLD);
    FlightControls turn = new FlightControls(false,true,true,false,false);
    one.fly(turn, 2f, WORLD);
    for (int i = 0; i < 120; i++) many.fly(turn, 1f / 60f, WORLD);
    assertEquals(one.x(), many.x(), 1f);
    assertEquals(one.y(), many.y(), 1f);
    float y = one.y();
    one.fly(new FlightControls(false,false,false,false,true), Float.NaN, WORLD);
    assertEquals(y, one.y());
    assertFalse(one.ultraUsed());
  }
}
