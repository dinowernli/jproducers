package me.dinowernli.jproducers;

import com.google.common.util.concurrent.ListenableFuture;
import me.dinowernli.jproducers.Annotations.Produces;
import me.dinowernli.junit.TestClass;
import org.junit.Test;

import java.lang.reflect.Method;

import static com.google.common.truth.Truth.assertThat;

@TestClass
public class TypesTest {
  @Test
  public void testDropsFuture() throws Throwable {
    Method producer = TypesTest.class.getMethod("produceString");
    assertThat(Types.extractProducedType(producer)).isEqualTo(String.class);
  }

  @Produces
  public static ListenableFuture<String> produceString() {
    return null;
  }
}
