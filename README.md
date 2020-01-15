Moshi
=====

Moshi is a modern JSON library for Android and Java. It makes it easy to parse JSON into Java
objects:

```java
String json = ...;

Moshi moshi = new Moshi.Builder().build();
JsonAdapter<BlackjackHand> jsonAdapter = moshi.adapter(BlackjackHand.class);

BlackjackHand blackjackHand = jsonAdapter.fromJson(json);
System.out.println(blackjackHand);
```

And it can just as easily serialize Java objects as JSON:

```java
BlackjackHand blackjackHand = new BlackjackHand(
    new Card('6', SPADES),
    Arrays.asList(new Card('4', CLUBS), new Card('A', HEARTS)));

Moshi moshi = new Moshi.Builder().build();
JsonAdapter<BlackjackHand> jsonAdapter = moshi.adapter(BlackjackHand.class);

String json = jsonAdapter.toJson(blackjackHand);
System.out.println(json);
```

### Built-in Type Adapters

Moshi has built-in support for reading and writing Java’s core data types:

 * Primitives (int, float, char...) and their boxed counterparts (Integer, Float, Character...).
 * Arrays, Collections, Lists, Sets, and Maps
 * Strings
 * Enums

It supports your model classes by writing them out field-by-field. In the example above Moshi uses
these classes:

```java
class BlackjackHand {
  public final Card hidden_card;
  public final List<Card> visible_cards;
  ...
}

class Card {
  public final char rank;
  public final Suit suit;
  ...
}

enum Suit {
  CLUBS, DIAMONDS, HEARTS, SPADES;
}
```

to read and write this JSON:

```json
{
  "hidden_card": {
    "rank": "6",
    "suit": "SPADES"
  },
  "visible_cards": [
    {
      "rank": "4",
      "suit": "CLUBS"
    },
    {
      "rank": "A",
      "suit": "HEARTS"
    }
  ]
}
```

The [Javadoc][javadoc] catalogs the complete Moshi API, which we explore below.

### Custom Type Adapters

With Moshi, it’s particularly easy to customize how values are converted to and from JSON. A type
adapter is any class that has methods annotated `@ToJson` and `@FromJson`.

For example, Moshi’s default encoding of a playing card is verbose: the JSON defines the rank and
suit in separate fields: `{"rank":"A","suit":"HEARTS"}`. With a type adapter, we can change the
encoding to something more compact: `"4H"` for the four of hearts or `"JD"` for the jack of
diamonds:

```java
class CardAdapter {
  @ToJson String toJson(Card card) {
    return card.rank + card.suit.name().substring(0, 1);
  }

  @FromJson Card fromJson(String card) {
    if (card.length() != 2) throw new JsonDataException("Unknown card: " + card);

    char rank = card.charAt(0);
    switch (card.charAt(1)) {
      case 'C': return new Card(rank, Suit.CLUBS);
      case 'D': return new Card(rank, Suit.DIAMONDS);
      case 'H': return new Card(rank, Suit.HEARTS);
      case 'S': return new Card(rank, Suit.SPADES);
      default: throw new JsonDataException("unknown suit: " + card);
    }
  }
}
```

Register the type adapter with the `Moshi.Builder` and we’re good to go.

```java
Moshi moshi = new Moshi.Builder()
    .add(new CardAdapter())
    .build();
```

Voilà:

```json
{
  "hidden_card": "6S",
  "visible_cards": [
    "4C",
    "AH"
  ]
}
```

#### Another example

Note that the method annotated with `@FromJson` does not need to take a String as an argument.
Rather it can take input of any type and Moshi will first parse the JSON to an object of that type
and then use the `@FromJson` method to produce the desired final value. Conversely, the method
annotated with `@ToJson` does not have to produce a String.

Assume, for example, that we have to parse a JSON in which the date and time of an event are
represented as two separate strings.

```json
{
  "title": "Blackjack tournament",
  "begin_date": "20151010",
  "begin_time": "17:04"
}
```

We would like to combine these two fields into one string to facilitate the date parsing at a
later point. Also, we would like to have all variable names in CamelCase. Therefore, the `Event`
class we want Moshi to produce like this:

```java
class Event {
  String title;
  String beginDateAndTime;
}
```

Instead of manually parsing the JSON line per line (which we could also do) we can have Moshi do the
transformation automatically. We simply define another class `EventJson` that directly corresponds
to the JSON structure:

```java
class EventJson {
  String title;
  String begin_date;
  String begin_time;
}
```

