package com.davidpe.cosmicaces.domain.game;

/**
 * Pure value object that identifies one game (partida). The value is supplied by the caller; there
 * is no global static counter, so tests and independent games never share identities.
 */
public record GameId(long value) {

  public GameId {
    if (value < 0) {
      throw new IllegalArgumentException("Game id must not be negative: " + value);
    }
  }
}
