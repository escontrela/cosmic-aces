package com.davidpe.cosmicaces.infrastructure.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.davidpe.cosmicaces.domain.player.AstraVariant;
import com.davidpe.cosmicaces.domain.player.FlightSpeeds;

/** File-backed, device-local storage for user-selected game settings. */
public final class GameSettingsStore {

  private static final String PREFERENCES_FILE = "cosmic-aces-settings";
  private static final String ASTRA_BOTTOM_FRACTION = "presentation.astraBottomFraction";
  private static final String VIDEO_FULLSCREEN = "video.fullscreen";
  private static final String VIDEO_WIDTH = "video.width";
  private static final String VIDEO_HEIGHT = "video.height";
  private static final String VIDEO_REFRESH_RATE = "video.refreshRate";
  private static final String WINDOW_WIDTH = "window.width";
  private static final String WINDOW_HEIGHT = "window.height";
  private static final String ASTRA_NORMAL_SPEED = "astra.speed.normal";
  private static final String ASTRA_TURBO_SPEED = "astra.speed.turbo";
  private static final String ASTRA_ULTRA_SPEED = "astra.speed.ultra";
  private static final String ASTRA_VARIANT = "astra.variant";

  private Preferences preferences;

  /** Initializes storage after LibGDX has created its application context. */
  public void initialize() {
    preferences = Gdx.app.getPreferences(PREFERENCES_FILE);
  }

  public float astraBottomFraction() {
    return preferences().getFloat(ASTRA_BOTTOM_FRACTION,
        GamePresentationSettings.DEFAULT_ASTRA_BOTTOM_FRACTION);
  }

  public void saveAstraBottomFraction(float fraction) {
    preferences().putFloat(ASTRA_BOTTOM_FRACTION, fraction).flush();
  }

  public FlightSpeeds flightSpeeds() {
    Preferences prefs = preferences();
    try {
      return new FlightSpeeds(prefs.getInteger(ASTRA_NORMAL_SPEED, FlightSpeeds.DEFAULTS.normal()),
          prefs.getInteger(ASTRA_TURBO_SPEED, FlightSpeeds.DEFAULTS.turbo()),
          prefs.getInteger(ASTRA_ULTRA_SPEED, FlightSpeeds.DEFAULTS.ultra()));
    } catch (IllegalArgumentException invalidSavedSettings) {
      return FlightSpeeds.DEFAULTS;
    }
  }

  public void saveFlightSpeeds(FlightSpeeds speeds) {
    preferences().putInteger(ASTRA_NORMAL_SPEED, speeds.normal())
        .putInteger(ASTRA_TURBO_SPEED, speeds.turbo())
        .putInteger(ASTRA_ULTRA_SPEED, speeds.ultra()).flush();
  }

  /** Saved Astra appearance; a missing or unknown value resolves to the original ship. */
  public AstraVariant astraVariant() {
    return AstraVariant.fromId(
        preferences().getString(ASTRA_VARIANT, AstraVariant.ORIGINAL.id()));
  }

  public void saveAstraVariant(AstraVariant variant) {
    preferences().putString(ASTRA_VARIANT, variant.id()).flush();
  }

  public SavedVideoPreference savedVideoPreference() {
    Preferences prefs = preferences();
    if (!prefs.contains(VIDEO_FULLSCREEN)) {
      return null;
    }
    return new SavedVideoPreference(prefs.getBoolean(VIDEO_FULLSCREEN),
        prefs.getInteger(VIDEO_WIDTH), prefs.getInteger(VIDEO_HEIGHT),
        prefs.getInteger(VIDEO_REFRESH_RATE), prefs.getInteger(WINDOW_WIDTH),
        prefs.getInteger(WINDOW_HEIGHT));
  }

  public void saveVideoPreference(VideoDisplaySettings.VideoState state) {
    Preferences prefs = preferences().putBoolean(VIDEO_FULLSCREEN, state.fullscreen())
        .putInteger(WINDOW_WIDTH, state.windowWidth())
        .putInteger(WINDOW_HEIGHT, state.windowHeight());
    VideoDisplaySettings.VideoMode mode = state.displayMode();
    if (mode != null) {
      prefs.putInteger(VIDEO_WIDTH, mode.width())
          .putInteger(VIDEO_HEIGHT, mode.height())
          .putInteger(VIDEO_REFRESH_RATE, mode.refreshRate());
    }
    prefs.flush();
  }

  private Preferences preferences() {
    if (preferences == null) {
      throw new IllegalStateException("Settings storage has not been initialized");
    }
    return preferences;
  }

  /** Last requested display mode; it is revalidated against the current monitor on launch. */
  public record SavedVideoPreference(boolean fullscreen, int width, int height, int refreshRate,
      int windowWidth, int windowHeight) {
    public VideoDisplaySettings.VideoMode mode() {
      if (width <= 0 || height <= 0 || refreshRate < 0) {
        return null;
      }
      return new VideoDisplaySettings.VideoMode(width, height, refreshRate);
    }
  }
}
