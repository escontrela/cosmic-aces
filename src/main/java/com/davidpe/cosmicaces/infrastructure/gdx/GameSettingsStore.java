package com.davidpe.cosmicaces.infrastructure.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.davidpe.cosmicaces.domain.player.AstraVariant;
import com.davidpe.cosmicaces.domain.player.FlightSpeeds;
import com.davidpe.cosmicaces.domain.weapon.MissileConfig;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionBindings;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ControllerSettings;
import com.davidpe.cosmicaces.infrastructure.gdx.input.ControllerSide;
import com.davidpe.cosmicaces.infrastructure.gdx.input.InputAction;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickSettings;
import java.util.EnumMap;

/** File-backed, device-local storage for user-selected game settings. */
public final class GameSettingsStore {

  private static final String PREFERENCES_FILE = "cosmic-aces-settings";
  private static final String CONTROLLER_VERSION = "controller.version";
  private static final int CONTROLLER_VERSION_VALUE = 1;
  private static final String CONTROLLER_PROFILE = "controller.profile";
  private static final String CONTROLLER_BINDINGS_CUSTOM = "controller.bindings.custom";
  private static final String CONTROLLER_BINDING_PREFIX = "controller.binding.";
  private static final String CONTROLLER_STICK_SWAP = "controller.stick.swapAxes";
  private static final String CONTROLLER_STICK_INVERT_X = "controller.stick.invertX";
  private static final String CONTROLLER_STICK_INVERT_Y = "controller.stick.invertY";
  private static final String CONTROLLER_STICK_DEAD_ZONE = "controller.stick.deadZone";
  private static final String ASTRA_BOTTOM_FRACTION = "presentation.astraBottomFraction";
  private static final String CAMERA_MODE = "presentation.cameraMode";
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
  private static final String ASTRA_MISSILES_PER_LIFE = "astra.missiles.perLife";
  private static final String GUIDED_MISSILE_MAX_SPEED = "weapon.guidedMissile.maxSpeed";

  private Preferences preferences;

  /** Initializes storage after LibGDX has created its application context. */
  public void initialize() {
    usePreferences(Gdx.app.getPreferences(PREFERENCES_FILE));
  }

  /**
   * Binds the preferences backend. Package-private so tests can supply an in-memory implementation
   * without booting LibGDX or touching the player's real settings file.
   */
  void usePreferences(Preferences preferences) {
    this.preferences = preferences;
  }

  public float astraBottomFraction() {
    return preferences().getFloat(ASTRA_BOTTOM_FRACTION,
        GamePresentationSettings.DEFAULT_ASTRA_BOTTOM_FRACTION);
  }

  public void saveAstraBottomFraction(float fraction) {
    preferences().putFloat(ASTRA_BOTTOM_FRACTION, fraction).flush();
  }

  /** Saved camera framing; a missing or unknown value resolves to the tracking camera. */
  public CameraMode cameraMode() {
    return CameraMode.fromId(
        preferences().getString(CAMERA_MODE, CameraMode.FOLLOW_ASTRA.id()));
  }

