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

package org.apache.james.transport.matchers.dlp;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.dlp.api.DLPConfigurationStore;
import org.apache.james.dlp.api.DLPRules;
import org.apache.james.metrics.api.GaugeRegistry;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import reactor.core.publisher.Mono;

public interface DlpRulesLoader {

    DlpDomainRules load(Domain domain);

    class Impl implements DlpRulesLoader {

        private final DLPConfigurationStore configurationStore;

        @Inject
        public Impl(DLPConfigurationStore configurationStore) {
            this.configurationStore = configurationStore;
        }

        @Override
        public DlpDomainRules load(Domain domain) {
          return toRules(Mono.from(configurationStore.list(domain)).block());
        }

        private DlpDomainRules toRules(DLPRules items) {
            DlpDomainRules.DlpDomainRulesBuilder builder = DlpDomainRules.builder();
            items.forEach(item ->
                item.getTargets().list().forEach(type ->
                    builder.rule(type, item.getId(), item.getRegexp())
                ));
            return builder.build();
        }
    }

    class Caching implements DlpRulesLoader {
        private final LoadingCache<Domain, DlpDomainRules> cache;

        public Caching(DlpRulesLoader wrapped, GaugeRegistry gaugeRegistry, Duration cacheDuration) {
            cache = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheDuration)
                .recordStats()
                .build(new CacheLoader<>() {
                    @Override
                    public DlpDomainRules load(Domain domain) {
                        return wrapped.load(domain);
                    }
                });

            gaugeRegistry.register("dlp.cache.hitRate", () -> cache.stats().hitRate());
            gaugeRegistry.register("dlp.cache.missCount", () -> cache.stats().missCount());
            gaugeRegistry.register("dlp.cache.hitCount", () -> cache.stats().hitCount());
            gaugeRegistry.register("dlp.cache.size", cache::size);
        }

        @Override
        public DlpDomainRules load(Domain domain) {
            try {
                return cache.get(domain);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
