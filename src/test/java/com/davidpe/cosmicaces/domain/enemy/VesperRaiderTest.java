package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.ship.Ship;
import com.davidpe.cosmicaces.domain.weapon.GunBurst;
import java.util.List;
import org.junit.jupiter.api.Test;

class VesperRaiderTest {

  private static final float SPEED = 100f;
  private static final float EPSILON = 0.001f;
  private static final WorldBounds WORLD = new WorldBounds(4000f, 12000f);

  @Test
  void descendsStraightDownWhenHeadingIsZero() {
    VesperRaider raider = raider(100f, 200f, 0f);
    raider.advance(1f);
    assertEquals(100f, raider.x(), EPSILON);
    assertEquals(100f, raider.y(), EPSILON);
  }

  @Test
  void positiveHeadingMovesRightAndDown() {
    VesperRaider raider = raider(0f, 100f, 20f);
    raider.advance(1f);
    assertEquals(34.202f, raider.x(), EPSILON);
    assertEquals(6.031f, raider.y(), EPSILON);
  }

  @Test
  void negativeHeadingMovesLeftAndDown() {
    VesperRaider raider = raider(0f, 100f, -20f);
    raider.advance(1f);
    assertEquals(-34.202f, raider.x(), EPSILON);
    assertEquals(6.031f, raider.y(), EPSILON);
  }

  @Test
  void nonPositiveOrNonFiniteDeltaDoesNotMoveTheRaider() {
    VesperRaider raider = raider(50f, 50f, 10f);
    raider.advance(0f);
    raider.advance(-1f);
    raider.advance(Float.NaN);
    raider.advance(Float.POSITIVE_INFINITY);
    assertEquals(50f, raider.x(), EPSILON);
    assertEquals(50f, raider.y(), EPSILON);
  }

  @Test
  void headingCanBeReplacedAndClearsTheTurn() {
    VesperRaider raider = raider(0f, 0f, 0f);
    raider.steerTowards(30f, 10f);
    assertEquals(1, raider.turnDirection());

    raider.setHeadingDegrees(-12f);
    assertEquals(-12f, raider.headingDegrees(), EPSILON);
    assertEquals(0, raider.turnDirection());
  }

  @Test
  void steeringIsBoundedAndRecordsTheTurnDirection() {
    VesperRaider raider = raider(0f, 0f, 0f);
    raider.steerTowards(90f, 20f);
    assertEquals(20f, raider.headingDegrees(), EPSILON);
    assertEquals(1, raider.turnDirection());

    raider.steerTowards(-90f, 20f);
    assertEquals(0f, raider.headingDegrees(), EPSILON);
    assertEquals(-1, raider.turnDirection());
  }

  @Test
  void steeringNeverOvershootsATargetWithinTheLimit() {
    VesperRaider raider = raider(0f, 0f, 0f);
    raider.steerTowards(10f, 90f);
    assertEquals(10f, raider.headingDegrees(), EPSILON);
    assertEquals(1, raider.turnDirection());
  }

  @Test
  void yawPoseAccompaniesTurnsAndBankPoseAccompaniesDrift() {
    VesperRaider raider = raider(0f, 0f, 0f);
    raider.steerTowards(30f, 10f);
    assertEquals(VesperRaiderSheet.Pose.YAW_RIGHT, raider.pose());

    raider.setHeadingDegrees(0f);
    raider.setDriftDirection(1);
    assertEquals(VesperRaiderSheet.Pose.LEFT, raider.pose());

    raider.setDriftDirection(-1);
    assertEquals(VesperRaiderSheet.Pose.RIGHT, raider.pose());

    raider.setDriftDirection(0);
    assertEquals(VesperRaiderSheet.Pose.NEUTRAL, raider.pose());
  }

  @Test
  void clampToWorldKeepsTheBoxInside() {
    VesperRaider low = raider(-50f, -50f, 0f);
    low.clampToWorld(WORLD);
    assertEquals(0f, low.x(), EPSILON);
    assertEquals(0f, low.y(), EPSILON);

    VesperRaider high = raider(WORLD.width() + 10f, WORLD.height() + 10f, 0f);
    high.clampToWorld(WORLD);
    assertEquals(WORLD.maxX(high.drawWidth()), high.x(), EPSILON);
    assertEquals(WORLD.maxY(high.drawHeight()), high.y(), EPSILON);
  }

  @Test
  void rejectsNonPositiveSpeedOrBox() {
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(0f, 10f, 10f, 0f, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(Float.NaN, 10f, 10f, 0f, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 0f, 10f, 0f, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 10f, -1f, 0f, 0f, 0f));
  }

