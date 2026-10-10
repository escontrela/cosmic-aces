package com.davidpe.cosmicaces.infrastructure.gdx.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
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
 * <p>The M61 Vulcan cue is a short PCM 16-bit burst cut ({@link #VULCAN_BURST_SECONDS}) played once
 * per real emission event reported by the weapons. Playing the full 1.95 s sample on every Astra
 * burst (every 0.08 s) would stack long clips; the short cut keeps the mix controlled and lets the
 * caller drive it from {@code GunBurst.emissionCount()} deltas without restarting the whole sample.
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
  /** Internal resource path of the short PCM 16-bit M61 burst cue, loaded as a {@link Sound}. */
  public static final String VULCAN_BURST_PATH =
      "assets/sounds/sound-machine-gun-burst-pcm16.wav";
  /** Duration of the derived M61 burst cue; kept at or below Astra's 0.08 s cadence. */
  public static final float VULCAN_BURST_SECONDS = 0.08f;
  /** Centralized ambience gain so the PO can balance the three cues without a mixing UI. */
  private static final float CABIN_AMBIENCE_VOLUME = 0.7f;
  /** Centralized M61 burst gain, balanced under the cabin ambience. */
  private static final float VULCAN_BURST_VOLUME = 0.6f;

  private final Music cabinAmbience;
  private final Sound vulcanBurst;
  private boolean cabinAmbienceAudible;

  public PhaseOneAudio() {
    Music loadedAmbience = null;
    Sound loadedBurst = null;
    try {
      loadedAmbience = Gdx.audio.newMusic(Gdx.files.internal(CABIN_AMBIENCE_PATH));
      loadedAmbience.setLooping(true);
      loadedAmbience.setVolume(CABIN_AMBIENCE_VOLUME);
      loadedBurst = Gdx.audio.newSound(Gdx.files.internal(VULCAN_BURST_PATH));
    } catch (RuntimeException | Error failure) {
      if (loadedBurst != null) {
        loadedBurst.dispose();
      }
      if (loadedAmbience != null) {
        loadedAmbience.dispose();
      }
      throw failure;
    }
    cabinAmbience = loadedAmbience;
    vulcanBurst = loadedBurst;
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
   * Plays the shared M61 cue once per real emission event. The caller passes the number of bursts
   * detected this frame (see {@link #burstsSince(long, long)}); zero is a no-op. Astra and Vesper
   * use the same effect; the short cut bounds each voice so repeated events do not stack long clips.
   */
  public void playVulcanBursts(int bursts) {
    for (int i = 0; i < bursts; i++) {
      vulcanBurst.play(VULCAN_BURST_VOLUME);
    }
  }

  /**
   * Number of real emission events between two samples of a monotonic weapon counter. Pure and
   * testable without an audio device; a non-increasing sample (reset or stale read) yields zero.
   */
  public static int burstsSince(long previousCount, long currentCount) {
    long delta = currentCount - previousCount;
    if (delta <= 0L) {
      return 0;
    }
    return delta > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) delta;
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
    if (vulcanBurst != null) {
      vulcanBurst.dispose();
    }
    cabinAmbienceAudible = false;
  }
}
