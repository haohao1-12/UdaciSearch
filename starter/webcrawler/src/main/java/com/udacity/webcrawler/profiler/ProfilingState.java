package com.udacity.webcrawler.profiler;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

/**
 * Helper class that records method performance data from the method interceptor.
 */
final class ProfilingState {
  private final Map<String, Duration> data = new ConcurrentHashMap<>();
  private final Map<String, String> threads = new ConcurrentHashMap<>();
  private final Map<String, Integer> freq = new ConcurrentHashMap<>();

  /**
   * Records the given method invocation data.
   *
   * @param callingClass the Java class of the object that called the method.
   * @param method       the method that was called.
   * @param elapsed      the amount of time that passed while the method was called.
   */
  void record(Class<?> callingClass, Method method, Duration elapsed, String threadId) {
    Objects.requireNonNull(callingClass);
    Objects.requireNonNull(method);
    Objects.requireNonNull(elapsed);
    Objects.requireNonNull(threadId);
    if (elapsed.isNegative()) {
      throw new IllegalArgumentException("negative elapsed time");
    }
    String key = formatMethodCall(callingClass, method);
    data.compute(key, (k, v) -> (v == null) ? elapsed : v.plus(elapsed));
    freq.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
    threads.compute(key, (k, v) -> (v == null) ? threadId : v + " " + threadId);
  }

  /**
   * Writes the method invocation data to the given {@link Writer}.
   *
   * <p>Recorded data is aggregated across calls to the same method. For example, suppose
   * {@link #record(Class, Method, Duration) record} is called three times for the same method
   * {@code M()}, with each invocation taking 1 second. The total {@link Duration} reported by
   * this {@code write()} method for {@code M()} should be 3 seconds.
   */
  void write(Writer writer) throws IOException {
    List<String> entries =
        data.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + " took " + formatDuration(e.getValue()) + System.lineSeparator())
            .collect(Collectors.toList());

    List<String> entries_thread =
            threads.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + " executed on threads " + e.getValue() + System.lineSeparator())
                    .collect(Collectors.toList());

    List<String> entries_freq =
            freq.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + " was executed for " + e.getValue() + " times " + System.lineSeparator())
                    .collect(Collectors.toList());



    // We have to use a for-loop here instead of a Stream API method because Writer#write() can
    // throw an IOException, and lambdas are not allowed to throw checked exceptions.
    for (String entry : entries) {
      writer.write(entry);
    }
    for (String entry : entries_thread) {
      writer.write(entry);
    }
    for (String entry : entries_freq) {
      writer.write(entry);
    }
  }

  /**
   * Formats the given method call for writing to a text file.
   *
   * @param callingClass the Java class of the object whose method was invoked.
   * @param method       the Java method that was invoked.
   * @return a string representation of the method call.
   */
  private static String formatMethodCall(Class<?> callingClass, Method method) {
    return String.format("%s#%s", callingClass.getName(), method.getName());
  }

  /**
   * Formats the given {@link Duration} for writing to a text file.
   */
  private static String formatDuration(Duration duration) {
    return String.format(
        "%sm %ss %sms", duration.toMinutes(), duration.toSecondsPart(), duration.toMillisPart());
  }
}
