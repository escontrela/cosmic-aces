package com.davidpe.cosmicaces.domain.player;

/** One sample of the player's held flight controls and Ultra activation edge. */
public record FlightControls(boolean left, boolean right, boolean up, boolean down,
    boolean activateUltra) {
  public static FlightControls neutral() {
    return new FlightControls(false, false, false, false, false);
  }

  public int lateral() {
    return (right ? 1 : 0) - (left ? 1 : 0);
  }

  public boolean turning() {
    return lateral() != 0 && (up || down);
  }
}
