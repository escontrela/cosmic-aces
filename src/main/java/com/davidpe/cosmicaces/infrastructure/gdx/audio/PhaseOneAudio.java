package com.davidpe.cosmicaces.infrastructure.gdx.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.utils.Disposable;

/**
 * Owns the PhaseOne audio resources on behalf of {@code PhaseOneScreen}. It encapsulates
 * {@code Gdx.audio} and {@code Gdx.files} so the domain and {@code PhaseOneGameController} never
 * import audio classes.
 *
 * <p>The cabin ambience is a 120 s stereo WAV streamed as looping {@link Music} (never loaded whole
 * into memory as a short effect). The LibGDX LWJGL3 WAV decoder only accepts PCM 16-bit, so the
 * Float32 source shipped by the PO is not loaded: this class plays the derived PCM 16-bit copy in
 * {@code assets/sounds}, keeping the original untouched.
 *
 * <p>Playback follows the pure policy {@link #cabinAmbienceAudible(boolean, boolean, float)}: the
 * loop accompanies Astra while she is active, the run has not finished and she is actually
 * advancing (forward speed above zero). {@link #updateCabinAmbience(boolean)} is idempotent so a
 * per-frame render never restarts or stacks the stream.
 */
public final class PhaseOneAudio implements Disposable {

  /** Internal resource path of the PCM 16-bit cabin ambience, streamed as {@link Music}. */
  public static final String CABIN_AMBIENCE_PATH =
      "assets/sounds/sound-airplane-cabin-near-ground-pcm16.wav";
  /** Centralized ambience gain so the PO can balance the three cues without a mixing UI. */
  private static final float CABIN_AMBIENCE_VOLUME = 0.7f;

  private final Music cabinAmbience;
  private boolean cabinAmbienceAudible;

  public PhaseOneAudio() {
    Music loaded = null;
    try {
      loaded = Gdx.audio.newMusic(Gdx.files.internal(CABIN_AMBIENCE_PATH));
      loaded.setLooping(true);
      loaded.setVolume(CABIN_AMBIENCE_VOLUME);
    } catch (RuntimeException | Error failure) {
      if (loaded != null) {
        loaded.dispose();
      }
      throw failure;
    }
    cabinAmbience = loaded;
    cabinAmbienceAudible = false;
  }

  /**
   * Keeps the single ambience stream aligned with the flight policy: starts it on the transition to
   * audible and pauses it when not, never calling {@code play()}/{@code pause()} again while the
   * state is unchanged, so a frame tick cannot restart, copy or stack the loop.
   */
  public void updateCabinAmbience(boolean audible) {
    if (audible == cabinAmbienceAudible) {
      return;
    }
    if (audible) {
      cabinAmbience.play();
    } else {
      cabinAmbience.pause();
    }
    cabinAmbienceAudible = audible;
  }

  /**
   * Pure play/pause decision for the cabin loop, based on the real ship/run state (never on a held
   * key): audible only while Astra can act, the run is not finished and the ship is actually
   * advancing forward with a finite positive speed. Testable without an audio device.
   */
  public static boolean cabinAmbienceAudible(boolean astraActive, boolean runFinished,
      float flightSpeed) {
    return astraActive && !runFinished && Float.isFinite(flightSpeed) && flightSpeed > 0f;
  }

  @Override
  public void dispose() {
    if (cabinAmbience != null) {
      cabinAmbience.stop();
      cabinAmbience.dispose();
    }
    cabinAmbienceAudible = false;
  }
}