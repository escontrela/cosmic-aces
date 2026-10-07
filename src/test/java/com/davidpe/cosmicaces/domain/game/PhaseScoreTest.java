package com.davidpe.cosmicaces.domain.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PhaseScoreTest {

  @Test
  void awardsOnePointPerCompleteSecondOnly() {
    PhaseScore score = new PhaseScore();

    assertEquals(0, score.advance(0.4f, false));
    assertEquals(1, score.advance(0.6f, false));
    assertEquals(1, score.advance(1f, false));
    assertEquals(2, score.totalPoints());
  }

  @Test
  void coincidenceAddsTenPointsPerAccumulatedSecond() {
    PhaseScore score = new PhaseScore();

    // Example from the ticket: 10 s of phase with 2 s of visible coincidence = 30 points.
    score.advance(2f, true);
    score.advance(8f, false);

    assertEquals(10 + 20, score.totalPoints());
  }

  @Test
  void doesNotAwardCoincidenceBonusWithoutVisibility() {
    PhaseScore score = new PhaseScore();

    score.advance(5f, false);

    assertEquals(5, score.totalPoints());
  }

  @Test
  void resultIsIndependentOfDeltaPartitioning() {
    PhaseScore singleStep = new PhaseScore();
    singleStep.advance(2f, true);
    singleStep.advance(8f, false);

    PhaseScore manySteps = new PhaseScore();
    for (int i = 0; i < 40; i++) {
      manySteps.advance(0.25f, i < 8);
    }

    assertEquals(singleStep.totalPoints(), manySteps.totalPoints());
    assertEquals(30, manySteps.totalPoints());
  }

  @Test
  void invalidDeltasAndRepeatedAdvanceDoNotDoubleCount() {
    PhaseScore score = new PhaseScore();

    assertEquals(1, score.advance(1f, false));
    assertEquals(0, score.advance(0f, false));
    assertEquals(0, score.advance(-3f, false));
    assertEquals(0, score.advance(Float.NaN, false));
    assertEquals(0, score.advance(Float.POSITIVE_INFINITY, false));
    assertEquals(0, score.advance(0.5f, false));
    assertEquals(1, score.advance(0.5f, false));

    assertEquals(2, score.totalPoints());
  }

  @Test
  void tracksBaseAndCoincidenceSecondsSeparately() {
    PhaseScore score = new PhaseScore();

    score.advance(3.5f, true);
    score.advance(1.5f, false);

    // 5 complete run seconds (5 base) plus 3 complete coincidence seconds (30 bonus).
    assertEquals(35, score.totalPoints());
  }
}
