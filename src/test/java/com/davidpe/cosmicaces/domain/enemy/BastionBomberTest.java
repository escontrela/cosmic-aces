package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BastionBomberTest {
  private static final float EPS = 0.001f;

  @Test
  void waitsTenSecondsThenFliesSouthTowardTheAlliedRunway() {
    BastionBomber bomber = new BastionBomber(100f, 1000f, 100f, 0f, 100f, 0f);
    assertTrue(BastionBomber.FLIGHT_SPEED < 150f,
        "the bomber must stay slower than Astra's default normal speed");

    bomber.advance(BastionBomber.PARKED_SECONDS - 0.01f);
    assertEquals(BastionBomber.FlightState.PARKED, bomber.flightState());
    assertEquals(1000f, bomber.centerY(), EPS);

    bomber.advance(0.02f);
    assertEquals(BastionBomber.FlightState.INBOUND, bomber.flightState());
    assertEquals(180f, bomber.headingDegrees(), EPS);
    assertEquals(100f, bomber.centerX(), EPS, "the approach stays on its straight runway axis");
    assertEquals(0, bomber.frameIndex(), "straight flight keeps a stable pose without banking");
    assertEquals(1000f - BastionBomber.FLIGHT_SPEED * 0.01f, bomber.centerY(), EPS);
  }

  @Test
  void entersAndMaintainsAnOrbitAroundTheAlliedBase() {
    BastionBomber bomber = new BastionBomber(100f, 1000f, 100f, 0f, 100f, 0f);
    bomber.advance(BastionBomber.PARKED_SECONDS + 2f);
    assertEquals(BastionBomber.FlightState.INBOUND, bomber.flightState());

    bomber.advance(10f);
    assertEquals(BastionBomber.FlightState.ORBITING, bomber.flightState());
    assertEquals(3, bomber.frameIndex(), "turns use the fourth sheet frame");
    assertEquals(BastionBomber.ORBIT_RADIUS,
        (float) Math.hypot(bomber.centerX() - 100f, bomber.centerY()), 0.01f);
    float firstX = bomber.centerX();
    float firstY = bomber.centerY();

    bomber.advance(BastionBomber.TURN_FRAME_SECONDS);
    assertEquals(BastionBomber.ORBIT_RADIUS,
        (float) Math.hypot(bomber.centerX() - 100f, bomber.centerY()), 0.01f);
    assertNotEquals(firstX, bomber.centerX());
    assertNotEquals(firstY, bomber.centerY());
    assertEquals(4, bomber.frameIndex(), "the turn alternates to the fifth sheet frame");
  }

  @Test
  void sheetSlicesStayInsideTheImageAndHaveDistinctHorizontalFrames() {
    assertEquals(5, BastionBomberSheet.frameSlices().size());
    for (BastionBomberSheet.Slice frame : BastionBomberSheet.frameSlices()) {
      assertEquals(true, frame.x() >= 0 && frame.y() >= 0);
      assertEquals(true, frame.x() + frame.width() <= BastionBomberSheet.WIDTH);
      assertEquals(true, frame.y() + frame.height() <= BastionBomberSheet.HEIGHT);
      assertEquals(true, frame.width() > 0 && frame.height() > 0);
    }
    assertEquals("assets/images/enemy/bastion_bomber/bastion_bomber_roll_sheet_v1.png",
        BastionBomberSheet.internalPath());
    assertEquals(new BastionBomberSheet.Slice(4, 109, 376, 497),
        BastionBomberSheet.frameSlice(0));
    for (int index = 1; index < BastionBomberSheet.FRAME_COUNT; index++) {
      BastionBomberSheet.Slice previous = BastionBomberSheet.frameSlice(index - 1);
      BastionBomberSheet.Slice current = BastionBomberSheet.frameSlice(index);
      assertTrue(previous.x() + previous.width() < current.x(),
          "adjacent frame crops must not contain fragments from the neighboring aircraft");
    }
  }
}
