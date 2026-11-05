package com.davidpe.cosmicaces.infrastructure.gdx.input;

import java.util.Objects;

/**
 * Persisted, device-independent configuration of the single Joy-Con.
 *
 * <p>The profile stores the logical left/right preference instead of a backend connection id, so it
 * survives restarts. Bindings are only applied when {@code bindingsCustomised} is true; otherwise
 * the adapter keeps deriving recoverable defaults from the selected device's semantic mapping.
 * Stick orientation and dead zone are always applied.
 */
public record ControllerSettings(
    ControllerSide profile,
    ActionBindings bindings,
    StickSettings stickSettings,
    boolean bindingsCustomised) {

  public static final ControllerSettings DEFAULTS =
      new ControllerSettings(ControllerSide.UNKNOWN, ActionBindings.genericDefaults(),
          StickSettings.DEFAULTS, false);

  public ControllerSettings {
    profile = profile == null ? ControllerSide.UNKNOWN : profile;
    bindings = bindings == null ? ActionBindings.genericDefaults() : bindings;
    stickSettings = stickSettings == null ? StickSettings.DEFAULTS : stickSettings;
  }

  public ControllerSettings withProfile(ControllerSide newProfile) {
    return new ControllerSettings(Objects.requireNonNull(newProfile, "profile"), bindings,
        stickSettings, bindingsCustomised);
  }

  public ControllerSettings withBindings(ActionBindings newBindings, boolean customised) {
    return new ControllerSettings(profile, Objects.requireNonNull(newBindings, "bindings"),
        stickSettings, customised);
  }

  public ControllerSettings withStickSettings(StickSettings newStickSettings) {
    return new ControllerSettings(profile, bindings,
        Objects.requireNonNull(newStickSettings, "stickSettings"), bindingsCustomised);
  }
}
