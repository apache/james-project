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

package org.apache.james.server.core.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.server.core.MissingArgumentException;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class BasicConfigurationTest {
    @Test
    void configurationPathShouldMatchBeanContract() {
        EqualsVerifier.forClass(Configuration.ConfigurationPath.class)
            .verify();
    }

    @Test
    void buildShouldThrowWhenWorkingDirectoryIsMissing() {
        assertThatThrownBy(() -> Configuration.builder().build())
            .isInstanceOf(MissingArgumentException.class)
            .hasMessage("Server needs a working.directory env entry");
    }

    @Test
    void useWorkingDirectoryEnvPropertyShouldThrowWhenEnvVariableIsUnspecified() {
        assertThatThrownBy(() ->
            Configuration.builder()
                .useWorkingDirectoryEnvProperty())
            .isInstanceOf(MissingArgumentException.class)
            .hasMessage("Server needs a working.directory env entry");
    }

    @Test
    void buildShouldReturnConfigurationWithSuppliedValues() {
        Configuration.Basic configuration = Configuration.builder()
            .workingDirectory("/path")
            .configurationPath(new Configuration.ConfigurationPath("file://myconf/"))
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(configuration.directories().getRootDirectory()).isEqualTo("/path");
            softly.assertThat(configuration.configurationPath().asString()).isEqualTo("file://myconf/");
        });
    }

    @Test
    void buildShouldReturnConfigurationWithClassPathConfigurationPathWhenSpecified() {
        Configuration.Basic configuration = Configuration.builder()
            .workingDirectory("/path")
            .configurationFromClasspath()
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(configuration.directories().getRootDirectory()).isEqualTo("/path");
            softly.assertThat(configuration.configurationPath().asString()).isEqualTo("classpath:");
        });
    }

    @Test
    void configurationPathShouldDefaultToFileConf() {
        Configuration.Basic configuration = Configuration.builder()
            .workingDirectory("/path")
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(configuration.directories().getRootDirectory()).isEqualTo("/path");
            softly.assertThat(configuration.configurationPath().asString()).isEqualTo("file://conf/");
        });
    }

    @Test
    void useWorkingDirectoryEnvPropertyShouldReadSystemProperty() {
        try {
            System.setProperty("working.directory", "/path");

            Configuration.Basic configuration = Configuration.builder()
                .useWorkingDirectoryEnvProperty()
                .build();

            assertThat(configuration.directories().getRootDirectory()).isEqualTo("/path");
        } finally {
            System.clearProperty("working.directory");
        }
    }

    @Test
    void getConfDirectoryShouldReturnConfFolderOfRootDir() {
        try {
            System.setProperty("working.directory", "/path");

            Configuration.Basic configuration = Configuration.builder()
                .useWorkingDirectoryEnvProperty()
                .build();

            assertThat(configuration.directories().getConfDirectory()).isEqualTo("/path/conf/");
        } finally {
            System.clearProperty("working.directory");
        }
    }
}