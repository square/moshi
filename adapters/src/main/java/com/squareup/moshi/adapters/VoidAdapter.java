package com.squareup.moshi.adapters;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * An adapter for {@link Void}.
 *
 * @author Jin Kwon &lt;onacit_at_gmail.com&gt;
 */
public final class VoidAdapter extends JsonAdapter<Void> {

    /**
     * Adapts from JSON using specified JSON reader. The {@code fromJson}
     * method of {@code VoidAdapter} class invokes
     * {@link JsonReader#skipValue()} on specified {@code reader} and always
     * returns {@code null}.
     *
     * @param reader the JSON reader.
     * @return {@code null}.
     * @throws IOException if an I/O error occurs.
     * @see JsonReader#skipValue()
     */
    @Nullable
    @Override
    public Void fromJson(final JsonReader reader) throws IOException {
        reader.skipValue();
        return null;
    }

    /**
     * Adapts specified value to JSON using specified JSON writer. The
     * {@code toJson} method of {@code VoidAdapter} invokes
     * {@link JsonWriter#nullValue()} on specified {@code writer} and returns.
     *
     * @param writer the JSON writer.
     * @param value  the value to adapt.
     * @throws IOException if an I/O error occurs.
     * @see JsonWriter#nullValue()
     */
    @Override
    public void toJson(final JsonWriter writer, final @Nullable Void value)
            throws IOException {
        writer.nullValue();
    }
}
