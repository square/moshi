Change Log
==========

## Version 1.4.0

_2017-02-04_

Moshi 1.4 is a major release that adds _JSON values_ as a core part of the library. We consider any
Java object comprised of maps, lists, strings, numbers, booleans and nulls to be a JSON value. These
are equivalent to parsed JSON objects in JavaScript, [Gson][gson]’s `JsonElement`, and
[Jackson][jackson]’s `JsonNode`. Unlike Jackson and Gson, Moshi just uses Java’s built-in types for
its values:

<table>
<tr><th></th><th>JSON type</th><th>Java type</th></tr>
<tr><td>{...}</td><td>Object</td><td>Map&lt;String, Object&gt;</th></tr>
<tr><td>[...]</td><td>Array</td><td>List&lt;Object&gt;</th></tr>
<tr><td>"abc"</td><td>String</td><td>String</th></tr>
<tr><td>123</td><td>Number</td><td>Double, Long, or BigDecimal</th></tr>
<tr><td>true</td><td>Boolean</td><td>Boolean</th></tr>
<tr><td>null</td><td>null</td><td>null</th></tr>
</table>

Moshi's new API `JsonAdapter.toJsonValue()` converts your application classes to JSON values
comprised of the above types. Symmetrically, `JsonAdapter.fromJsonValue()` converts JSON values to
your application classes.

 *  New: `JsonAdapter.toJsonValue()` and `fromJsonValue()`.
 *  New: `JsonReader.readJsonValue()` reads a JSON value from a stream.
 *  New: `Moshi.adapter(Type, Class<? extends Annotation>)` lets you look up the adapter for a
    qualified type.
 *  New: `JsonAdapter.serializeNulls()` and `indent()` return JSON adapters that customize the
    format of the encoded JSON.
 *  New: `JsonReader.selectName()` and `selectString()` optimize decoding JSON with known names and
    values.
 *  New: `Types.nextAnnotations()` reduces the amount of code required to implement a custom
    `JsonAdapter.Factory`.
 *  Fix: Don't fail on large longs that have a fractional component like `9223372036854775806.0`.

## Version 1.3.1

_2016-10-21_

 *  Fix: Don't incorrectly report invalid input when a slash character is escaped. When we tightened
    our invalid escape handling we missed the one character that is valid both escaped `\/` and
    unescaped `/`.

## Version 1.3.0

_2016-10-15_

 *  New: Permit `@ToJson` and `@FromJson` methods to take any number of `JsonAdapter` parameters to
    delegate to. This is supported for `@ToJson` methods that take a `JsonWriter` and `@FromJson`
    methods that take a `JsonReader`.
 *  New: Throw `JsonEncodingException` when the incoming data is not valid JSON. Use this to
    differentiate data format problems from connectivity problems.
 *  New: Upgrade to Okio 1.11.0.

    ```xml
    <dependency>
      <groupId>com.squareup.okio</groupId>
      <artifactId>okio</artifactId>
      <version>1.11.0</version>
    </dependency>
    ```

 *  New: Omit Kotlin (`kotlin.*`) and Scala (`scala.*`) platform types when encoding objects using
    their fields. This should make it easier to avoid unexpected dependencies on platform versions.
 *  Fix: Explicitly limit reading and writing to 31 levels of nested structure. Previously no
    specific limit was enforced, but deeply nested documents would fail with either an
    `ArrayIndexOutOfBoundsException` due to a bug in `JsonWriter`'s path management, or a
    `StackOverflowError` due to excessive recursion.
 *  Fix: Require enclosed types to specify their enclosing type with
    `Types.newParameterizedTypeWithOwner()`. Previously this API did not exist and looking up
    adapters for enclosed parameterized types as not possible.
 *  Fix: Fail on invalid escapes. Previously any character could be escaped. With this fix only
    characters permitted to be escaped may be escaped. Use `JsonReader.setLenient(true)` to read
    JSON documents that escape characters that should not be escaped.

## Version 1.2.0

