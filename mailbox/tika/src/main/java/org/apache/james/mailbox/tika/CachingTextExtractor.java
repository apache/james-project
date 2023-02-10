/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.tika;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.Weigher;
import com.google.common.hash.Hashing;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CachingTextExtractor implements TextExtractor {
    private final TextExtractor underlying;
    private final AsyncCache<String, ParsedContent> cache;
    private final Metric weightMetric;

    public CachingTextExtractor(TextExtractor underlying, Duration cacheEvictionPeriod, Long cacheWeightInBytes,
                                MetricFactory metricFactory, GaugeRegistry gaugeRegistry) {
        this.underlying = underlying;
        this.weightMetric = metricFactory.generate("textExtractor.cache.weight");

        RemovalListener<String, ParsedContent> removalListener =
            (key, value, removalCause) -> Optional.ofNullable(value)
                .map(this::computeWeight)
                .ifPresent(weightMetric::remove);

        Weigher<String, ParsedContent> weigher =
            (key, parsedContent) -> computeWeight(parsedContent);

        cache = Caffeine.newBuilder()
            .expireAfterAccess(cacheEvictionPeriod)
            .maximumWeight(cacheWeightInBytes)
            .weigher(weigher)
            .evictionListener(removalListener)
            .recordStats()
            .buildAsync();

        recordStats(gaugeRegistry);
    }

    public void recordStats(GaugeRegistry gaugeRegistry) {
        gaugeRegistry
            .register(
                "textExtractor.cache.hit.rate",
                () -> cache.synchronous().stats().hitRate())
            .register(
                "textExtractor.cache.hit.count",
                () -> cache.synchronous().stats().hitCount());
            gaugeRegistry.register(
                "textExtractor.cache.load.count",
                () -> cache.synchronous().stats().loadCount())
            .register(
                "textExtractor.cache.eviction.count",
                () -> cache.synchronous().stats().evictionCount())
            .register(
                "textExtractor.cache.load.exception.rate",
                () -> cache.synchronous().stats().loadFailureRate())
            .register(
                "textExtractor.cache.load.miss.rate",
                () -> cache.synchronous().stats().missRate())
            .register(
                "textExtractor.cache.load.miss.count",
                () -> cache.synchronous().stats().missCount())
            .register(
                "textExtractor.cache.size",
                cache.synchronous()::estimatedSize);
    }

    private int computeWeight(ParsedContent parsedContent) {
        return parsedContent.getTextualContent()
            .map(String::length)
            .map(this::utf16LengthToBytesCount)
            .orElse(0);
    }

    private int utf16LengthToBytesCount(Integer value) {
        return value * 2;
    }

    @Override
    public Mono<ParsedContent> extractContentReactive(InputStream inputStream, ContentType contentType) {
        return Mono
                .fromCallable(() -> IOUtils.toByteArray(inputStream))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(bytes ->
                    Mono.fromCallable(() -> Hashing.sha256().hashBytes(bytes).toString())
                        .subscribeOn(Schedulers.parallel())
                        .publishOn(Schedulers.boundedElastic())
                        .flatMap(key -> Mono.fromFuture(cache.get(key, (a, b) -> retrieveAndUpdateWeight(bytes, contentType).toFuture())))
                );
    }

    @Override
    public ParsedContent extractContent(InputStream inputStream, ContentType contentType) {
        return extractContentReactive(inputStream, contentType).block();
    }

    private Mono<ParsedContent> retrieveAndUpdateWeight(byte[] bytes, ContentType contentType) {
        return underlying.extractContentReactive(new ByteArrayInputStream(bytes), contentType)
            .doOnNext(next -> weightMetric.add(computeWeight(next)));
    }

}
