package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.*;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private final Object object;
  private final ProfilingState state;


  // TODO: You will need to add more instance fields and constructor arguments to this class.
  ProfilingMethodInterceptor(Object object, Clock clock, ProfilingState state) {
    this.object = object;
    this.clock = Objects.requireNonNull(clock);
    this.state = Objects.requireNonNull(state);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // TODO: This method interceptor should inspect the called method to see if it is a profiled
    //       method. For profiled methods, the interceptor should record the start time, then
    //       invoke the method using the object that is being profiled. Finally, for profiled
    //       methods, the interceptor should record how long the method call took, using the
    //       ProfilingState methods.

      Instant start = clock.instant();
      try {
          return method.invoke(object, args);
      } catch (InvocationTargetException e) {
          throw e.getTargetException();
      } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
      } finally {
        if (method.getAnnotation(Profiled.class) != null) {
          Duration duration = Duration.between(start, clock.instant());
          state.record(object.getClass(), method, duration);
      }
    }

  }
}
