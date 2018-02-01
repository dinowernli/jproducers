package me.dinowernli.jproducers.example;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.BindingAnnotation;
import me.dinowernli.jproducers.Annotations.ProducerModule;
import me.dinowernli.jproducers.Annotations.Produces;
import me.dinowernli.jproducers.Present;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

@ProducerModule
public class ExampleModule {
  private static ListeningExecutorService executor =
      MoreExecutors.listeningDecorator(Executors.newCachedThreadPool(
          new ThreadFactoryBuilder()
              .setDaemon(true)
              .build()));

  @Retention(RetentionPolicy.RUNTIME)
  @BindingAnnotation
  @interface Foo {}

  @Retention(RetentionPolicy.RUNTIME)
  @BindingAnnotation
  @interface Bar {}

  @Retention(RetentionPolicy.RUNTIME)
  @BindingAnnotation
  @interface Baz {}

  @Produces
  public static int someNumber() {
    return 42;
  }

  @Produces
  @Foo
  public static String produceFoo() {
    return "foo";
  }

  @Produces
  @Bar
  public static String produceBar(@Foo Present<String> fooString) throws ExecutionException {
    return "bar[" + fooString.get() + "]";
  }

  @Produces
  public static ListenableFuture<Long> produceLong() {
    return executor.submit(() -> {
      Thread.sleep(1000);  // Thinking....
      return 1234L;
    });
  }

  @Produces
  public static ImmutableList<String> produceWholeBunchOfString() {
    return ImmutableList.of("woop", "woop");
  }

  @Produces
  public static String someString(
      Present<ImmutableList<String>> strings,
      @Bar Present<String> bar,
      Present<Integer> number,
      Present<Long> asyncNumber,
      @Baz Present<Double> explicitNumber) throws ExecutionException {
    return String.format("The numbers were: [%d, %f], bar: %s. Async number: %d. Strings: %s",
        number.get(), explicitNumber.get(), bar.get(), asyncNumber.get(), strings.get());
  }
}
