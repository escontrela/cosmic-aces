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
 * <p>The explosion cue is the PO's mono PCM 16-bit WAV loaded whole as a {@link Sound} and played
 * once per live-to-destroyed transition of Astra or Vesper. The screen owns that transition and asks
 * {@link #explosionCues(boolean, boolean, boolean, boolean)} how many fresh destructions happened,
 * so a ship that stays destroyed across frames never replays it and a mutual collision produces one
 * voice per ship.
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
  /**
   * Internal resource path of the mono PCM 16-bit explosion cue loaded as a {@link Sound}. The
   * original ships as PCM 16-bit, so the LWJGL3 WAV decoder accepts it unchanged.
   */
  public static final String EXPLOSION_PATH = "assets/sounds/sound-crunchy-explosion1.wav";
  /** Duration of the derived M61 burst cue; kept at or below Astra's 0.08 s cadence. */
  public static final float VULCAN_BURST_SECONDS = 0.08f;
  /** Centralized ambience gain so the PO can balance the three cues without a mixing UI. */
  private static final float CABIN_AMBIENCE_VOLUME = 0.7f;
  /** Centralized M61 burst gain, balanced under the cabin ambience. */
  private static final float VULCAN_BURST_VOLUME = 0.6f;
  /** Centralized explosion gain; slightly above the shots to read as a destruction. */
  private static final float EXPLOSION_VOLUME = 0.8f;

  private final Music cabinAmbience;
  private final Sound vulcanBurst;
  private final Sound explosion;
  private boolean cabinAmbienceAudible;

  public PhaseOneAudio() {
    Music loadedAmbience = null;
    Sound loadedBurst = null;
    Sound loadedExplosion = null;
    try {
      loadedAmbience = Gdx.audio.newMusic(Gdx.files.internal(CABIN_AMBIENCE_PATH));
      loadedAmbience.setLooping(true);
      loadedAmbience.setVolume(CABIN_AMBIENCE_VOLUME);
      loadedBurst = Gdx.audio.newSound(Gdx.files.internal(VULCAN_BURST_PATH));
      loadedExplosion = Gdx.audio.newSound(Gdx.files.internal(EXPLOSION_PATH));
    } catch (RuntimeException | Error failure) {
      if (loadedExplosion != null) {
        loadedExplosion.dispose();
      }
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
    explosion = loadedExplosion;
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
   * Plays the shared explosion cue once per live-to-destroyed transition. The screen passes the
   * number of fresh destructions detected this frame (see {@link #explosionCues}); zero is a no-op,
   * one covers a single ship and two cover a mutual collision, one voice each.
   */
  public void playExplosions(int explosions) {
    for (int i = 0; i < explosions; i++) {
      explosion.play(EXPLOSION_VOLUME);
    }
  }

  /**
   * Number of explosion cues to play this frame from both ships' destruction state and their
   * already-signalled flags. Pure and testable without an audio device: a ship contributes a cue
   * only while it is destroyed and no cue was emitted for that destruction yet, so a ship that stays
   * destroyed across frames adds nothing and a simultaneous destruction of both adds two.
   */
  public static int explosionCues(boolean astraDestroyed, boolean astraSpawned,
      boolean raiderDestroyed, boolean raiderSpawned) {
    int cues = 0;
    if (astraDestroyed && !astraSpawned) {
      cues++;
    }
    if (raiderDestroyed && !raiderSpawned) {
      cues++;
    }
    return cues;
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
    if (explosion != null) {
      explosion.dispose();
    }
    cabinAmbienceAudible = false;
  }
}
