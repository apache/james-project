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

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.james.server.core.MissingArgumentException;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.Configuration.ConfigurationPath;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CassandraRabbitMQJamesConfigurationTest {
    @Test
    void buildShouldThrowWhenWorkingDirectoryMissing() {
        assertThatThrownBy(() -> CassandraRabbitMQJamesConfiguration.builder().build())
            .isInstanceOf(MissingArgumentException.class)
            .hasMessage("Server needs a working.directory env entry");
    }

    @Test
    void buildShouldThrowWhenLuceneSearch() {
        assertThatThrownBy(() -> CassandraRabbitMQJamesConfiguration.builder()
                .workingDirectory("/path")
                .configurationPath(new ConfigurationPath("file://myconf/"))
                .searchConfiguration(SearchConfiguration.lucene())
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Lucene search is not supported by distributed-app: its index is local to each node.");
    }

    @Test
    void buildShouldNotSuggestLuceneWhenSearchImplementationIsUnknown(@TempDir File workingDirectory) throws Exception {
        Path configurationDirectory = Files.createDirectory(workingDirectory.toPath().resolve("conf"));
        Files.writeString(configurationDirectory.resolve("search.properties"), "implementation=opensearh");

        assertThatThrownBy(() -> CassandraRabbitMQJamesConfiguration.builder()
                .workingDirectory(workingDirectory)
                .configurationPath(new ConfigurationPath("file://conf/"))
                .build())
            .hasMessageContaining("should be")
            .hasMessageNotContaining("lucene");
    }

    @Test
    void useWorkingDirectoryEnvPropertyShouldThrowWhenEnvVariableIsUnspecified() {
        assertThatThrownBy(() ->
            CassandraRabbitMQJamesConfiguration.builder()
                .useWorkingDirectoryEnvProperty())
            .isInstanceOf(MissingArgumentException.class)
            .hasMessage("Server needs a working.directory env entry");
    }

    @Test
    void buildShouldReturnConfigurationWithSuppliedValues() {
        Configuration configuration = CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory("/path")
            .configurationPath(new ConfigurationPath("file://myconf/"))
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(configuration.directories().getRootDirectory()).isEqualTo("/path");
            softly.assertThat(configuration.configurationPath().asString()).isEqualTo("file://myconf/");
        });
    }

    @Test
    void buildShouldReturnConfigurationWithClassPathConfigurationPathWhenSpecified() {
        Configuration configuration = CassandraRabbitMQJamesConfiguration.builder()
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
        Configuration configuration = CassandraRabbitMQJamesConfiguration.builder()
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

            Configuration configuration = CassandraRabbitMQJamesConfiguration.builder()
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

            Configuration configuration = CassandraRabbitMQJamesConfiguration.builder()
                .useWorkingDirectoryEnvProperty()
                .build();

            assertThat(configuration.directories().getConfDirectory()).isEqualTo("/path/conf/");
        } finally {
            System.clearProperty("working.directory");
        }
    }
}