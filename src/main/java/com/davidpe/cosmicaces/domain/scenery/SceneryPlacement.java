package com.davidpe.cosmicaces.domain.scenery;

import java.util.Objects;

/** Minimal future structure placement contract; it reserves space but loads/draws no asset. */
public record SceneryPlacement(
        String id,
        String assetId,
        Anchor anchor,
        double anchorX,
        double anchorY,
        WorldRect bounds,
        int drawOrder,
        double reservationMargin
) {
    public enum Anchor { CENTER, BOTTOM_LEFT, BOTTOM_CENTER }

    public SceneryPlacement {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (assetId == null || assetId.isBlank()) throw new IllegalArgumentException("assetId must not be blank");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(bounds, "bounds");
        if (!Double.isFinite(anchorX) || !Double.isFinite(anchorY)) throw new IllegalArgumentException("anchor must be finite");
        if (!Double.isFinite(reservationMargin) || reservationMargin < 0) {
            throw new IllegalArgumentException("reservation margin must be finite and non-negative");
        }
        double expectedX = switch (anchor) {
            case CENTER -> bounds.centerX();
            case BOTTOM_LEFT -> bounds.x();
            case BOTTOM_CENTER -> bounds.centerX();
        };
        double expectedY = switch (anchor) {
            case CENTER -> bounds.centerY();
            case BOTTOM_LEFT, BOTTOM_CENTER -> bounds.y();
        };
        if (Double.compare(anchorX, expectedX) != 0 || Double.compare(anchorY, expectedY) != 0) {
            throw new IllegalArgumentException("anchor coordinates must match the selected bounds anchor");
        }
    }

    public WorldRect reservation() {
        return bounds.expanded(reservationMargin);
    }
}
