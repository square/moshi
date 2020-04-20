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

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link JsonReader} that keeps an audit trail of any mapping issues,
 * and delegates to the underlying {@link JsonReader}.
 */
public class AuditJsonReader extends DelegatingJsonReader implements DataMappingAuditor {
  private List<UnknownEnum> unknownEnums;

  public AuditJsonReader(JsonReader delegate) {
    super(delegate);
  }

  @Override
  public void addUnknownEnum(UnknownEnum unknownEnum) {
    if (unknownEnums == null) {
      unknownEnums = new ArrayList<>();
    }
    unknownEnums.add(unknownEnum);
  }

  @Override
  public List<UnknownEnum> getUnknownEnums() {
    return unknownEnums;
  }
}
