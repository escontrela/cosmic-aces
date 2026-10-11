package com.davidpe.cosmicaces.domain.player;

/**
 * Visual model chosen for Astra. Only the appearance changes: flight, weapons, collisions and rules
 * are shared. Each constant carries a stable persistence id so saved preferences never depend on a
 * user-facing name; a missing or unknown id falls back to {@link #ORIGINAL}.
 */
public enum AstraVariant {

  /** The original Astra ship, backed by the bundled v3 sheets. */
  ORIGINAL("original"),

  /** The F-16 MLU 755 look, backed by the bundled v4 sheets. */
  F16_MLU_755("f16-mlu-755");

  private final String id;

  AstraVariant(String id) {
    this.id = id;
  }

  /** Stable identifier stored in the local preferences file. */
  public String id() {
    return id;
  }

  /** Resolves a saved id, returning {@link #ORIGINAL} for missing or unknown values. */
  public static AstraVariant fromId(String id) {
    if (id == null) {
      return ORIGINAL;
    }
    for (AstraVariant variant : values()) {
      if (variant.id.equals(id)) {
        return variant;
      }
    }
    return ORIGINAL;
  }
}
