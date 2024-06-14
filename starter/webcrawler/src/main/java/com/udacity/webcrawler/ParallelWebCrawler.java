package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final Duration timeout;
  private final int popularWordCount;
  private final ForkJoinPool pool;
  private final PageParserFactory parserFactory;
  private final List<Pattern> ignoredUrls;
  private final int maxDepth;

  @Inject
  ParallelWebCrawler(
          Clock clock,
          @Timeout Duration timeout,
          @PopularWordCount int popularWordCount,
          @MaxDepth int maxDepth,
          @TargetParallelism int threadCount, PageParserFactory parserFactory,
          @IgnoredUrls List<Pattern> ignoredUrls) {
    this.clock = Objects.requireNonNull(clock);
    this.timeout = Objects.requireNonNull(timeout);
    this.popularWordCount = popularWordCount;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    this.parserFactory = Objects.requireNonNull(parserFactory);
    this.ignoredUrls = Objects.requireNonNull(ignoredUrls);
    this.maxDepth = maxDepth;
  }

  @Override
  public CrawlResult crawl(List<String> startingUrls) {
    Instant deadline = clock.instant().plus(timeout);
    Map<String, Integer> counts = new ConcurrentHashMap<>();
    Set<String> visitedUrls = new ConcurrentSkipListSet<>();

    for (String url : startingUrls) {
//      crawlInternal(url, deadline, maxDepth, counts, visitedUrls);
      pool.invoke(new CrawlTask(url, deadline, maxDepth, counts, visitedUrls, ignoredUrls, parserFactory, clock));
    }

    if (counts.isEmpty()) {
      return new CrawlResult.Builder()
              .setWordCounts(counts)
              .setUrlsVisited(visitedUrls.size())
              .build();
    }

    return new CrawlResult.Builder()
            .setWordCounts(WordCounts.sort(counts, popularWordCount))
            .setUrlsVisited(visitedUrls.size())
            .build();
  }

  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }

  final class CrawlTask extends RecursiveAction {
    String url;
    Instant deadline;
    int maxDepth;
    Map<String, Integer> counts;
    Set<String> visitedUrls;
    List<Pattern> ignoredUrls;
    PageParserFactory parserFactory;
    Clock clock;

    @Inject
    public CrawlTask(
            String url,
            Instant deadline,
            int maxDepth,
            Map<String, Integer> counts,
            Set<String> visitedUrls,
            List<Pattern> ignoredUrls,
            PageParserFactory parserFactory,
            Clock clock
            ) {
      this.url = url;
      this.deadline = deadline;
      this.maxDepth = maxDepth;
      this.counts = counts;
      this.visitedUrls = visitedUrls;
      this.ignoredUrls = ignoredUrls;
      this.parserFactory = parserFactory;
      this.clock = clock;
    }



    @Override
    protected void compute() {
      if (maxDepth == 0 || clock.instant().isAfter(deadline)) {
        return;
      }
      for (Pattern pattern : ignoredUrls) {
        if (pattern.matcher(url).matches()) {
          return;
        }
      }
      if (visitedUrls.contains(url)) {
        return;
      }
      visitedUrls.add(url);
      PageParser.Result result = parserFactory.get(url).parse();

      for (Map.Entry<String, Integer> e : result.getWordCounts().entrySet()) {
        if (counts.containsKey(e.getKey())) {
          counts.put(e.getKey(), e.getValue() + counts.get(e.getKey()));
        } else {
          counts.put(e.getKey(), e.getValue());
        }
      }
      for (String link : result.getLinks()) {
        invokeAll(new CrawlTask(link, deadline, maxDepth-1, counts, visitedUrls, ignoredUrls,
                parserFactory, clock));
      }
    }
  }

}
