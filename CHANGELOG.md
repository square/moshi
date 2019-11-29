Change Log
==========

## Version 1.9.2

_2019-11-17_

 * Fix: Generate correct adapters for several special cases including reified inline types, public
   classes enclosed in internal classes, deprecated types with `-Werror`, primitives in type
   parameters, nullables in type parameters, and type aliases in type parameters.


## Version 1.9.1

_2019-10-30_

 * Fix: "abstract function ... cannot have code" code gen crash when parsing Kotlin metadata.
 * Fix: Generate code to support constructors with more than 32 parameters. The 1.9.0 release had
   a regression where classes with 33+ parameters would crash upon decoding.
 * Fix: Generate code to support more constructor cases, such as classes with non-property
   parameters and classes with multiple constructors.
 * Fix: Generate code to handle type aliases in type parameters.


## Version 1.9.0

_2019-10-29_

 * **This release requires kotlin-reflect or moshi-kotlin-codegen for all Kotlin classes.** 
 
   Previously Moshi wouldn't differentiate between Kotlin classes and Java classes if Kotlin was
   not configured. This caused bad runtime behavior such as putting null into non-nullable fields!
   If you attempt to create an adapter for a Kotlin type, Moshi will throw an
   `IllegalArgumentException`.
   
   Fix this with either the reflection adapter:
   
   ```kotlin
   val moshi = Moshi.Builder()
       // ... add your own JsonAdapters and factories ...
       .add(KotlinJsonAdapterFactory())
       .build()
   ```
   
   Or the codegen annotation processor:

   ```kotlin
   @JsonClass(generateAdapter = true)
   data class BlackjackHand(
           val hidden_card: Card,
           val visible_cards: List<Card>
   )
   ```
   
   The [Kotlin documentation][moshi_kotlin_docs] explains the required build configuration changes.

 * New: Change how Moshi's generated adapters call constructors. Previous generated code used a 
   combination of the constructor and `copy()` method to set properties that have default values.
   With this update we call the same synthetic constructor that Kotlin uses. This is less surprising 
   though it requires us to generate some tricky code.
 * New: Make `Rfc3339DateJsonAdapter` null-safe. Previously Moshi would refuse to decode null dates.
   Restore that behavior by explicitly forbidding nulls with `Rfc3339DateJsonAdapter().nonNull()`.
 * New: Require Kotlin 1.3.50 or newer.
 * New: `JsonWriter.valueSink()` streams JSON-formatted data inline. Use this to do basic includes
   of raw JSON within a streamed document.
 * New: Support Gradle incremental processing in code gen.
 * New: Improve error messages. This includes better errors when field names and JSON names
   disagree, and when failing on an unknown field.
 * New: Support default values in `PolymorphicJsonAdapterFactory`.
 * New: Permit multiple labels for each subtype in `PolymorphicJsonAdapterFactory`. The first label
   is used when writing an object to JSON.
 * New: Forbid automatic encoding of platform classes in `kotlinx`. As with `java.*`, `android.*`,
   and `kotlin.*` Moshi wants you to specify how to encode platform types.
 * New: `@JsonClass(generator=...)` makes it possible for third-party libraries to provide generated
   adapters when Moshi's default adapters are insufficient.
 * Fix: Simplify wildcard types like `List<? extends Number>` to their base types `List<Number>`
   when finding type adapters. This is especially useful with Kotlin where wildcards may be added
   automatically.
 * Fix: Use the correct name when the `@Json` annotation uses field targeting like `@field:Json`.
 * Fix: Support multiple transient properties in `KotlinJsonAdapter`.
 * Fix: Don't explode attempting to resolve self-referential type variables like in
   `Comparable<T extends Comparable<T>>`.
 * Fix: Don't infinite loop on `skipValue()` at the end an object or array. Also disallow calling
   `skipValue()` at the end of a document.


## Version 1.8.0

