package com.davidpe.cosmicaces.infrastructure.gdx.input;

import java.util.List;

/**
 * Source of the currently connected gamepads.
 *
 * <p>It is polled once per frame instead of installing global listeners, which keeps ownership in
 * one place and avoids listener accumulation when screens are replaced. A source may return an
 * empty list at any time; that is a normal state, not an error.
 */
public interface GamepadSource {

  List<GamepadDevice> devices();
}
