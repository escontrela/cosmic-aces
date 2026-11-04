package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure three-initial editor checks; no LibGDX/OpenGL context is required. */
class HallOfFameInitialsTest {

  @Test
  void startsAtVisualAaaOnTheFirstPositionWithoutPersistingAnything() {
    HallOfFameInitials initials = new HallOfFameInitials();

    assertEquals("AAA", initials.initials());
    assertEquals(0, initials.cursor());
    assertTrue(initials.isSelected(0));
    assertFalse(initials.isSelected(1));
    assertEquals('A', initials.letterAt(0));
    assertEquals('A', initials.letterAt(2));
  }

  @Test
  void movesTheCursorBetweenTheThreePositionsAndClampsAtBothEnds() {
    HallOfFameInitials initials = new HallOfFameInitials();

    assertFalse(initials.moveCursor(-1), "the first position does not wrap to the last one");
    assertEquals(0, initials.cursor());

    assertTrue(initials.moveCursor(1));
    assertEquals(1, initials.cursor());
    assertTrue(initials.moveCursor(1));
    assertEquals(2, initials.cursor());
    assertFalse(initials.moveCursor(1), "the last position does not wrap to the first one");
    assertEquals(2, initials.cursor());
  }

  @Test
  void cyclesTheSelectedLetterThroughTheAlphabetAndWrapsAtBothEnds() {
    HallOfFameInitials initials = new HallOfFameInitials();

    initials.cycleLetter(1);
    assertEquals("BAA", initials.initials());
    initials.cycleLetter(-1);
    assertEquals("AAA", initials.initials());
    initials.cycleLetter(-1);
    assertEquals("ZAA", initials.initials());
    initials.cycleLetter(1);
    assertEquals("AAA", initials.initials());
    initials.cycleLetter(25);
    assertEquals("ZAA", initials.initials());

    initials.moveCursor(2);
    initials.cycleLetter(2);
    assertEquals("ZAC", initials.initials());
    assertEquals('Z', initials.letterAt(0));
    assertEquals('A', initials.letterAt(1));
    assertEquals('C', initials.letterAt(2));
  }

  @Test
  void aHugeDeltaStaysInsideTheAlphabetAndTheThreePositions() {
    HallOfFameInitials initials = new HallOfFameInitials();

    initials.cycleLetter(Integer.MIN_VALUE);
    for (int position = 0; position < HallOfFameInitials.LENGTH; position++) {
      char letter = initials.letterAt(position);
      assertTrue(letter >= 'A' && letter <= 'Z', "letter must stay in A-Z: " + letter);
    }
    initials.moveCursor(Integer.MIN_VALUE);
    assertEquals(0, initials.cursor());
    initials.moveCursor(Integer.MAX_VALUE);
    assertEquals(2, initials.cursor());
  }

  @Test
  void resetReturnsToTheStartingPoint() {
    HallOfFameInitials initials = new HallOfFameInitials();
    initials.moveCursor(2);
    initials.cycleLetter(3);

    initials.reset();

    assertEquals("AAA", initials.initials());
    assertEquals(0, initials.cursor());
  }

  @Test
  void rejectsPositionsOutsideTheEditor() {
    HallOfFameInitials initials = new HallOfFameInitials();

    assertThrows(IllegalArgumentException.class, () -> initials.letterAt(-1));
    assertThrows(IllegalArgumentException.class, () -> initials.letterAt(3));
    assertThrows(IllegalArgumentException.class, () -> initials.isSelected(3));
  }
}
