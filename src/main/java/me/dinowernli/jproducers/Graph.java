package me.dinowernli.jproducers;


import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Key;

public class Graph<T> {
  public static <T> Graph<T> forKey(Key<T> key) {
    return new Graph<>();
  }

  public <I> void addInput(Key<I> key, I value) {
    throw new UnsupportedOperationException();
  }

  public ListenableFuture<T> run() {
    return Futures.immediateFailedFuture(new RuntimeException("not implemented"));
  }
}
