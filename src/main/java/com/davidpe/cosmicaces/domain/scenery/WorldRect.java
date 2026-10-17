package com.davidpe.cosmicaces.domain.scenery;

/** Axis-aligned rectangle in world coordinates, with its origin at the lower left. */
public record WorldRect(double x, double y, double width, double height) {
    public WorldRect {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("rectangle coordinates must be finite and dimensions positive");
        }
    }

    public double right() { return x + width; }
    public double top() { return y + height; }
    public double centerX() { return x + width / 2; }
    public double centerY() { return y + height / 2; }

    public WorldRect expanded(double margin) {
        if (!Double.isFinite(margin) || margin < 0) throw new IllegalArgumentException("margin must be finite and non-negative");
        return new WorldRect(x - margin, y - margin, width + margin * 2, height + margin * 2);
    }

    public boolean intersects(WorldRect other) {
        return x < other.right() && right() > other.x && y < other.top() && top() > other.y;
    }
}
