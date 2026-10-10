package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.*;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.FlightControls;
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
    assertEquals(100f, state.aheadDistance(), 1e-4f);
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
    assertEquals(100f, state.aheadDistance(), 1e-4f);
  }

  @Test void combatConvergesToWiderZoomAndSmallerAhead() {
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
    assertEquals(30f, state.aheadDistance(), 0.5f);
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
    assertEquals(100f, state.aheadDistance(), 0.5f);
  }

  private static FlightCameraState.CombatTarget targetAt(float centerX, float centerY) {
    return new FlightCameraState.CombatTarget(true, centerX, centerY, ENEMY_HALF_BOX);
  }

  /** Reference view centre used by the state for the exploration framing at the given yaw. */
  private static float refX(Astra astra, float yawDegrees) {
    return astra.x() + astra.drawWidth() / 2f
        + (float) Math.sin(Math.toRadians(yawDegrees)) * 100f;
  }

  private static float refY(Astra astra, float yawDegrees) {
    return astra.y() + astra.drawHeight() / 2f
        + (float) Math.cos(Math.toRadians(yawDegrees)) * 100f;
  }
}
