package com.davidpe.cosmicaces.infrastructure.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class CameraModeTest {

  @Test
  void defaultsToTheTrackingCamera() {
    assertEquals(CameraMode.FOLLOW_ASTRA, CameraMode.fromId(null));
    assertEquals(CameraMode.FOLLOW_ASTRA, CameraMode.fromId(""));
  }

  @Test
  void resolvesEachStableIdIncludingTheDefault() {
    assertEquals(CameraMode.FOLLOW_ASTRA, CameraMode.fromId("follow-astra"));
    assertEquals(CameraMode.NORTH_UP, CameraMode.fromId("north-up"));
  }

  @Test
  void unknownSavedValuesFallBackToTheTrackingCamera() {
    assertEquals(CameraMode.FOLLOW_ASTRA, CameraMode.fromId("NORTE_FIJO"));
    assertEquals(CameraMode.FOLLOW_ASTRA, CameraMode.fromId("0"));
    assertEquals(CameraMode.FOLLOW_ASTRA, CameraMode.fromId("north_up"));
  }

  @Test
  void persistenceIdsAreStableAndDoNotDependOnOrdinals() {
    assertEquals("follow-astra", CameraMode.FOLLOW_ASTRA.id());
    assertEquals("north-up", CameraMode.NORTH_UP.id());
    assertNotEquals(CameraMode.FOLLOW_ASTRA.id(), CameraMode.NORTH_UP.id());
  }
}
