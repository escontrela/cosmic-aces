package com.davidpe.cosmicaces.domain.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AstraVariantTest {

  @Test
  void resolvesEachStableIdBackToItsVariant() {
    for (AstraVariant variant : AstraVariant.values()) {
      assertEquals(variant, AstraVariant.fromId(variant.id()));
    }
  }

  @Test
  void fallsBackToOriginalForMissingOrUnknownValues() {
    assertEquals(AstraVariant.ORIGINAL, AstraVariant.fromId(null));
    assertEquals(AstraVariant.ORIGINAL, AstraVariant.fromId(""));
    assertEquals(AstraVariant.ORIGINAL, AstraVariant.fromId("not-a-ship"));
  }

  @Test
  void idsAreStableAndUnique() {
    Set<String> ids = new HashSet<>();
    for (AstraVariant variant : AstraVariant.values()) {
      assertNotNull(variant.id());
      ids.add(variant.id());
    }
    assertEquals(AstraVariant.values().length, ids.size());
    assertEquals("original", AstraVariant.ORIGINAL.id());
    assertEquals("f16-mlu-755", AstraVariant.F16_MLU_755.id());
  }
}
