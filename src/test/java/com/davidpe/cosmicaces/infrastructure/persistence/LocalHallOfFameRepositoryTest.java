package com.davidpe.cosmicaces.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.davidpe.cosmicaces.application.score.HallOfFameLoadResult;
import com.davidpe.cosmicaces.application.score.HallOfFameLoadResult.Status;
import com.davidpe.cosmicaces.application.score.HallOfFameStorageException;
import com.davidpe.cosmicaces.domain.score.HallOfFame;
import com.davidpe.cosmicaces.domain.score.HallOfFameEntry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalHallOfFameRepositoryTest {

  private Path directory;
  private Path file;
  private LocalHallOfFameRepository repository;

  @BeforeEach
  void createRepositoryInsideTarget() throws IOException {
    directory = Path.of("target", "test-tmp", "hall-of-fame", UUID.randomUUID().toString());
    Files.createDirectories(directory);
    file = directory.resolve("hall-of-fame-v1.json");
    repository = new LocalHallOfFameRepository(file);
  }

  @AfterEach
  void removeRepositoryDirectory() throws IOException {
    restoreWritable(directory);
    if (directory == null || !Files.exists(directory)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException failure) {
          throw new UncheckedIOException(failure);
        }
      });
    }
  }

  @Test
  void loadReturnsEmptyWhenNoFileExistsYet() {
    HallOfFameLoadResult result = repository.load();

    assertEquals(Status.EMPTY, result.status());
    assertTrue(result.ranking().isEmpty());
    assertTrue(result.writable());
    assertFalse(Files.exists(file));
  }

  @Test
  void saveAndReloadRoundTripsAcrossRepositoryInstances() throws IOException {
    HallOfFame ranking = ranking(
        entry("id-b", "BBB", 500, "2026-11-03T10:00:00Z"),
        entry("id-a", "AAA", 900, "2026-11-02T23:30:00+01:00[Europe/Madrid]"),
        entry("id-ñ-日本", "CCC", 100, "2026-11-01T08:15:00-05:00[America/New_York]"));

    repository.load();
    repository.save(ranking);

    HallOfFameLoadResult loaded = new LocalHallOfFameRepository(file).load();

    assertEquals(Status.LOADED, loaded.status());
    assertEquals(ranking.entries(), loaded.ranking().entries());
    assertEquals("id-a", loaded.ranking().entries().get(0).id());
    assertEquals(LocalDate.of(2026, 11, 2), loaded.ranking().entries().get(0).playedOn());
  }

  @Test
  void saveKeepsExactlyTheTopFiftyInCanonicalOrder() throws IOException {
    List<HallOfFameEntry> entries = new ArrayList<>();
    for (int index = 0; index < HallOfFame.MAX_ENTRIES; index++) {
      entries.add(entry("id-" + index, initials(index), 1000 - index,
          "2026-11-03T" + String.format(Locale.ROOT, "%02d", index % 24) + ":00:00Z"));
    }
    entries.set(10, entry("tie-new", "TNW", 990, "2026-11-03T12:00:00Z"));
    entries.set(11, entry("tie-old", "TOL", 990, "2026-11-03T11:00:00Z"));
    HallOfFame ranking = HallOfFame.of(entries);

    repository.load();
    repository.save(ranking);

    HallOfFameLoadResult loaded = new LocalHallOfFameRepository(file).load();

    assertEquals(Status.LOADED, loaded.status());
    assertEquals(HallOfFame.MAX_ENTRIES, loaded.ranking().size());
    assertEquals(ranking.entries(), loaded.ranking().entries());
    assertTrue(indexOf(loaded.ranking(), "tie-old") < indexOf(loaded.ranking(), "tie-new"));
  }

  @Test
  void saveReplacesTheWholeSnapshot() throws IOException {
    repository.load();
    repository.save(ranking(
        entry("a", "AAA", 10, "2026-11-03T10:00:00Z"),
        entry("b", "BBB", 20, "2026-11-03T11:00:00Z")));
    repository.save(ranking(entry("c", "CCC", 30, "2026-11-03T12:00:00Z")));

    HallOfFameLoadResult loaded = new LocalHallOfFameRepository(file).load();

    assertEquals(List.of("c"),
        loaded.ranking().entries().stream().map(HallOfFameEntry::id).toList());
  }

  @Test
  void successfulSaveLeavesNoTemporaryFiles() throws IOException {
    repository.load();
    repository.save(ranking(entry("id", "ABC", 1, "2026-11-03T10:00:00Z")));

    try (Stream<Path> siblings = Files.list(directory)) {
      assertTrue(siblings.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
    }
  }

  @Test
  void malformedJsonIsInvalidAndNeverOverwritten() throws IOException {
    String original = "{\"version\":1,\"entries\":[";
    write(original);

    HallOfFameLoadResult result = repository.load();

    assertEquals(Status.INVALID_DATA, result.status());
    assertFalse(result.writable());
    assertTrue(result.ranking().isEmpty());
    assertThrows(HallOfFameStorageException.class,
        () -> repository.save(ranking(entry("id", "ABC", 1, "2026-11-03T10:00:00Z"))));
    assertEquals(original, Files.readString(file));
  }

  @Test
  void unknownOrMissingVersionIsInvalid() throws IOException {
    write("{\"version\":2,\"entries\":[]}");
    assertEquals(Status.INVALID_DATA, repository.load().status());

    write("{\"entries\":[]}");
    assertEquals(Status.INVALID_DATA, repository.load().status());
  }

  @Test
  void duplicateDurableIdsAreInvalid() throws IOException {
    write(document(row("same", "AAA", "10", "2026-11-03T10:00:00Z"),
        row("same", "BBB", "20", "2026-11-03T11:00:00Z")));

    assertEquals(Status.INVALID_DATA, repository.load().status());
  }

  @Test
  void invalidRowsRejectTheWholeDocument() throws IOException {
    List<String> invalidDocuments = List.of(
        document(row("x", "AAA", "-5", "2026-11-03T10:00:00Z")),
        document(row("x", "aa", "10", "2026-11-03T10:00:00Z")),
        document(row("x", "ABCD", "10", "2026-11-03T10:00:00Z")),
        document(row("x", "AAA", "10", "not-a-date")),
        document(row("x", "AAA", "10.5", "2026-11-03T10:00:00Z")),
        document(row("x", "AAA", "9999999999", "2026-11-03T10:00:00Z")),
        document(row("x", "AAA", "\"10\"", "2026-11-03T10:00:00Z")),
        document(row("", "AAA", "10", "2026-11-03T10:00:00Z")),
        document(row("x".repeat(LocalHallOfFameRepository.MAX_ID_LENGTH + 1), "AAA", "10",
            "2026-11-03T10:00:00Z")),
        "{\"version\":1,\"entries\":{}}",
        "{\"version\":1}",
        "[]",
        "null");

    for (String invalidDocument : invalidDocuments) {
      write(invalidDocument);
      LocalHallOfFameRepository fresh = new LocalHallOfFameRepository(file);
      assertEquals(Status.INVALID_DATA, fresh.load().status(), invalidDocument);
    }
  }

  @Test
  void moreThanFiftyRowsAreInvalid() throws IOException {
    StringBuilder rows = new StringBuilder();
    for (int index = 0; index <= HallOfFame.MAX_ENTRIES; index++) {
      if (index > 0) {
        rows.append(',');
      }
      rows.append(row("id-" + index, "AAA", Integer.toString(index),
          "2026-11-03T10:00:00Z"));
    }
    write("{\"version\":1,\"entries\":[" + rows + "]}");

    assertEquals(Status.INVALID_DATA, repository.load().status());
  }

  @Test
  void oversizedFileIsInvalidWithoutLoadingItWhole() throws IOException {
    Files.writeString(file, " ".repeat(LocalHallOfFameRepository.MAX_FILE_BYTES + 1));

    assertEquals(Status.INVALID_DATA, repository.load().status());
  }

  @Test
  void unreadablePathIsAStorageFailureAndSaveIsBlocked() throws IOException {
    Files.createDirectories(file);

    HallOfFameLoadResult result = repository.load();

    assertEquals(Status.STORAGE_FAILURE, result.status());
    assertFalse(result.writable());
    assertThrows(HallOfFameStorageException.class,
        () -> repository.save(ranking(entry("id", "ABC", 1, "2026-11-03T10:00:00Z"))));
    assertTrue(Files.isDirectory(file));
  }

  @Test
  void saveRequiresALoadFirst() {
    assertThrows(IllegalStateException.class, () -> repository.save(HallOfFame.empty()));
  }

  @Test
  void failedLoadBlocksSavingUntilAValidLoadSucceeds() throws IOException {
    write("not json");
    assertEquals(Status.INVALID_DATA, repository.load().status());
    assertThrows(HallOfFameStorageException.class, () -> repository.save(HallOfFame.empty()));

    write("{\"version\":1,\"entries\":[]}");
    assertEquals(Status.LOADED, repository.load().status());

    HallOfFame ranking = ranking(entry("id", "ABC", 7, "2026-11-03T10:00:00Z"));
    repository.save(ranking);
    assertEquals(ranking.entries(),
        new LocalHallOfFameRepository(file).load().ranking().entries());
  }

  @Test
  void failedSaveLeavesThePreviousSnapshotIntact() throws IOException {
    HallOfFame first = ranking(entry("first", "AAA", 10, "2026-11-03T10:00:00Z"));
    repository.load();
    repository.save(first);
    String before = Files.readString(file);

    directory.toFile().setWritable(false);
    try {
      assumeFalse(Files.isWritable(directory),
          "Filesystem does not enforce a read-only directory");
      assertThrows(HallOfFameStorageException.class,
          () -> repository.save(ranking(entry("second", "BBB", 20, "2026-11-03T11:00:00Z"))));
    } finally {
      directory.toFile().setWritable(true);
    }

    assertEquals(before, Files.readString(file));
    assertEquals(first.entries(),
        new LocalHallOfFameRepository(file).load().ranking().entries());
  }

  @Test
  void replacementFailureRaisesStorageExceptionAndCleansItsTemporaryFile() throws IOException {
    repository.load();
    repository.save(ranking(entry("first", "AAA", 10, "2026-11-03T10:00:00Z")));
    Files.delete(file);
    Files.createDirectories(file);
    Files.writeString(file.resolve("keep.txt"), "keep");

    assertThrows(HallOfFameStorageException.class,
        () -> repository.save(ranking(entry("second", "BBB", 20, "2026-11-03T11:00:00Z"))));

    assertTrue(Files.isDirectory(file));
    try (Stream<Path> siblings = Files.list(directory)) {
      assertTrue(siblings.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
    }
  }

  @Test
  void defaultFilePathUsesThePerUserGameDirectory() {
    assertTrue(LocalHallOfFameRepository.defaultFilePath()
        .endsWith(Path.of(".cosmic-aces", "hall-of-fame-v1.json")));
  }

  private void write(String content) throws IOException {
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }

  private static HallOfFame ranking(HallOfFameEntry... entries) {
    return HallOfFame.of(List.of(entries));
  }

  private static HallOfFameEntry entry(String id, String initials, int points, String finishedAt) {
    return new HallOfFameEntry(id, initials, points, ZonedDateTime.parse(finishedAt));
  }

  private static String initials(int index) {
    return "A" + (char) ('A' + index % 26) + "A";
  }

  private static int indexOf(HallOfFame ranking, String id) {
    for (int index = 0; index < ranking.size(); index++) {
      if (ranking.entries().get(index).id().equals(id)) {
        return index;
      }
    }
    throw new AssertionError("Missing entry " + id);
  }

  private static String document(String... rows) {
    return "{\"version\":1,\"entries\":[" + String.join(",", rows) + "]}";
  }

  private static String row(String id, String initials, String pointsJson, String finishedAt) {
    return "{\"id\":\"" + id + "\",\"initials\":\"" + initials + "\",\"points\":" + pointsJson
        + ",\"finishedAt\":\"" + finishedAt + "\"}";
  }

  private static void restoreWritable(Path root) throws IOException {
    if (root == null || !Files.exists(root)) {
      return;
    }
    root.toFile().setWritable(true, false);
    try (Stream<Path> paths = Files.walk(root)) {
      paths.forEach(path -> path.toFile().setWritable(true, false));
    }
  }
}
