package com.davidpe.cosmicaces.infrastructure.persistence;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.davidpe.cosmicaces.application.score.HallOfFameLoadResult;
import com.davidpe.cosmicaces.application.score.HallOfFameRepository;
import com.davidpe.cosmicaces.application.score.HallOfFameStorageException;
import com.davidpe.cosmicaces.domain.score.HallOfFame;
import com.davidpe.cosmicaces.domain.score.HallOfFameEntry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * File-backed {@link HallOfFameRepository} that stores a versioned JSON snapshot in the local game
 * data directory.
 *
 * <p>The file holds at most {@link HallOfFame#MAX_ENTRIES} rows with a durable id, three initials,
 * points and the ISO-8601 terminal instant including its zone. The zone is kept so the displayed
 * DD/MM/YY date does not shift when the application restarts elsewhere. The adapter never reads the
 * clock, generates identities or scores a game; those belong to the application.
 *
 * <p>Loading is defensive: the file is capped at {@link #MAX_FILE_BYTES}, the document must carry a
 * known version and an entries array, and any invalid row invalidates the whole document. A failed
 * load never authorizes overwriting the original: {@link #save(HallOfFame)} refuses until a later
 * load succeeds. Writing uses a sibling temporary file, forces it to disk and replaces the target
 * atomically when the filesystem supports it; otherwise it falls back to a replacement move in the
 * same directory. A failure leaves the previous snapshot intact and removes the temporary file.
 *
 * <p>The path is always supplied by the composition root. {@link #defaultFilePath()} proposes the
 * per-user location {@code <user.home>/.cosmic-aces/hall-of-fame-v1.json}; tests and smoke runs must
 * point at a repository-local directory instead.
 */
public final class LocalHallOfFameRepository implements HallOfFameRepository {

  /** Current persisted schema version. Unknown versions are rejected instead of guessed. */
  public static final int FORMAT_VERSION = 1;

  /** Upper bound for the whole file; a top 50 snapshot is far smaller than this. */
  static final int MAX_FILE_BYTES = 1_048_576;

  /** Upper bound for a durable entry id; UUID-sized identities are well below it. */
  static final int MAX_ID_LENGTH = 128;

  /** Upper bound for the ISO-8601 terminal instant, including its zone id. */
  static final int MAX_TIMESTAMP_LENGTH = 64;

  private static final String DIRECTORY_NAME = ".cosmic-aces";
  private static final String FILE_NAME = "hall-of-fame-v1.json";

  private final Path file;
  private boolean loadAttempted;
  private boolean loadFailed;

  /**
   * Creates a repository for the given snapshot file.
   *
   * @param file target file; its parent directory is created on the first successful save
   */
  public LocalHallOfFameRepository(Path file) {
    Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    if (absolute.getFileName() == null) {
      throw new IllegalArgumentException("Hall of Fame file must name a file: " + file);
    }
    this.file = absolute;
  }

  /** Per-user default location, resolved by the composition root at startup. */
  public static Path defaultFilePath() {
    return Path.of(System.getProperty("user.home"), DIRECTORY_NAME, FILE_NAME);
  }

  /** Effective snapshot file used by this adapter. */
  public Path file() {
    return file;
  }

  @Override
  public HallOfFameLoadResult load() {
    HallOfFameLoadResult result;
    try {
      if (Files.notExists(file)) {
        result = HallOfFameLoadResult.empty();
      } else if (!Files.isRegularFile(file)) {
        result = HallOfFameLoadResult.storageFailure();
      } else {
        result = HallOfFameLoadResult.loaded(parse(readBounded(file)));
      }
    } catch (IOException unreadableFile) {
      result = HallOfFameLoadResult.storageFailure();
    } catch (SecurityException unreadableFile) {
      result = HallOfFameLoadResult.storageFailure();
    } catch (RuntimeException invalidDocument) {
      result = HallOfFameLoadResult.invalidData();
    }
    loadAttempted = true;
    loadFailed = !result.writable();
    return result;
  }

  @Override
  public void save(HallOfFame ranking) {
    Objects.requireNonNull(ranking, "ranking");
    if (!loadAttempted) {
      throw new IllegalStateException("load() must be called before save()");
    }
    if (loadFailed) {
      throw new HallOfFameStorageException(
          "Refusing to replace Hall of Fame data that could not be loaded: " + file);
    }
    try {
      write(serialize(ranking));
    } catch (IOException | SecurityException writeFailure) {
      throw new HallOfFameStorageException("Could not save the Hall of Fame to " + file,
          writeFailure);
    }
  }

  private static String readBounded(Path file) throws IOException {
    try (InputStream input = Files.newInputStream(file)) {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[8_192];
      int total = 0;
      int read;
      while ((read = input.read(buffer)) != -1) {
        total += read;
        if (total > MAX_FILE_BYTES) {
          throw new InvalidDocumentException();
        }
        output.write(buffer, 0, read);
      }
      return output.toString(StandardCharsets.UTF_8);
    }
  }

  private void write(String json) throws IOException {
    Path parent = file.getParent();
    Files.createDirectories(parent);
    Path temporary = Files.createTempFile(parent, file.getFileName().toString() + ".", ".tmp");
    boolean moved = false;
    try {
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING)) {
        ByteBuffer buffer = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
      try {
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupportedAtomicMove) {
        // Same-directory replacement: the complete temporary file already exists, so a failure
        // here leaves the previous snapshot untouched instead of truncating it.
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
      moved = true;
    } finally {
      if (!moved) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // A leftover sibling temporary file is harmless and never replaces the snapshot.
        }
      }
    }
  }

  private static String serialize(HallOfFame ranking) {
    JsonValue root = new JsonValue(JsonValue.ValueType.object);
    root.addChild("version", new JsonValue(FORMAT_VERSION));
    JsonValue rows = new JsonValue(JsonValue.ValueType.array);
    for (HallOfFameEntry entry : ranking.entries()) {
      JsonValue row = new JsonValue(JsonValue.ValueType.object);
      row.addChild("id", new JsonValue(entry.id()));
      row.addChild("initials", new JsonValue(entry.initials()));
      row.addChild("points", new JsonValue(entry.points()));
      row.addChild("finishedAt", new JsonValue(entry.finishedAt().toString()));
      rows.addChild(row);
    }
    root.addChild("entries", rows);
    return root.toJson(JsonWriter.OutputType.json) + "\n";
  }

  private static HallOfFame parse(String json) {
    JsonValue root = new JsonReader().parse(json);
    if (root == null || !root.isObject()) {
      throw new InvalidDocumentException();
    }
    JsonValue version = root.get("version");
    if (version == null || !version.isLong() || version.asLong() != FORMAT_VERSION) {
      throw new InvalidDocumentException();
    }
    JsonValue rows = root.get("entries");
    if (rows == null || !rows.isArray() || rows.size() > HallOfFame.MAX_ENTRIES) {
      throw new InvalidDocumentException();
    }
    List<HallOfFameEntry> entries = new ArrayList<>(rows.size());
    for (JsonValue row : rows) {
      entries.add(parseEntry(row));
    }
    return HallOfFame.of(entries);
  }

  private static HallOfFameEntry parseEntry(JsonValue row) {
    if (row == null || !row.isObject()) {
      throw new InvalidDocumentException();
    }
    String id = requiredString(row, "id");
    if (id.length() > MAX_ID_LENGTH) {
      throw new InvalidDocumentException();
    }
    String initials = requiredString(row, "initials");
    JsonValue points = row.get("points");
    if (points == null || !points.isLong()) {
      throw new InvalidDocumentException();
    }
    long pointsValue = points.asLong();
    if (pointsValue < 0 || pointsValue > Integer.MAX_VALUE) {
      throw new InvalidDocumentException();
    }
    String finishedAt = requiredString(row, "finishedAt");
    if (finishedAt.length() > MAX_TIMESTAMP_LENGTH) {
      throw new InvalidDocumentException();
    }
    ZonedDateTime finished;
    try {
      finished = ZonedDateTime.parse(finishedAt);
    } catch (DateTimeParseException invalidDate) {
      throw new InvalidDocumentException();
    }
    try {
      return new HallOfFameEntry(id, initials, (int) pointsValue, finished);
    } catch (IllegalArgumentException invalidEntry) {
      throw new InvalidDocumentException();
    }
  }

  private static String requiredString(JsonValue row, String name) {
    JsonValue value = row.get(name);
    if (value == null || !value.isString()) {
      throw new InvalidDocumentException();
    }
    return value.asString();
  }

  /** Control-flow signal for a document that violates the persisted contract. */
  private static final class InvalidDocumentException extends RuntimeException {
    InvalidDocumentException() {
      super();
    }
  }
}
