package com.squareup.moshi;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class AsArrayJsonAdapter<T> extends ClassJsonAdapter<T> {
	public static final Factory FACTORY = new Factory() {
		@Override
		public @Nullable JsonAdapter<?> create(@Nonnull Type type, Set<? extends Annotation> annotations, Moshi moshi) {
			Class<?> rawType = getRawType(type, annotations);
			if (rawType == null)
				return null;
			ClassFactory<Object> classFactory = ClassFactory.get(rawType);
			Map<String, FieldBinding<?>> fields = new TreeMap<>();
			for (Type t = type; t != Object.class; t = Types.getGenericSuperclass(t))
				createFieldBindings(moshi, t, fields);
			return new AsArrayJsonAdapter<>(classFactory, fields).nullSafe();
		}
	};

	AsArrayJsonAdapter(ClassFactory<T> classFactory, Map<String, FieldBinding<?>> fieldsArray) {
		super(classFactory, fieldsArray);
	}

	@Override
	public T fromJson(JsonReader reader) throws IOException {
		T result;
		try {
			result = classFactory.newInstance();
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			Throwable targetException = e.getTargetException();
			if (targetException instanceof RuntimeException) throw (RuntimeException) targetException;
			if (targetException instanceof Error) throw (Error) targetException;
			throw new RuntimeException(targetException);
		} catch (IllegalAccessException e) {
			throw new AssertionError();
		}

		try {
			reader.beginArray();
			for (FieldBinding<?> fieldBinding : fieldsArray) {
				if (!reader.hasNext())
					throw new RuntimeException("Array has too much element");
				fieldBinding.read(reader, result);
			}
			reader.endArray();
			return result;
		} catch (IllegalAccessException e) {
			throw new AssertionError();
		}
	}

	@Override
	public void toJson(JsonWriter writer, Object value) throws IOException {
		try {
			writer.beginArray();
			for (FieldBinding<?> fieldBinding : fieldsArray)
				fieldBinding.write(writer, value);
			writer.endArray();
		} catch (IllegalAccessException e) {
	      throw new AssertionError();
	    }
	}
}
