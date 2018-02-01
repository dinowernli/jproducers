package me.dinowernli.jproducers;

import com.google.inject.BindingAnnotation;
import me.dinowernli.jproducers.Annotations.ProducerModule;
import me.dinowernli.jproducers.Annotations.Produces;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.ExecutionException;

@ProducerModule
public class ExampleModule {
  @Retention(RetentionPolicy.RUNTIME)
  @BindingAnnotation
  @interface Foo {}

  @Retention(RetentionPolicy.RUNTIME)
  @BindingAnnotation
  @interface Bar {}

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
  public static String someString(
      @Bar Present<String> bar, Present<Integer> number) throws ExecutionException {
    return "The number was: " + number.get() + ", " + bar.get();
  }
}
