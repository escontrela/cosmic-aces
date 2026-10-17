package com.davidpe.cosmicaces.domain.scenery;

/** Logical decorative resource types; these ids are not filesystem paths. */
public enum SceneryType {
    TREE(new String[]{"tree-01"}, 64, 94, 112),
    BUSH(new String[]{"bush-01"}, 40, 64, 76),
    PUDDLE(new String[]{"puddle-01", "puddle-02", "puddle-03"}, 42, 70, 270),
    ROCK(new String[]{"rock-cluster-01"}, 42, 72, 190);

    private final String[] assetIds;
    private final double minSize;
    private final double maxSize;
    private final double candidateSpacing;

    SceneryType(String[] assetIds, double minSize, double maxSize, double candidateSpacing) {
        this.assetIds = assetIds;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.candidateSpacing = candidateSpacing;
    }

    String assetId(int variantIndex) { return assetIds[variantIndex]; }
    int variantCount() { return assetIds.length; }
    double minSize() { return minSize; }
    double maxSize() { return maxSize; }
    double candidateSpacing() { return candidateSpacing; }
}
