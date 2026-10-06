package com.davidpe.cosmicaces.domain.game;

import java.util.Objects;

/**
 * Persistent state of the player for one game (partida): the current phase, the points and the
 * lives, together with the game identity used to discard events from a previous game or phase.
 *
 * <p>Initial lives and the general scoring policy are supplied from outside: this class only
 * encodes minimal, validated mutations. It depends on no framework.
 */
public final class GameState {

  private final GameId gameId;
  private GamePhase phase;
  private int points;
  private int lives;

  public GameState(GameId gameId, GamePhase phase, int points, int lives) {
    this.gameId = Objects.requireNonNull(gameId, "gameId");
    this.phase = Objects.requireNonNull(phase, "phase");
    if (points < 0) {
      throw new IllegalArgumentException("Points must not be negative: " + points);
    }
    if (lives < 0) {
      throw new IllegalArgumentException("Lives must not be negative: " + lives);
    }
    this.points = points;
    this.lives = lives;
  }

  public GameId gameId() {
    return gameId;
  }

  public GamePhase phase() {
    return phase;
  }

  public int points() {
    return points;
  }

  public int lives() {
    return lives;
  }

  /** Moves the state to the given phase. */
  public void changePhase(GamePhase next) {
    this.phase = Objects.requireNonNull(next, "next");
  }

  /** Adds the given positive amount to the accumulated points. */
  public void addPoints(int amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("Added points must be positive: " + amount);
    }
    points += amount;
  }

  /** Removes one life, never going below zero. */
  public void loseLife() {
    if (lives > 0) {
      lives--;
    }
  }

  /**
   * Applies the immutable result of a completed phase. The result is an authoritative snapshot, so
   * its points and lives reconcile the state instead of being added again: values already reported
   * through {@link PointsEarned} are never counted twice and applying the same result twice is
   * idempotent. The coordinator decides the phase change.
   */
  public void completePhase(PhaseResult result) {
    Objects.requireNonNull(result, "result");
    this.points = result.points();
    this.lives = result.lives();
  }

  /** Returns an immutable snapshot of the current state as a {@link PhaseResult}. */
  public PhaseResult snapshot() {
    return new PhaseResult(gameId, phase, points, lives);
  }
}
