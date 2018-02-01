package me.dinowernli.jproducers.example;

import com.google.inject.BindingAnnotation;
import me.dinowernli.jproducers.Annotations.ProducerModule;
import me.dinowernli.jproducers.Annotations.Produces;
import me.dinowernli.jproducers.Present;

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
  public static String someString(
      @Bar Present<String> bar,
      Present<Integer> number,
      @Baz Present<Double> explicitNumber) throws ExecutionException {
    return String.format("The numbers were: [%d, %f], bar: %s",
        number.get(), explicitNumber.get(), bar.get());
  }
}
