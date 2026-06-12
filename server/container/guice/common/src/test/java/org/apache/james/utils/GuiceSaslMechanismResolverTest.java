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

import java.util.Map;
import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.api.sasl.TestingDefaultPackageSaslMechanism;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class GuiceSaslMechanismResolverTest {
    private static final HierarchicalConfiguration<ImmutableNode> EMPTY_CONFIGURATION = new BaseHierarchicalConfiguration();

    @Test
    void resolveShouldResolveSimpleNameFromDefaultSaslPackage() throws Exception {
        // GIVEN a resolver using a test instantiator that models James default SASL package resolution
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new MapBackedSaslMechanismInstantiator());

        // WHEN resolving a simple class name
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of("TestingDefaultPackageSaslMechanism"),
            EMPTY_CONFIGURATION, ImmutableMap.of());

        // THEN the mechanism is instantiated from org.apache.james.protocols.api.sasl
        assertThat(mechanisms).hasOnlyElementsOfType(TestingDefaultPackageSaslMechanism.class);
    }

    @Test
    void resolveShouldResolveFullyQualifiedClassName() throws Exception {
        // GIVEN a resolver that also accepts extension class names
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new MapBackedSaslMechanismInstantiator());

        // WHEN resolving a fully qualified class name
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of(ExternalFakeSaslMechanism.class.getCanonicalName()),
            EMPTY_CONFIGURATION, ImmutableMap.of());

        // THEN the mechanism is instantiated without relying on the default package
        assertThat(mechanisms).hasOnlyElementsOfType(ExternalFakeSaslMechanism.class);
    }

    @Test
    void resolveShouldUseFactoryBindingBeforeDirectInstantiation() throws Exception {
        // GIVEN a factory binding for a configured mechanism class
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.example.realm", "example.org");
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new MapBackedSaslMechanismInstantiator());
        SaslMechanismFactory factory = serverConfiguration ->
            new FactoryBackedSaslMechanism(serverConfiguration.getString("auth.example.realm"));

        // WHEN resolving that class name
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of(ExternalFakeSaslMechanism.class.getCanonicalName()),
            configuration, ImmutableMap.of(ExternalFakeSaslMechanism.class, factory));

        // THEN the factory creates the server-specific mechanism instance
        assertThat(mechanisms)
            .singleElement()
            .isInstanceOfSatisfying(FactoryBackedSaslMechanism.class,
                mechanism -> assertThat(mechanism.realm()).isEqualTo("example.org"));
    }

    @Test
    void resolveShouldCreateFactoryBackedMechanismsFromCurrentServerConfiguration() throws Exception {
        // GIVEN two server configurations using the same configured SASL mechanism class
        BaseHierarchicalConfiguration firstConfiguration = new BaseHierarchicalConfiguration();
        firstConfiguration.addProperty("auth.example.realm", "first.example.org");
        BaseHierarchicalConfiguration secondConfiguration = new BaseHierarchicalConfiguration();
        secondConfiguration.addProperty("auth.example.realm", "second.example.org");
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new MapBackedSaslMechanismInstantiator());
        SaslMechanismFactory factory = serverConfiguration ->
            new FactoryBackedSaslMechanism(serverConfiguration.getString("auth.example.realm"));

        // WHEN resolving the same configured mechanism for each server
        SaslMechanism firstMechanism = testee.resolve(ImmutableList.of(ExternalFakeSaslMechanism.class.getCanonicalName()),
            firstConfiguration, ImmutableMap.of(ExternalFakeSaslMechanism.class, factory))
            .getFirst();
        SaslMechanism secondMechanism = testee.resolve(ImmutableList.of(ExternalFakeSaslMechanism.class.getCanonicalName()),
            secondConfiguration, ImmutableMap.of(ExternalFakeSaslMechanism.class, factory))
            .getFirst();

        // THEN each mechanism is created from that server's configuration, not from a global singleton
        assertThat(firstMechanism)
            .isInstanceOfSatisfying(FactoryBackedSaslMechanism.class,
                mechanism -> assertThat(mechanism.realm()).isEqualTo("first.example.org"));
        assertThat(secondMechanism)
            .isInstanceOfSatisfying(FactoryBackedSaslMechanism.class,
                mechanism -> assertThat(mechanism.realm()).isEqualTo("second.example.org"));
    }

    @Test
    void resolveShouldPreserveConfiguredOrderForDistinctMechanisms() throws Exception {
        // GIVEN a configured mechanism list with distinct SASL mechanism names
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new MapBackedSaslMechanismInstantiator());

        // WHEN resolving the list
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of(
                ExternalFakeSaslMechanism.class.getCanonicalName(),
                "TestingDefaultPackageSaslMechanism"),
            EMPTY_CONFIGURATION, ImmutableMap.of());

        // THEN configured order is preserved
        assertThat(mechanisms)
            .extracting(SaslMechanism::name)
            .containsExactly("EXTERNAL-FAKE", "DEFAULT");
    }

    @Test
    void resolveShouldDeduplicateMechanismNamesCaseInsensitively() throws Exception {
        // GIVEN two configured classes returning the same SASL mechanism name with different case
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new MapBackedSaslMechanismInstantiator());

        // WHEN resolving both classes
        ImmutableList<SaslMechanism> mechanisms = testee.resolve(ImmutableList.of(
                DuplicateUpperCaseSaslMechanism.class.getCanonicalName(),
                DuplicateLowerCaseSaslMechanism.class.getCanonicalName()),
            EMPTY_CONFIGURATION, ImmutableMap.of());

        // THEN first occurrence wins and configured order remains stable
        assertThat(mechanisms)
            .hasSize(1)
            .hasOnlyElementsOfType(DuplicateUpperCaseSaslMechanism.class);
    }

    @Test
    void resolveShouldFailWhenClassDoesNotExist() {
        // GIVEN a resolver used for configured SASL mechanism entries
        GuiceSaslMechanismResolver testee = new GuiceSaslMechanismResolver(new MapBackedSaslMechanismInstantiator());

        // WHEN resolving an unknown class name
        // THEN startup wiring can fail fast with the configured entry in the error
        assertThatThrownBy(() -> testee.resolve(ImmutableList.of("MissingSaslMechanism"), EMPTY_CONFIGURATION, ImmutableMap.of()))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("MissingSaslMechanism");
    }

    private static class MapBackedSaslMechanismInstantiator implements SaslMechanismInstantiator {
        private final Map<String, Class<? extends SaslMechanism>> classes = ImmutableMap.<String, Class<? extends SaslMechanism>>builder()
            .put("TestingDefaultPackageSaslMechanism", TestingDefaultPackageSaslMechanism.class)
            .put(TestingDefaultPackageSaslMechanism.class.getCanonicalName(), TestingDefaultPackageSaslMechanism.class)
            .put(ExternalFakeSaslMechanism.class.getCanonicalName(), ExternalFakeSaslMechanism.class)
            .put(FactoryBackedSaslMechanism.class.getCanonicalName(), FactoryBackedSaslMechanism.class)
            .put(DuplicateUpperCaseSaslMechanism.class.getCanonicalName(), DuplicateUpperCaseSaslMechanism.class)
            .put(DuplicateLowerCaseSaslMechanism.class.getCanonicalName(), DuplicateLowerCaseSaslMechanism.class)
            .build();

        @Override
        public Class<? extends SaslMechanism> locate(ClassName className) throws ClassNotFoundException {
            return Optional.ofNullable(classes.get(className.getName()))
                .orElseThrow(() -> new ClassNotFoundException(className.getName()));
        }

        @Override
        public SaslMechanism instantiate(ClassName className) throws ClassNotFoundException {
            try {
                return locate(className).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new ClassNotFoundException(className.getName(), e);
            }
        }
    }

    public static class FactoryBackedSaslMechanism extends FixedNameSaslMechanism {
        private final String realm;

        public FactoryBackedSaslMechanism() {
            this("unused");
        }

        private FactoryBackedSaslMechanism(String realm) {
            super("FACTORY");
            this.realm = realm;
        }

        private String realm() {
            return realm;
        }
    }

    public static class DuplicateUpperCaseSaslMechanism extends FixedNameSaslMechanism {
        public DuplicateUpperCaseSaslMechanism() {
            super("DUPLICATE");
        }
    }

    public static class DuplicateLowerCaseSaslMechanism extends FixedNameSaslMechanism {
        public DuplicateLowerCaseSaslMechanism() {
            super("duplicate");
        }
    }

    private abstract static class FixedNameSaslMechanism implements SaslMechanism {
        private final String name;

        private FixedNameSaslMechanism(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public SaslExchange start(SaslInitialRequest request) {
            return new FixedStepExchange();
        }
    }

    private record FixedStepExchange() implements SaslExchange {
        @Override
        public SaslStep firstStep() {
            return new SaslStep.Failure("not implemented");
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            return new SaslStep.Failure("not implemented");
        }

        @Override
        public void abort() {
        }

        @Override
        public void close() {
        }
    }
}
