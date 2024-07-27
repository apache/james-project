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

package org.apache.james.transport.matchers;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.util.DurationParser;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;

public record CacheSettings(Duration duration, int size) {
    public static Optional<CacheSettings> parse(String condition) {
        if (!condition.contains("?")) {
            return Optional.empty();
        }
        String cacheSettings = condition.substring(condition.indexOf('?') + 1);
        ImmutableMap<String, String> cacheSettingMap = Splitter.on('&').trimResults().omitEmptyStrings().splitToStream(cacheSettings)
            .map(CacheSettings::asPair)
            .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue));
        boolean enabled = Optional.ofNullable(cacheSettingMap.get("cacheenabled"))
            .map(Boolean::parseBoolean)
            .orElse(true);
        int size = Optional.ofNullable(cacheSettingMap.get("cachesize"))
            .map(Integer::parseInt)
            .orElse(10_000);
        Duration duration = Optional.ofNullable(cacheSettingMap.get("cacheduration"))
            .map(DurationParser::parse)
            .orElse(Duration.ofDays(1));

        Preconditions.checkArgument(size > 0, "size must be an integer greater than 0");

        if (!enabled) {
            return Optional.empty();
        }
        return Optional.of(new CacheSettings(duration, size));
    }

    private static Pair<String, String> asPair(String condition) {
        Preconditions.checkArgument(condition.contains("="), "Each pair of the cache setting must be of the form key=value. Got " + condition);

        int separatorPosition = condition.indexOf('=');
        String key = condition.substring(0, separatorPosition).toLowerCase(Locale.US);
        String value = condition.substring(separatorPosition + 1);
        return Pair.of(key, value);
    }

    Cache<String, Boolean> createAssociatedCache() {
        return CacheBuilder.newBuilder().expireAfterWrite(duration).maximumSize(size).build();
    }
}
