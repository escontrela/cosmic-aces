package com.davidpe.cosmicaces.domain.scenery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable deterministic scenery collection with read-only rectangle queries. */
public final class SceneryField {
    private static final Comparator<SceneryInstance> BY_ID = Comparator.comparing(SceneryInstance::id);
    private final List<SceneryInstance> instances;

    SceneryField(List<SceneryInstance> instances) {
        this.instances = instances.stream().sorted(BY_ID).toList();
    }

    public List<SceneryInstance> instances() { return instances; }

    public List<SceneryInstance> query(WorldRect area) {
        List<SceneryInstance> found = new ArrayList<>();
        for (SceneryInstance instance : instances) {
            if (instance.footprint().intersects(area)) found.add(instance);
        }
        return List.copyOf(found);
    }
}