_2018-11-09_

 * New: Support JSON objects that include type information in the JSON. The new
   `PolymorphicJsonAdapterFactory` writes a type field when encoding, and reads it when decoding.
 * New: Fall back to the reflection-based `KotlinJsonAdapterFactory` if it is enabled and a
   generated adapter is not found. This makes it possible to use reflection-based JSON adapters in
   development (so you don't have to wait for code to be regenerated on every build) and generated
   JSON adapters in production (so you don't need the kotlin-reflect library).
 * New: The `peekJson()` method on `JsonReader` let you read ahead on a JSON stream without
   consuming it. This builds on Okio's new `Buffer.peek()` API.
 * New: The `beginFlatten()` and `endFlatten()` methods on `JsonWriter` suppress unwanted nesting
   when composing adapters. Previously it was necessary to flatten objects in memory before writing.
 * New: Upgrade to Okio 1.16.0. We don't yet require Kotlin-friendly Okio 2.1 but Moshi works fine
   with that release.

   ```kotlin
   implementation("com.squareup.okio:okio:1.16.0")
   ```

 * Fix: Don't return partially-constructed adapters when using a Moshi instance concurrently.
 * Fix: Eliminate warnings and errors in generated `.kt` triggered by type variance, primitive
   types, and required values.
 * Fix: Improve the supplied rules (`moshi.pro`) to better retain symbols used by Moshi. We
   recommend R8 when shrinking code.
 * Fix: Remove code generation companion objects. This API was neither complete nor necessary.


## Version 1.7.0

_2018-09-24_

 * New: `EnumJsonAdapter` makes it easy to specify a fallback value for unknown enum constants.
   By default Moshi throws an `JsonDataException` if it reads an unknown enum constant. With this
   you can specify a fallback value or null.

   ```java
   new Moshi.Builder()
       .add(EnumJsonAdapter.create(IsoCurrency.class)
           .withUnknownFallback(IsoCurrency.USD))
       .build();
   ```

   Note that this adapter is in the optional `moshi-adapters` module.

   ```groovy
   implementation 'com.squareup.moshi:moshi-adapters:1.7.0'
   ```

 * New: Embed R8/ProGuard rules in the `.jar` file.
 * New: Use `@CheckReturnValue` in more places. We hope this will encourage you to use `skipName()`
   instead of `nextName()` for better performance!
 * New: Forbid automatic encoding of platform classes in `androidx`. As with `java.*`, `android.*`,
   and `kotlin.*` Moshi wants you to specify how to encode platform types.
 * New: Improve error reporting when creating an adapter fails.
 * New: Upgrade to Okio 1.15.0. We don't yet require Kotlin-friendly Okio 2.x but Moshi works fine
   with that release.

   ```groovy
   implementation 'com.squareup.okio:okio:1.15.0'
   ```

 * Fix: Return false from `JsonReader.hasNext()` at document's end.
 * Fix: Improve code gen to handle several broken cases. Our generated adapters had problems with
   nulls, nested parameterized types, private transient properties, generic type aliases, fields
   with dollar signs in their names, and named companion objects.


## Version 1.6.0

_2018-05-14_

 * **Moshi now supports codegen for Kotlin.** We've added a new annotation processor that generates
   a small and fast JSON adapter for your Kotlin types. It can be used on its own or with the
   existing `KotlinJsonAdapterFactory` adapter.

 * **Moshi now resolves all type parameters.** Previously Moshi wouldn't resolve type parameters on
   top-level classes.

 * New: Support up to 255 levels of nesting when reading and writing JSON. Previously Moshi would
   reject JSON input that contained more than 32 levels of nesting.
 * New: Write encoded JSON to a stream with `JsonWriter.value(BufferedSource)`. Use this to emit a
   JSON value without decoding it first.
 * New: `JsonAdapter.nonNull()` returns a new JSON adapter that forbids explicit nulls in the JSON
   body. Use this to detect and fail eagerly on unwanted nulls.
 * New: `JsonReader.skipName()` is like `nextName()` but it avoids allocating when a name is
   unknown. Use this when `JsonReader.selectName()` returns -1.
 * New: Automatic module name of `com.squareup.moshi` for use with the Java Platform Module System.
   This moves moshi-adapters into its own `.adapters` package and forwards the existing adapter. It
   moves the moshi-kotlin into its own `.kotlin.reflect` package and forwards the existing adapter.
 * New: Upgrade to Okio 1.14.0.

    ```xml
     <dependency>
       <groupId>com.squareup.okio</groupId>
       <artifactId>okio</artifactId>
       <version>1.14.0</version>
     </dependency>

     com.squareup.okio:okio:1.14.0
    ```

 * Fix: Fail fast if there are trailing non-whitespace characters in the JSON passed to
   `JsonAdapter.fromJson(String)`. Previously such data was ignored!
 * Fix: Fail fast when Kotlin types are abstract, inner, or object instances.
 * Fix: Fail fast if `name()` is called out of sequence.
 * Fix: Handle asymmetric `Type.equals()` methods when doing type comparisons. Previously it was
   possible that a registered type adapter would not be used because its `Type.equals()` method was
   not consistent with a user-provided type.
 * Fix: `JsonValueReader.selectString()` now returns -1 for non-strings instead of throwing.
 * Fix: Permit reading numbers as strings when the `JsonReader` was created from a JSON value. This
   was always supported when reading from a stream but broken when reading from a decoded value.
 * Fix: Delegate to user-adapters in the adapter for Object.class. Previously when Moshi encountered
   an opaque Object it would only use the built-in adapters. With this change user-installed
   adapters for types like `String` will always be honored.

## Version 1.5.0

_2017-05-14_

 *  **Moshi now uses `@Nullable` to annotate all possibly-null values.** We've added a compile-time
    dependency on the JSR 305 annotations. This is a [provided][maven_provided] dependency and does
    not need to be included in your build configuration, `.jar` file, or `.apk`. We use
    `@ParametersAreNonnullByDefault` and all parameters and return types are never null unless
    explicitly annotated `@Nullable`.

 *  **Warning: Moshi APIs in this update are source-incompatible for Kotlin callers.** Nullability
    was previously ambiguous and lenient but now the compiler will enforce strict null checks.

 *  **Kotlin models are now supported via the `moshi-kotlin` extension.** `KotlinJsonAdapterFactory`
    is the best way to use Kotlin with Moshi. It honors default values and is null-safe. Kotlin
    users that don't use this factory should write custom adapters for their JSON types. Otherwise
    Moshi cannot properly initialize delegated properties of the objects it decodes.

 *  New: Upgrade to Okio 1.13.0.

    ```xml
     <dependency>
       <groupId>com.squareup.okio</groupId>
       <artifactId>okio</artifactId>
       <version>1.13.0</version>
     </dependency>

     com.squareup.okio:okio:1.13.0
    ```

 *  New: You may now declare delegates in `@ToJson` and `@FromJson` methods. If one of the arguments
    to the method is a `JsonAdapter` of the same type, that will be the next eligible adapter for
    that type. This may be useful for composing adapters.
 *  New: `Types.equals(Type, Type)` makes it easier to compare types in `JsonAdapter.Factory`.
 *  Fix: Retain the sign on negative zero.


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
    adapters for enclosed parameterized types was not possible.
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
 *  New: `JsonAdapter.failOnUnknown()` returns a new JSON adapter that throws if an unknown value is
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
 [maven_provided]: https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html
 [moshi_kotlin_docs]: https://github.com/square/moshi/blob/master/README.md#kotlin
