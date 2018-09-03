package com.squareup.moshi.adapters;

import com.squareup.moshi.Json;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import java.io.IOException;
import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A JsonAdapter for enums that allows having a fallback enum value when a deserialized string does
 * not match any enum value.
 */
public final class EnumJsonAdapter<T extends Enum<T>> extends JsonAdapter<T> {
  final Class<T> enumType;
  final String[] nameStrings;
  final T[] constants;
  final JsonReader.Options options;
  final boolean useFallbackValue;
  final @Nullable T fallbackValue;

  public static <T extends Enum<T>> EnumJsonAdapter<T> create(Class<T> enumType) {
    return new EnumJsonAdapter<>(enumType, null, false);
  }

  /**
   * Create a new adapter for this enum with a fallback value to use when the JSON string does not
   * match any of the enum's constants. Note that this value will not be used when the JSON value is
   * null, absent, or not a string. Also, the string values are case-sensitive, and this fallback
   * value will be used even on case mismatches.
   */
  public EnumJsonAdapter<T> withUnknownFallback(@Nullable T fallbackValue) {
    return new EnumJsonAdapter<>(enumType, fallbackValue, true);
  }

  EnumJsonAdapter(Class<T> enumType, @Nullable T fallbackValue, boolean useFallbackValue) {
    this.enumType = enumType;
    this.fallbackValue = fallbackValue;
    this.useFallbackValue = useFallbackValue;
    try {
      constants = enumType.getEnumConstants();
      nameStrings = new String[constants.length];
      for (int i = 0; i < constants.length; i++) {
        String constantName = constants[i].name();
        Json annotation = enumType.getField(constantName).getAnnotation(Json.class);
        String name = annotation != null ? annotation.name() : constantName;
        nameStrings[i] = name;
      }
      options = JsonReader.Options.of(nameStrings);
    } catch (NoSuchFieldException e) {
      throw new AssertionError("Missing field in " + enumType.getName(), e);
    }
  }

  @Override public @Nonnull T fromJson(JsonReader reader) throws IOException {
    int index = reader.selectString(options);
    if (index != -1) return constants[index];

    String path = reader.getPath();
    if (!useFallbackValue) {
      String name = reader.nextString();
      throw new JsonDataException("Expected one of "
          + Arrays.asList(nameStrings) + " but was " + name + " at path " + path);
    }
    if (reader.peek() != JsonReader.Token.STRING) {
      throw new JsonDataException(
          "Expected a string but was " + reader.peek() + " at path " + path);
    }
    reader.skipValue();
    return fallbackValue;
  }

  @Override public void toJson(JsonWriter writer, T value) throws IOException {
    if (value == null) {
      throw new NullPointerException(
          "value was null! Wrap in .nullSafe() to write nullable values.");
    }
    writer.value(nameStrings[value.ordinal()]);
  }

  @Override public String toString() {
    return "EnumJsonAdapter(" + enumType.getName() + ")";
  }
}
