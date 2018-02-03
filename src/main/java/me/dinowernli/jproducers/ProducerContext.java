package me.dinowernli.jproducers;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.BindingAnnotation;
import com.google.inject.Key;
import me.dinowernli.jproducers.Annotations.Produces;
import me.dinowernli.jproducers.Annotations.ProducesIntoSet;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProducerContext {
  private final ExecutorService executor;

  /** Holds all the available producer method which directly produced a specific key. */
  private final ImmutableMap<Key<?>, Method> producers;

  /** Holds the producers which produce elements into a set for a given key type. */
  private final ImmutableMultimap<Key<?>, Method> setProducers;

  public static ProducerContext forClasses(Class<?>... classes) {
    // TODO(dino): Add a factory method which scans for all classes marked @ProducerModule.
    ExecutorService threadPool = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder()
            .setDaemon(true)
            .build());
    return new ProducerContext(ImmutableList.copyOf(classes), threadPool);
  }

  @VisibleForTesting
  static ProducerContext createForTesting(Class<?>... classes) {
    return new ProducerContext(
        ImmutableList.copyOf(classes), MoreExecutors.newDirectExecutorService());
  }

  private ProducerContext(ImmutableList<Class<?>> classes, ExecutorService executor) {
    this.executor = executor;

    HashMap<Key<?>, Method> producers = new HashMap<>();
    HashMultimap<Key<?>, Method> setProducers = HashMultimap.create();
    computeProducerMap(classes, producers, setProducers);

    this.producers = ImmutableMap.copyOf(producers);
    this.setProducers = ImmutableMultimap.copyOf(setProducers);
  }

  /**
   * Returns a new {@link Graph} instance which can be used to produce a value for the supplied type
   * with no annotations.
   */
  public <T> Graph<T> newGraph(Class<T> clazz) {
    return newGraph(Key.get(clazz));
  }

  /**
   * Returns a new {@link Graph} instance which can be used to produce a value for the supplied key.
   */
  public <T> Graph<T> newGraph(Key<T> key) {
    HashMap<Key<?>, Node<?>> nodes = new HashMap<>();
    HashMap<Key<?>, Node<?>> explicitInputs = new HashMap<>();
    addNode(producers.get(key), true /* expectUnique */, nodes, explicitInputs);

    return new Graph<>(executor, nodes, explicitInputs, key);
  }

  /** Returns the set of keys for which graphs can be created. */
  public ImmutableSet<Key<?>> availableKeys() {
    return producers.keySet();
  }

  /**
   * Creates a node for the supplied producer and returns it.
   *
   * This method recursively creates nodes for all dependencies of the supplied producer and adds
   * them to the "nodes" and "explicitInputs" maps.
   */
  private Node<?> addNode(
      Method producer,
      boolean expectUnique,
      HashMap<Key<?>, Node<?>> nodes,
      HashMap<Key<?>, Node<?>> explicitInputs) {

    // TODO(dino): Change this signature to take a type rather than a producer. This method should
    // add a node which outputs that type.

    Key<?> currentKey = producerKeyForReturnType(producer);
    if (nodes.containsKey(currentKey) && expectUnique) {
      return nodes.get(currentKey);
    }

    // Resolve all the dependencies of this producer.
    ImmutableList.Builder<Node<?>> directDependencies = ImmutableList.builder();
    for (int i = 0; i < producer.getGenericParameterTypes().length; ++i) {
      ParameterizedType genericType = (ParameterizedType) producer.getGenericParameterTypes()[i];
      Type presentType = genericType.getActualTypeArguments()[0];  // TODO(dino): support non-present.
      ImmutableList<Annotation> annotations =
          ImmutableList.copyOf(producer.getParameterAnnotations()[i]);
      Key<?> dependencyKey = producerKeyForParameterType(genericType, annotations);

      // Try to find a producer which produces this key straight-up.
      Method dependencyProducer = producers.get(dependencyKey);
      if (dependencyProducer != null) {
        Node<?> depNode = addNode(dependencyProducer, true /* expectUnique */, nodes, explicitInputs);
        directDependencies.add(depNode);
        continue;
      }

      // Try to resolve a bunch of producers which produce elements into this set.
      if (presentType instanceof ParameterizedType
          && ((ParameterizedType) presentType).getRawType().equals(ImmutableSet.class)) {
        // Construct a key for the individual elements.
        Type elementType = ((ParameterizedType) presentType).getActualTypeArguments()[0];
        Key elementKey;
        if (dependencyKey.getAnnotation() == null) {
          elementKey = Key.get(elementType);
        } else {
          elementKey = Key.get(elementType, dependencyKey.getAnnotation());
        }

        // Find all the producers.
        Collection<Method> elementProducers = setProducers.get(elementKey);
        if (!elementProducers.isEmpty()) {
          // Add a compute node for each element.
          ImmutableList.Builder<Node<?>> elementNodes = ImmutableList.builder();
          for (Method elementProducer : elementProducers) {
            // TODO(dino): Can't add the node to the nodes of the graph because there is no key to
            // identify them by... Investigate replacing the map with a list.
            Node<?> elementNode = addNode(elementProducer, false /* expectUnique */, nodes, explicitInputs);
            elementNodes.add(elementNode);
          }

          // Add a special compute node which assembles the elements.
          Node<?> assemblyNode = Node.createSetAssemblyNode(elementNodes.build());
          nodes.put(dependencyKey, assemblyNode);
          directDependencies.add(assemblyNode);
          continue;
        }
      }

      // At this point, our only option is to expect a value for this key as input to the graph.
      Node<?> constantNode = Node.createConstantNode();
      nodes.put(dependencyKey, constantNode);
      explicitInputs.put(dependencyKey, constantNode);
      directDependencies.add(constantNode);
    }

    // Create a node for this producer, wiring up its direct dependencies.
    Node<?> currentNode = Node.createComputedNode(producer, directDependencies.build());
    nodes.put(currentKey, currentNode);
    return currentNode;
  }

  private static void computeProducerMap(
      ImmutableList<Class<?>> classes,
      Map<Key<?>, Method> producers,
      Multimap<Key<?>, Method> setProducers) {
    for (Class<?> clazz : classes) {
      for (Method method : clazz.getDeclaredMethods()) {
        if (!Modifier.isStatic(method.getModifiers())) {
          throw new IllegalArgumentException("Cannot have non-static producer: " + method);
        }

        // Check for regular producer.
        if (method.isAnnotationPresent(Produces.class)) {
          Key<?> key = producerKeyForReturnType(method);
          if (producers.containsKey(key)) {
            Method existing = producers.get(key);
            throw new IllegalArgumentException(String.format(
                "Already have producer [%s] for key [%s]. Cannot add new producer [%s]",
                existing.getName(), key, method.getName()));
          }
          producers.put(key, method);
          continue;
        }

        // Check for set producer.
        if (method.isAnnotationPresent(ProducesIntoSet.class)) {
          Key<?> key = producerKeyForReturnType(method);
          setProducers.put(key, method);
          continue;
        }
      }
    }
  }

  /** Returns the {@link Key} representing the return type of the supplied method. */
  private static Key<?> producerKeyForReturnType(Method method) {
    ImmutableSet<Class<? extends Annotation>> annotations =
        Arrays.stream(method.getDeclaredAnnotations())
            .filter(ProducerContext::isBindingAnnotation)
            .map(Annotation::annotationType)
            .collect(ImmutableSet.toImmutableSet());

    Type producedType = Types.extractProducedType(method);
    if (annotations.isEmpty()) {
      return Key.get(producedType);
    } else if (annotations.size() == 1) {
      return Key.get(producedType, annotations.iterator().next());
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

    // By taking type argument[0] below, we encode that all producer arguments must be presents.
    // TODO(dino): Support parameters which are not presents.
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