  @Test
  void rejectsNonFinitePosition() {
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 10f, 10f, Float.NaN, 0f, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 10f, 10f, 0f, Float.NaN, 0f));
    assertThrows(IllegalArgumentException.class, () -> new VesperRaider(100f, 10f, 10f, 0f, 0f, Float.NaN));
  }

  @Test
  void shotAtUsesTheDescentForwardVectorForBothTurnDirections() {
    GunBurst.Shot straight = raider(0f, 0f, 0f).shotAt(0f, 0f, 0f, 1000f);
    assertEquals(0f, straight.forwardX(), 1e-6f);
    assertEquals(-1f, straight.forwardY(), 1e-6f);

    GunBurst.Shot right = raider(0f, 0f, 0f).shotAt(0f, 0f, 90f, 1000f);
    assertEquals(1f, right.forwardX(), 1e-6f);
    assertEquals(0f, right.forwardY(), 1e-6f);

    GunBurst.Shot left = raider(0f, 0f, 0f).shotAt(0f, 0f, -90f, 1000f);
    assertEquals(-1f, left.forwardX(), 1e-6f);
    assertEquals(0f, left.forwardY(), 1e-6f);
  }

  @Test
  void shotAtProducesTwoDistinctSideCannonMuzzles() {
    GunBurst.Shot shot = raider(100f, 200f, 0f).shotAt(100f, 200f, 0f, 1000f);
    assertEquals(2, shot.muzzles().size());
    assertNotEquals(shot.muzzles().get(0).x(), shot.muzzles().get(1).x(), 1e-3f,
        "the two side cannons must not share a lateral origin");
  }

  @Test
  void shotMuzzlesRotateWithTheHeading() {
    GunBurst.Shot shot = raider(0f, 0f, 0f).shotAt(0f, 0f, 90f, 1000f);
    // Heading 90: forward=(1,0), right=(0,1); the lateral cannon split moves to world Y.
    assertEquals(shot.muzzles().get(0).x(), shot.muzzles().get(1).x(), 0.05f);
    assertNotEquals(shot.muzzles().get(0).y(), shot.muzzles().get(1).y(), 1e-3f);
    for (GunBurst.Muzzle muzzle : shot.muzzles()) {
      assertTrue(Float.isFinite(muzzle.x()) && Float.isFinite(muzzle.y()));
    }
  }

  @Test
  void shotAtSampleKeepsTheHistoricalHeadingAndPose() {
    VesperRaider raider = raider(100f, 200f, 0f);
    raider.steerTowards(30f, 10f); // a bounded right turn latches the yaw-right pose
    assertEquals(VesperRaiderSheet.Pose.YAW_RIGHT, raider.pose());
    VesperRaider.ShotSample sample = raider.captureShotSample();

    // The live state changes after the capture; the sample must stay frozen.
    raider.setHeadingDegrees(90f);
    raider.setDriftDirection(0);
    assertEquals(VesperRaiderSheet.Pose.NEUTRAL, raider.pose());
    assertNotEquals(sample.headingDegrees(), raider.headingDegrees(), EPSILON);

    GunBurst.Shot historical = raider.shotAt(sample, 1000f);
    float radians = (float) Math.toRadians(sample.headingDegrees());
    assertEquals((float) Math.sin(radians), historical.forwardX(), 1e-5f,
        "the frozen direction must follow the sample heading");
    assertEquals((float) -Math.cos(radians), historical.forwardY(), 1e-5f);

    // Both mouths come from the captured yaw-right pose, not the live neutral pose.
    List<VesperRaiderSheet.CannonMouth> mouths =
        VesperRaiderSheet.cannonMouths(VesperRaiderSheet.Pose.YAW_RIGHT);
    float rightX = (float) Math.cos(radians);
    float rightY = (float) Math.sin(radians);
    for (int index = 0; index < mouths.size(); index++) {
      VesperRaiderSheet.CannonMouth mouth = mouths.get(index);
      float expectedX = sample.centerX()
          + rightX * mouth.lateral() * raider.drawWidth()
          + historical.forwardX() * mouth.forward() * raider.drawHeight();
      float expectedY = sample.centerY()
          + rightY * mouth.lateral() * raider.drawWidth()
          + historical.forwardY() * mouth.forward() * raider.drawHeight();
      assertEquals(expectedX, historical.muzzles().get(index).x(), 1e-3f);
      assertEquals(expectedY, historical.muzzles().get(index).y(), 1e-3f);
    }
    assertNotEquals(historical.muzzles().get(0).x(),
        raider.shotAt(raider.captureShotSample(), 1000f).muzzles().get(0).x(), 1e-3f,
        "the historical pose must not collapse to the live neutral pose");
  }

  @Test
  void shotAtSampleRejectsAMissingSample() {
    assertThrows(IllegalArgumentException.class,
        () -> raider(0f, 0f, 0f).shotAt((VesperRaider.ShotSample) null, 100f));
  }

  @Test
  void spritePlacementCentersFlightRegionsAndAnchorsTheFiringVariant() throws Exception {
    VesperRaider raider = raider(0f, 0f, 0f);
    raider.setDriftDirection(1);
    assertEquals(VesperRaiderSheet.Pose.LEFT, raider.pose());
    TextureRegion region = new TextureRegion();

    Ship.SpritePlacement idle = spritePlacement(raider, region);
    assertTrue(idle.scale() > 0f);
    assertEquals(0f, idle.offsetX(), 1e-4f, "flight drawing stays centered on the box");
    assertEquals(0f, idle.offsetY(), 1e-4f);

    raider.setMuzzleFlashVisible(true);
    Ship.SpritePlacement firing = spritePlacement(raider, region);
    VesperRaiderSheet.FiringPlacement expected =
        VesperRaiderSheet.firingPlacement(VesperRaiderSheet.Pose.LEFT);
    assertEquals(expected.offsetXPx() * idle.scale(), firing.offsetX(), 1e-3f);
    assertEquals(expected.offsetYPx() * idle.scale(), firing.offsetY(), 1e-3f);
    assertEquals(idle.scale(), firing.scale(), 1e-4f, "the uniform scale never changes");
  }

  @Test
  void continuousSteeringIntegratesBoundedAngularVelocityAndAcceleration() {
    float maxRate = 130f;
    float maxAccel = 360f;
    float dt = 1f / 120f;
    float[][] cases = {{0f, 180f}, {0f, 90f}, {90f, -90f}, {179f, -179f}, {0f, -180f}};
    for (float[] expected : cases) {
      VesperRaider raider = raider(0f, 0f, expected[0]);
      float previousHeading = raider.headingDegrees();
      float previousOmega = raider.angularVelocityDegrees();
      float maxOmega = 0f;
      for (int i = 0; i < 600; i++) { // 5 s at 120 Hz
        raider.steerTowards(expected[1], maxRate, maxAccel, dt);
        float omega = raider.angularVelocityDegrees();
        float step = Math.abs(normalizeYaw(raider.headingDegrees() - previousHeading));
        assertTrue(step <= maxRate * dt + 0.001f,
            "heading jumped " + step + " from " + previousHeading + " to " + raider.headingDegrees());
        assertTrue(Math.abs(omega - previousOmega) <= maxAccel * dt + 0.001f,
            "angular velocity jumped from " + previousOmega + " to " + omega);
        assertTrue(Math.abs(omega) <= maxRate + 0.001f, "angular velocity exceeded its maximum");
        assertTrue(Float.isFinite(raider.headingDegrees()) && Float.isFinite(omega),
            "heading and angular velocity must stay finite");
        maxOmega = Math.max(maxOmega, Math.abs(omega));
        previousHeading = raider.headingDegrees();
        previousOmega = omega;
      }
      assertEquals(0f, normalizeYaw(raider.headingDegrees() - expected[1]), 1f,
          "turn from " + expected[0] + " to " + expected[1] + " must converge");
      assertTrue(maxOmega > 1f, "the turn must actually rotate the heading");
    }
  }

  @Test
  void speedRampRespectsAccelerationAndDecelerationLimits() {
    VesperRaider raider = raider(0f, 0f, 0f); // current speed starts at SPEED == 100
    float dt = 1f / 120f;
    float acceleration = 300f;
    float deceleration = 450f;
    float previous = raider.currentSpeed();
    for (int i = 0; i < 120; i++) { // ramp up toward 500
      raider.advance(dt, 500f, acceleration, deceleration);
      float delta = raider.currentSpeed() - previous;
      assertTrue(delta >= -0.001f && delta <= acceleration * dt + 0.001f, "up ramp " + delta);
      previous = raider.currentSpeed();
    }
    assertTrue(raider.currentSpeed() > 100f, "the speed must ramp up");
    for (int i = 0; i < 120; i++) { // ramp down toward 100
      raider.advance(dt, 100f, acceleration, deceleration);
      float delta = raider.currentSpeed() - previous;
      assertTrue(delta <= 0.001f && delta >= -deceleration * dt - 0.001f, "down ramp " + delta);
      previous = raider.currentSpeed();
    }
    assertTrue(raider.currentSpeed() < 500f, "the speed must ramp down");
  }

  @Test
  void invalidDeltasDoNotChangeSteeringOrSpeedState() {
    VesperRaider raider = raider(0f, 0f, 0f);
    raider.steerTowards(90f, 130f, 360f, 1f / 120f);
    raider.advance(1f / 120f, 500f, 300f, 450f);
    float heading = raider.headingDegrees();
    float omega = raider.angularVelocityDegrees();
    float speed = raider.currentSpeed();
    float x = raider.x();
    float y = raider.y();
    for (float bad : new float[] {0f, -1f, Float.NaN, Float.POSITIVE_INFINITY}) {
      raider.steerTowards(90f, 130f, 360f, bad);
      raider.advance(bad, 500f, 300f, 450f);
    }
    assertEquals(heading, raider.headingDegrees(), EPSILON);
    assertEquals(omega, raider.angularVelocityDegrees(), EPSILON);
    assertEquals(speed, raider.currentSpeed(), EPSILON);
    assertEquals(x, raider.x(), EPSILON);
    assertEquals(y, raider.y(), EPSILON);
  }

  @Test
  void approachingAFixedTargetDoesNotAlternateTheTurnSign() {
    VesperRaider raider = raider(0f, 0f, 0f);
    float previousSign = 0f;
    int flips = 0;
    for (int i = 0; i < 600; i++) {
      raider.steerTowards(150f, 130f, 360f, 1f / 120f);
      float omega = raider.angularVelocityDegrees();
      if (Math.abs(omega) > 0.1f) {
        float sign = Math.signum(omega);
        if (previousSign != 0f && sign != previousSign) {
          flips++;
        }
        previousSign = sign;
      }
    }
    assertEquals(0, flips, "a fixed target must not flip the turn side");
    assertEquals(150f, raider.headingDegrees(), 1f);
  }

  @Test
  void fixedTargetHeadingConvergesWithinOneDegreeAcrossFrameRates() {
    float previous = Float.NaN;
    for (float dt : new float[] {1f / 30f, 1f / 60f, 1f / 120f}) {
      VesperRaider raider = raider(0f, 0f, 0f);
      int steps = Math.round(5f / dt);
      for (int i = 0; i < steps; i++) {
        raider.steerTowards(135f, 130f, 360f, dt);
      }
      assertEquals(135f, raider.headingDegrees(), 1f,
          "the heading must converge within one degree at " + (1f / dt) + " Hz");
      if (!Float.isNaN(previous)) {
        assertEquals(previous, raider.headingDegrees(), 1f,
            "frame rate must not change the settled heading");
      }
      previous = raider.headingDegrees();
    }
  }

  @Test
  void straightCruiseKeepsPositionConsistentAcrossFrameRates() {
    float[] positions = new float[3];
    float[] rates = {30f, 60f, 120f};
    for (int r = 0; r < rates.length; r++) {
      VesperRaider raider = raider(0f, 0f, 0f);
      float dt = 1f / rates[r];
      int steps = Math.round(5f / dt);
      for (int i = 0; i < steps; i++) {
        raider.advance(dt, raider.currentSpeed(), 300f, 450f); // target == current: no ramp
      }
      positions[r] = raider.y();
    }
    for (float position : positions) {
      assertEquals(positions[0], position, 2f,
          "a straight cruise must land within two world units across frame rates");
    }
  }

  /** Invokes the protected placement hook via reflection; no GL context is required. */
  private static Ship.SpritePlacement spritePlacement(VesperRaider raider, TextureRegion region)
      throws Exception {
    java.lang.reflect.Method method =
        Ship.class.getDeclaredMethod("spritePlacement", TextureRegion.class);
    method.setAccessible(true);
    return (Ship.SpritePlacement) method.invoke(raider, region);
  }

  private static VesperRaider raider(float x, float y, float headingDegrees) {
    return new VesperRaider(SPEED, 10f, 10f, x, y, headingDegrees);
  }

  private static float normalizeYaw(float yaw) {
    float normalized = yaw % 360f;
    if (normalized > 180f) normalized -= 360f;
    if (normalized <= -180f) normalized += 360f;
    return normalized;
  }
}
