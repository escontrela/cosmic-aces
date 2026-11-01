package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.infrastructure.gdx.CameraMode;
import org.junit.jupiter.api.Test;

/**
 * Adapter tests for {@link FlightCamera}. They cover the real {@link OrthographicCamera} transform
 * without a window or GL context: the mode reaches position, {@code up} and zoom on the first frame
 * and on every valid update. The only stubbed step is the frustum update whose inverse matrix needs
 * the LibGDX native math library; position, up, zoom and the projection remain the real ones.
 */
class FlightCameraTest {
  private static final WorldBounds WORLD = new WorldBounds(3200f, 12000f);
  private static final float ENEMY_HALF_BOX = 40f;

  @Test void northUpAppliesTheExactCentreAndWorldNorthOnTheFirstFrame() {
    for (float heading : new float[] {0f, 90f, 180f, -90f}) {
      Astra astra = new Astra();
      astra.placeAt(1600f, 6000f, heading, WORLD);
      OrthographicCamera camera = camera(1024f, 768f);

      new FlightCamera(camera, astra, 0.18f, CameraMode.NORTH_UP);

      assertNorthCentred(camera, astra);
      assertEquals(1f, camera.zoom, 1e-6f);
    }
  }

  @Test void northUpKeepsTheExactCentreAndNorthUpThroughManoeuvresAndFullTurn() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 45f, WORLD);
    OrthographicCamera camera = camera(1024f, 768f);
    FlightCamera flight = new FlightCamera(camera, astra, 0.30f, CameraMode.NORTH_UP);
    FlightControls[] manoeuvres = {
        new FlightControls(false, false, true, false, false), // north
        new FlightControls(false, false, false, true, false), // south
        new FlightControls(true, false, false, false, false), // west
        new FlightControls(false, true, false, false, false) // east
    };

    for (FlightControls controls : manoeuvres) {
      for (int frame = 0; frame < 60; frame++) {
        astra.fly(controls, 1f / 60f, WORLD);
        flight.update(astra, 1f / 60f, FlightCameraState.CombatTarget.none(), 1024f, 768f);
        assertNorthCentred(camera, astra);
      }
    }

    float previousHeading = astra.yawDegrees();
    float turned = 0f;
    for (int frame = 0; frame < 180; frame++) {
      astra.fly(new FlightControls(true, false, true, false, false), 1f / 60f, WORLD);
      flight.update(astra, 1f / 60f, FlightCameraState.CombatTarget.none(), 1024f, 768f);
      assertNorthCentred(camera, astra);
      turned += Math.abs(shortestTurn(previousHeading, astra.yawDegrees()));
      previousHeading = astra.yawDegrees();
    }
    assertTrue(turned > 360f, "the camera must stay north-up through a complete turn");
  }

  @Test void northUpRecentresImmediatelyOnRespawn() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 9000f, 90f, WORLD);
    OrthographicCamera camera = camera(1440f, 900f);
    FlightCamera flight = new FlightCamera(camera, astra, 0.18f, CameraMode.NORTH_UP);

    astra.respawnAt(1500f, 800f, WORLD);
    flight.update(astra, 1f / 60f, FlightCameraState.CombatTarget.none(), 1440f, 900f);

    assertNorthCentred(camera, astra);
  }

  @Test void northUpIgnoresInvalidDeltasWithoutRecentring() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    OrthographicCamera camera = camera(1024f, 768f);
    FlightCamera flight = new FlightCamera(camera, astra, 0.18f, CameraMode.NORTH_UP);
    float centredX = camera.position.x;
    float centredY = camera.position.y;

    astra.placeAt(1600f, 6200f, 90f, WORLD);
    flight.update(astra, 0f, FlightCameraState.CombatTarget.none(), 1024f, 768f);
    flight.update(astra, Float.NaN, FlightCameraState.CombatTarget.none(), 1024f, 768f);
    flight.update(astra, -1f, FlightCameraState.CombatTarget.none(), 1024f, 768f);

    assertEquals(centredX, camera.position.x, 0f);
    assertEquals(centredY, camera.position.y, 0f);

    flight.update(astra, 1f / 60f, FlightCameraState.CombatTarget.none(), 1024f, 768f);
    assertNorthCentred(camera, astra);
  }

  @Test void followAppliesTheInterpolatedHeadingAsUp() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 90f, WORLD);
    OrthographicCamera camera = camera(1024f, 768f);

    new FlightCamera(camera, astra, 0.18f, CameraMode.FOLLOW_ASTRA);

    assertEquals(1f, camera.up.x, 1e-5f);
    assertEquals(0f, camera.up.y, 1e-5f);
    assertFalse(Math.abs(camera.position.x - astraCentreX(astra)) < 1f
            && Math.abs(camera.position.y - astraCentreY(astra)) < 1f,
        "the tracking framing offsets Astra instead of centring her");
  }

  @Test void legacyConstructorsKeepTheTrackingFraming() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 90f, WORLD);
    OrthographicCamera noFraction = camera(1024f, 768f);
    OrthographicCamera withFraction = camera(1024f, 768f);

    new FlightCamera(noFraction, astra);
    new FlightCamera(withFraction, astra, 0.30f);

    assertEquals(1f, noFraction.up.x, 1e-5f);
    assertEquals(1f, withFraction.up.x, 1e-5f);
    assertEquals(Math.cos(Math.toRadians(90d)), noFraction.up.y, 1e-5f);
  }

  @Test void combatZoomReachesTheCameraInBothModes() {
    for (CameraMode mode : CameraMode.values()) {
      Astra astra = new Astra();
      astra.placeAt(1600f, 6000f, 0f, WORLD);
      OrthographicCamera camera = camera(1024f, 768f);
      FlightCamera flight = new FlightCamera(camera, astra, 0.18f, mode);
      FlightCameraState.CombatTarget target = new FlightCameraState.CombatTarget(true,
          astraCentreX(astra), astraCentreY(astra), ENEMY_HALF_BOX);

      for (int frame = 0; frame < 240; frame++) {
        flight.update(astra, 1f / 60f, target, 1024f, 768f);
      }

      assertEquals(1.25f, camera.zoom, 0.005f, "mode " + mode);
      if (mode == CameraMode.NORTH_UP) {
        assertNorthCentred(camera, astra);
      }
    }
  }

  @Test void northUpStaysCentredAcrossTheAcceptedLogicalSizesAndAResize() {
    Astra astra = new Astra();
    astra.placeAt(2000f, 5000f, 210f, WORLD);
    OrthographicCamera camera = camera(1024f, 768f);
    FlightCamera flight = new FlightCamera(camera, astra, 0.18f, CameraMode.NORTH_UP);

    for (float[] viewport : new float[][] {
        {1024f, 768f}, {1920f, 1080f}, {1440f, 900f}}) {
      camera.setToOrtho(false, viewport[0], viewport[1]);
      flight.update(astra, 1f / 60f, FlightCameraState.CombatTarget.none(),
          viewport[0], viewport[1]);
      assertNorthCentred(camera, astra);
    }
  }

  @Test void twoConsecutiveScreensUseIndependentModes() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 90f, WORLD);
    OrthographicCamera followCamera = camera(1024f, 768f);
    OrthographicCamera northCamera = camera(1024f, 768f);

    FlightCamera follow = new FlightCamera(followCamera, astra, 0.18f, CameraMode.FOLLOW_ASTRA);
    FlightCamera north = new FlightCamera(northCamera, astra, 0.18f, CameraMode.NORTH_UP);
    float followUpX = followCamera.up.x;
    float followPositionY = followCamera.position.y;

    north.update(astra, 1f / 60f, FlightCameraState.CombatTarget.none(), 1024f, 768f);

    assertNorthCentred(northCamera, astra);
    assertEquals(followUpX, followCamera.up.x, 0f);
    assertEquals(followPositionY, followCamera.position.y, 0f);
    assertTrue(followCamera.up.x > 0.99f);
  }

  private static OrthographicCamera camera(float width, float height) {
    OrthographicCamera camera = new HeadlessCamera();
    camera.setToOrtho(false, width, height);
    return camera;
  }

  /**
   * Real camera except for the frustum update, which calls a native matrix inverse. No test here
   * reads the frustum, and skipping it keeps the suite hermetic without extracting native files.
   */
  private static final class HeadlessCamera extends OrthographicCamera {
    @Override
    public void update(boolean updateFrustum) {
      super.update(false);
    }
  }

  private static void assertNorthCentred(OrthographicCamera camera, Astra astra) {
    assertEquals(astraCentreX(astra), camera.position.x, 0f);
    assertEquals(astraCentreY(astra), camera.position.y, 0f);
    assertEquals(0f, camera.up.x, 0f);
    assertEquals(1f, camera.up.y, 0f);
  }

  private static float astraCentreX(Astra astra) {
    return astra.x() + astra.drawWidth() / 2f;
  }

  private static float astraCentreY(Astra astra) {
    return astra.y() + astra.drawHeight() / 2f;
  }

  private static float shortestTurn(float from, float to) {
    return (float) Math.IEEEremainder(to - from, 360d);
  }
}
