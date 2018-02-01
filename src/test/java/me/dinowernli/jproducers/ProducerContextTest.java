package me.dinowernli.jproducers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.BindingAnnotation;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import me.dinowernli.jproducers.Annotations.Produces;
import me.dinowernli.jproducers.ProducerContextTest.FutureFakeProducerModule.Bar;
import me.dinowernli.junit.TestClass;
import org.junit.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.ExecutionException;

import static com.google.common.truth.Truth.assertThat;

@TestClass
public class ProducerContextTest {
  static class FakeProducerModule {
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

  static class FutureFakeProducerModule {
    @Retention(RetentionPolicy.RUNTIME)
    @BindingAnnotation
    @interface Foo {}

    @Retention(RetentionPolicy.RUNTIME)
    @BindingAnnotation
    @interface Bar {}

    @Produces
    @Foo
    public static ListenableFuture<String> produceFoo() {
      return Futures.immediateFuture("hello!");
    }

    @Produces
    @Bar
    public static long produceLength(@Foo Present<String> foo) throws ExecutionException {
      return foo.get().length();
    }
  }

  @Test
  public void testFutureExecution() throws Throwable {
    ProducerContext context = ProducerContext.createForTesting(FutureFakeProducerModule.class);
    ListenableFuture<Long> result = context.newGraph(Key.get(Long.class, Bar.class)).run();
    assertThat(result.isDone()).isTrue();
    assertThat(result.get()).isEqualTo(6);
  }

  static class GenericProducerModule {
    @Produces
    static ImmutableList<String> produceList() {
      return ImmutableList.of("foo", "bar", "foo");
    }

    @Produces
    static ImmutableSet<String> produceSet(Present<ImmutableList<String>> list)
        throws ExecutionException {
      return ImmutableSet.copyOf(list.get());
    }
  }

  @Test
  public void testGenericExecution() throws Throwable {
    ProducerContext context = ProducerContext.createForTesting(GenericProducerModule.class);
    ListenableFuture<ImmutableSet<String>> result =
        context.newGraph(Key.get(new TypeLiteral<ImmutableSet<String>>() {})).run();
    assertThat(result.isDone()).isTrue();
    assertThat(result.get()).containsExactly("foo", "bar");
  }

  static class FailingProducerModule {
    @Produces
    static int produceInt() {
      throw new IllegalStateException("this is an expected exception");
    }
  }

  @Test(expected = ExecutionException.class)
  public void testFailure() throws Throwable {
    ProducerContext context = ProducerContext.createForTesting(FailingProducerModule.class);
    ListenableFuture<Integer> result = context.newGraph(Integer.class).run();
    assertThat(result.isDone()).isTrue();
    result.get();
  }
}