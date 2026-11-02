package com.davidpe.cosmicaces.domain.weapon;

/** Immutable world-space view of one live aircraft, independent of the camera and textures. */
public record MissileTarget(Reference reference, float x, float y, float velocityX, float velocityY,
    float radius) {
  public enum Kind { ASTRA, VESPER, ESCORT, GHOSTWING, GERAN4, BASTION }

  /** Identity belongs to one entity and one life; a respawn must use a new generation. */
  public record Reference(Kind kind, long entityId, int lifeGeneration) {
    public Reference {
      if (kind == null || lifeGeneration < 0) {
        throw new IllegalArgumentException("Target kind and non-negative generation are required");
      }
    }
  }

  public MissileTarget {
    if (reference == null || !Float.isFinite(x) || !Float.isFinite(y)
        || !Float.isFinite(velocityX) || !Float.isFinite(velocityY)
        || !Float.isFinite(radius) || radius <= 0f) {
      throw new IllegalArgumentException("Target identity, coordinates and positive radius are required");
    }
  }

  public boolean isBoss() {
    return reference.kind() == Kind.BASTION;
  }

  public String name() {
    return switch (reference.kind()) {
      case ASTRA -> "Astra";
      case VESPER -> "Vesper Raider";
      case ESCORT -> "Escolta Bastion";
      case GHOSTWING -> "Ghostwing";
      case GERAN4 -> "Geran-4";
      case BASTION -> "Bastion Bomber";
    };
  }
}
