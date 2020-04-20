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
