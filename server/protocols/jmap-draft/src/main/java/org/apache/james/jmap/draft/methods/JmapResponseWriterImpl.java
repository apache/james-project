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

package org.apache.james.jmap.draft.methods;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.jmap.draft.json.ObjectMapperFactory;
import org.apache.james.jmap.draft.model.InvocationResponse;
import org.apache.james.jmap.draft.model.Property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.github.fge.lambdas.Throwing;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;

public class JmapResponseWriterImpl implements JmapResponseWriter {
    public static final String PROPERTIES_FILTER = "propertiesFilter";

    private static class CachedObjectMapper {
        private final ObjectMapper objectMapper;
        private final Optional<? extends Set<? extends Property>> properties;
        private final Optional<? extends Set<? extends Property>> subProperties;

        private CachedObjectMapper(ObjectMapper objectMapper,
                                   Optional<? extends Set<? extends Property>> properties,
                                   Optional<? extends Set<? extends Property>> subProperties) {
            this.objectMapper = objectMapper;
            this.properties = properties;
            this.subProperties = subProperties;
        }

        Optional<ObjectMapper> cachedMapperIfApplicable(JmapResponse jmapResponse) {
            if (jmapResponse.getProperties().equals(properties)
                && jmapResponse.getFilterProvider().map(Pair::getKey).equals(subProperties)) {

                return Optional.of(objectMapper);
            }
            return Optional.empty();
        }
    }


    private final ObjectMapper objectMapper;
    private final Cache<Long, CachedObjectMapper> writerCache;

    @Inject
    public JmapResponseWriterImpl(ObjectMapperFactory objectMapperFactory) {
        this.objectMapper = objectMapperFactory.forWriting();

        writerCache = CacheBuilder.newBuilder()
            .maximumSize(128)
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();
    }

    @Override
    public Flux<InvocationResponse> formatMethodResponse(Flux<JmapResponse> jmapResponses) {
        return jmapResponses.map(Throwing.function(jmapResponse -> {
            ObjectMapper objectMapper = retrieveObjectMapperFromCache(jmapResponse)
                .cachedMapperIfApplicable(jmapResponse)
                .orElseGet(() -> newConfiguredObjectMapper(jmapResponse));

            return new InvocationResponse(
                    jmapResponse.getResponseName(),
                    objectMapper.valueToTree(jmapResponse.getResponse()),
                    jmapResponse.getMethodCallId());
        }));
    }

    private CachedObjectMapper retrieveObjectMapperFromCache(JmapResponse jmapResponse) throws ExecutionException {
        return writerCache.get(computeCachingKey(jmapResponse), () -> new CachedObjectMapper(
            newConfiguredObjectMapper(jmapResponse),
            jmapResponse.getProperties(),
            jmapResponse.getFilterProvider().map(Pair::getKey)));
    }

    private ObjectMapper newConfiguredObjectMapper(JmapResponse jmapResponse) {
        FilterProvider filterProvider = jmapResponse
            .getFilterProvider()
            .map(Pair::getValue)
            .orElseGet(SimpleFilterProvider::new)
            .setDefaultFilter(SimpleBeanPropertyFilter.serializeAll())
            .addFilter(PROPERTIES_FILTER, getPropertiesFilter(jmapResponse.getProperties()));

        return objectMapper.copy().setFilterProvider(filterProvider);

    }

    private long computeCachingKey(JmapResponse jmapResponse) {
        long lowBits = jmapResponse.getProperties().hashCode();
        long highBits = jmapResponse.getFilterProvider()
            .map(Pair::getKey)
            .map(Object::hashCode)
            .map(i -> (long) i)
            .orElse((long) jmapResponse.getResponseName().hashCode());

        return lowBits + (highBits >> 32);
    }
    
    private PropertyFilter getPropertiesFilter(Optional<? extends Set<? extends Property>> properties) {
        return properties
                .map(this::toFieldNames)
                .map(SimpleBeanPropertyFilter::filterOutAllExcept)
                .orElse(SimpleBeanPropertyFilter.serializeAll());
    }
    
    private Set<String> toFieldNames(Set<? extends Property> properties) {
        return properties.stream()
            .map(Property::asFieldName)
            .collect(ImmutableSet.toImmutableSet());
    }
}
