package me.dinowernli.jproducers;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

/** Holds the execution state of a single producer in a specific graph execution. */
class Node<T> {
  private final Optional<Method> producer;
  private final ImmutableList<Node<?>> dependencies;
  private final SettableFuture<T> value;

  static <T> Node<T> createComputedNode(Method producer, ImmutableList<Node<?>> dependencies) {
    return new Node<>(Optional.of(producer), dependencies);
  }

  static <T> Node<T> createConstantNode() {
    return new Node<>(Optional.empty(), ImmutableList.of() /* dependencies */);
  }

  static <T> Node<ImmutableSet<T>> createSetAssemblyNode(ImmutableList<Node<?>> dependencies) {
    Method producer;
    try {
      producer = Node.class.getDeclaredMethod("produceSet");
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Unable to find set assembly producer", e);
    }
    return new Node<>(Optional.of(producer), dependencies);
  }

  public static <T> ImmutableSet<T> produceSet(T... values) {
    return ImmutableSet.<T>builder()
        .addAll(Arrays.asList(values))
        .build();
  }

  private Node(Optional<Method> producer, ImmutableList<Node<?>> dependencies) {
    this.producer = producer;
    this.dependencies = dependencies;
    this.value = SettableFuture.create();
  }

  /**
   * Returns whether the output of this node is ready to be consumed (i.e., contains either a value
   * or an error).
   */
  boolean isDone() {
    return value.isDone();
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

  void acceptValue(Object object) {
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
