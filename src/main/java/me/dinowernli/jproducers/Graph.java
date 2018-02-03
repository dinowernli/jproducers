package me.dinowernli.jproducers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Key;

import java.util.Map;
import java.util.concurrent.ExecutorService;

/** Represents a single execution of a graph for a specific output type. */
public class Graph<T> {
  private final ExecutorService executor;

  /** The root node of this graph. */
  private final Node<T> root;

  /** Maps expected explicit input keys to whether an input has actually been provided. */
  private final ImmutableMap<Key<?>, Node<?>> explicitInputs;

  Graph(
      ExecutorService executor,
      Node<T> root,
      ImmutableMap<Key<?>, Node<?>> explicitInputs) {
    this.executor = executor;
    this.root = root;
    this.explicitInputs = explicitInputs;
  }

  public <I> Graph<T> addInput(Key<I> key, I value) {
    if (!explicitInputs.containsKey(key)) {
      throw new IllegalArgumentException("Attempted to bind unexpected input for key: " + key);
    }
    Node<?> node = explicitInputs.get(key);
    if (node.isDone()) {
      throw new IllegalArgumentException("Attempted to bind already-bound input for key: " + key);
    }
    node.acceptValue(value);
    return this;
  }

  /** Kicks off the execution of this graph. */
  public ListenableFuture<T> run() {
    for (Map.Entry<Key<?>, Node<?>> explicitInput : explicitInputs.entrySet()) {
      if (!explicitInput.getValue().isDone()) {
        return Futures.immediateFailedFuture(
            new RuntimeException("Missing input for key: " + explicitInput.getKey()));
      }
    }
    return processNode(root);
  }

  /**
   * Recursively kicks off execution of all required nodes for the supplied node and wires up the
   * callbacks that make sure that results are propagated back to the supplied node. Returns a
   * future which tracks the execution progress of the supplied node itself.
   */
  private <O> ListenableFuture<O> processNode(Node<O> node) {
    for (Node<?> dependencyNode : node.dependencies()) {
      ListenableFuture<?> dependencyValue = processNode(dependencyNode);
      dependencyValue.addListener(() -> onDependencyDone(node), executor);
    }

    // Trigger this explicitly here for degenerate cases where there are no dependencies, etc.
    onDependencyDone(node);

    return node.value();
  }

  /**
   * Called whenever a dependency of the supplied node has finished executing.
   */
  private void onDependencyDone(Node<?> node) {
    ImmutableList<Node<?>> dependencies = node.dependencies();

    // We can't run this node if there are dependencies which haven't run yet.
    if (dependencies.stream().anyMatch(d -> !d.isDone())) {
      return;
    }

    // Construct present for all the arguments.
    Object[] arguments = new Object[dependencies.size()];
    for (int i = 0; i < dependencies.size(); ++i) {
      Present<?> present;
      try {
        Node<?> dependencyNode = dependencies.get(i);
        present = Present.successful(dependencyNode.value().get());
      } catch (Throwable t) {
        present = Present.failed(t);
      }
      arguments[i] = present;
    }

    // Run the actual producer.
    executor.submit(() -> node.execute(arguments));
  }
}
