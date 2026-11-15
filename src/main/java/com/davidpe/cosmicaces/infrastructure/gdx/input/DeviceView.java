package com.davidpe.cosmicaces.infrastructure.gdx.input;

/**
 * Presentation-safe view of one device for the settings UI.
 *
 * <p>It contains only value data, never the backend controller object, so a disconnected device can
 * still be shown as unavailable without retaining a stale native handle.
 */
public record DeviceView(
    String uniqueId,
    String name,
    ControllerSide side,
    boolean joyCon,
    boolean combined,
    boolean connected,
    boolean selected) {

  /** True for a single left or right Joy-Con, the only family the product accepts. */
  public boolean individualJoyCon() {
    return joyCon && !combined && side != ControllerSide.UNKNOWN;
  }

  /** Copy with a new connection state, preserving identity and selection. */
  public DeviceView withConnected(boolean isConnected) {
    return new DeviceView(uniqueId, name, side, joyCon, combined, isConnected, selected);
  }
}
