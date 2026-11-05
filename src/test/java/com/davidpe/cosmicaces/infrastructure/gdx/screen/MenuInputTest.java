package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.infrastructure.gdx.input.ActionSnapshot;
import com.davidpe.cosmicaces.infrastructure.gdx.input.InputAction;
import com.davidpe.cosmicaces.infrastructure.gdx.input.StickDirection;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pure translation of the merged sample into menu decisions; no window is required. */
class MenuInputTest {

  @Test
  void missingOrEmptySampleIsNeutral() {
    assertFalse(MenuInput.from(null).confirm());
    assertEquals(MenuInput.EMPTY, MenuInput.from(ActionSnapshot.EMPTY));
  }

  @Test
  void heldActionsAreNotMenuEdges() {
    ActionSnapshot held = new ActionSnapshot(Set.of(InputAction.START_GAME,
        InputAction.CONFIRM, InputAction.BACK, InputAction.SETTINGS),
        Set.of(), new StickDirection(false, true, false, false), StickDirection.NEUTRAL, false);

    MenuInput menu = MenuInput.from(held);

    assertFalse(menu.startGame());
    assertFalse(menu.confirm());
    assertFalse(menu.back());
    assertFalse(menu.settings());
    assertFalse(menu.right(), "a held stick direction must not repeat navigation");
  }

  @Test
  void mapsEveryMenuActionAndStickEdge() {
    ActionSnapshot edges = new ActionSnapshot(Set.of(),
        Set.of(InputAction.START_GAME, InputAction.CONFIRM, InputAction.BACK,
            InputAction.SETTINGS),
        StickDirection.NEUTRAL,
        new StickDirection(false, true, true, false), true);

    MenuInput menu = MenuInput.from(edges);

    assertTrue(menu.startGame());
    assertTrue(menu.confirm());
    assertTrue(menu.back());
    assertTrue(menu.settings());
    assertFalse(menu.left());
    assertTrue(menu.right());
    assertTrue(menu.up());
    assertFalse(menu.down());
  }

  @Test
  void videoFunctionsUseMissionButtonsOnlyInsideTheirPage() {
    ActionSnapshot edges = new ActionSnapshot(Set.of(),
        Set.of(InputAction.ARM_MISSILE, InputAction.ULTRA, InputAction.MINIMAP),
        StickDirection.NEUTRAL, StickDirection.NEUTRAL, true);

    MenuInput menu = MenuInput.from(edges);

    assertTrue(menu.videoToggleDestination());
    assertTrue(menu.videoRestoreWindow());
    assertTrue(menu.videoRefreshModes());
    assertFalse(menu.confirm(), "mission buttons must not turn into a menu confirm");
  }

  @Test
  void mergedKeyboardAndGamepadConflictStaysOneLogicalMenuEdge() {
    ActionSnapshot keyboard = new ActionSnapshot(Set.of(InputAction.CONFIRM),
        Set.of(InputAction.CONFIRM), StickDirection.NEUTRAL, StickDirection.NEUTRAL, true);
    ActionSnapshot gamepad = new ActionSnapshot(Set.of(InputAction.CONFIRM),
        Set.of(InputAction.CONFIRM), StickDirection.NEUTRAL, StickDirection.NEUTRAL, true);

    ActionSnapshot merged = ActionSnapshot.merge(keyboard, gamepad);

    assertEquals(Set.of(InputAction.CONFIRM), merged.justPressed());
    assertTrue(MenuInput.from(merged).confirm());
  }
}
