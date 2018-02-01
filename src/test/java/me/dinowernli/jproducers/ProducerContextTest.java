package me.dinowernli.jproducers;

import com.google.common.util.concurrent.ListenableFuture;
import me.dinowernli.jproducers.Annotations.Produces;
import me.dinowernli.junit.TestClass;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

@TestClass
public class ProducerContextTest {
  private static class FakeProducerModule {
    @Produces
    public static String foo() {
      return "hello world";
    }
  }

  @Test
  public void testSimpleExecution() throws Throwable {
    ProducerContext context = ProducerContext.createForTesting(FakeProducerModule.class);
    ListenableFuture<String> result = context.newGraph(String.class).run();
    assertThat(result.isDone()).isTrue();
    assertThat(result.get()).isEqualTo("hello world");
  }
}