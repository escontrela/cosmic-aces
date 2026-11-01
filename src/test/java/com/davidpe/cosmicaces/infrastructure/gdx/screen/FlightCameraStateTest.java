package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.*;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import com.davidpe.cosmicaces.infrastructure.gdx.CameraMode;
import com.davidpe.cosmicaces.infrastructure.gdx.GamePresentationSettings;
import com.davidpe.cosmicaces.infrastructure.gdx.VirtualScreenSize;
import org.junit.jupiter.api.Test;

class FlightCameraStateTest {
  private static final WorldBounds WORLD = new WorldBounds(3200f, 12000f);
  /** Enemy box half used by the framing tests; only its size relative to the view matters. */
  private static final float ENEMY_HALF_BOX = 40f;

  @Test void followsShortestTurnAcrossHeadingWrap() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 179f, WORLD);
    FlightCameraState state = new FlightCameraState(astra);
    astra.placeAt(1600f, 6000f, -179f, WORLD);
    state.update(astra, 0.25f);
    assertTrue(Math.abs(state.yawDegrees()) > 170f);
    assertTrue(Math.abs(Math.IEEEremainder(state.yawDegrees() - 179f, 360d)) < 3f);
  }

  @Test void positionAndZoomTrackNormalTurboAndUltraSmoothly() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    FlightCameraState state = new FlightCameraState(astra);
    float oldY = state.y();
    astra.fly(new FlightControls(false,false,true,false,false), 1f, WORLD);
    state.update(astra, 0.25f);
    assertTrue(state.y() > oldY);
    assertTrue(state.zoom() > 1f && state.zoom() < 1.08f);
    astra.fly(new FlightControls(false,false,false,false,true), 0.1f, WORLD);
    state.update(astra, 0.25f);
    assertTrue(state.zoom() > 1.08f && state.zoom() < 1.15f);
  }

  @Test void ignoresNonPositiveAndNonFiniteDelta() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    FlightCameraState state = new FlightCameraState(astra);
    state.update(astra, 0f, targetAt(refX(astra, 0f), refY(astra, 0f)));
    state.update(astra, Float.NaN, targetAt(refX(astra, 0f), refY(astra, 0f)));
    assertFalse(state.combatActive());
    assertEquals(1f, state.zoom(), 1e-4f);
    assertEquals(defaultExplorationAhead(), state.aheadDistance(), 1e-4f);
  }

  @Test void entersCombatWhenEnemyIntersectsTheExplorationView() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    FlightCameraState state = new FlightCameraState(astra);
    state.update(astra, 0.25f, targetAt(refX(astra, 0f), refY(astra, 0f)));
    assertTrue(state.combatActive());
    // One frame never jumps straight to the combat zoom.
    assertTrue(state.zoom() > 1f && state.zoom() < 1.25f);
  }

  @Test void entersCombatWithARotatedCamera() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 90f, WORLD);
    FlightCameraState state = new FlightCameraState(astra);
    state.update(astra, 0.25f, targetAt(refX(astra, 90f), refY(astra, 90f)));
    assertTrue(state.combatActive());
  }

  @Test void staysInExplorationWhenEnemyIsOutOfView() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    FlightCameraState state = new FlightCameraState(astra);
    state.update(astra, 0.25f, targetAt(refX(astra, 0f) + 8000f, refY(astra, 0f)));
    assertFalse(state.combatActive());
    assertEquals(1f, state.zoom(), 1e-4f);
    assertEquals(defaultExplorationAhead(), state.aheadDistance(), 1e-4f);
  }

  @Test void combatLatchUsesTheEffectiveViewportDimensions() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    float targetX = refX(astra, 0f) + 700f;
    float targetY = refY(astra, 0f);

    FlightCameraState reference = new FlightCameraState(astra);
    reference.update(astra, 0.25f, targetAt(targetX, targetY), 1024f, 768f);
    assertFalse(reference.combatActive());

    FlightCameraState fullHd = new FlightCameraState(astra);
    fullHd.update(astra, 0.25f, targetAt(targetX, targetY), 1920f, 1080f);
    assertTrue(fullHd.combatActive());
  }

  @Test void explorationKeepsAstraNearTheBottomAcrossEffectiveViewportHeights() {
    for (float[] viewport : new float[][] {
        {1024f, 768f}, {1920f, 1080f}, {1440f, 900f}}) {
      Astra astra = new Astra();
      astra.placeAt(1600f, 6000f, 0f, WORLD);
      FlightCameraState state = new FlightCameraState(astra);

      state.update(astra, 1f, FlightCameraState.CombatTarget.none(), viewport[0], viewport[1]);

      float astraCenterAboveBottom = viewport[1] * state.zoom() / 2f - state.aheadDistance();
      float bottomFraction = astraCenterAboveBottom / (viewport[1] * state.zoom());
      assertEquals(FlightCameraState.ASTRA_BOTTOM_FRACTION, bottomFraction, 0.001f);
    }
  }

  @Test void combatConvergesToWiderZoomAndKeepsAstraAtTheExplorationViewportPosition() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    FlightCameraState state = new FlightCameraState(astra);
    float cx = refX(astra, 0f);
    float cy = refY(astra, 0f);
    for (int frame = 0; frame < 240; frame++) {
      state.update(astra, 1f / 60f, targetAt(cx, cy));
    }
    assertTrue(state.combatActive());
    assertEquals(1.25f, state.zoom(), 0.005f);
    float astraCenterAboveBottom = VirtualScreenSize.HEIGHT * state.zoom() / 2f
        - state.aheadDistance();
    float bottomFraction = astraCenterAboveBottom / (VirtualScreenSize.HEIGHT * state.zoom());
    assertEquals(FlightCameraState.ASTRA_BOTTOM_FRACTION, bottomFraction, 0.001f);
  }

  @Test void selectedAstraBottomPositionSurvivesCombatZoomAndViewportResize() {
    float selectedFraction = 0.30f;
    for (float[] viewport : new float[][] {{1024f, 768f}, {1920f, 1080f}, {1440f, 900f}}) {
      Astra astra = new Astra();
      astra.placeAt(1600f, 6000f, 0f, WORLD);
      FlightCameraState state = new FlightCameraState(astra, selectedFraction);
      float ahead = FlightCameraState.explorationAheadDistance(
          viewport[1], 1f, selectedFraction);
      float targetX = astra.x() + astra.drawWidth() / 2f;
      float targetY = astra.y() + astra.drawHeight() / 2f + ahead;

      for (int frame = 0; frame < 240; frame++) {
        state.update(astra, 1f / 60f, targetAt(targetX, targetY), viewport[0], viewport[1]);
      }

      float astraCenterAboveBottom = viewport[1] * state.zoom() / 2f - state.aheadDistance();
      float bottomFraction = astraCenterAboveBottom / (viewport[1] * state.zoom());
      assertTrue(state.combatActive());
      assertEquals(selectedFraction, bottomFraction, 0.001f);
    }
  }

  @Test void combatHoldsThroughEdgeCrossingsAndReleasesAfterSustainedExit() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    FlightCameraState state = new FlightCameraState(astra);
    float cx = refX(astra, 0f);
    float cy = refY(astra, 0f);
    state.update(astra, 0.25f, targetAt(cx, cy));
    assertTrue(state.combatActive());
    // Just beyond the base exploration view but inside the exit margin: stays engaged.
    state.update(astra, 0.25f, targetAt(cx + 600f, cy));
    assertTrue(state.combatActive());
    // Beyond the exit margin: released only after the hold has elapsed, so a brief cross is stable.
    state.update(astra, 0.25f, targetAt(cx + 700f, cy));
    assertTrue(state.combatActive());
    state.update(astra, 0.25f, targetAt(cx + 700f, cy));
    assertTrue(state.combatActive());
    state.update(astra, 0.25f, targetAt(cx + 700f, cy));
    assertFalse(state.combatActive());
  }

  @Test void releasesCombatImmediatelyWhenEnemyDisappears() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    FlightCameraState state = new FlightCameraState(astra);
    state.update(astra, 0.25f, targetAt(refX(astra, 0f), refY(astra, 0f)));
    assertTrue(state.combatActive());
    state.update(astra, 0.25f, FlightCameraState.CombatTarget.none());
    assertFalse(state.combatActive());
  }

  @Test void cameraReturnsToExplorationAfterCombatEnds() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    FlightCameraState state = new FlightCameraState(astra);
    float cx = refX(astra, 0f);
    float cy = refY(astra, 0f);
    for (int frame = 0; frame < 240; frame++) {
      state.update(astra, 1f / 60f, targetAt(cx, cy));
    }
    for (int frame = 0; frame < 240; frame++) {
      state.update(astra, 1f / 60f, FlightCameraState.CombatTarget.none());
    }
    assertFalse(state.combatActive());
    assertEquals(1f, state.zoom(), 0.005f);
    assertEquals(defaultExplorationAhead(), state.aheadDistance(), 0.5f);
  }

  // ---- NORTH_UP framing (COS-102) ----

  @Test void northUpStartsExactlyCentredWithWorldNorthUpForEveryHeading() {
    for (float heading : new float[] {0f, 90f, 180f, -90f}) {
      Astra astra = new Astra();
      astra.placeAt(1600f, 6000f, heading, WORLD);
      FlightCameraState state = new FlightCameraState(astra, 0.30f, CameraMode.NORTH_UP);

      assertNorthCentred(state, astra);
      assertEquals(1f, state.zoom(), 1e-4f);
    }
  }

  @Test void northUpKeepsAstraExactlyCentredThroughEveryManoeuvreAndFullTurn() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    FlightCameraState state = new FlightCameraState(astra, 0.30f, CameraMode.NORTH_UP);
    FlightControls[] manoeuvres = {
        new FlightControls(false, false, true, false, false), // north
        new FlightControls(false, false, false, true, false), // south
        new FlightControls(true, false, false, false, false), // west
        new FlightControls(false, true, false, false, false) // east
    };
    for (FlightControls controls : manoeuvres) {
      for (int frame = 0; frame < 90; frame++) {
        astra.fly(controls, 1f / 60f, WORLD);
        state.update(astra, 1f / 60f);
        assertNorthCentred(state, astra);
      }
    }

    float previousHeading = astra.yawDegrees();
    float turned = 0f;
    for (int frame = 0; frame < 180; frame++) {
      astra.fly(new FlightControls(true, false, true, false, false), 1f / 60f, WORLD);
      state.update(astra, 1f / 60f);
      assertNorthCentred(state, astra);
      turned += Math.abs(shortestTurn(previousHeading, astra.yawDegrees()));
      previousHeading = astra.yawDegrees();
    }
    assertTrue(turned > 360f);
  }

  @Test void northUpRecentresImmediatelyOnRespawnOrTeleport() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 9000f, 90f, WORLD);
    FlightCameraState state = new FlightCameraState(astra, 0.30f, CameraMode.NORTH_UP);

    astra.respawnAt(1500f, 800f, WORLD);
    state.update(astra, 1f / 60f);
    assertNorthCentred(state, astra);

    astra.placeAt(200f, 11000f, -135f, WORLD);
    state.update(astra, 1f / 60f);
    assertNorthCentred(state, astra);
  }

  @Test void northUpIgnoresThePersistedBottomFraction() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    FlightCameraState low = new FlightCameraState(astra, 0.10f, CameraMode.NORTH_UP);
    FlightCameraState high = new FlightCameraState(astra, 0.35f, CameraMode.NORTH_UP);

    for (int frame = 0; frame < 60; frame++) {
      astra.fly(new FlightControls(false, false, true, false, false), 1f / 60f, WORLD);
      low.update(astra, 1f / 60f);
      high.update(astra, 1f / 60f);
      assertNorthCentred(low, astra);
      assertNorthCentred(high, astra);
    }
    assertEquals(low.y(), high.y(), 1e-6f);
    assertEquals(0f, low.aheadDistance(), 1e-6f);
  }

  @Test void northUpLatchUsesTheCentredFrameInsteadOfTheFollowLookAhead() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    // Inside the follow look-ahead frame but beyond the north-up half height plus enemy box.
    float targetX = astraCentreX(astra);
    float targetY = astraCentreY(astra) + 500f;

    FlightCameraState north = new FlightCameraState(astra, 0.18f, CameraMode.NORTH_UP);
    north.update(astra, 1f / 60f, targetAt(targetX, targetY), 1024f, 768f);
    assertFalse(north.combatActive());

    FlightCameraState follow = new FlightCameraState(astra);
    follow.update(astra, 1f / 60f, targetAt(targetX, targetY), 1024f, 768f);
    assertTrue(follow.combatActive());
  }

  @Test void northUpLatchEngagesWhenTheTargetIsOnlyInsideItsOwnFrame() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 180f, WORLD);
    // Behind the follow look-ahead, which points south at this heading, inside the north-up frame.
    float targetY = astraCentreY(astra) + 300f;
    float targetX = astraCentreX(astra);

    FlightCameraState north = new FlightCameraState(astra, 0.18f, CameraMode.NORTH_UP);
    north.update(astra, 1f / 60f, targetAt(targetX, targetY), 1024f, 768f);
    assertTrue(north.combatActive());

    FlightCameraState follow = new FlightCameraState(astra);
    follow.update(astra, 1f / 60f, targetAt(targetX, targetY), 1024f, 768f);
    assertFalse(follow.combatActive());
  }

  @Test void northUpCombatZoomKeepsTheExactCentreAndReturnsToExploration() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    FlightCameraState state = new FlightCameraState(astra, 0.30f, CameraMode.NORTH_UP);

    for (int frame = 0; frame < 240; frame++) {
      state.update(astra, 1f / 60f,
          targetAt(astraCentreX(astra), astraCentreY(astra)), 1024f, 768f);
      assertNorthCentred(state, astra);
    }
    assertTrue(state.combatActive());
    assertEquals(1.25f, state.zoom(), 0.005f);

    for (int frame = 0; frame < 240; frame++) {
      state.update(astra, 1f / 60f, FlightCameraState.CombatTarget.none(), 1024f, 768f);
      assertNorthCentred(state, astra);
    }
    assertFalse(state.combatActive());
    assertEquals(1f, state.zoom(), 0.005f);
  }

  @Test void northUpKeepsTheFlightModeZoomWhileStayingCentred() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    FlightCameraState state = new FlightCameraState(astra, 0.30f, CameraMode.NORTH_UP);
    astra.fly(new FlightControls(false, false, false, false, true), 0.1f, WORLD);

    for (int frame = 0; frame < 240; frame++) {
      state.update(astra, 1f / 60f);
      assertNorthCentred(state, astra);
    }
    assertEquals(1.15f, state.zoom(), 0.005f);
  }

  @Test void northUpKeepsTheExactCentreAcrossTheAcceptedLogicalSizes() {
    for (float[] viewport : new float[][] {
        {1024f, 768f}, {1920f, 1080f}, {1440f, 900f}}) {
      Astra astra = new Astra();
      astra.placeAt(1600f, 6000f, 0f, WORLD);
      FlightCameraState state = new FlightCameraState(astra, 0.30f, CameraMode.NORTH_UP);

      for (int frame = 0; frame < 60; frame++) {
        astra.fly(new FlightControls(true, false, true, false, false), 1f / 60f, WORLD);
        state.update(astra, 1f / 60f,
            targetAt(astraCentreX(astra), astraCentreY(astra)), viewport[0], viewport[1]);
        assertNorthCentred(state, astra);
        assertTrue(state.combatActive());
      }
    }
  }

  @Test void northUpCameraNeverMovesAstra() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 45f, WORLD);
    FlightCameraState state = new FlightCameraState(astra, 0.30f, CameraMode.NORTH_UP);
    float astraX = astra.x();
    float astraY = astra.y();
    float astraYaw = astra.yawDegrees();

    for (int frame = 0; frame < 60; frame++) {
      state.update(astra, 1f / 60f,
          targetAt(astraCentreX(astra), astraCentreY(astra)), 1024f, 768f);
    }
    assertEquals(astraX, astra.x(), 0f);
    assertEquals(astraY, astra.y(), 0f);
    assertEquals(astraYaw, astra.yawDegrees(), 0f);
  }

  @Test void northUpKeepsTheDeltaGuardsWithoutRecentringOnInvalidFrames() {
    Astra astra = new Astra();
    astra.placeAt(1600f, 6000f, 0f, WORLD);
    FlightCameraState state = new FlightCameraState(astra, 0.30f, CameraMode.NORTH_UP);
    float centredX = state.x();
    float centredY = state.y();

    astra.placeAt(1600f, 6200f, 90f, WORLD);
    state.update(astra, 0f);
    state.update(astra, Float.NaN);
    state.update(astra, -1f);
    assertEquals(centredX, state.x(), 0f);
    assertEquals(centredY, state.y(), 0f);
    assertEquals(0f, state.aheadDistance(), 0f);

    state.update(astra, 1f / 60f);
    assertNorthCentred(state, astra);
  }

  private static void assertNorthCentred(FlightCameraState state, Astra astra) {
    assertEquals(astraCentreX(astra), state.x(), 1e-4f);
    assertEquals(astraCentreY(astra), state.y(), 1e-4f);
    assertEquals(0f, state.yawDegrees(), 1e-6f);
    assertEquals(0f, state.aheadDistance(), 1e-6f);
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

  private static FlightCameraState.CombatTarget targetAt(float centerX, float centerY) {
    return new FlightCameraState.CombatTarget(true, centerX, centerY, ENEMY_HALF_BOX);
  }

  /** Reference view centre used by the state for the exploration framing at the given yaw. */
  private static float refX(Astra astra, float yawDegrees) {
    return astra.x() + astra.drawWidth() / 2f
        + (float) Math.sin(Math.toRadians(yawDegrees)) * defaultExplorationAhead();
  }

  private static float refY(Astra astra, float yawDegrees) {
    return astra.y() + astra.drawHeight() / 2f
        + (float) Math.cos(Math.toRadians(yawDegrees)) * defaultExplorationAhead();
  }

  private static float defaultExplorationAhead() {
    return FlightCameraState.explorationAheadDistance(VirtualScreenSize.HEIGHT, 1f);
  }
}
