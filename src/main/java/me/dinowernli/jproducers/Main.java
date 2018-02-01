package me.dinowernli.jproducers;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Key;

import java.util.logging.Logger;

public class Main {
  private static final Logger logger = Logger.getLogger(Main.class.getName());

  public static void main(String[] args) throws Throwable {
    setupLogging();

    // TODO(dino):
    // - Producers returning futures
    // - Producing into sets and maps
    // - Producing primitive types
    // - Convenience methods for binding inputs to constants, futures, etc

    ProducerContext context = ProducerContext.forClasses(ExampleModule.class);
    logger.info("Created context, available keys:\n\t" + formatKeys(context.availableKeys()));

    Graph<String> graph = context.newGraph(Key.get(String.class));
    logger.info("Created graph");

    ListenableFuture<String> result = graph.run();
    logger.info("Started graph");

    logger.info("Result: " + result.get());
  }

  private static void setupLogging() {
    System.setProperty(
        "java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %2$s %5$s%6$s%n");
  }

  private static String formatKeys(ImmutableSet<Key<?>> keys) {
    return Joiner.on("\n\t").join(keys);
  }
}
