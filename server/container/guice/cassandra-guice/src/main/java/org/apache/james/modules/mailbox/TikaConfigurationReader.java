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

package org.apache.james.modules.mailbox;

import java.time.Duration;
import java.util.Optional;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.util.Size;
import org.apache.james.util.TimeConverter;

import com.github.fge.lambdas.Throwing;

public class TikaConfigurationReader {
    public static final String TIKA_ENABLED = "tika.enabled";
    public static final String TIKA_CACHE_ENABLED = "tika.cache.enabled";
    public static final String TIKA_HOST = "tika.host";
    public static final String TIKA_PORT = "tika.port";
    public static final String TIKA_TIMEOUT_IN_MS = "tika.timeoutInMillis";
    public static final String TIKA_CACHE_EVICTION_PERIOD = "tika.cache.eviction.period";
    public static final String TIKA_CACHE_WEIGHT_MAX = "tika.cache.weight.max";

    public static TikaConfiguration readTikaConfiguration(PropertiesConfiguration configuration) {
        Optional<Boolean> enabled = Optional.ofNullable(
            configuration.getBoolean(TIKA_ENABLED, null));

        Optional<Boolean> cacheEnabled = Optional.ofNullable(
            configuration.getBoolean(TIKA_CACHE_ENABLED, null));

        Optional<String> host = Optional.ofNullable(
            configuration.getString(TIKA_HOST, null));

        Optional<Integer> port = Optional.ofNullable(
            configuration.getInteger(TIKA_PORT, null));

        Optional<Integer> timeoutInMillis = Optional.ofNullable(
            configuration.getInteger(TIKA_TIMEOUT_IN_MS, null));

        Optional<Duration> cacheEvictionPeriod = Optional.ofNullable(
            configuration.getString(TIKA_CACHE_EVICTION_PERIOD,
                null))
            .map(rawString -> TimeConverter.getMilliSeconds(rawString, TimeConverter.Unit.SECONDS))
            .map(Duration::ofMillis);

        Optional<Long> cacheWeight = Optional.ofNullable(
            configuration.getString(TIKA_CACHE_WEIGHT_MAX, null))
            .map(Throwing.function(Size::parse))
            .map(Size::asBytes);

        return TikaConfiguration.builder()
            .enable(enabled)
            .host(host)
            .port(port)
            .timeoutInMillis(timeoutInMillis)
            .cacheEnable(cacheEnabled)
            .cacheEvictionPeriod(cacheEvictionPeriod)
            .cacheWeightInBytes(cacheWeight)
            .build();
    }
}
