package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.infrastructure.gdx.CameraDistance;
import com.davidpe.cosmicaces.infrastructure.gdx.CameraMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class WelcomeScreenSettingsMenuTest {

  @Test
  void keepsTheExistingSectionsAndAddsTheCameraSelectorInBothModes() {
    assertEquals(List.of("Vídeo", "Encuadre de Astra", "Velocidades de Astra", "Nave de Astra",
            "Armamento", "Cámara", "Controles"),
        labels(WelcomeScreen.rootOptions(true)));
    assertEquals(List.of("Vídeo", "Encuadre de Astra", "Cámara", "Controles"),
        labels(WelcomeScreen.rootOptions(false)));
  }

  @Test
  void everyRootOptionOpensItsOwnSettingsPage() {
    List<WelcomeScreen.SettingsPage> pages = WelcomeScreen.rootOptions(true).stream()
        .map(WelcomeScreen.RootOption::page)
        .toList();

    assertEquals(WelcomeScreen.rootOptions(true).size(), pages.stream().distinct().count());
    assertTrue(pages.contains(WelcomeScreen.SettingsPage.CAMERA),
        "the camera page must be reachable from the root menu");
    assertTrue(pages.contains(WelcomeScreen.SettingsPage.ARMAMENT),
        "the armament page must be reachable when settings are persisted");
    assertTrue(pages.contains(WelcomeScreen.SettingsPage.CONTROLS),
        "the controls page must be reachable from the root menu");
  }

  @Test
  void missileCapacityAdjustsOneStepAndStaysWithinBounds() {
    assertEquals(1, WelcomeScreen.adjustMissilesPerLife(2, -1));
    assertEquals(3, WelcomeScreen.adjustMissilesPerLife(2, 1));
    assertEquals(0, WelcomeScreen.adjustMissilesPerLife(0, -1));
    assertEquals(10, WelcomeScreen.adjustMissilesPerLife(10, 1));
  }

  @Test
  void armamentMenuCyclesBetweenCapacityAndMissileSpeed() {
    assertEquals(1, WelcomeScreen.cycleArmamentSelection(0, 1));
    assertEquals(0, WelcomeScreen.cycleArmamentSelection(1, 1));
    assertEquals(1, WelcomeScreen.cycleArmamentSelection(0, -1));
    assertEquals(0, WelcomeScreen.cycleArmamentSelection(1, -1));
  }

  @Test
  void guidedMissileSpeedAdjustsByOneHundredAndStaysFasterThanAircraft() {
    assertEquals(1400, WelcomeScreen.adjustGuidedMissileMaxSpeed(1500, -1));
    assertEquals(1600, WelcomeScreen.adjustGuidedMissileMaxSpeed(1500, 1));
    assertEquals(1100, WelcomeScreen.adjustGuidedMissileMaxSpeed(1100, -1));
    assertEquals(3000, WelcomeScreen.adjustGuidedMissileMaxSpeed(3000, 1));
  }

  @Test
  void cameraSelectionTogglesInBothDirectionsAndWrapsAround() {
    assertEquals(CameraMode.NORTH_UP, WelcomeScreen.cycleCameraMode(CameraMode.FOLLOW_ASTRA, 1));
    assertEquals(CameraMode.NORTH_UP, WelcomeScreen.cycleCameraMode(CameraMode.FOLLOW_ASTRA, -1));
    assertEquals(CameraMode.FOLLOW_ASTRA, WelcomeScreen.cycleCameraMode(CameraMode.NORTH_UP, 1));
    assertEquals(CameraMode.FOLLOW_ASTRA, WelcomeScreen.cycleCameraMode(CameraMode.NORTH_UP, -1));
  }

  @Test
  void cameraPageFocusMovesBetweenTheModeAndDistanceRows() {
    assertEquals(WelcomeScreen.CameraRow.DISTANCE.ordinal(),
        WelcomeScreen.cycleCameraRow(WelcomeScreen.CameraRow.MODE.ordinal(), 1));
    assertEquals(WelcomeScreen.CameraRow.MODE.ordinal(),
        WelcomeScreen.cycleCameraRow(WelcomeScreen.CameraRow.DISTANCE.ordinal(), 1));
    assertEquals(WelcomeScreen.CameraRow.DISTANCE.ordinal(),
        WelcomeScreen.cycleCameraRow(WelcomeScreen.CameraRow.MODE.ordinal(), -1));
    assertEquals(WelcomeScreen.CameraRow.MODE.ordinal(),
        WelcomeScreen.cycleCameraRow(WelcomeScreen.CameraRow.DISTANCE.ordinal(), -1));
  }

  @Test
  void cameraDistanceWalksEveryLevelAndStopsAtBothLimits() {
    assertEquals(CameraDistance.CURRENT, WelcomeScreen.cycleCameraDistance(CameraDistance.CURRENT, -1),
        "the historical framing is the closest view and cannot zoom in further");
    assertEquals(CameraDistance.FAR, WelcomeScreen.cycleCameraDistance(CameraDistance.CURRENT, 1));
    assertEquals(CameraDistance.FARTHEST, WelcomeScreen.cycleCameraDistance(CameraDistance.FAR, 1));
    assertEquals(CameraDistance.FARTHEST,
        WelcomeScreen.cycleCameraDistance(CameraDistance.FARTHEST, 1));
    assertEquals(CameraDistance.FAR, WelcomeScreen.cycleCameraDistance(CameraDistance.FARTHEST, -1));
    assertEquals(CameraDistance.CURRENT, WelcomeScreen.cycleCameraDistance(CameraDistance.FAR, -1));
  }

  @Test
  void everyCameraDistanceHasAStableUserFacingLabel() {
    assertEquals("Actual", WelcomeScreen.cameraDistanceLabel(CameraDistance.CURRENT));
    assertEquals("Alejada", WelcomeScreen.cameraDistanceLabel(CameraDistance.FAR));
    assertEquals("Más alejada", WelcomeScreen.cameraDistanceLabel(CameraDistance.FARTHEST));
  }

  private static List<String> labels(List<WelcomeScreen.RootOption> options) {
    return options.stream().map(WelcomeScreen.RootOption::label).toList();
  }
}
