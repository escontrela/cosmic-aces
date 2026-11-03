package com.davidpe.cosmicaces.application.score;

/**
 * Signals that the persisted Hall of Fame could not be fully replaced.
 *
 * <p>The failure is recoverable: the previous snapshot must still be intact and the caller may retry
 * with the same candidate and durable entry id. No screen may report a saved game when this
 * exception is thrown.
 */
public final class HallOfFameStorageException extends RuntimeException {

  public HallOfFameStorageException(String message) {
    super(message);
  }

  public HallOfFameStorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
