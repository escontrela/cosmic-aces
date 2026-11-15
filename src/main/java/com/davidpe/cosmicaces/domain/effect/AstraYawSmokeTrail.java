package com.davidpe.cosmicaces.domain.effect;

/**
 * Astra's turn-smoke trail: the name published by COS-152/COS-153 for the shared {@link
 * TurnSmokeTrail} model.
 *
 * <p>It adds no state and no rule of its own. The generic model is reused by {@link
 * TurnSmokeTracker} for the enemy wakes of COS-154, while Astra's presentation adapter keeps its
 * original API and every published test keeps compiling and passing unchanged.
 */
public final class AstraYawSmokeTrail extends TurnSmokeTrail {}