And another class with the appropriate `@FromJson` and `@ToJson` methods that are telling Moshi how
to convert an `EventJson` to an `Event` and back. Now, whenever we are asking Moshi to parse a JSON
to an `Event` it will first parse it to an `EventJson` as an intermediate step. Conversely, to
serialize an `Event` Moshi will first create an `EventJson` object and then serialize that object as
usual.

```java
class EventJsonAdapter {
  @FromJson Event eventFromJson(EventJson eventJson) {
    Event event = new Event();
    event.title = eventJson.title;
    event.beginDateAndTime = eventJson.begin_date + " " + eventJson.begin_time;
    return event;
  }

  @ToJson EventJson eventToJson(Event event) {
    EventJson json = new EventJson();
    json.title = event.title;
    json.begin_date = event.beginDateAndTime.substring(0, 8);
    json.begin_time = event.beginDateAndTime.substring(9, 14);
    return json;
  }
}
```

Again we register the adapter with Moshi.

```java
Moshi moshi = new Moshi.Builder()
    .add(new EventJsonAdapter())
    .build();
```

We can now use Moshi to parse the JSON directly to an `Event`.

```java
JsonAdapter<Event> jsonAdapter = moshi.adapter(Event.class);
Event event = jsonAdapter.fromJson(json);
```

### Adapter convenience methods

Moshi provides a number of convenience methods for `JsonAdapter` objects:
- `nullSafe()`
- `nonNull()`
- `lenient()`
- `failOnUnknown()`
- `indent()`
- `serializeNulls()`

These factory methods wrap an existing `JsonAdapter` into additional functionality.
For example, if you have an adapter that doesn't support nullable values, you can use `nullSafe()` to make it null safe:

```java
String dateJson = "\"2018-11-26T11:04:19.342668Z\"";
String nullDateJson = "null";

// Hypothetical IsoDateDapter, doesn't support null by default
JsonAdapter<Date> adapter = new IsoDateDapter();

Date date = adapter.fromJson(dateJson);
System.out.println(date); // Mon Nov 26 12:04:19 CET 2018

Date nullDate = adapter.fromJson(nullDateJson);
// Exception, com.squareup.moshi.JsonDataException: Expected a string but was NULL at path $

Date nullDate = adapter.nullSafe().fromJson(nullDateJson);
System.out.println(nullDate); // null
```

In contrast to `nullSafe()` there is `nonNull()` to make an adapter refuse null values. Refer to the Moshi JavaDoc for details on the various methods.

### Parse JSON Arrays

Say we have a JSON string of this structure:

```json
[
  {
    "rank": "4",
    "suit": "CLUBS"
  },
  {
    "rank": "A",
    "suit": "HEARTS"
  }
]
```

We can now use Moshi to parse the JSON string into a `List<Card>`.

```java
String cardsJsonResponse = ...;
Type type = Types.newParameterizedType(List.class, Card.class);
JsonAdapter<List<Card>> adapter = moshi.adapter(type);
List<Card> cards = adapter.fromJson(cardsJsonResponse);
```

### Fails Gracefully

Automatic databinding almost feels like magic. But unlike the black magic that typically accompanies
reflection, Moshi is designed to help you out when things go wrong.

```
JsonDataException: Expected one of [CLUBS, DIAMONDS, HEARTS, SPADES] but was ANCHOR at path $.visible_cards[2].suit
  at com.squareup.moshi.JsonAdapters$11.fromJson(JsonAdapters.java:188)
  at com.squareup.moshi.JsonAdapters$11.fromJson(JsonAdapters.java:180)
	...
```

Moshi always throws a standard `java.io.IOException` if there is an error reading the JSON document,
or if it is malformed. It throws a `JsonDataException` if the JSON document is well-formed, but
doesn’t match the expected format.

### Built on Okio

Moshi uses [Okio][okio] for simple and powerful I/O. It’s a fine complement to [OkHttp][okhttp],
which can share buffer segments for maximum efficiency.

### Borrows from Gson

Moshi uses the same streaming and binding mechanisms as [Gson][gson]. If you’re a Gson user you’ll
find Moshi works similarly. If you try Moshi and don’t love it, you can even migrate to Gson without
much violence!

