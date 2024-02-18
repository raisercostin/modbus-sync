/*
 * Copyright 2021 Jakob Hjelm - https://github.com/komposten
 *
 * This is a free code sample: you can use, redistribute it and/or modify
 * it under the terms of the MIT license (https://choosealicense.com/licenses/mit/).
 */
package com.namekis.modbusync.impl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.fasterxml.jackson.databind.util.StdConverter;

/**
 * <p>
 * A {@link Converter} that finds and calls an object's {@link PostConstruct} method and
 * then returns the object as-is.
 * </p>
 * <p>
 * <b>Basic usage:</b>
 * <ol>
 * <li>Create a sub-class that is parameterised for the class you want to deserialise.
 * <li>Enable the converter using
 * <code>@JsonDeserialize(converter = YourPostConstructConverter.class)</code>
 * <li>Annotate a void, no-arg method with <code>@PostConstruct</code>
 * </ol>
 * The <code>@PostConstruct</code> method should now be called after deserialisation.
 * </p>
 * <p>
 * <b>Combining post-constructing with a normal converter</b>
 * <ol>
 * <li>Create your custom converter and enable it using
 * <code>@JsonDeserialize(converter = YourConverter.class)</code>.
 * <li>Instantiate <code>PostConstructConverter</code> inside
 * <code>YourConverter#convert</code>.
 * <li>Call {@link #convert(Object) PostConstructConverter#convert} on the value before or
 * after conversion to invoke its <code>@PostConstruct</code> method.
 * </ol>
 * </p>
 *
 * @param <T> The type of the values to invoke post-construction for.
 * @author Jakob (Komposten) Hjelm
 */
public class PostConstructConverter<T> extends StdConverter<T, T> {
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface PostConstruct {}

  @Override
  public T convert(T value) {
    Class<?> klass = value.getClass();

    Method method = null;

    for (Method declaredMethod : klass.getDeclaredMethods()) {
      PostConstruct postConstruct = declaredMethod.getAnnotation(PostConstruct.class);
      if (postConstruct != null) {
        method = declaredMethod;
        break;
      }
    }

    if (method != null) {
      try {
        method.setAccessible(true);
        if (method.getParameterCount() == 0) {
          method.invoke(value);
        } else {
          method.invoke(null); // Replace null with an InvocationContext if needed
        }
      } catch (IllegalAccessException | IllegalArgumentException
          | InvocationTargetException e) {
        throw new IllegalArgumentException("Failed to invoke post-construct method", e);
      }
    }

    return value;
  }
}