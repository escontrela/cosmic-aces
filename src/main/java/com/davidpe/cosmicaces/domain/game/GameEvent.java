package com.davidpe.cosmicaces.domain.game;

/**
 * A game event that a screen publishes for the coordinator. Every event carries the identity of the
 * game and the phase in which it happened, so the coordinator can ignore messages produced by a
 * previous screen, game or phase. Events are pure Java and immutable.
 */
public sealed interface GameEvent
    permits StartRequested, PointsEarned, LifeLost, PhaseCompleted, GameAbandoned {

  /** Identity of the game (partida) the event belongs to. */
  GameId gameId();

  /** Phase in which the event was produced. */
  GamePhase phase();
}
