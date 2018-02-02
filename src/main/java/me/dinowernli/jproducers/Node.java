package me.dinowernli.jproducers;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Key;

import java.lang.reflect.Method;
import java.util.Optional;

/** Holds the execution state of a single producer in a specific graph execution. */
class Node<T> {
  private final Key<T> outputKey;
  private final Optional<Method> producer;
  private final ImmutableList<Node<?>> dependencies;
  private final SettableFuture<T> value;

  static <T> Node<T> createComputedNode(
      Key<T> outputKey, Method producer, ImmutableList<Node<?>> dependencies) {
    return new Node<>(outputKey, Optional.of(producer), dependencies);
  }

  static <T> Node<T> createConstantNode(Key<T> outputKey, T value) {
    Node<T> result = new Node<>(outputKey, Optional.empty(), ImmutableList.of() /* dependencies */);
    result.acceptValue(value);
    return result;
  }

  private Node(Key<T> outputKey, Optional<Method> producer, ImmutableList<Node<?>> dependencies) {
    this.outputKey = outputKey;
    this.producer = producer;
    this.dependencies = dependencies;
    this.value = SettableFuture.create();
  }

  /**
   * Returns the key describing the output of this node.
   */
  Key<T> outputKey() {
    return outputKey;
  }

  /**
   * Returns a future which completes when this node has executed and produced a value.
   */
  ListenableFuture<T> value() {
    return value;
  }

  /**
   * Returns the nodes which have to have completed before this node can run.
   */
  ImmutableList<Node<?>> dependencies() {
    return dependencies;
  }

  void execute(Object[] arguments) {
    Object output;
    try {
      output = producer.get().invoke(null /* receiver */, arguments);
    } catch (Throwable t) {
      acceptError(new RuntimeException("Unable to execute producer", t));
      return;
    }

    // Propagate the output back to the node.
    if (output instanceof ListenableFuture) {
      ListenableFuture<?> outFuture = (ListenableFuture<?>) output;
      Futures.addCallback(outFuture, new NodeFutureCallback(this), MoreExecutors.directExecutor());
    } else {
      acceptValue(output);
    }
  }

  private void acceptValue(Object object) {
    Preconditions.checkState(!value.isDone());
    value.set((T) object);
  }

  private void acceptError(Throwable error) {
    Preconditions.checkState(!value.isDone());
    value.setException(error);
  }

  /**
   * A {@link FutureCallback} which informs the supplied node of the future's outcome.
   */
  private static class NodeFutureCallback implements FutureCallback<Object> {
    private final Node<?> node;

    private <T> NodeFutureCallback(Node<T> node) {
      this.node = node;
    }

    @Override
    public void onSuccess(Object result) {
      node.acceptValue(result);
    }

    @Override
    public void onFailure(Throwable t) {
      node.acceptError(t);
    }
  }
}
