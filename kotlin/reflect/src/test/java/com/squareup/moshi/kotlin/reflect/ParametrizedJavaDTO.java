package com.squareup.moshi.kotlin.reflect;

import java.util.List;
import java.util.Objects;

public class ParametrizedJavaDTO<T> {

    final List<T> items;

    ParametrizedJavaDTO(List<T> items) {
        this.items = items;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParametrizedJavaDTO<?> testData = (ParametrizedJavaDTO<?>) o;
        return Objects.equals(items, testData.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items);
    }
}
