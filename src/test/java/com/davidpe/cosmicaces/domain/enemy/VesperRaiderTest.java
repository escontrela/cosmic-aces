package com.davidpe.cosmicaces.domain.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.ship.Ship;
import com.davidpe.cosmicaces.domain.weapon.GunBurst;
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
}
