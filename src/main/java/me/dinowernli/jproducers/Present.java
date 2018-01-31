package me.dinowernli.jproducers;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

/** Holds the result of a terminated future, i.e., either a value or an error. */
public class Present<T> {
  private final Optional<T> value;
  private final Optional<Throwable> error;

  public static <T> Present<T> successful(T value) {
    return new Present<>(Optional.of(value), Optional.empty());
  }

  public static <T> Present<T> failed(Throwable t) {
    return new Present<>(Optional.empty(), Optional.of(t));
  }

  private Present(Optional<T> value, Optional<Throwable> error) {
    this.value = value;
    this.error = error;
  }

  public T get() throws ExecutionException {
    if (value.isPresent()) {
      return value.get();
    } else {
      throw new ExecutionException(error.get());
    }
  }
}
