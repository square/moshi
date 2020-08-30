package com.squareup.moshi;

/**
 * Listens to any events triggered by {@link JsonAdapter#fromJson} or {@link JsonAdapter#toJson}.
 */
public interface JsonEventListener {
  /** An event that was triggered by {@link JsonAdapter#fromJson}. */
  void onReadEvent(JsonReadEvent event);

  /** An event that was triggered by {@link JsonAdapter#toJson}. */
  void onWriteEvent(JsonWriteEvent event);

  /** @return a deep copy of the event listener. */
  JsonEventListener copy();

  interface JsonEvent {
    /** @return the path where the event occurred within the JSON. */
    String getPath();
  }

  /** An event that was triggered by {@link JsonAdapter#fromJson}. */
  interface JsonReadEvent extends JsonEvent {}

  /** An event that was triggered by {@link JsonAdapter#toJson}. */
  interface JsonWriteEvent extends JsonEvent {}
}
