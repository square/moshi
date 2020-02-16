package com.squareup.moshi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class JsonAdapterStateTest {

    private Moshi moshi;

    @BeforeEach
    public void initializeBeforeEach() {
        this.moshi = new Moshi.Builder().build();
    }

    @Test
    public void nonNullJsonAdapterTest() throws IOException {
        String json = "null";

        JsonAdapter<Person> adapter = moshi.adapter(Person.class);
        boolean exceptionThrown = false;
        try {
            adapter.nonNull().fromJson(json);
        } catch (JsonDataException exception) {
            exceptionThrown = true;
        }

        assertTrue(exceptionThrown);
    }

    @Test
    public void nullSafeJsonAdapterTest() throws IOException {
        String json = "null";

        JsonAdapter<Person> adapter = moshi.adapter(Person.class);
        Person generatedPerson = adapter.nullSafe().fromJson(json);

        assertNull(generatedPerson);
    }

    @Test
    public void nullSafeToNullSafeJsonAdapterTest() throws IOException {
        String json = "null";

        JsonAdapter<Person> adapter = moshi.adapter(Person.class).nullSafe();

        assertNull(adapter.fromJson(json));
        assertNull(adapter.nullSafe().fromJson(json));
    }

    @Test
    public void nullSafeToNonNullJsonAdapterTest() throws IOException {
        String json = "null";

        JsonAdapter<Person> adapter = moshi.adapter(Person.class).nullSafe();

        assertNull(adapter.fromJson(json));
        boolean exceptionThrown = false;
        try {
            adapter.nonNull().fromJson(json);
        } catch (JsonDataException exception) {
            exceptionThrown = true;
        }

        assertTrue(exceptionThrown);
    }

    @Test
    public void lenientIsTrueTest() throws IOException {
        String json = "{\"name\": \"Rafael\", \"age\": 24, \"money\": Infinity}";

        JsonAdapter<Person> adapter = moshi.adapter(Person.class).lenient();

        Person testPerson = adapter.fromJson(json);
        assertThat(testPerson.money).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    public void lenientToLenientIsTrueTest() throws IOException {
        String json = "{\"name\": \"Rafael\", \"age\": 24, \"money\": Infinity}";

        JsonAdapter<Person> adapter = moshi.adapter(Person.class).lenient();

        Person testPerson = adapter.lenient().fromJson(json);
        assertThat(testPerson.money).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    public void notLenientTest() throws IOException {
        String json = "{\"name\": \"Rafael\", \"age\": 24, \"money\": Infinity}";

        JsonAdapter<Person> adapter = moshi.adapter(Person.class);
        boolean exceptionThrown = false;

        try {
            adapter.fromJson(json);
        }  catch (JsonEncodingException expected) {
            exceptionThrown = true;
        }
        assertThat(exceptionThrown);
    }

    @Test
    public void LenientToNotLenientTest() throws IOException {
        String json = "{\"name\": \"Rafael\", \"age\": 24, \"money\": Infinity}";

        JsonAdapter<Person> adapter = moshi.adapter(Person.class).lenient();

        Person testPerson = adapter.fromJson(json);



        assertThat(testPerson.money).isEqualTo(Double.POSITIVE_INFINITY);
    }

    public void notNullSerializableJsonAdapterTest() throws IOException {
        JsonAdapter<Person> adapter = moshi.adapter(Person.class);
        Person samplePerson = new Person(null, null, null);

        String generatedJson = adapter.toJson(samplePerson);

        assertEquals(generatedJson, "{}");
    }

    @DynamicTest
    public void nullSerializableJsonAdapterTest() throws IOException {
        Person samplePerson = new Person(null, null, null);

        String sampleJson = "{\"age\":null,\"money\":null,\"name\":null}";
        JsonAdapter<Person> adapter = moshi.adapter(Person.class).serializeNulls();
        String generatedJson = adapter.toJson(samplePerson);

        assertEquals(generatedJson, sampleJson);
    }

    @Test
    public void nullSerializableToNullSerializableJsonAdapterTest() throws IOException {
        Person samplePerson = new Person(null, null, null);

        String sampleJson = "{\"age\":null,\"money\":null,\"name\":null}";
        JsonAdapter<Person> adapter = moshi.adapter(Person.class).serializeNulls();

        assertEquals(adapter.toJson(samplePerson), sampleJson);
        assertEquals(adapter.serializeNulls().toJson(samplePerson), sampleJson);
    }

    @Test
    public void failOnUnknownJsonAdapterTest() throws Exception {

        String sampleJson = "{\"name\": \"Rafael\", \"ag\": 24, \"money\": Infinity}";

        JsonAdapter<Person> adapter = moshi.adapter(Person.class);
        boolean assertOccurred = false;

        try{
            Person generatedPerson = adapter.failOnUnknown().fromJson(sampleJson);
        }
        catch(JsonDataException e) {
            assertOccurred = true;
        }
        assertTrue(assertOccurred);
    }

    @Test
    public void failOnUnknownToFailOnUnknownJsonAdapterTest() throws Exeption {
        String sampleJson = "{\"name\": \"Rafael\", \"ag\": 24, \"money\": Infinity}";

        JsonAdapter<Person> adapter = moshi.adapter(Person.class).failOnUnknown();

        boolean assertOccurred = false;

        try{
            Person generatedPerson = adapter.failOnUnknown().fromJson(sampleJson);
        }
        catch(JsonDataException e) {
            assertOccurred = true;
        }
        assertTrue(assertOccurred);
    }

    @Test
    public void notFailOnUnknownTest() {
        String sampleJson = "{\"name\": \"Rafael\", \"ag\": 24, \"money\": Infinity}";

        JsonAdapter<Person> adapter = moshi.adapter(Person.class);

        boolean assertOccurred = false;

        try{
            Person generatedPerson = adapter.fromJson(sampleJson);
        }
        catch(JsonDataException e) {
            assertOccurred = true;
        }
        assertFalse(assertOccurred);
    }

    public static class Person {
        String name;
        Integer age;
        Double money;

        public Person(String name, Integer age, Double money) {
            this.name = name;
            this.age = age;
            this.money = money;
        }
    }

}
