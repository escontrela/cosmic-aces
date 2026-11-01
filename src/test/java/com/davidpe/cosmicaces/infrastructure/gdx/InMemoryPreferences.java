package com.davidpe.cosmicaces.infrastructure.gdx;

import com.badlogic.gdx.Preferences;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory {@link Preferences} used by tests so settings can be exercised without booting LibGDX
 * or touching the player's real settings file. Missing keys behave like the real backends: the
 * no-default getters throw {@link IllegalArgumentException}.
 */
final class InMemoryPreferences implements Preferences {

  private final Map<String, Object> values = new LinkedHashMap<>();
  private int flushCount;

  int flushCount() {
    return flushCount;
  }

  @Override
  public Preferences putBoolean(String key, boolean value) {
    values.put(key, value);
    return this;
  }

  @Override
  public Preferences putInteger(String key, int value) {
    values.put(key, value);
    return this;
  }

  @Override
  public Preferences putLong(String key, long value) {
    values.put(key, value);
    return this;
  }

  @Override
  public Preferences putFloat(String key, float value) {
    values.put(key, value);
    return this;
  }

  @Override
  public Preferences putString(String key, String value) {
    values.put(key, value);
    return this;
  }

  @Override
  public Preferences put(Map<String, ?> vals) {
    values.putAll(vals);
    return this;
  }

  @Override
  public boolean getBoolean(String key) {
    return require(key, Boolean.class);
  }

  @Override
  public int getInteger(String key) {
    return require(key, Integer.class);
  }

  @Override
  public long getLong(String key) {
    return require(key, Long.class);
  }

  @Override
  public float getFloat(String key) {
    return require(key, Float.class);
  }

  @Override
  public String getString(String key) {
    return require(key, String.class);
  }

  @Override
  public boolean getBoolean(String key, boolean defValue) {
    Object value = values.get(key);
    return value instanceof Boolean saved ? saved : defValue;
  }

  @Override
  public int getInteger(String key, int defValue) {
    Object value = values.get(key);
    return value instanceof Integer saved ? saved : defValue;
  }

  @Override
  public long getLong(String key, long defValue) {
    Object value = values.get(key);
    return value instanceof Long saved ? saved : defValue;
  }

  @Override
  public float getFloat(String key, float defValue) {
    Object value = values.get(key);
    return value instanceof Float saved ? saved : defValue;
  }

  @Override
  public String getString(String key, String defValue) {
    Object value = values.get(key);
    if (value == null) {
      return defValue;
    }
    if (value instanceof String saved) {
      return saved;
    }
    throw new IllegalArgumentException("Preference '" + key + "' is not a string");
  }

  @Override
  public Map<String, ?> get() {
    return Map.copyOf(values);
  }

  @Override
  public boolean contains(String key) {
    return values.containsKey(key);
  }

  @Override
  public void clear() {
    values.clear();
  }

  @Override
  public void remove(String key) {
    values.remove(key);
  }

  @Override
  public void flush() {
    flushCount++;
  }

  private <T> T require(String key, Class<T> type) {
    Object value = values.get(key);
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException("Preference '" + key + "' is missing or not a "
          + type.getSimpleName());
    }
    return type.cast(value);
  }
}
