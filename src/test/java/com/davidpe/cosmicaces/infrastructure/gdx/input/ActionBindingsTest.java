package com.davidpe.cosmicaces.infrastructure.gdx.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Recoverable bindings and semantic-mapping fallbacks. */
class ActionBindingsTest {

  @Test
  void genericDefaultsBindEveryActionAndOnlyShareContextualButtons() {
    ActionBindings bindings = ActionBindings.genericDefaults();

    Set<Integer> buttons = new HashSet<>();
    for (InputAction action : InputAction.values()) {
      int button = bindings.buttonFor(action);
      assertTrue(button >= 0, "every action needs a recoverable default");
      buttons.add(button);
    }
    assertEquals(InputAction.values().length - 1, buttons.size(),
        "only the contextual FIRE/CONFIRM pair may share a physical button");
    assertEquals(bindings.buttonFor(InputAction.FIRE),
        bindings.buttonFor(InputAction.CONFIRM));
  }

  @Test
  void joyConMappingUsesSemanticButtonsAndAFreeFallback() {
    ControllerMappingView mapping =
        new ControllerMappingView(0, 1, 2, 3, 8, 13, 4, 5, 0, 1);
    ActionBindings bindings = ActionBindings.defaults(mapping);

    assertEquals(0, bindings.buttonFor(InputAction.FIRE));
    assertEquals(0, bindings.buttonFor(InputAction.CONFIRM));
    assertEquals(1, bindings.buttonFor(InputAction.MINIMAP));
    assertEquals(2, bindings.buttonFor(InputAction.ARM_MISSILE));
    assertEquals(3, bindings.buttonFor(InputAction.ULTRA));
    assertEquals(8, bindings.buttonFor(InputAction.START_GAME));
    assertEquals(13, bindings.buttonFor(InputAction.BACK));
    assertEquals(4, bindings.buttonFor(InputAction.SETTINGS));
  }

  @Test
  void missingMappingFallsBackToFreeButtons() {
    ActionBindings bindings = ActionBindings.defaults(ControllerMappingView.unknown());

    Set<Integer> buttons = new HashSet<>();
    for (InputAction action : InputAction.values()) {
      assertTrue(bindings.buttonFor(action) >= 0);
      buttons.add(bindings.buttonFor(action));
    }
    assertEquals(InputAction.values().length - 1, buttons.size());
  }

  @Test
  void rebindingReturnsANewValueWithoutMutatingTheOriginal() {
    ActionBindings original = ActionBindings.genericDefaults();
    ActionBindings changed = original.withBinding(InputAction.FIRE, 9);

    assertEquals(9, changed.buttonFor(InputAction.FIRE));
    assertEquals(0, original.buttonFor(InputAction.FIRE));
    assertFalse(original.actionsFor(9).contains(InputAction.FIRE));
    assertTrue(changed.actionsFor(9).contains(InputAction.FIRE));
  }

  @Test
  void negativePhysicalButtonsAreRejected() {
    ActionBindings bindings = ActionBindings.genericDefaults();

    assertThrows(IllegalArgumentException.class,
        () -> bindings.withBinding(InputAction.FIRE, -1));
  }

  @Test
  void sharedButtonsExposeEveryContextualAction() {
    ActionBindings bindings = ActionBindings.genericDefaults();

    assertEquals(Set.of(InputAction.FIRE, InputAction.CONFIRM),
        bindings.actionsFor(bindings.buttonFor(InputAction.FIRE)));
  }

  @Test
  void rejectsIncompatibleActionsInsideMissionAndMenuContexts() {
    ActionBindings bindings = ActionBindings.genericDefaults();

    assertEquals(InputAction.MINIMAP,
        bindings.conflictingAction(InputAction.FIRE,
            bindings.buttonFor(InputAction.MINIMAP)));
    assertEquals(InputAction.BACK,
        bindings.conflictingAction(InputAction.ARM_MISSILE,
            bindings.buttonFor(InputAction.BACK)));
    assertEquals(InputAction.BACK,
        bindings.conflictingAction(InputAction.CONFIRM,
            bindings.buttonFor(InputAction.BACK)));
    assertEquals(InputAction.START_GAME,
        bindings.conflictingAction(InputAction.SETTINGS,
            bindings.buttonFor(InputAction.START_GAME)));
  }

  @Test
  void keepsConfirmAndTheVideoAuxiliaryActionsDistinct() {
    ActionBindings bindings = ActionBindings.genericDefaults()
        .withBinding(InputAction.FIRE, 9);

    assertEquals(InputAction.CONFIRM,
        bindings.conflictingAction(InputAction.ARM_MISSILE,
            bindings.buttonFor(InputAction.CONFIRM)));
    assertEquals(InputAction.ULTRA,
        bindings.conflictingAction(InputAction.CONFIRM,
            bindings.buttonFor(InputAction.ULTRA)));
  }

  @Test
  void allowsTheExistingBackAndSettingsNavigationGesture() {
    ActionBindings bindings = ActionBindings.genericDefaults();

    assertNull(bindings.conflictingAction(InputAction.BACK,
        bindings.buttonFor(InputAction.SETTINGS)));
    assertNull(bindings.conflictingAction(InputAction.SETTINGS,
        bindings.buttonFor(InputAction.BACK)));
  }

  @Test
  void allowsReusingAButtonAcrossDifferentContexts() {
    ActionBindings bindings = ActionBindings.genericDefaults();

    assertNull(bindings.conflictingAction(InputAction.FIRE,
        bindings.buttonFor(InputAction.CONFIRM)));
    assertNull(bindings.conflictingAction(InputAction.FIRE,
        bindings.buttonFor(InputAction.START_GAME)));
    assertNull(bindings.conflictingAction(InputAction.ULTRA,
        bindings.buttonFor(InputAction.SETTINGS)));
  }

  @Test
  void completeMapsMustCoverEveryActionWithNonNegativeButtons() {
    ActionBindings bindings = ActionBindings.genericDefaults();

    assertEquals(bindings.asMap(),
        ActionBindings.ofCompleteMap(bindings.asMap()).asMap());
    assertThrows(IllegalArgumentException.class,
        () -> ActionBindings.ofCompleteMap(java.util.Map.of(InputAction.FIRE, 0)));
    assertThrows(IllegalArgumentException.class,
        () -> ActionBindings.ofCompleteMap(java.util.Map.of(InputAction.FIRE, -1)));
  }
}
