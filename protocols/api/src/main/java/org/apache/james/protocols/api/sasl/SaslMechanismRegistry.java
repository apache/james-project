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

package org.apache.james.protocols.api.sasl;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Registry exposing configured SASL mechanisms per protocol.
 */
public final class SaslMechanismRegistry {
    private final ImmutableMap<SaslProtocol, ImmutableList<SaslMechanism>> mechanismsByProtocol;
    private final ImmutableList<SaslAuthenticationServiceFactory<?>> serviceFactories;

    public SaslMechanismRegistry(Collection<SaslMechanism> mechanisms) {
        this(mechanisms, ImmutableList.of());
    }

    public SaslMechanismRegistry(Collection<SaslMechanism> mechanisms, Collection<SaslAuthenticationServiceFactory<?>> serviceFactories) {
        this.mechanismsByProtocol = Arrays.stream(SaslProtocol.values())
            .collect(ImmutableMap.toImmutableMap(Function.identity(), protocol -> mechanismsFor(mechanisms, protocol)));
        this.serviceFactories = ImmutableList.copyOf(serviceFactories);
    }

    /**
     * Finds a configured mechanism by protocol and case-insensitive SASL mechanism name.
     */
    public Optional<SaslMechanism> find(String mechanismName, SaslProtocol protocol) {
        String normalizedName = normalize(mechanismName);
        return mechanismsByProtocol.getOrDefault(protocol, ImmutableList.of())
            .stream()
            .filter(mechanism -> normalize(mechanism.name()).equals(normalizedName))
            .findFirst();
    }

    /**
     * Lists mechanisms configured for the protocol and currently available in the session context.
     */
    public Stream<SaslMechanism> availableFor(SaslProtocol protocol, SaslSessionContext context) {
        return mechanismsByProtocol.getOrDefault(protocol, ImmutableList.of())
            .stream()
            .filter(mechanism -> mechanism.isAvailable(context));
    }

    /**
     * Initializes services for all mechanisms configured for the protocol.
     */
    public void initialize(SaslProtocol protocol, SaslSessionContext context) {
        mechanismsByProtocol.getOrDefault(protocol, ImmutableList.of())
            .stream()
            .flatMap(mechanism -> mechanism.requiredServices(protocol).stream())
            .distinct()
            .forEach(serviceType -> registerService(protocol, context, serviceType));
    }

    /**
     * Lists configured mechanisms for the protocol, regardless of session availability.
     */
    public Stream<SaslMechanism> configuredFor(SaslProtocol protocol) {
        return mechanismsByProtocol.getOrDefault(protocol, ImmutableList.of())
            .stream();
    }

    private ImmutableList<SaslMechanism> mechanismsFor(Collection<SaslMechanism> mechanisms, SaslProtocol protocol) {
        return mechanisms.stream()
            .filter(mechanism -> mechanism.supports(protocol))
            .collect(Collectors.toMap(
                mechanism -> normalize(mechanism.name()),
                Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new))
            .values()
            .stream()
            .collect(ImmutableList.toImmutableList());
    }

    private String normalize(String mechanismName) {
        return mechanismName.toUpperCase(Locale.US);
    }

    private <T> void registerService(SaslProtocol protocol, SaslSessionContext context, Class<T> serviceType) {
        serviceFactory(protocol, serviceType)
            .flatMap(factory -> factory.create(context))
            .ifPresent(service -> context.register(serviceType, service));
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<SaslAuthenticationServiceFactory<T>> serviceFactory(SaslProtocol protocol, Class<T> serviceType) {
        return serviceFactories.stream()
            .filter(factory -> factory.protocol() == protocol)
            .filter(factory -> factory.serviceType().equals(serviceType))
            .findFirst()
            .map(factory -> (SaslAuthenticationServiceFactory<T>) factory);
    }
}
