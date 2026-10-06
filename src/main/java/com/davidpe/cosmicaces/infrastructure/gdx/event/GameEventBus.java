package com.davidpe.cosmicaces.infrastructure.gdx.event;

import com.davidpe.cosmicaces.domain.game.GameEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Small synchronous event bus used by the screens and the coordinator. Handlers are registered per
 * concrete event type and receive only events of that exact type, so a screen can publish a game
 * event without the coordinator importing the screen. It has no external dependencies and no
 * LibGDX types, so it can be exercised without a graphics context.
 */
public final class GameEventBus implements GameEventPublisher {

  private final Map<Class<?>, List<Consumer<? extends GameEvent>>> handlers = new HashMap<>();

  /** Registers a handler for the given concrete event type and returns its cancellation handle. */
  public <E extends GameEvent> Subscription subscribe(Class<E> type, Consumer<E> handler) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(handler, "handler");
    handlers.computeIfAbsent(type, key -> new ArrayList<>()).add(handler);
    return () -> remove(type, handler);
  }

  @Override
  public void publish(GameEvent event) {
    Objects.requireNonNull(event, "event");
    List<Consumer<? extends GameEvent>> registered = handlers.get(event.getClass());
    if (registered == null || registered.isEmpty()) {
      return;
    }
    // Deliver over a copy so a handler may subscribe or unsubscribe during delivery.
    for (Consumer<? extends GameEvent> handler : List.copyOf(registered)) {
      deliver(handler, event);
    }
  }

  private void remove(Class<?> type, Consumer<? extends GameEvent> handler) {
    List<Consumer<? extends GameEvent>> registered = handlers.get(type);
    if (registered == null) {
      return;
    }
    registered.remove(handler);
    if (registered.isEmpty()) {
      handlers.remove(type);
    }
  }

  @SuppressWarnings("unchecked")
  private static void deliver(Consumer<? extends GameEvent> handler, GameEvent event) {
    ((Consumer<GameEvent>) handler).accept(event);
  }
}
