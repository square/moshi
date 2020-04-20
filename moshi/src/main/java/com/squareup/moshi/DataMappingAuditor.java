/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