_2016-05-28_

 *  New: Take advantage of Okio's new `Options` feature when reading field names and enum values.
    This has a significant impact on performance. We measured parsing performance improve from 89k
    ops/sec to 140k ops/sec on one benchmark on one machine.
 *  New: Upgrade to Okio 1.8.0.

    ```xml
    <dependency>
      <groupId>com.squareup.okio</groupId>
      <artifactId>okio</artifactId>
      <version>1.8.0</version>
    </dependency>
    ```

 *  New: Support types that lack no-argument constructors objects on Android releases prior to
    Gingerbread.
 *  Fix: Add writer value overload for boxed booleans. Autoboxing resolves boxed longs and doubles
    to `value(Number)`, but a boxed boolean would otherwise resolve to value(boolean) with an
    implicit call to booleanValue() which has the potential to throw NPEs.
 *  Fix: Be more aggressive about canonicalizing types.

## Version 1.1.0

_2016-01-19_

 *  New: Support [RFC 7159][rfc_7159], the latest JSON specification. This removes the constraint
    that the root value must be an array or an object. It may now take any value: array, object,
    string, number, boolean, or null. Previously this was only permitted if the adapter was
    configured to be lenient.
 *  New: Enum constants may be annotated with `@Json` to customize their encoded value.
 *  New: Create new builder from Moshi instance with `Moshi.newBuilder()`.
 *  New: `Types.getRawType()` and `Types.collectionElementType()` APIs to assist in defining generic
    type adapter factories.

## Version 1.0.0

_2015-09-27_

 *  **API Change**: Replaced `new JsonReader()` with `JsonReader.of()` and `new JsonWriter()` with
    `JsonWriter.of()`. If your code calls either of these constructors it will need to be updated to
    call the static factory method instead.
 *  **API Change**: Don’t throw `IOException` on `JsonAdapter.toJson(T)`. Code that calls this
    method may need to be fixed to no longer catch an impossible `IOException`.
 *  Fix: the JSON adapter for `Object` no longer fails when encountering `null` in the stream.
 *  New: `@Json` annotation can customize a field's name. This is particularly handy for fields
    whose names are Java keywords, like `default` or `public`.
 *  New: `Rfc3339DateJsonAdapter` converts between a `java.util.Date` and a string formatted with
    RFC 3339 (like `2015-09-26T18:23:50.250Z`). This class is in the new `moshi-adapters`
    subproject. You will need to register this adapter if you want this date formatting behavior.
    See it in action in the [dates example][dates_example].
 *  New: `Moshi.adapter()` keeps a cache of all created adapters. For best efficiency, application
    code should keep a reference to required adapters in a field.
 *  New: The `Types` factory class makes it possible to compose types like `List<Card>` or
    `Map<String, Integer>`. This is useful to look up JSON adapters for parameterized types.
 *  New: `JsonAdapter.failOnUnknown()` returns a new JSON adapter that throws if an unknonw value is
    encountered on the stream. Use this in development and debug builds to detect typos in field
    names. This feature shouldn’t be used in production because it makes migrations very difficult.

## Version 0.9.0

_2015-06-16_

 *  Databinding for primitive types, strings, enums, arrays, collections, and maps.
 *  Databinding for plain old Java objects.
 *  [JSONPath](http://goessner.net/articles/JsonPath/) support for both `JsonReader` and
    `JsonWriter`.
 *  Throw `JsonDataException` when there’s a data binding problem.
 *  Adapter methods: `@ToJson` and `@FromJson`.
 *  Qualifier annotations: `@JsonQualifier` to permit different type adapters for the same Java
    type.
 *  Imported code from Gson: `JsonReader`, `JsonWriter`. Also some internal classes:
    `LinkedHashTreeMap` for hash-collision avoidance and `Types` for typesafe databinding.


 [dates_example]: https://github.com/square/moshi/blob/master/examples/src/main/java/com/squareup/moshi/recipes/ReadAndWriteRfc3339Dates.java
 [rfc_7159]: https://tools.ietf.org/html/rfc7159
 [gson]: https://github.com/google/gson
 [jackson]: http://wiki.fasterxml.com/JacksonHome
