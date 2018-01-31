package me.dinowernli.jproducers;

import me.dinowernli.jproducers.Annotations.ProducerModule;
import me.dinowernli.jproducers.Annotations.Produces;

import java.util.concurrent.ExecutionException;

@ProducerModule
public class ExampleModule {
  @Produces
  public static Integer someNumber() {
    // TODO(dino): Special-case primitive types so that the return type here can be "int".
    return 42;
  }

  @Produces
  public static String someString(Present<Integer> number) throws ExecutionException {
    return "The number was: " + number.get();
  }
}
