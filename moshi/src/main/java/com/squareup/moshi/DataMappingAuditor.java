package com.squareup.moshi;

import java.util.List;

/**
 * Keeps track of any data mapping mismatches that occur during deserialization.
 */
public interface DataMappingAuditor {
  /**
   * Track an unknown enum value.
   */
  void addUnknownEnum(UnknownEnum unknownEnum);

  /**
   * Returns a list of any enum data mismatch events that occurred during deserialization.
   */
  List<UnknownEnum> getUnknownEnums();

  /**
   * Metadata associated with an enum data mismatch event that occurred during deserialization.
   */
  final class UnknownEnum {
    public final String path;
    public final String name;

    public UnknownEnum(String path, String name) {
      this.path = path;
      this.name = name;
    }
  }
}
