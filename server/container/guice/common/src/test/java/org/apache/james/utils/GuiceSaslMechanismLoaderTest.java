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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismLoadingException;
import org.apache.james.protocols.api.sasl.TestingDefaultPackageSaslMechanism;
import org.junit.jupiter.api.Test;

class GuiceSaslMechanismLoaderTest {
    private static final FileSystem THROWING_FILE_SYSTEM = new FileSystem() {
        @Override
        public InputStream getResource(String url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getFile(String fileURL) throws FileNotFoundException {
            throw new FileNotFoundException();
        }

        @Override
        public File getBasedir() {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    void loadShouldResolveSimpleNameFromDefaultSaslPackage() {
        // GIVEN a loader using James default SASL package as implicit prefix
        GuiceSaslMechanismLoader testee = new GuiceSaslMechanismLoader(
            GuiceGenericLoader.forTesting(new ExtendedClassLoader(THROWING_FILE_SYSTEM)));

        // WHEN loading a simple class name
        List<SaslMechanism> mechanisms = testee.load(List.of("TestingDefaultPackageSaslMechanism"));

        // THEN the mechanism is instantiated from org.apache.james.protocols.api.sasl
        assertThat(mechanisms).hasOnlyElementsOfType(TestingDefaultPackageSaslMechanism.class);
    }

    @Test
    void loadShouldResolveFullyQualifiedClassName() {
        // GIVEN a loader that also accepts extension class names
        GuiceSaslMechanismLoader testee = new GuiceSaslMechanismLoader(
            GuiceGenericLoader.forTesting(new ExtendedClassLoader(THROWING_FILE_SYSTEM)));

        // WHEN loading a fully qualified class name
        List<SaslMechanism> mechanisms = testee.load(List.of(ExternalFakeSaslMechanism.class.getCanonicalName()));

        // THEN the mechanism is instantiated without relying on the default package
        assertThat(mechanisms).hasOnlyElementsOfType(ExternalFakeSaslMechanism.class);
    }

    @Test
    void loadShouldFailWhenClassDoesNotExist() {
        // GIVEN a loader used for configured SASL mechanism entries
        GuiceSaslMechanismLoader testee = new GuiceSaslMechanismLoader(
            GuiceGenericLoader.forTesting(new ExtendedClassLoader(THROWING_FILE_SYSTEM)));

        // WHEN loading an unknown class name
        // THEN startup wiring can fail fast with the configured entry in the error
        assertThatThrownBy(() -> testee.load(List.of("MissingSaslMechanism")))
            .isInstanceOf(SaslMechanismLoadingException.class)
            .hasMessageContaining("MissingSaslMechanism");
    }

    @Test
    void loadShouldFailWhenClassIsNotASaslMechanism() {
        // GIVEN a configured class name that exists but does not implement the SASL SPI
        GuiceSaslMechanismLoader testee = new GuiceSaslMechanismLoader(
            GuiceGenericLoader.forTesting(new ExtendedClassLoader(THROWING_FILE_SYSTEM)));

        // WHEN loading that class
        // THEN the loader rejects it instead of returning an invalid mechanism
        assertThatThrownBy(() -> testee.load(List.of(Object.class.getCanonicalName())))
            .isInstanceOf(SaslMechanismLoadingException.class)
            .hasMessageContaining(Object.class.getCanonicalName());
    }
}
