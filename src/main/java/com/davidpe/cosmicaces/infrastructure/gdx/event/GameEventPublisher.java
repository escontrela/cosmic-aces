package com.davidpe.cosmicaces.infrastructure.gdx.event;

import com.davidpe.cosmicaces.domain.game.GameEvent;

/**
 * Contract through which screens publish game events. Depending on this small interface instead of a
 * concrete bus keeps the screens decoupled from the delivery mechanism and lets the coordinator
 * subscribe to the events it needs.
 */
@FunctionalInterface
public interface GameEventPublisher {

  /** Publishes one game event. Delivery is synchronous. */
  void publish(GameEvent event);
}
