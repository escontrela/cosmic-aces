package com.davidpe.cosmicaces.infrastructure.gdx.event;

/**
 * Handle that cancels a bus subscription so a screen or coordinator can stop receiving events and
 * the bus does not retain references to it. Cancelling more than once has no effect.
 */
@FunctionalInterface
public interface Subscription {

  /** Cancels the subscription. */
  void cancel();
}
