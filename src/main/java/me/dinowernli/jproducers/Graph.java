package me.dinowernli.jproducers;


import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Key;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/** Represents a single execution of a graph for a specific output type. */
public class Graph<T> {
  private final ExecutorService executor;
  private final HashMap<Key<?>, Node<?>> nodes;

  /** Maps expected explicit input keys to whether an input has actually been provided. */
  private final HashMap<Key<?>, Boolean> explicitInputs;

  private final Key<T> outputKey;

  Graph(
      ExecutorService executor,
      HashMap<Key<?>, Node<?>> nodes,
      Set<Key<?>> explicitInputs,
      Key<T> outputKey) {
    this.executor = executor;
    this.nodes = nodes;
    this.outputKey = outputKey;
    this.explicitInputs = createExplicitInputMap(explicitInputs);
  }

  public <I> Graph<T> addInput(Key<I> key, I value) {
    if (!explicitInputs.containsKey(key)) {
      throw new IllegalArgumentException("Attempted to bind unexpected input for key: " + key);
    }
    if (explicitInputs.get(key)) {
      throw new IllegalArgumentException("Attempted to bind already-bound input for key: " + key);
    }

    explicitInputs.put(key, true);
    nodes.put(key, Node.createConstantNode(value));
    return this;
  }

  /** Kicks off the execution of this graph. */
  public ListenableFuture<T> run() {
    for (Map.Entry<Key<?>, Boolean> explicitInput : explicitInputs.entrySet()) {
      if (!explicitInput.getValue()) {
        return Futures.immediateFailedFuture(
            new RuntimeException("Missing input for key: " + explicitInput.getKey()));
      }
    }
    return (ListenableFuture<T>) processNode(nodes.get(outputKey));
  }

  private <O> ListenableFuture<O> processNode(Node<O> node) {
    for (Key<?> dependencyKey : node.dependencies()) {
      Node<?> dependencyNode = nodes.get(dependencyKey);
      ListenableFuture<?> dependencyValue = processNode(dependencyNode);
      dependencyValue.addListener(() -> onDependencyDone(node), executor);
    }

    // Trigger this explicitly here for degenerate cases where there are no dependencies, etc.
    onDependencyDone(node);

    return node.value();
  }

  /** Called whenever a dependency of the supplied node has finished executing. */
  private void onDependencyDone(Node<?> node) {
    ImmutableList<Key<?>> dependencies = node.dependencies();

    // Check that all dependencies have produced values.
    for (Key<?> dependency : dependencies) {
      Node<?> dependencyNode = nodes.get(dependency);
      if (!dependencyNode.value().isDone()) {
        return;
      }
    }

    // Construct present for all the arguments.
    Object[] arguments = new Object[dependencies.size()];
    for (int i = 0; i < dependencies.size(); ++i) {
      Present<?> present;
      try {
        Node<?> dependencyNode = nodes.get(dependencies.get(i));
        present = Present.successful(dependencyNode.value().get());
      } catch (Throwable t) {
        present = Present.failed(t);
      }
      arguments[i] = present;
    }

    // Run the actual producer.
    executor.submit(() -> {
      try {
        node.acceptValue(node.producer().invoke(null /* receiver */, arguments));
      } catch (Throwable t) {
        node.acceptError(new RuntimeException("Unable to execute producer", t));
      }
    });
  }

  private static HashMap<Key<?>, Boolean> createExplicitInputMap(Set<Key<?>> explicitInputs) {
    HashMap<Key<?>, Boolean> result = new HashMap<>();
    explicitInputs.forEach(k -> result.put(k, false));
    return result;
  }
}
