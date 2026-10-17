package com.davidpe.cosmicaces.domain.scenery;

/** Deterministic decorative instance in global world coordinates. */
public record SceneryInstance(
        String id,
        SceneryType type,
        String assetId,
        double x,
        double y,
        double width,
        double height,
        double rotationDegrees,
        double scale
) {
    public SceneryInstance {
        if (id == null || id.isBlank() || assetId == null || assetId.isBlank() || type == null) {
            throw new IllegalArgumentException("instance identity, type and asset id are required");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !positive(width) || !positive(height)
                || !Double.isFinite(rotationDegrees) || !positive(scale)) {
            throw new IllegalArgumentException("instance geometry must be finite and positive");
        }
    }

    /** Conservative axis-aligned footprint, including the selected rotation. */
    public WorldRect footprint() {
        double radians = Math.toRadians(rotationDegrees);
        double extentX = (Math.abs(Math.cos(radians)) * width + Math.abs(Math.sin(radians)) * height) / 2;
        double extentY = (Math.abs(Math.sin(radians)) * width + Math.abs(Math.cos(radians)) * height) / 2;
        return new WorldRect(x - extentX, y - extentY, extentX * 2, extentY * 2);
    }

    private static boolean positive(double value) {
        return Double.isFinite(value) && value > 0;
    }
}
