package com.davidpe.cosmicaces.infrastructure.gdx.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Pure Hall of Fame geometry and text format checks; no LibGDX/OpenGL context is required. */
class HallOfFameLayoutTest {

  @Test
  void fitsTheArtworkWithoutDeformingIt() {
    HallOfFameLayout.Fit reference = HallOfFameLayout.fitCentered(800f, 600f);
    assertEquals(0f, reference.x(), 0.001f);
    assertEquals(0f, reference.y(), 0.001f);
    assertEquals(800f, reference.width(), 0.001f);
    assertEquals(600f, reference.height(), 0.001f);

    HallOfFameLayout.Fit wide = HallOfFameLayout.fitCentered(1920f, 1080f);
    assertEquals(1080f * HallOfFameLayout.ART_WIDTH / HallOfFameLayout.ART_HEIGHT,
        wide.width(), 0.001f);
    assertEquals((1920f - wide.width()) / 2f, wide.x(), 0.001f);

    HallOfFameLayout.Fit tall = HallOfFameLayout.fitCentered(1000f, 1000f);
    assertEquals(1000f, tall.width(), 0.001f);
    assertEquals(1000f * HallOfFameLayout.ART_HEIGHT / HallOfFameLayout.ART_WIDTH,
        tall.height(), 0.001f);
    assertEquals((1000f - tall.height()) / 2f, tall.y(), 0.001f);
  }

  @Test
  void keepsTheTableInsideTheArtworkAwayFromBadgeAndAircraft() {
    for (float[] viewport : new float[][] {{800f, 600f}, {1920f, 1080f}, {1000f, 1000f}}) {
      HallOfFameLayout.Fit art = HallOfFameLayout.fitCentered(viewport[0], viewport[1]);
      HallOfFameLayout.Area table = HallOfFameLayout.tableArea(viewport[0], viewport[1]);

      assertTrue(table.x() >= art.x(), "table starts inside the artwork");
      assertTrue(table.y() >= art.y(), "table starts inside the artwork");
      assertTrue(table.x() + table.width() <= art.x() + art.width() + 0.001f,
          "table ends inside the artwork");
      assertTrue(table.y() + table.height() <= art.y() + art.height() + 0.001f,
          "table ends inside the artwork");
      assertTrue(HallOfFameLayout.headerBaselineY(art) > table.y() + table.height(),
          "headers stay above the clipped table");
      assertTrue(HallOfFameLayout.titleBaselineY(art)
          > HallOfFameLayout.headerBaselineY(art), "title stays above the headers");
    }
  }

  @Test
  void scalesRowsWithTheArtworkSoTheSameRankingFitsAnyWindow() {
    HallOfFameLayout.Area reference = HallOfFameLayout.tableArea(800f, 600f);
    HallOfFameLayout.Area fullHd = HallOfFameLayout.tableArea(1920f, 1080f);
    float referenceRow = HallOfFameLayout.rowHeight(600f);
    float fullHdRow = HallOfFameLayout.rowHeight(1080f);

    assertEquals(30f, referenceRow, 0.001f);
    assertEquals(54f, fullHdRow, 0.001f);
    assertEquals(8, HallOfFameLayout.visibleRows(reference, referenceRow));
    assertEquals(8, HallOfFameLayout.visibleRows(fullHd, fullHdRow));
  }

  @Test
  void alwaysDrawsAtLeastOneRowAndClampsTinyAreas() {
    assertEquals(1, HallOfFameLayout.visibleRows(new HallOfFameLayout.Area(0f, 0f, 10f, 5f), 34f));
    assertEquals(1, HallOfFameLayout.visibleRows(new HallOfFameLayout.Area(0f, 0f, 10f, 5f), 0f));
    assertEquals(1.0f, HallOfFameLayout.artScale(0f), 0.001f);
  }

  @Test
  void returnsSafeRowRangesForEmptyOneAndFullRankings() {
    assertEquals(new HallOfFameLayout.RowRange(0, 0),
        HallOfFameLayout.visibleRowRange(0, 10, 0.0));
    assertEquals(new HallOfFameLayout.RowRange(0, 1),
        HallOfFameLayout.visibleRowRange(1, 10, 0.0));
    assertEquals(new HallOfFameLayout.RowRange(0, 11),
        HallOfFameLayout.visibleRowRange(50, 10, 0.0));
    assertEquals(new HallOfFameLayout.RowRange(40, 50),
        HallOfFameLayout.visibleRowRange(50, 10, 40.0));
    assertEquals(new HallOfFameLayout.RowRange(39, 50),
        HallOfFameLayout.visibleRowRange(50, 10, 39.4));
    assertEquals(new HallOfFameLayout.RowRange(49, 50),
        HallOfFameLayout.visibleRowRange(50, 10, 100.0));
    assertEquals(new HallOfFameLayout.RowRange(0, 11),
        HallOfFameLayout.visibleRowRange(50, 10, -5.0));
  }

  @Test
  void rowBaselinesMoveUpByTheSubRowOffset() {
    HallOfFameLayout.Area area = new HallOfFameLayout.Area(0f, 0f, 500f, 340f);
    float first = HallOfFameLayout.rowBaseline(area, 34f, 0.0, 0);
    float second = HallOfFameLayout.rowBaseline(area, 34f, 0.0, 1);
    float shifted = HallOfFameLayout.rowBaseline(area, 34f, 0.5, 0);

    assertEquals(34f, first - second, 0.001f);
    assertEquals(17f, first - shifted, 0.001f);
  }

  @Test
  void keepsColumnsOrderedAndInsideTheTable() {
    HallOfFameLayout.Area area = new HallOfFameLayout.Area(100f, 50f, 600f, 300f);
    HallOfFameLayout.Columns columns = HallOfFameLayout.columns(area);

    assertEquals(130f, columns.positionX(), 0.001f);
    assertEquals(238f, columns.initialsX(), 0.001f);
    assertEquals(316f, columns.pointsLeftX(), 0.001f);
    assertEquals(532f, columns.pointsRightX(), 0.001f);
    assertEquals(580f, columns.dateX(), 0.001f);
    assertTrue(columns.positionX() < columns.initialsX());
    assertTrue(columns.initialsX() < columns.pointsLeftX());
    assertTrue(columns.pointsLeftX() < columns.pointsRightX());
    assertTrue(columns.pointsRightX() < columns.dateX());
    assertTrue(columns.dateX() < area.x() + area.width());
  }

  @Test
  void formatsPositionsScoresAndDatesWithoutTruncatingThem() {
    assertEquals("01", HallOfFameLayout.positionLabel(1));
    assertEquals("09", HallOfFameLayout.positionLabel(9));
    assertEquals("10", HallOfFameLayout.positionLabel(10));
    assertEquals("50", HallOfFameLayout.positionLabel(50));
    assertThrows(IllegalArgumentException.class, () -> HallOfFameLayout.positionLabel(0));

    assertEquals("0", HallOfFameLayout.scoreLabel(0));
    assertEquals("2147483647", HallOfFameLayout.scoreLabel(Integer.MAX_VALUE));

    assertEquals("03/11/26", HallOfFameLayout.dateLabel(LocalDate.of(2026, 11, 3)));
    assertEquals("05/01/99", HallOfFameLayout.dateLabel(LocalDate.of(1999, 1, 5)));
  }
}
