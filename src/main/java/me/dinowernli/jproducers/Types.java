package me.dinowernli.jproducers;

import com.google.common.util.concurrent.ListenableFuture;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

class Types {
  /**
   * Extracts the actual type produced by the given producer.
   */
  static Type extractProducedType(Method producer) {
    Type returnType = producer.getGenericReturnType();
    if (returnType instanceof ParameterizedType) {
      // This branch hits if the declared type was actually generic. Dissect it further.
      return extractProducedType((ParameterizedType) producer.getGenericReturnType());
    } else {
      // In the non-generic case the produced type is the actual declared type.
      return returnType;
    }
  }

  /**
   * Extracts the actual type being produced for a given declared type. For instance, for the type
   * ListenableFuture<Double>, this returns Double. For the declared type Foo, however, this returns
   * Foo itself.
   */
  private static Type extractProducedType(ParameterizedType parameterizedType) {
    if (parameterizedType.getRawType().equals(ListenableFuture.class)) {
      return parameterizedType.getActualTypeArguments()[0];
    }
    return parameterizedType;
  }
}
