package me.dinowernli.jproducers;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Key;

import java.lang.reflect.Method;
import java.util.Optional;

/** Holds the execution state of a single producer in a specific graph execution. */
class Node<T> {
  private final Optional<Method> producer;
  private final ImmutableList<Key<?>> dependencies;
  private final SettableFuture<T> value;

  static <T> Node<T> createComputedNode(
      Method producer, ImmutableList<Key<?>> dependencies) {
    return new Node<>(Optional.of(producer), dependencies);
  }

  static <T> Node<T> createConstantNode(T value) {
    Node<T> result = new Node<>(Optional.empty(), ImmutableList.of() /* dependencies */);
    result.acceptValue(value);
    return result;
  }

  private Node(Optional<Method> producer, ImmutableList<Key<?>> dependencies) {
    this.producer = producer;
    this.dependencies = dependencies;
    this.value = SettableFuture.create();
  }

  ListenableFuture<T> value() {
    return value;
  }

  ImmutableList<Key<?>> dependencies() {
    return dependencies;
  }

  Method producer() {
    return producer.get();
  }

  void acceptValue(Object object) {
    value.set((T) object);
  }

  void acceptError(Throwable error) {
    value.setException(error);
  }
}
