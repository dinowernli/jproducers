package me.dinowernli.jproducers;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Key;

import java.util.logging.Logger;

public class Main {
  private static final Logger logger = Logger.getLogger(Main.class.getName());

  public static void main(String[] args) throws Throwable {
    ProducerContext context = ProducerContext.forClasses(ExampleModule.class);
    logger.info("Created producer context");

    Graph<String> graph = context.newGraph(Key.get(String.class));
    logger.info("Created graph");

    ListenableFuture<String> result = graph.run();
    logger.info("Started graph");
    
    logger.info("Result: " + result.get());
  }
}
