package com.squareup.moshi;

import com.squareup.moshi.internal.Util;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;

public class UtilTest {

    @Test
    public void testNullDifferentName() {
        String jsonName = "jsonName";
        String propertyName = "propertyName";
        JsonReader jsonReader = TestUtil.newReader("null");

        JsonDataException jsonDataException = Util.unexpectedNull(propertyName, jsonName, jsonReader);
        assertEquals(jsonDataException.getMessage(), "Non-null value 'propertyName' (JSON name 'jsonName') was null at $");
    }

    @Test
    public void testNullSameName() {
        String jsonName = "sameName";
        String propertyName = "sameName";
        JsonReader jsonReader = TestUtil.newReader("null");

        JsonDataException jsonDataException = Util.unexpectedNull(propertyName, jsonName, jsonReader);
        assertEquals(jsonDataException.getMessage(), "Non-null value 'sameName' was null at $");
    }

    @Test
    public void testMissingProperty() {
        String jsonName = "jsonName";
        String propertyName = "";
        JsonReader jsonReader = TestUtil.newReader("null");

        JsonDataException jsonDataException = Util.missingProperty(propertyName, jsonName, jsonReader);
        assertEquals(jsonDataException.getMessage(), "Required value '' (JSON name 'jsonName') missing at $");
    }

    @Test
    public void testMissingPropertyName() {
        String jsonName = "jsonName";
        String propertyName = "jsonName";
        JsonReader jsonReader = TestUtil.newReader("null");

        JsonDataException jsonDataException = Util.missingProperty(propertyName, jsonName, jsonReader);
        assertEquals(jsonDataException.getMessage(), "Required value 'jsonName' missing at $");
    }

}

