Change Log
==========

## Version 0.9.0

_2015-06-16_

 * Databinding for primitive types, strings, enums, arrays, collections, and maps.
 * Databinding for plain old Java objects.
 * [JSONPath](http://goessner.net/articles/JsonPath/) support for both `JsonReader` and
   `JsonWriter`.
 * Throw `JsonDataException` when there’s a data binding problem.
 * Adapter methods: `@ToJson` and `@FromJson`.
 * Qualifier annotations: `@JsonQualifier` to permit different type adapters for the same Java type.
 * Imported code from Gson: `JsonReader`, `JsonWriter`. Also some internal classes:
   `LinkedHashTreeMap` for hash-collision avoidance and `Types` for typesafe databinding.