  public void saveCameraMode(CameraMode mode) {
    preferences().putString(CAMERA_MODE, mode.id()).flush();
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

  /** Missing, invalid or incorrectly typed values fall back without overwriting preferences. */
  public int astraMissilesPerLife() {
    Preferences prefs = preferences();
    try {
      int count = prefs.getInteger(ASTRA_MISSILES_PER_LIFE,
          MissileConfig.DEFAULTS.astraMissilesPerLife());
      return count >= MissileConfig.MIN_ASTRA_MISSILES && count <= MissileConfig.MAX_ASTRA_MISSILES
          ? count : MissileConfig.DEFAULTS.astraMissilesPerLife();
    } catch (IllegalArgumentException | ClassCastException invalidSavedSetting) {
      return MissileConfig.DEFAULTS.astraMissilesPerLife();
    }
  }

  public void saveAstraMissilesPerLife(int count) {
    MissileConfig.DEFAULTS.withAstraMissilesPerLife(count);
    preferences().putInteger(ASTRA_MISSILES_PER_LIFE, count).flush();
  }

  /** Saved maximum speed shared by Astra and escort missiles, with a safe default on bad data. */
  public int guidedMissileMaxSpeed() {
    Preferences prefs = preferences();
    try {
      int speed = prefs.getInteger(GUIDED_MISSILE_MAX_SPEED,
          (int) MissileConfig.DEFAULTS.maxSpeed());
      return speed >= MissileConfig.MIN_MAX_SPEED && speed <= MissileConfig.MAX_MAX_SPEED
          ? speed : (int) MissileConfig.DEFAULTS.maxSpeed();
    } catch (IllegalArgumentException | ClassCastException invalidSavedSetting) {
      return (int) MissileConfig.DEFAULTS.maxSpeed();
    }
  }

  public void saveGuidedMissileMaxSpeed(int speed) {
    if (speed < MissileConfig.MIN_MAX_SPEED || speed > MissileConfig.MAX_MAX_SPEED) {
      throw new IllegalArgumentException("Guided missile speed must be between 1100 and 3000 u/s");
    }
    preferences().putInteger(GUIDED_MISSILE_MAX_SPEED, speed).flush();
  }

  /** Saved Astra appearance; a missing or unknown value resolves to the original ship. */
  public AstraVariant astraVariant() {
    return AstraVariant.fromId(
        preferences().getString(ASTRA_VARIANT, AstraVariant.ORIGINAL.id()));
  }

  public void saveAstraVariant(AstraVariant variant) {
    preferences().putString(ASTRA_VARIANT, variant.id()).flush();
  }

  /**
   * Saved controller profile, bindings and stick rules. A missing, unknown or corrupt version and
   * every invalid field fall back to recoverable defaults without rewriting the stored values.
   */
  public ControllerSettings controllerSettings() {
    Preferences prefs = preferences();
    try {
      if (!prefs.contains(CONTROLLER_VERSION)
          || readInteger(prefs, CONTROLLER_VERSION, -1) != CONTROLLER_VERSION_VALUE) {
        return ControllerSettings.DEFAULTS;
      }
      ControllerSide profile = ControllerSide.fromId(readString(prefs, CONTROLLER_PROFILE,
          ControllerSide.UNKNOWN.id()));
      boolean custom = readBoolean(prefs, CONTROLLER_BINDINGS_CUSTOM, false);
      ActionBindings bindings = custom
          ? readControllerBindings(prefs) : ControllerSettings.DEFAULTS.bindings();
      boolean swap = readBoolean(prefs, CONTROLLER_STICK_SWAP,
          StickSettings.DEFAULTS.swapAxes());
      boolean invertX = readBoolean(prefs, CONTROLLER_STICK_INVERT_X,
          StickSettings.DEFAULTS.invertX());
      boolean invertY = readBoolean(prefs, CONTROLLER_STICK_INVERT_Y,
          StickSettings.DEFAULTS.invertY());
      float deadZone = readFloat(prefs, CONTROLLER_STICK_DEAD_ZONE,
          StickSettings.DEFAULT_DEAD_ZONE);
      if (!Float.isFinite(deadZone) || deadZone < StickSettings.MIN_DEAD_ZONE
          || deadZone > StickSettings.MAX_DEAD_ZONE) {
        deadZone = StickSettings.DEFAULT_DEAD_ZONE;
      }
      return new ControllerSettings(profile, bindings,
          new StickSettings(swap, invertX, invertY, deadZone), custom);
    } catch (RuntimeException invalidSavedControllerData) {
      return ControllerSettings.DEFAULTS;
    }
  }

  public void saveControllerSettings(ControllerSettings settings) {
    Preferences prefs = preferences();
    prefs.putInteger(CONTROLLER_VERSION, CONTROLLER_VERSION_VALUE)
        .putString(CONTROLLER_PROFILE, settings.profile().id())
        .putBoolean(CONTROLLER_BINDINGS_CUSTOM, settings.bindingsCustomised());
    settings.bindings().asMap().forEach((action, button) ->
        prefs.putInteger(CONTROLLER_BINDING_PREFIX + action.name(), button));
    StickSettings stick = settings.stickSettings();
    prefs.putBoolean(CONTROLLER_STICK_SWAP, stick.swapAxes())
        .putBoolean(CONTROLLER_STICK_INVERT_X, stick.invertX())
        .putBoolean(CONTROLLER_STICK_INVERT_Y, stick.invertY())
        .putFloat(CONTROLLER_STICK_DEAD_ZONE, stick.deadZone())
        .flush();
  }

  /** Removes only controller keys; video, Astra, armament and ranking settings stay untouched. */
  public void resetControllerSettings() {
    Preferences prefs = preferences();
    prefs.remove(CONTROLLER_VERSION);
    prefs.remove(CONTROLLER_PROFILE);
    prefs.remove(CONTROLLER_BINDINGS_CUSTOM);
    for (InputAction action : InputAction.values()) {
      prefs.remove(CONTROLLER_BINDING_PREFIX + action.name());
    }
    prefs.remove(CONTROLLER_STICK_SWAP);
    prefs.remove(CONTROLLER_STICK_INVERT_X);
    prefs.remove(CONTROLLER_STICK_INVERT_Y);
    prefs.remove(CONTROLLER_STICK_DEAD_ZONE);
    prefs.flush();
  }

  /**
   * Reads every saved binding, falling back to the generic default per missing/invalid action and
   * replacing only the later action of an incompatible duplicate. Values from different contexts
   * may share a button.
   */
  private static ActionBindings readControllerBindings(Preferences prefs) {
    ActionBindings defaults = ActionBindings.genericDefaults();
    EnumMap<InputAction, Integer> accepted = new EnumMap<>(InputAction.class);
    for (InputAction action : InputAction.values()) {
      int candidate = readButton(prefs, action, defaults.buttonFor(action));
      int fallback = defaults.buttonFor(action);
      if (ActionBindings.conflictIn(accepted, action, candidate) != null) {
        candidate = fallback;
      }
      while (ActionBindings.conflictIn(accepted, action, candidate) != null) {
        candidate++;
      }
      accepted.put(action, candidate);
    }
    return ActionBindings.ofCompleteMap(accepted);
  }

  private static int readButton(Preferences prefs, InputAction action, int defaultValue) {
    String key = CONTROLLER_BINDING_PREFIX + action.name();
    int button = readInteger(prefs, key, defaultValue);
    return button >= 0 ? button : defaultValue;
  }

  private static int readInteger(Preferences prefs, String key, int defaultValue) {
    try {
      return prefs.getInteger(key, defaultValue);
    } catch (RuntimeException invalidStoredValue) {
      return defaultValue;
    }
  }

  private static float readFloat(Preferences prefs, String key, float defaultValue) {
    try {
      return prefs.getFloat(key, defaultValue);
    } catch (RuntimeException invalidStoredValue) {
      return defaultValue;
    }
  }

  private static boolean readBoolean(Preferences prefs, String key, boolean defaultValue) {
    try {
      return prefs.getBoolean(key, defaultValue);
    } catch (RuntimeException invalidStoredValue) {
      return defaultValue;
    }
  }

  private static String readString(Preferences prefs, String key, String defaultValue) {
    try {
      return prefs.getString(key, defaultValue);
    } catch (RuntimeException invalidStoredValue) {
      return defaultValue;
    }
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
