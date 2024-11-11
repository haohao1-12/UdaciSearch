package com.udacity.webcrawler;

import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;

public final class crawlInternalAction extends RecursiveAction {
    String url;
    Instant deadline;
    int maxDepth;
    Map<String, Integer> counts;
    Set<String> visitedUrls;
    Clock clock;
    List<Pattern> ignoredUrls;
    PageParserFactory parserFactory;


    @Inject
    private crawlInternalAction(String url, Instant deadline, int maxDepth,
                                Map<String, Integer> counts,
                                Set<String> visitedUrls,
                                Clock clock,
                                List<Pattern> ignoredUrls,
                                PageParserFactory parserFactory) {
        this.url = url;
        this.deadline = deadline;
        this.maxDepth = maxDepth;
        this.counts = counts;
        this.visitedUrls = visitedUrls;
        this.clock = clock;
        this.ignoredUrls = ignoredUrls;
        this.parserFactory = parserFactory;
    }

    public static final class Builder{
        private String url;
        private Instant deadline;
        private int maxDepth;
        private Map<String, Integer> counts;
        private Set<String> visitedUrls;
        private Clock clock;
        private List<Pattern> ignoredUrls;
        private PageParserFactory parserFactory;

        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }
        public Builder setDeadline(Instant deadline) {
            this.deadline = deadline;
            return this;
        }
        public Builder setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }
        public Builder setCounts(Map<String, Integer> counts) {
            this.counts = counts;
            return this;
        }
        public Builder setVisitedUrls(Set<String> visitedUrls) {
            this.visitedUrls = visitedUrls;
            return this;
        }
        public Builder setClock(Clock clock) {
            this.clock = clock;
            return this;
        }
        public Builder setIgnoredUrls(List<Pattern> ignoredUrls) {
            this.ignoredUrls = ignoredUrls;
            return this;
        }
        public Builder setParserFactory(PageParserFactory parserFactory) {
            this.parserFactory = parserFactory;
            return this;
        }
        public crawlInternalAction build() {
            return new crawlInternalAction(url, deadline, maxDepth, counts, visitedUrls, clock, ignoredUrls, parserFactory);
        }
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
        if (!visitedUrls.add(url)) {
            return;
        }
        PageParser.Result result = parserFactory.get(url).parse();

        for (Map.Entry<String, Integer> e : result.getWordCounts().entrySet()) {
            counts.merge(e.getKey(), e.getValue(), Integer::sum);
        }

        List<crawlInternalAction> actions = new ArrayList<>();

        for (String link : result.getLinks()) {
            actions.add(new crawlInternalAction(link, deadline, maxDepth-1, counts, visitedUrls, clock, ignoredUrls, parserFactory));
        }
        invokeAll(actions);
    }
}
