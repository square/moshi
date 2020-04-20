package com.squareup.moshi;

import javax.annotation.Nullable;
import java.io.IOException;

public abstract class DelegatingJsonReader implements JsonReader {
  public final JsonReader delegate;

  public DelegatingJsonReader(JsonReader delegate) {
    this.delegate = delegate;
  }

  @Override
  public final void setLenient(boolean lenient) {
    delegate.setLenient(lenient);
  }

  @Override
  public final boolean isLenient() {
    return delegate.isLenient();
  }

  @Override
  public final void setFailOnUnknown(boolean failOnUnknown) {
    delegate.setFailOnUnknown(failOnUnknown);
  }

  @Override
  public final boolean failOnUnknown() {
    return delegate.failOnUnknown();
  }

  @Override
  public final void beginArray() throws IOException {
    delegate.beginArray();
  }

  @Override
  public final void endArray() throws IOException {
    delegate.endArray();
  }

  @Override
  public final void beginObject() throws IOException {
    delegate.beginObject();
  }

  @Override
  public final void endObject() throws IOException {
    delegate.endObject();
  }

  @Override
  public final boolean hasNext() throws IOException {
    return delegate.hasNext();
  }

  @Override
  public final Token peek() throws IOException {
    return delegate.peek();
  }

  @Override
  public final String nextName() throws IOException {
    return delegate.nextName();
  }

  @Override
  public final int selectName(Options options) throws IOException {
    return delegate.selectName(options);
  }

  @Override
  public final void skipName() throws IOException {
    delegate.skipName();
  }

  @Override
  public final String nextString() throws IOException {
    return delegate.nextString();
  }

  @Override
  public final int selectString(Options options) throws IOException {
    return delegate.selectString(options);
  }

  @Override
  public final boolean nextBoolean() throws IOException {
    return delegate.nextBoolean();
  }

  @Nullable
  @Override
  public final <T> T nextNull() throws IOException {
    return delegate.nextNull();
  }

  @Override
  public final double nextDouble() throws IOException {
    return delegate.nextDouble();
  }

  @Override
  public final long nextLong() throws IOException {
    return delegate.nextLong();
  }

  @Override
  public final int nextInt() throws IOException {
    return delegate.nextInt();
  }

  @Override
  public final void skipValue() throws IOException {
    delegate.skipValue();
  }

  @Override
  public final void promoteNameToValue() throws IOException {
    delegate.promoteNameToValue();
  }

  @Nullable
  @Override
  public final Object readJsonValue() throws IOException {
    return delegate.readJsonValue();
  }

  @Override
  public final JsonReader peekJson() {
    return delegate.peekJson();
  }

  @Override
  public final String getPath() {
    return delegate.getPath();
  }

  @Override
  public final void close() throws IOException {
    delegate.close();
  }
}
