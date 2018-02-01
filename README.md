# jproducers


[![Build Status](https://travis-ci.org/dinowernli/jproducers.svg?branch=master)](https://travis-ci.org/dinowernli/jproducers)

The `jproducers` library allows specifying asynchronous computation pipelines declaratively and letting the library do the scheduling for you.

Features:
* Alleviates the need of complicated chains of `Futures.transform()`.
* Automatic error propagation through the producer graph.
* Only producers which are actually needed get executed.
* Makes it easier to express async computations which fan out and then back in.
* Producer flows can easily be executed in tests using a producer context backed by a direct executor.

## Example

Here is an example class which declares a few producers.

```java
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
```

The code to execute a graph then looks like:

```java

ProducerContext context = ProducerContext.forClasses(FutureFakeProducerModule.class);
ListenableFuture<Long> result = context
    .newGraph(Key.get(Long.class, Bar.class))
    .run();

```

## Building and running

To run the example, execute:

`bazel run  //src/main/java/me/dinowernli/jproducers/example`

To run all tests, execute:

`bazel test //src/...`
