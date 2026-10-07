package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.*;

import com.davidpe.cosmicaces.domain.game.WorldBounds;
import com.davidpe.cosmicaces.domain.player.Astra;
import com.davidpe.cosmicaces.domain.player.FlightControls;
import org.junit.jupiter.api.Test;

class FlightCameraStateTest {
  private static final WorldBounds WORLD = new WorldBounds(3200f, 12000f);

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
}
