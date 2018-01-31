package me.dinowernli.jproducers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Key;
import me.dinowernli.jproducers.Annotations.Produces;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProducerContext {
  private final ExecutorService executor;
  private final ImmutableMap<Key<?>, Method> producers;

  public static ProducerContext forClasses(Class<?>... classes) {
    // TODO(dino): Add a factory method which scans for all classes marked @ProducerModule.
    ExecutorService threadPool = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder()
            .setDaemon(true)
            .build());
    return new ProducerContext(ImmutableList.copyOf(classes), threadPool);
  }

  private ProducerContext(ImmutableList<Class<?>> classes, ExecutorService executor) {
    this.executor = executor;
    this.producers = computeProducerMap(classes);
  }

  public <T> Graph<T> newGraph(Key<T> key) {
    HashMap<Key<?>, Node<?>> nodes = new HashMap<>();
    addDependencies(producers.get(key), nodes);
    return new Graph<T>(executor, nodes, key);
  }

  private void addDependencies(Method producer, HashMap<Key<?>, Node<?>> dependencies) {
    Key<?> currentKey = producerKeyForReturnType(producer);
    if (dependencies.containsKey(currentKey)) {
      return;
    }

    // Construct a node based on the dependencies.
    ImmutableList.Builder<Key<?>> dependencyKeys = ImmutableList.builder();
    for (int i = 0; i < producer.getGenericParameterTypes().length; ++i) {
      ParameterizedType genericType = (ParameterizedType) producer.getGenericParameterTypes()[i];
      AnnotatedType annotatedType = producer.getAnnotatedParameterTypes()[i];
      Key<?> dependencyKey = producerKeyForParameterType(genericType, annotatedType);

      dependencyKeys.add(dependencyKey);

      Method dependencyProducer = producers.get(dependencyKey);
      addDependencies(dependencyProducer, dependencies);
    }

    // Add the current node.
    dependencies.put(currentKey, Node.createComputedNode(producer, dependencyKeys.build()));
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

        Key<?> key = producerKeyForReturnType(method);
        if (producers.containsKey(key)) {
          throw new IllegalArgumentException("Found duplicate producer for key: " + key);
        }
        producers.put(key, method);
      }
    }
    return ImmutableMap.copyOf(producers);
  }

  private static Key<?> producerKeyForReturnType(Method method) {
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

  private static Key<?> producerKeyForParameterType(ParameterizedType parametrizedType, AnnotatedType annotatedType) {
    ImmutableSet<Class<? extends Annotation>> annotations =
        Arrays.stream(annotatedType.getDeclaredAnnotations())
            .map(Annotation::annotationType)
            .filter(t -> !t.equals(Produces.class))
            .collect(ImmutableSet.toImmutableSet());

    // TODO(dino): Implement this check properly.
    if (!parametrizedType.getTypeName().contains("Present")) {
      throw new IllegalArgumentException("Expected " + parametrizedType.getTypeName() + " to be a present");
    }

    Type presentType = parametrizedType.getActualTypeArguments()[0];
    if (annotations.isEmpty()) {
      return Key.get(presentType);
    } else if (annotations.size() == 1) {
      return Key.get(presentType, annotations.iterator().next());
    } else {
      throw new IllegalArgumentException(
          "Can only have one annotation, but got multiple for type: " + parametrizedType);
    }
  }
}