But the two libraries have a few important differences:

 * **Moshi has fewer built-in type adapters.** For example, you need to configure your own date
   adapter. Most binding libraries will encode whatever you throw at them. Moshi refuses to
   serialize platform types (`java.*`, `javax.*`, and `android.*`) without a user-provided type
   adapter. This is intended to prevent you from accidentally locking yourself to a specific JDK or
   Android release.
 * **Moshi is less configurable.** There’s no field naming strategy, versioning, instance creators,
   or long serialization policy. Instead of naming a field `visibleCards` and using a policy class
   to convert that to `visible_cards`, Moshi wants you to just name the field `visible_cards` as it
   appears in the JSON.
 * **Moshi doesn’t have a `JsonElement` model.** Instead it just uses built-in types like `List` and
   `Map`.
 * **No HTML-safe escaping.** Gson encodes `=` as `\u003d` by default so that it can be safely
   encoded in HTML without additional escaping. Moshi encodes it naturally (as `=`) and assumes that
   the HTML encoder – if there is one – will do its job.

### Custom field names with @Json

Moshi works best when your JSON objects and Java objects have the same structure. But when they
don't, Moshi has annotations to customize data binding.

Use `@Json` to specify how Java fields map to JSON names. This is necessary when the JSON name
contains spaces or other characters that aren’t permitted in Java field names. For example, this
JSON has a field name containing a space:

```json
{
  "username": "jesse",
  "lucky number": 32
}
```

With `@Json` its corresponding Java class is easy:

```java
class Player {
  String username;
  @Json(name = "lucky number") int luckyNumber;

  ...
}
```

Because JSON field names are always defined with their Java fields, Moshi makes it easy to find
fields when navigating between Java and JSON.

### Alternate type adapters with @JsonQualifier

Use `@JsonQualifier` to customize how a type is encoded for some fields without changing its
encoding everywhere. This works similarly to the qualifier annotations in dependency injection
tools like Dagger and Guice.

Here’s a JSON message with two integers and a color:

```json
{
  "width": 1024,
  "height": 768,
  "color": "#ff0000"
}
```

By convention, Android programs also use `int` for colors:

```java
class Rectangle {
  int width;
  int height;
  int color;
}
```

But if we encoded the above Java class as JSON, the color isn't encoded properly!

```json
{
  "width": 1024,
  "height": 768,
  "color": 16711680
}
```

The fix is to define a qualifier annotation, itself annotated `@JsonQualifier`:

```java
@Retention(RUNTIME)
@JsonQualifier
public @interface HexColor {
}
```

Next apply this `@HexColor` annotation to the appropriate field:

```java
class Rectangle {
  int width;
  int height;
  @HexColor int color;
}
```

And finally define a type adapter to handle it:

```java
/** Converts strings like #ff0000 to the corresponding color ints. */
class ColorAdapter {
  @ToJson String toJson(@HexColor int rgb) {
    return String.format("#%06x", rgb);
  }

  @FromJson @HexColor int fromJson(String rgb) {
    return Integer.parseInt(rgb.substring(1), 16);
  }
}
```

Use `@JsonQualifier` when you need different JSON encodings for the same type. Most programs
shouldn’t need this `@JsonQualifier`, but it’s very handy for those that do.

### Omit fields with `transient`

Some models declare fields that shouldn’t be included in JSON. For example, suppose our blackjack
hand has a `total` field with the sum of the cards:

```java
public final class BlackjackHand {
  private int total;

  ...
}
```

By default, all fields are emitted when encoding JSON, and all fields are accepted when decoding
JSON. Prevent a field from being included by adding Java’s `transient` keyword:

```java
public final class BlackjackHand {
  private transient int total;

  ...
}
```

Transient fields are omitted when writing JSON. When reading JSON, the field is skipped even if the
JSON contains a value for the field. Instead it will get a default value.


### Default Values & Constructors

When reading JSON that is missing a field, Moshi relies on the Java or Android runtime to assign
the field’s value. Which value it uses depends on whether the class has a no-arguments constructor.

If the class has a no-arguments constructor, Moshi will call that constructor and whatever value
it assigns will be used. For example, because this class has a no-arguments constructor the `total`
field is initialized to `-1`.

```java
public final class BlackjackHand {
  private int total = -1;
  ...

  private BlackjackHand() {
  }

  public BlackjackHand(Card hidden_card, List<Card> visible_cards) {
    ...
  }
}
```

If the class doesn’t have a no-arguments constructor, Moshi can’t assign the field’s default value,
**even if it’s specified in the field declaration**. Instead, the field’s default is always `0` for
numbers, `false` for booleans, and `null` for references. In this example, the default value of
`total` is `0`!

```java
public final class BlackjackHand {
  private int total = -1;
  ...

  public BlackjackHand(Card hidden_card, List<Card> visible_cards) {
    ...
  }
}
```

This is surprising and is a potential source of bugs! For this reason consider defining a
no-arguments constructor in classes that you use with Moshi, using `@SuppressWarnings("unused")` to
prevent it from being inadvertently deleted later:

