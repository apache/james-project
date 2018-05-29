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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.Weigher;
import com.google.common.util.concurrent.UncheckedExecutionException;

public class CachingTextExtractor implements TextExtractor {
    private final TextExtractor underlying;
    private final Cache<String, ParsedContent> cache;
    private final Metric weightMetric;

    public CachingTextExtractor(TextExtractor underlying, Duration cacheEvictionPeriod, Long cacheWeightInBytes, MetricFactory metricFactory) {
        this.underlying = underlying;
        this.weightMetric = metricFactory.generate("textExtractor.cache.weight");

        Weigher<String, ParsedContent> weigher =
            (key, parsedContent) -> {
                int size = getSize(parsedContent);
                weightMetric.add(size);
                return size;
            };
        RemovalListener<String, ParsedContent> removalListener =
            notification -> Optional.ofNullable(notification.getValue())
                .map(this::getSize)
                .ifPresent(weightMetric::remove);

        this.cache = CacheBuilder.<String, String>newBuilder()
            .expireAfterAccess(cacheEvictionPeriod.toMillis(), TimeUnit.MILLISECONDS)
            .maximumWeight(cacheWeightInBytes)
            .weigher(weigher)
            .recordStats()
            .removalListener(removalListener)
            .build();
        recordStats(metricFactory);
    }

    public void recordStats(MetricFactory metricFactory) {
        metricFactory.register(
            "textExtractor.cache.hit.rate",
            () -> cache.stats().hitRate());
        metricFactory.register(
            "textExtractor.cache.hit.count",
            () -> cache.stats().hitCount());
        metricFactory.register(
            "textExtractor.cache.load.count",
            () -> cache.stats().loadCount());
        metricFactory.register(
            "textExtractor.cache.eviction.count",
            () -> cache.stats().evictionCount());
        metricFactory.register(
            "textExtractor.cache.load.exception.rate",
            () -> cache.stats().loadExceptionRate());
        metricFactory.register(
            "textExtractor.cache.load.miss.rate",
            () -> cache.stats().missRate());
        metricFactory.register(
            "textExtractor.cache.load.miss.count",
            () -> cache.stats().missCount());
        metricFactory.register(
            "textExtractor.cache.size",
            cache::size);
    }

    private int getSize(ParsedContent parsedContent) {
        return parsedContent.getTextualContent()
            .map(String::length)
            .map(this::utf16LengthToBytesCount)
            .orElse(0);
    }

    private int utf16LengthToBytesCount(Integer value) {
        return value * 2;
    }

    @Override
    public ParsedContent extractContent(InputStream inputStream, String contentType) throws Exception {
        byte[] bytes = IOUtils.toByteArray(inputStream);
        String key = DigestUtils.sha256Hex(bytes);
        try {
            return cache.get(key,
                () -> underlying.extractContent(new ByteArrayInputStream(bytes), contentType));
        } catch (UncheckedExecutionException | ExecutionException e) {
            throw unwrap(e);
        }
    }

    private Exception unwrap(Exception e) {
        return Optional.ofNullable(e.getCause())
            .filter(throwable -> throwable instanceof Exception)
            .map(throwable -> (Exception) throwable)
            .orElse(e);
    }

    @VisibleForTesting
    long size() {
        return cache.size();
    }
}
