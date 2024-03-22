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

package org.apache.james.events;

import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class RoutingKeyConverter {
    private static final char SEPARATOR = ':';

    static class RoutingKey {

        static RoutingKey empty() {
            return new RoutingKey(Optional.empty());
        }

        static RoutingKey of(RegistrationKey key) {
            return new RoutingKey(Optional.of(key));
        }

        private final Optional<RegistrationKey> registrationKey;

        private RoutingKey(Optional<RegistrationKey> registrationKey) {
            this.registrationKey = registrationKey;
        }

        String asString() {
            return registrationKey.map(key -> key.getClass().getSimpleName() + SEPARATOR + key.asString())
                .orElse(EMPTY_ROUTING_KEY);
        }
    }

    @VisibleForTesting
    static RoutingKeyConverter forFactories(RegistrationKey.Factory... factories) {
        return new RoutingKeyConverter(ImmutableSet.copyOf(factories));
    }

    private final Map<String, RegistrationKey.Factory> factories;

    @Inject
    public RoutingKeyConverter(Set<RegistrationKey.Factory> factories) {
        this.factories = factories.stream()
            .collect(ImmutableMap.toImmutableMap(factory -> factory.forClass().getSimpleName(), f -> f));
    }

    RegistrationKey toRegistrationKey(String routingKey) {
        int separatorIndex = routingKey.indexOf(SEPARATOR);
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("Routing key needs to match the 'classFQDN:value' pattern");
        }
        String registrationClass = routingKey.substring(0, separatorIndex);
        String value = routingKey.substring(separatorIndex + 1);

        return Optional.ofNullable(factories.get(registrationClass))
            .orElseThrow(() -> new IllegalArgumentException("No factory for " + registrationClass))
            .fromString(value);
    }
}