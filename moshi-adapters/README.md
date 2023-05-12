Adapters
===================

Prebuilt Moshi `JsonAdapter`s for various things, such as `Rfc3339DateJsonAdapter` for parsing `java.util.Date`s

To use, supply an instance of your desired converter when building your `Moshi` instance.

```java
Moshi moshi = new Moshi.Builder()
    .add(Date.class, new Rfc3339DateJsonAdapter())
    //etc
    .build();
```

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
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/com/squareup/moshi/moshi-adapters/
