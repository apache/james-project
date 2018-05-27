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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;
import com.google.common.util.concurrent.UncheckedExecutionException;


public class CachingTextExtractor implements TextExtractor {
    private final TextExtractor underlying;
    private final Cache<String, ParsedContent> cache;

    public CachingTextExtractor(TextExtractor underlying, Duration cacheEvictionPeriod, Long cacheWeightInBytes) {
        this.underlying = underlying;

        Weigher<String, ParsedContent> weigher = (key, parsedContent) -> getSize(parsedContent);
        this.cache = CacheBuilder.<String, String>newBuilder()
            .expireAfterAccess(cacheEvictionPeriod.toMillis(), TimeUnit.MILLISECONDS)
            .maximumWeight(cacheWeightInBytes)
            .weigher(weigher)
            .build();
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
}
