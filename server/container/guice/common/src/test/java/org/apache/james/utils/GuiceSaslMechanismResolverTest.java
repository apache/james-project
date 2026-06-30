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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;

class GuiceSaslMechanismResolverTest {
    private static final HierarchicalConfiguration<ImmutableNode> EMPTY_CONFIGURATION = new BaseHierarchicalConfiguration();

    @Test
    void resolveShouldUseEnabledDefaultFactoriesWhenNoFactoryClassIsConfigured() throws Exception {
        // GIVEN an absent auth.saslMechanisms configuration and an ordered default factory list
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new ReflectionGuiceLoader());

        // WHEN resolving mechanisms for this server
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of(),
            ImmutableList.of(factory("PLAIN"), factory("OAUTHBEARER")),
            EMPTY_CONFIGURATION);

        // THEN defaults are used in their declared order
        assertThat(mechanisms)
            .extracting(SaslMechanism::name)
            .containsExactly("PLAIN", "OAUTHBEARER");
    }

    @Test
    void resolveShouldResolveSimpleFactoryNameFromDefaultSaslPackage() throws Exception {
        // GIVEN a configured built-in SASL factory simple name
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new ReflectionGuiceLoader());

        // WHEN resolving it
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of("TestingDefaultPackageSaslMechanismFactory"),
            ImmutableList.of(),
            EMPTY_CONFIGURATION);

        // THEN the factory is loaded from org.apache.james.protocols.sasl
        assertThat(mechanisms)
            .extracting(SaslMechanism::name)
            .containsExactly("DEFAULT");
    }

    @Test
    void resolveShouldResolveFullyQualifiedFactoryName() throws Exception {
        // GIVEN a configured extension factory FQCN
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new ReflectionGuiceLoader());

        // WHEN resolving it
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of(ExternalFakeSaslMechanismFactory.class.getCanonicalName()),
            ImmutableList.of(),
            EMPTY_CONFIGURATION);

        // THEN the extension factory is loaded directly
        assertThat(mechanisms)
            .extracting(SaslMechanism::name)
            .containsExactly("EXTERNAL-FAKE");
    }

    @Test
    void resolveShouldUseConfiguredFactoriesInsteadOfDefaults() throws Exception {
        // GIVEN both defaults and an explicit auth.saslMechanisms configuration
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new ReflectionGuiceLoader());

        // WHEN resolving mechanisms
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of(ExternalFakeSaslMechanismFactory.class.getCanonicalName()),
            ImmutableList.of(factory("DEFAULT")),
            EMPTY_CONFIGURATION);

        // THEN the configured list replaces defaults
        assertThat(mechanisms)
            .extracting(SaslMechanism::name)
            .containsExactly("EXTERNAL-FAKE");
    }

    @Test
    void resolveShouldCreateConfiguredFactoriesFromCurrentServerConfiguration() throws Exception {
        // GIVEN two server configurations using the same configured SASL factory
        BaseHierarchicalConfiguration firstConfiguration = new BaseHierarchicalConfiguration();
        firstConfiguration.addProperty("auth.example.realm", "FIRST");
        BaseHierarchicalConfiguration secondConfiguration = new BaseHierarchicalConfiguration();
        secondConfiguration.addProperty("auth.example.realm", "SECOND");
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new ReflectionGuiceLoader());

        // WHEN resolving the same configured factory for each server
        SaslMechanism firstMechanism = testee.resolve(ImmutableList.of(ConfigurableFakeSaslMechanismFactory.class.getCanonicalName()),
                ImmutableList.of(),
                firstConfiguration)
            .getFirst();
        SaslMechanism secondMechanism = testee.resolve(ImmutableList.of(ConfigurableFakeSaslMechanismFactory.class.getCanonicalName()),
                ImmutableList.of(),
                secondConfiguration)
            .getFirst();

        // THEN each mechanism is created from that server's configuration, not from a global singleton
        assertThat(firstMechanism.name()).isEqualTo("FIRST");
        assertThat(secondMechanism.name()).isEqualTo("SECOND");
    }

    @Test
    void resolveShouldPreserveConfiguredOrderForDistinctMechanisms() throws Exception {
        // GIVEN several distinct SASL mechanism factories in a configured order
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new ReflectionGuiceLoader());

        // WHEN resolving them
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of(),
            ImmutableList.of(factory("FIRST"), factory("SECOND"), factory("THIRD")),
            EMPTY_CONFIGURATION);

        // THEN the resolved mechanisms keep the configured order
        assertThat(mechanisms)
            .extracting(SaslMechanism::name)
            .containsExactly("FIRST", "SECOND", "THIRD");
    }

    @Test
    void resolveShouldDeduplicateMechanismNamesCaseInsensitively() throws Exception {
        // GIVEN two factories returning the same SASL mechanism name with different case
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new ReflectionGuiceLoader());

        // WHEN resolving both factories
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of(),
            ImmutableList.of(factory("DUPLICATE"), factory("duplicate")),
            EMPTY_CONFIGURATION);

        // THEN first occurrence wins and order remains stable
        assertThat(mechanisms)
            .extracting(SaslMechanism::name)
            .containsExactly("DUPLICATE");
    }

    @Test
    void resolveShouldFailWhenConfiguredFactoryClassDoesNotExist() {
        // GIVEN a resolver used for configured SASL mechanism factory entries
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new ReflectionGuiceLoader());

        // WHEN resolving an unknown factory class name
        // THEN startup wiring can fail fast with the configured entry in the error
        assertThatThrownBy(() -> testee.resolve(ImmutableList.of("MissingSaslMechanismFactory"),
            ImmutableList.of(),
            EMPTY_CONFIGURATION))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("MissingSaslMechanismFactory");
    }

    private static SaslMechanismFactory factory(String mechanismName) {
        return serverConfiguration -> new FixedNameSaslMechanism(mechanismName);
    }

    private static class ReflectionGuiceLoader implements GuiceLoader {
        @Override
        public <T> T instantiate(ClassName className) throws ClassNotFoundException {
            return this.<T>withNamingSheme(NamingScheme.IDENTITY).instantiate(className);
        }

        @Override
        public <T> InvocationPerformer<T> withNamingSheme(NamingScheme namingSheme) {
            return new ReflectionInvocationPerformer<>(namingSheme);
        }

        @Override
        public <T> InvocationPerformer<T> withChildModule(Module childModule) {
            return new ReflectionInvocationPerformer<>(NamingScheme.IDENTITY);
        }
    }

    private static class ReflectionInvocationPerformer<T> implements GuiceLoader.InvocationPerformer<T> {
        private final NamingScheme namingScheme;

        private ReflectionInvocationPerformer(NamingScheme namingScheme) {
            this.namingScheme = namingScheme;
        }

        @Override
        public T instantiate(ClassName className) throws ClassNotFoundException {
            try {
                return locateClass(className).getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new ClassNotFoundException(className.getName(), e);
            }
        }

        @Override
        public Class<T> locateClass(ClassName className) throws ClassNotFoundException {
            Optional<Class<T>> locatedClass = namingScheme.toFullyQualifiedClassNames(className)
                .map(FullyQualifiedClassName::getName)
                .map(this::tryLocateClass)
                .flatMap(Optional::stream)
                .findFirst();
            return locatedClass.orElseThrow(() -> new ClassNotFoundException(className.getName()));
        }

        @Override
        public GuiceLoader.InvocationPerformer<T> withChildModule(Module childModule) {
            return new ReflectionInvocationPerformer<>(namingScheme);
        }

        @Override
        public GuiceLoader.InvocationPerformer<T> withNamingSheme(NamingScheme namingSheme) {
            return new ReflectionInvocationPerformer<>(namingSheme);
        }

        @SuppressWarnings("unchecked")
        private Optional<Class<T>> tryLocateClass(String className) {
            try {
                return Optional.of((Class<T>) Class.forName(className));
            } catch (ClassNotFoundException e) {
                return Optional.empty();
            }
        }
    }
}
