package me.dinowernli.jproducers;


import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Key;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;

/** Represents a single execution of a graph for a specific output type. */
public class Graph<T> {
  private final ExecutorService executor;
  private final HashMap<Key<?>, Node<?>> nodes;
  private final Key<T> outputKey;

  public Graph(ExecutorService executor, HashMap<Key<?>, Node<?>> nodes, Key<T> outputKey) {
    this.executor = executor;
    this.nodes = nodes;
    this.outputKey = outputKey;
  }

  public <I> void addInput(Key<I> key, I value) {
    nodes.put(key, Node.createConstantNode(key, value));
  }

  public ListenableFuture<T> run() {
    return (ListenableFuture<T>) processNode(nodes.get(outputKey));
  }

  private <O> ListenableFuture<O> processNode(Node<O> node) {
    for (Key<?> dependencyKey : node.dependencies()) {
      Node<?> dependencyNode = nodes.get(dependencyKey);
      ListenableFuture<?> dependencyValue = processNode(dependencyNode);
      dependencyValue.addListener(() -> onDependencyDone(node), executor);
    }
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
}
