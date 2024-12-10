Adapters
===================

Prebuilt Moshi `JsonAdapter`s for various purposes, such as the `Rfc3339DateJsonAdapter` for parsing `java.util.Date` objects in the RFC 3339 format.

### How to Use
To use an adapter, supply an instance of your desired converter when building your `Moshi` instance:

```java
import com.squareup.moshi.Moshi;
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter;
import java.util.Date;

Moshi moshi = new Moshi.Builder()
    .add(Date.class, new Rfc3339DateJsonAdapter())
    .build();
```

// Example of valid JSON parsing
String json = "{\"date\": \"2024-11-24T15:30:00Z\"}";
Date date = moshi.adapter(Date.class).fromJson(json);
System.out.println("Parsed date: " + date);

// Example of handling invalid JSON
try {
    String invalidJson = "{\"date\": \"invalid-date\"}";
    Date invalidDate = moshi.adapter(Date.class).fromJson(invalidJson);
} catch (Exception e) {
    System.err.println("Failed to parse date: " + e.getMessage());
}

Download
--------

Download [the latest JAR][1] or grab via [Maven][2]:
```xml
<dependency>
  <groupId>com.squareup.moshi</groupId>
  <artifactId>moshi-adapters</artifactId>
  <version>latest.version</version>
</dependency>
```
or [Gradle][2]:
```groovy
implementation 'com.squareup.moshi:moshi-adapters:latest.version'
```

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].



 [1]: https://search.maven.org/remote_content?g=com.squareup.moshi&a=moshi-adapters&v=LATEST
 [2]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.squareup.moshi%22%20a%3A%22moshi-adapters%22
 [snap]: https://s01.oss.sonatype.org/content/repositories/snapshots/com/squareup/moshi/moshi-adapters/
