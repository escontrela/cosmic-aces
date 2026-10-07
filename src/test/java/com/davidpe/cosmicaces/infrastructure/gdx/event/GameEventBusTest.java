package com.davidpe.cosmicaces.infrastructure.gdx.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.davidpe.cosmicaces.domain.game.GameId;
import com.davidpe.cosmicaces.domain.game.GamePhase;
import com.davidpe.cosmicaces.domain.game.LifeLost;
import com.davidpe.cosmicaces.domain.game.PointsEarned;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GameEventBusTest {

  private static final GameId GAME = new GameId(1L);

  @Test
  void dispatchesOnlyToHandlersOfTheExactEventType() {
    GameEventBus bus = new GameEventBus();
    List<Integer> received = new ArrayList<>();
    bus.subscribe(PointsEarned.class, event -> received.add(event.points()));

    bus.publish(new PointsEarned(GAME, GamePhase.PLAYING_PHASE_ONE, 10));
    bus.publish(new LifeLost(GAME, GamePhase.PLAYING_PHASE_ONE));

    assertEquals(List.of(10), received);
  }

  @Test
  void cancelStopsDelivery() {
    GameEventBus bus = new GameEventBus();
    AtomicInteger deliveries = new AtomicInteger();
    Subscription subscription =
        bus.subscribe(PointsEarned.class, event -> deliveries.incrementAndGet());

    bus.publish(new PointsEarned(GAME, GamePhase.PLAYING_PHASE_ONE, 1));
    subscription.cancel();
    subscription.cancel();
    bus.publish(new PointsEarned(GAME, GamePhase.PLAYING_PHASE_ONE, 1));

    assertEquals(1, deliveries.get());
  }

  @Test
  void publishingWithoutSubscribersIsIgnored() {
    GameEventBus bus = new GameEventBus();

    assertDoesNotThrow(() -> bus.publish(new LifeLost(GAME, GamePhase.PLAYING_PHASE_ONE)));
  }

  @Test
  void aHandlerCanUnsubscribeAnotherHandlerDuringDelivery() {
    GameEventBus bus = new GameEventBus();
    List<String> order = new ArrayList<>();
    Subscription[] second = new Subscription[1];
    bus.subscribe(
        PointsEarned.class,
        event -> {
          order.add("first");
          second[0].cancel();
        });
    second[0] = bus.subscribe(PointsEarned.class, event -> order.add("second"));

    bus.publish(new PointsEarned(GAME, GamePhase.PLAYING_PHASE_ONE, 1));

    assertEquals(List.of("first", "second"), order);
  }

  @Test
  void rejectsNullArguments() {
    GameEventBus bus = new GameEventBus();

    assertThrows(NullPointerException.class, () -> bus.subscribe(null, event -> {}));
    assertThrows(NullPointerException.class, () -> bus.subscribe(PointsEarned.class, null));
    assertThrows(NullPointerException.class, () -> bus.publish(null));
  }
}
