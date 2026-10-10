package com.davidpe.cosmicaces.infrastructure.gdx.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure policy of the cabin ambience play/pause rule; no audio device or native backend is needed.
 */
class PhaseOneAudioTest {

  @Test
  void ambienceHeardWhileAstraAdvancesNormally() {
    assertTrue(PhaseOneAudio.cabinAmbienceAudible(true, false, 150f));
    assertTrue(PhaseOneAudio.cabinAmbienceAudible(true, false, 650f));
    assertTrue(PhaseOneAudio.cabinAmbienceAudible(true, false, 0.5f));
  }

  @Test
  void ambienceStopsWhenAstraCannotAct() {
    assertFalse(PhaseOneAudio.cabinAmbienceAudible(false, false, 150f));
  }

  @Test
  void ambienceStopsWhenTheRunHasFinished() {
    assertFalse(PhaseOneAudio.cabinAmbienceAudible(true, true, 150f));
  }

  @Test
  void ambienceStopsWhenAstraIsNotAdvancing() {
    assertFalse(PhaseOneAudio.cabinAmbienceAudible(true, false, 0f));
    assertFalse(PhaseOneAudio.cabinAmbienceAudible(true, false, -1f));
  }

  @Test
  void ambienceIgnoresNonFiniteSpeeds() {
    assertFalse(PhaseOneAudio.cabinAmbienceAudible(true, false, Float.NaN));
    assertFalse(PhaseOneAudio.cabinAmbienceAudible(true, false, Float.POSITIVE_INFINITY));
    assertFalse(PhaseOneAudio.cabinAmbienceAudible(true, false, Float.NEGATIVE_INFINITY));
  }
}