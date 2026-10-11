package com.davidpe.cosmicaces.infrastructure.gdx.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.weapon.GunTuning;
import org.junit.jupiter.api.Test;

/**
 * Pure policy of the cabin ambience play/pause rule and of the M61 burst detection; no audio device
 * or native backend is needed.
 */
class PhaseOneAudioTest {

  @Test
  void burstCountIsThePositiveDeltaBetweenCounterSamples() {
    assertEquals(1, PhaseOneAudio.burstsSince(0L, 1L));
    assertEquals(3, PhaseOneAudio.burstsSince(5L, 8L));
    assertEquals(0, PhaseOneAudio.burstsSince(8L, 8L));
    assertEquals(0, PhaseOneAudio.burstsSince(8L, 5L), "a stale or reset sample yields no cue");
  }

  @Test
  void vulcanCueIsShortEnoughForAstraCadence() {
    assertTrue(PhaseOneAudio.VULCAN_BURST_SECONDS <= GunTuning.BURST_INTERVAL_SECONDS,
        "the cue must not be longer than the fastest cadence, or bursts would stack");
  }

  @Test
  void freshAstraDestructionPlaysOneExplosionCue() {
    assertEquals(1, PhaseOneAudio.explosionCues(true, false, false, false));
  }

  @Test
  void freshRaiderDestructionPlaysOneExplosionCue() {
    assertEquals(1, PhaseOneAudio.explosionCues(false, false, true, false));
  }

  @Test
  void stillDestroyedShipsPlayNoFurtherCues() {
    assertEquals(0, PhaseOneAudio.explosionCues(true, true, false, false),
        "Astra already signalled stays silent while destroyed");
    assertEquals(0, PhaseOneAudio.explosionCues(false, false, true, true),
        "Vesper already signalled stays silent while destroyed");
    assertEquals(0, PhaseOneAudio.explosionCues(true, true, true, true),
        "both still destroyed produce no additional voices");
  }

  @Test
  void simultaneousMutualDestructionPlaysTwoExplosionCues() {
    assertEquals(2, PhaseOneAudio.explosionCues(true, false, true, false));
    assertEquals(1, PhaseOneAudio.explosionCues(true, false, true, true),
        "a raider that already signalled does not add a second voice");
  }

  @Test
  void respecifiedLiveStateKeepsFlagsReadyForTheNextDestruction() {
    // The screen clears both flags while a ship is alive, so the next destruction is fresh again.
    assertEquals(0, PhaseOneAudio.explosionCues(false, false, false, false));
    assertEquals(1, PhaseOneAudio.explosionCues(true, false, false, false));
  }

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