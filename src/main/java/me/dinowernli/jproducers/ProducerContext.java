package me.dinowernli.jproducers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.BindingAnnotation;
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

  /**
   * Returns a new {@link Graph} instance which can be used to produce a value for the supplied key.
   */
  public <T> Graph<T> newGraph(Key<T> key) {
    HashMap<Key<?>, Node<?>> nodes = new HashMap<>();
    addDependencies(producers.get(key), nodes);
    return new Graph<>(executor, nodes, key);
  }

  /** Returns the set of keys for which graphs can be created. */
  public ImmutableSet<Key<?>> availableKeys() {
    return producers.keySet();
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
      ImmutableList<Annotation> annotations =
          ImmutableList.copyOf(producer.getParameterAnnotations()[i]);
      Key<?> dependencyKey = producerKeyForParameterType(genericType, annotations);
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
          Method existing = producers.get(key);
          throw new IllegalArgumentException(String.format(
              "Already have producer [%s] for key [%s]. Cannot add new producer [%s]",
              existing.getName(), key, method.getName()));
        }
        producers.put(key, method);
      }
    }
    return ImmutableMap.copyOf(producers);
  }

  /** Returns the {@link Key} representing the return type of the supplied method. */
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

  private static Key<?> producerKeyForParameterType(
      ParameterizedType parametrizedType, ImmutableList<Annotation> annotations) {
    ImmutableSet<Class<? extends Annotation>> annotationSet = annotations.stream()
        .filter(ProducerContext::isBindingAnnotation)
        .map(Annotation::annotationType)
        .collect(ImmutableSet.toImmutableSet());

    // TODO(dino): Support futures and straight-up return types.
    if (!parametrizedType.getRawType().equals(Present.class)) {
      throw new IllegalArgumentException(
          "Expected " + parametrizedType.getTypeName() + " to be a Present");
    }

    Type presentType = parametrizedType.getActualTypeArguments()[0];
    if (annotationSet.isEmpty()) {
      return Key.get(presentType);
    } else if (annotationSet.size() == 1) {
      return Key.get(presentType, annotationSet.iterator().next());
    } else {
      throw new IllegalArgumentException(
          "Can only have one annotation, but got multiple for type: " + parametrizedType);
    }
  }

  /**
   * Returns whether the supplied annotation is itself annotations with {@link BindingAnnotation}.
   */
  private static boolean isBindingAnnotation(Annotation annotation) {
    return annotation.annotationType().isAnnotationPresent(BindingAnnotation.class);
  }
}