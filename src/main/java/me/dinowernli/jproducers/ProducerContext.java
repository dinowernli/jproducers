package me.dinowernli.jproducers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Key;
import me.dinowernli.jproducers.Annotations.Produces;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ProducerContext {
  private final ImmutableList<Class<?>> classes;
  private final ImmutableMap<Key<?>, Method> producers;

  public static ProducerContext forClasses(Class<?>... classes) {
    // TODO(dino): Add a factory method which scans for all classes marked @ProducerModule.
    return new ProducerContext(ImmutableList.copyOf(classes));
  }

  private ProducerContext(ImmutableList<Class<?>> classes) {
    this.classes = classes;
    this.producers = computeProducerMap(classes);
  }

  public <T> Graph<T> newGraph(Key<T> key) {
    return Graph.forKey(key);
  }

  private static ImmutableMap<Key<?>, Method> computeProducerMap(ImmutableList<Class<?>> classes) {
    Map<Key<?>, Method> producers = new HashMap<>();
    for (Class<?> clazz : classes) {
      for (Method method : clazz.getDeclaredMethods()) {
        if (!method.isAnnotationPresent(Produces.class)) {
          continue;
        }
        if (!Modifier.isStatic(method.getModifiers())) {
          throw new IllegalArgumentException("Cannot have non-static producer: " + method);
        }

        Key<?> key = producerKey(method);
        if (producers.containsKey(key)) {
          throw new IllegalArgumentException("Found duplicate producer for key: " + key);
        }
        producers.put(key, method);
      }
    }
    return ImmutableMap.copyOf(producers);
  }

  private static Key<?> producerKey(Method method) {
    ImmutableSet<Class<? extends Annotation>> annotations =
        Arrays.stream(method.getDeclaredAnnotations())
            .map(Annotation::annotationType)
            .filter(t -> !t.equals(Produces.class))
            .collect(ImmutableSet.toImmutableSet());
    if (annotations.isEmpty()) {
      return Key.get(method.getGenericReturnType());
    } else if (annotations.size() == 1) {
      return Key.get(method.getGenericReturnType(), annotations.iterator().next());
    } else {
      throw new IllegalArgumentException(
          "Can only have one non-Produces annotation, but got multiple for method: " + method);
    }
  }
}
