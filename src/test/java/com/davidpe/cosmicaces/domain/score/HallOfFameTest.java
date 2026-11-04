package com.davidpe.cosmicaces.domain.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidpe.cosmicaces.domain.score.HallOfFameRegistration.Status;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HallOfFameTest {

  private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

  private static ZonedDateTime at(int minute) {
    return ZonedDateTime.of(2026, 11, 3, 12, minute, 0, 0, MADRID);
  }

  private static HallOfFameEntry entry(String id, String initials, int points, ZonedDateTime at) {
    return new HallOfFameEntry(id, initials, points, at);
  }

  private static HallOfFame rankingOf(int rows) {
    List<HallOfFameEntry> entries = new ArrayList<>();
    for (int i = 1; i <= rows; i++) {
      entries.add(entry("game-" + i, "ABC", i, at(i % 60)));
    }
    return HallOfFame.of(entries);
  }

  @Test
  void emptyRankingHasNoEntriesAndAcceptsZeroPoints() {
    HallOfFame ranking = HallOfFame.empty();

    assertTrue(ranking.isEmpty());
    assertEquals(0, ranking.size());
    assertTrue(ranking.qualifies(entry("game-0", "AAA", 0, at(0))));

    HallOfFameRegistration registration = ranking.register(entry("game-0", "AAA", 0, at(0)));

    assertEquals(Status.REGISTERED, registration.status());
    assertEquals(1, registration.ranking().size());
    assertEquals(0, registration.ranking().entries().get(0).points());
    assertTrue(ranking.isEmpty(), "registering must not mutate the previous snapshot");
  }

  @Test
  void registersRowsSortedByPointsDescending() {
    HallOfFame ranking = HallOfFame.empty();

    ranking = ranking.register(entry("a", "AAA", 120, at(1))).ranking();
    ranking = ranking.register(entry("b", "BBB", 900, at(2))).ranking();
    ranking = ranking.register(entry("c", "CCC", 450, at(3))).ranking();

    assertEquals(List.of("b", "c", "a"), ids(ranking));
  }

  @Test
  void earlierGameWinsPointTie() {
    HallOfFame ranking = HallOfFame.empty();

    ranking = ranking.register(entry("later", "LTR", 500, at(30))).ranking();
    ranking = ranking.register(entry("earlier", "ERL", 500, at(10))).ranking();

    assertEquals(List.of("earlier", "later"), ids(ranking));
  }

  @Test
  void identicalInstantKeepsPersistedInsertionOrder() {
    HallOfFame ranking = HallOfFame.empty();

    ranking = ranking.register(entry("first", "FST", 500, at(10))).ranking();
    ranking = ranking.register(entry("second", "SCD", 500, at(10))).ranking();

    assertEquals(List.of("first", "second"), ids(ranking));
    assertEquals(
        List.of("second", "first"),
        ids(HallOfFame.of(List.of(entry("second", "SCD", 500, at(10)), entry("first", "FST", 500, at(10))))));
  }

  @Test
  void fortyNineRowsStillAcceptAnyValidEntry() {
    HallOfFame ranking = rankingOf(49);

    assertTrue(ranking.qualifies(entry("extra", "ZZZ", 0, at(59))));
    assertEquals(Status.REGISTERED, ranking.register(entry("extra", "ZZZ", 0, at(59))).status());
    assertEquals(50, ranking.register(entry("extra", "ZZZ", 0, at(59))).ranking().size());
  }

  @Test
  void fullRankingKeepsOlderRowOnTieAtCutoff() {
    HallOfFame ranking = rankingOf(50);
    HallOfFameEntry cutoff = ranking.entries().get(HallOfFame.MAX_ENTRIES - 1);
    HallOfFameEntry tiedCandidate =
        entry("tied", "TIE", cutoff.points(), cutoff.finishedAt());

    assertFalse(ranking.qualifies(tiedCandidate));
    HallOfFameRegistration registration = ranking.register(tiedCandidate);

    assertEquals(Status.DID_NOT_QUALIFY, registration.status());
    assertSame(ranking, registration.ranking());
    assertEquals(50, registration.ranking().size());
    assertEquals("game-1", registration.ranking().entries().get(49).id());
  }

  @Test
  void fullRankingAcceptsCandidateThatBeatsCutoff() {
    HallOfFame ranking = rankingOf(50);
    HallOfFameEntry cutoff = ranking.entries().get(HallOfFame.MAX_ENTRIES - 1);
    HallOfFameEntry morePoints = entry("more", "MOR", cutoff.points() + 1, at(58));
    HallOfFameEntry earlierTie =
        entry("earlier", "ERL", cutoff.points(), cutoff.finishedAt().minusMinutes(1));

    assertTrue(ranking.qualifies(morePoints));
    assertTrue(ranking.qualifies(earlierTie));

    HallOfFame updated = ranking.register(morePoints).ranking();

    assertEquals(50, updated.size());
    assertTrue(ids(updated).contains("more"));
    assertFalse(ids(updated).contains("game-1"));
  }

  @Test
  void keepsOnlyTheTopFiftyWhenFiftyOneGamesAreRegistered() {
    HallOfFame ranking = HallOfFame.empty();

    for (int i = 1; i <= 51; i++) {
      ranking = ranking.register(entry("game-" + i, "ABC", i, at(i % 60))).ranking();
    }

    assertEquals(50, ranking.size());
    assertEquals("game-51", ranking.entries().get(0).id());
    assertEquals("game-2", ranking.entries().get(49).id());
    assertFalse(ids(ranking).contains("game-1"));
  }

  @Test
  void duplicateDurableIdNeverInsertsTwice() {
    HallOfFame ranking = HallOfFame.empty();
    HallOfFameEntry original = entry("same-game", "AAA", 300, at(5));
    ranking = ranking.register(original).ranking();

    HallOfFameRegistration repeated = ranking.register(entry("same-game", "BBB", 999, at(6)));

    assertEquals(Status.ALREADY_REGISTERED, repeated.status());
    assertSame(ranking, repeated.ranking());
    assertEquals(List.of(original), repeated.ranking().entries());
    assertFalse(repeated.ranking().qualifies(original));
  }

  @Test
  void allowsRepeatedInitialsForDifferentGames() {
    HallOfFame ranking = HallOfFame.empty();

    ranking = ranking.register(entry("one", "AAA", 100, at(1))).ranking();
    ranking = ranking.register(entry("two", "AAA", 200, at(2))).ranking();

    assertEquals(List.of("two", "one"), ids(ranking));
  }

  @Test
  void rejectsInvalidEntries() {
    assertThrows(IllegalArgumentException.class, () -> entry("id", "aaa", 1, at(0)));
    assertThrows(IllegalArgumentException.class, () -> entry("id", "AA", 1, at(0)));
    assertThrows(IllegalArgumentException.class, () -> entry("id", "AAAA", 1, at(0)));
    assertThrows(IllegalArgumentException.class, () -> entry("id", "A1A", 1, at(0)));
    assertThrows(IllegalArgumentException.class, () -> entry("id", null, 1, at(0)));
    assertThrows(IllegalArgumentException.class, () -> entry(" ", "AAA", 1, at(0)));
    assertThrows(IllegalArgumentException.class, () -> entry(null, "AAA", 1, at(0)));
    assertThrows(IllegalArgumentException.class, () -> entry("id", "AAA", -1, at(0)));
    assertThrows(
        NullPointerException.class, () -> new HallOfFameEntry("id", "AAA", 1, null));
  }

  @Test
  void acceptsMaximumIntegerPoints() {
    HallOfFame ranking = HallOfFame.empty();

    HallOfFame updated = ranking.register(entry("max", "MAX", Integer.MAX_VALUE, at(0))).ranking();

    assertEquals(Integer.MAX_VALUE, updated.entries().get(0).points());
  }

  @Test
  void snapshotIsImmutableAndDoesNotMutateTheInputCollection() {
    List<HallOfFameEntry> input =
        new ArrayList<>(List.of(entry("a", "AAA", 10, at(1)), entry("b", "BBB", 20, at(2))));
    HallOfFame ranking = HallOfFame.of(input);

    input.add(entry("c", "CCC", 30, at(3)));
    input.remove(0);

    assertEquals(List.of("b", "a"), ids(ranking));
    assertThrows(UnsupportedOperationException.class, () -> ranking.entries().add(entry("d", "DDD", 1, at(4))));
  }

  @Test
  void normalizesLoadedCollectionToCanonicalTopFifty() {
    List<HallOfFameEntry> loaded = new ArrayList<>();
    for (int i = 51; i >= 1; i--) {
      loaded.add(entry("game-" + i, "ABC", i, at(i % 60)));
    }

    HallOfFame ranking = HallOfFame.of(loaded);

    assertEquals(50, ranking.size());
    assertEquals("game-51", ranking.entries().get(0).id());
    assertEquals("game-2", ranking.entries().get(49).id());
  }

  @Test
  void normalizationRejectsDuplicateIds() {
    List<HallOfFameEntry> loaded =
        List.of(entry("same", "AAA", 10, at(1)), entry("same", "BBB", 20, at(2)));

    assertThrows(IllegalArgumentException.class, () -> HallOfFame.of(loaded));
  }

  @Test
  void eligibilityQueryDoesNotInsertOrReserveARow() {
    HallOfFame ranking = rankingOf(50);
    HallOfFameEntry candidate = entry("candidate", "CAN", 999, at(59));

    assertTrue(ranking.qualifies(candidate));
    assertEquals(50, ranking.size());
    assertFalse(ids(ranking).contains("candidate"));
  }

  @Test
  void eligibilityAndRegistrationAgreeAtTheCutoff() {
    HallOfFame ranking = rankingOf(50);
    HallOfFameEntry cutoff = ranking.entries().get(HallOfFame.MAX_ENTRIES - 1);
    List<HallOfFameEntry> candidates =
        List.of(
            entry("tie", "TIE", cutoff.points(), cutoff.finishedAt()),
            entry("more", "MOR", cutoff.points() + 1, at(59)),
            entry("less", "LES", cutoff.points() - 1, at(59)),
            entry("earlier", "ERL", cutoff.points(), cutoff.finishedAt().minusSeconds(1)));

    for (HallOfFameEntry candidate : candidates) {
      boolean registered = ranking.register(candidate).registered();
      assertEquals(ranking.qualifies(candidate), registered, candidate.id());
    }
  }

  @Test
  void preservesTheLocalDateOfTheEndAcrossTimeZones() {
    ZonedDateTime lateNight = ZonedDateTime.of(2026, 11, 4, 0, 30, 0, 0, MADRID);
    HallOfFameEntry entry = entry("late", "LTE", 10, lateNight);

    assertEquals(LocalDate.of(2026, 11, 4), entry.playedOn());
    assertEquals(lateNight.toInstant(), entry.finishedInstant());
  }

  private static List<String> ids(HallOfFame ranking) {
    return ranking.entries().stream().map(HallOfFameEntry::id).toList();
  }
}
