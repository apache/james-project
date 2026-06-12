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

package org.apache.james.utils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class GuiceSaslMechanismResolver {
    private final SaslMechanismInstantiator instantiator;

    @Inject
    public GuiceSaslMechanismResolver(SaslMechanismInstantiator instantiator) {
        this.instantiator = instantiator;
    }

    public ImmutableList<SaslMechanism> resolve(Collection<String> mechanismClassNames,
                                                HierarchicalConfiguration<ImmutableNode> serverConfiguration,
                                                Map<Class<? extends SaslMechanism>, SaslMechanismFactory> factories) throws ConfigurationException {
        try {
            return mechanismClassNames.stream()
                .map(ClassName::new)
                .map(Throwing.function(className -> resolve(className, serverConfiguration, factories)))
                .collect(Collectors.toMap(
                    mechanism -> normalize(mechanism.name()),
                    Function.identity(),
                    (first, second) -> first,
                    LinkedHashMap::new))
                .values()
                .stream()
                .collect(ImmutableList.toImmutableList());
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ConfigurationException configurationException) {
                throw configurationException;
            }
            throw e;
        }
    }

    private SaslMechanism resolve(ClassName mechanismClassName,
                                  HierarchicalConfiguration<ImmutableNode> serverConfiguration,
                                  Map<Class<? extends SaslMechanism>, SaslMechanismFactory> factories) throws ConfigurationException {
        Class<? extends SaslMechanism> mechanismClass = locate(mechanismClassName);
        SaslMechanismFactory factory = factories.get(mechanismClass);
        if (factory != null) {
            return factory.create(serverConfiguration);
        }
        // Fall back to direct instantiation for mechanisms that do not need server-specific configuration.
        return instantiate(mechanismClassName);
    }

    private Class<? extends SaslMechanism> locate(ClassName mechanismClassName) throws ConfigurationException {
        try {
            return instantiator.locate(mechanismClassName);
        } catch (Exception e) {
            throw new ConfigurationException("Can not load SASL mechanism " + mechanismClassName.getName(), e);
        }
    }

    private SaslMechanism instantiate(ClassName mechanismClassName) throws ConfigurationException {
        try {
            return instantiator.instantiate(mechanismClassName);
        } catch (Exception e) {
            throw new ConfigurationException("Can not load SASL mechanism " + mechanismClassName.getName(), e);
        }
    }

    private String normalize(String mechanismName) {
        return mechanismName.toUpperCase(Locale.US);
    }
}