```java
public final class BlackjackHand {
  private int total = -1;
  ...

  @SuppressWarnings("unused") // Moshi uses this!
  private BlackjackHand() {
  }

  public BlackjackHand(Card hidden_card, List<Card> visible_cards) {
    ...
  }
}
```

Kotlin
------

Moshi is a great JSON library for Kotlin. It understands Kotlin’s non-nullable types and default
parameter values. When you use Kotlin with Moshi you may use reflection, codegen, or both.

#### Reflection

The reflection adapter uses Kotlin’s reflection library to convert your Kotlin classes to and from
JSON. Enable it by adding the `KotlinJsonAdapterFactory` to your `Moshi.Builder`:

```kotlin
val moshi = Moshi.Builder()
    // ... add your own JsonAdapters and factories ...
    .add(KotlinJsonAdapterFactory())
    .build()
```

Moshi’s adapters are ordered by precedence, so you always want to add the Kotlin adapter after your
own custom adapters. Otherwise the `KotlinJsonAdapterFactory` will take precedence and your custom
adapters will not be called.

The reflection adapter requires the following additional dependency:

```xml
<dependency>
  <groupId>com.squareup.moshi</groupId>
  <artifactId>moshi-kotlin</artifactId>
  <version>1.9.2</version>
</dependency>
```

```kotlin
implementation("com.squareup.moshi:moshi-kotlin:1.9.2")
```

Note that the reflection adapter transitively depends on the `kotlin-reflect` library which is a
2.5 MiB .jar file.

#### Codegen

Moshi’s Kotlin codegen support is an annotation processor. It generates a small and fast adapter for
each of your Kotlin classes at compile time. Enable it by annotating each class that you want to
encode as JSON:

```kotlin
@JsonClass(generateAdapter = true)
data class BlackjackHand(
        val hidden_card: Card,
        val visible_cards: List<Card>
)
```

The codegen adapter requires that your Kotlin types and their properties be either `internal` or
`public` (this is Kotlin’s default visibility).

Kotlin codegen has no additional runtime dependency. You’ll need to [enable kapt][kapt] and then
add the following to your build to enable the annotation processor:

```xml
<dependency>
  <groupId>com.squareup.moshi</groupId>
  <artifactId>moshi-kotlin-codegen</artifactId>
  <version>1.9.2</version>
  <scope>provided</scope>
</dependency>
```

```kotlin
kapt("com.squareup.moshi:moshi-kotlin-codegen:1.9.2")
```

You must also have the `kotlin-stdlib` dependency on the classpath during compilation in order for
the compiled code to have the required metadata annotations that Moshi's processor looks for.

#### Limitations

If your Kotlin class has a superclass, it must also be a Kotlin class. Neither reflection or codegen
support Kotlin types with Java supertypes or Java types with Kotlin supertypes. If you need to
convert such classes to JSON you must create a custom type adapter.

The JSON encoding of Kotlin types is the same whether using reflection or codegen. Prefer codegen
for better performance and to avoid the `kotlin-reflect` dependency; prefer reflection to convert
both private and protected properties. If you have configured both, generated adapters will be used
on types that are annotated `@JsonClass(generateAdapter = true)`.

Download
--------

Download [the latest JAR][dl] or depend via Maven:

```xml
<dependency>
  <groupId>com.squareup.moshi</groupId>
  <artifactId>moshi</artifactId>
  <version>1.9.2</version>
</dependency>
```
or Gradle:
```kotlin
implementation("com.squareup.moshi:moshi:1.9.2")
```

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].


R8 / ProGuard
--------

If you are using R8 or ProGuard add the options from [this file](https://github.com/square/moshi/blob/master/moshi/src/main/resources/META-INF/proguard/moshi.pro). If using Android, this requires Android Gradle Plugin 3.2.0+.

The `moshi-kotlin` artifact additionally requires the options from [this file](https://github.com/square/moshi/blob/master/kotlin/reflect/src/main/resources/META-INF/proguard/moshi-kotlin.pro)

You might also need rules for Okio which is a dependency of this library.

License
--------

    Copyright 2015 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


 [dl]: https://search.maven.org/classic/remote_content?g=com.squareup.moshi&a=moshi&v=LATEST
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/com/squareup/moshi/
 [okio]: https://github.com/square/okio/
 [okhttp]: https://github.com/square/okhttp/
 [gson]: https://github.com/google/gson/
 [javadoc]: https://square.github.io/moshi/1.x/moshi/
 [kapt]: https://kotlinlang.org/docs/reference/kapt.html
