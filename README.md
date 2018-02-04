# jproducers

[![Build Status](https://travis-ci.org/dinowernli/jproducers.svg?branch=master)](https://travis-ci.org/dinowernli/jproducers)

The `jproducers` library allows specifying asynchronous computations declaratively and letting the library do the scheduling and waiting for you. This is particularly useful to express computations which would otherwise need complex chains of `Futures.transform`.

## Code example

Here is an example class which declares a few producers.

```java
static class Producers {
  @Retention(RetentionPolicy.RUNTIME)
  @BindingAnnotation
  @interface Foo {}

  @Produces
  @Foo
  public static ListenableFuture<String> produceFoo() {
    return Futures.immediateFuture("hello!");
  }

  @Produces
  public static long produceLength(@Foo Present<String> foo) throws ExecutionException {
    return foo.get().length();
  }
}
```

The code to execute a graph then looks like:

```java
ProducerContext context = ProducerContext.forClasses(Producers.class);
ListenableFuture<Long> result = context
    .newGraph(Key.get(Long.class))
    .run();
```

## Producers

In order to orchestrate a computation, `jproducers` constructs a connected graph of producer nodes. Each node is called a `producer`, and is defined by a Java method, e.g.:

```java
@Produces
static String produceSomeString(Present<Long> someNumber) throws ExecutionException {
  return "hello world: " + someNumber.get();
}
```

Producers may have arguments, which `jproducers` uses to determine which other producers need to be run beforehand.

## Return types

The return type of a producer method determines the produced type. If a producer returns a `ListenableFuture`, the library takes care of waiting for the future before invoking downstream producers.

The following producers all have produced type `@Bar String`:

```java
@Produces
@Bar
static ListenableFuture<String> produceAsyncBar() {
  return Futures.immediateFailedFuture(new RuntimeException("failed!"));
}

@Produces
@Bar
static String produceBar() {
  return "hello world";
}
```

## Error propagation

A producer can indicate failure by throwing an exception (or returning a failed future). If this happens, downstream producers are passed an instance of `Present` containing the error. This allows errors to be propagated throughout a producer graph.

```java
@Produces
static long produceNumUsers(Present<UserInfoResponse> response) {
  try {
    return response.get().getNumUsers();
  } catch (ExecutionException e) {
    throw new RuntimeException("User info rpc failed", e);
  }
}
```

## Set producers

It is possible to use `@ProducersIntoSet` to declare that a producer produces and element in a set of values:

```java
@ProducesIntoSet
static UserDetailsRequest produceOwnerDetailsRequest(Present<Long> ownerId) throws ExecutionException {
  return new UserDetailsRequest(ownerId.get());
}

@ProducesIntoSet
static UserDetailsRequest producerOtherDetailsRequest(Present<String> username) throws ExecutionException {
  return new UserDetailsRequest(username.get());
}
```

Downstream producers can then continue their computation of the full set:

```java
@Produces
static ListenableFuture<ImmutableSet<UserDetails>> produceDetails(
    Present<ImmutableSet<UserDetailsRequest>> requests,
    Present<UserInfoClient> client) throws ExecutionException {
  return client.makeRequests(requests.get());
}
```

## Other features

* Because the graph is constructed based on a desired output type, only the necessary nodes are ever executed.
* Supports backing a `ProducerContext` with direct executors for testing.

## Building and running

To run the example, execute:

`bazel run  //src/main/java/me/dinowernli/jproducers/example`

To run all tests, execute:

`bazel test //src/...`
