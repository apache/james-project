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

import java.io.File;
import java.util.Optional;

import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.modules.queue.rabbitmq.MailQueueViewChoice;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.MissingArgumentException;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.PropertiesProvider;

import com.github.fge.lambdas.Throwing;

public class CassandraRabbitMQJamesConfiguration implements Configuration {
    public static class Builder {
        private Optional<SearchConfiguration> searchConfiguration;
        private Optional<BlobStoreConfiguration> blobStoreConfiguration;
        private Optional<String> rootDirectory;
        private Optional<ConfigurationPath> configurationPath;
        private Optional<MailQueueViewChoice> mailQueueViewChoice;
        private Optional<UsersRepositoryModuleChooser.Implementation> usersRepositoryImplementation;

        private Builder() {
            searchConfiguration = Optional.empty();
            rootDirectory = Optional.empty();
            configurationPath = Optional.empty();
            blobStoreConfiguration = Optional.empty();
            usersRepositoryImplementation = Optional.empty();
            mailQueueViewChoice = Optional.empty();
        }

        public Builder workingDirectory(String path) {
            rootDirectory = Optional.of(path);
            return this;
        }

        public Builder workingDirectory(File file) {
            rootDirectory = Optional.of(file.getAbsolutePath());
            return this;
        }

        public Builder useWorkingDirectoryEnvProperty() {
            rootDirectory = Optional.ofNullable(System.getProperty(WORKING_DIRECTORY));
            if (!rootDirectory.isPresent()) {
                throw new MissingArgumentException("Server needs a working.directory env entry");
            }
            return this;
        }

        public Builder configurationPath(ConfigurationPath path) {
            configurationPath = Optional.of(path);
            return this;
        }

        public Builder configurationFromClasspath() {
            configurationPath = Optional.of(new ConfigurationPath(FileSystem.CLASSPATH_PROTOCOL));
            return this;
        }

        public Builder blobStore(BlobStoreConfiguration blobStoreConfiguration) {
            this.blobStoreConfiguration = Optional.of(blobStoreConfiguration);
            return this;
        }

        public Builder searchConfiguration(SearchConfiguration searchConfiguration) {
            this.searchConfiguration = Optional.of(searchConfiguration);
            return this;
        }

        public Builder usersRepository(UsersRepositoryModuleChooser.Implementation implementation) {
            this.usersRepositoryImplementation = Optional.of(implementation);
            return this;
        }

        public Builder mailQueueViewChoice(MailQueueViewChoice mailQueueViewChoice) {
            this.mailQueueViewChoice = Optional.of(mailQueueViewChoice);
            return this;
        }

        public CassandraRabbitMQJamesConfiguration build() {
            ConfigurationPath configurationPath = this.configurationPath.orElse(new ConfigurationPath(FileSystem.FILE_PROTOCOL_AND_CONF));
            JamesServerResourceLoader directories = new JamesServerResourceLoader(rootDirectory
                .orElseThrow(() -> new MissingArgumentException("Server needs a working.directory env entry")));

            FileSystemImpl fileSystem = new FileSystemImpl(directories);
            BlobStoreConfiguration blobStoreConfiguration = this.blobStoreConfiguration.orElseGet(Throwing.supplier(
                () -> BlobStoreConfiguration.parse(
                    new PropertiesProvider(fileSystem, configurationPath))));

            SearchConfiguration searchConfiguration = this.searchConfiguration.orElseGet(Throwing.supplier(
                () -> SearchConfiguration.parse(
                    new PropertiesProvider(fileSystem, configurationPath))));

            FileConfigurationProvider configurationProvider = new FileConfigurationProvider(fileSystem, Basic.builder()
                .configurationPath(configurationPath)
                .workingDirectory(directories.getRootDirectory())
                .build());
            UsersRepositoryModuleChooser.Implementation usersRepositoryChoice = usersRepositoryImplementation.orElseGet(
                () -> UsersRepositoryModuleChooser.Implementation.parse(configurationProvider));

            MailQueueViewChoice mailQueueViewChoice = this.mailQueueViewChoice.orElseGet(Throwing.supplier(
                () -> MailQueueViewChoice.parse(
                    new PropertiesProvider(fileSystem, configurationPath))));

            return new CassandraRabbitMQJamesConfiguration(
                configurationPath,
                directories,
                blobStoreConfiguration,
                searchConfiguration,
                usersRepositoryChoice,
                mailQueueViewChoice);
        }
    }

    public static CassandraRabbitMQJamesConfiguration.Builder builder() {
        return new Builder();
    }

    private final ConfigurationPath configurationPath;
    private final JamesDirectoriesProvider directories;
    private final BlobStoreConfiguration blobStoreConfiguration;
    private final SearchConfiguration searchConfiguration;
    private final UsersRepositoryModuleChooser.Implementation usersRepositoryImplementation;
    private final MailQueueViewChoice mailQueueViewChoice;

    public CassandraRabbitMQJamesConfiguration(ConfigurationPath configurationPath, JamesDirectoriesProvider directories, BlobStoreConfiguration blobStoreConfiguration, SearchConfiguration searchConfiguration, UsersRepositoryModuleChooser.Implementation usersRepositoryImplementation, MailQueueViewChoice mailQueueViewChoice) {
        this.configurationPath = configurationPath;
        this.directories = directories;
        this.blobStoreConfiguration = blobStoreConfiguration;
        this.searchConfiguration = searchConfiguration;
        this.usersRepositoryImplementation = usersRepositoryImplementation;
        this.mailQueueViewChoice = mailQueueViewChoice;
    }

    public MailQueueViewChoice getMailQueueViewChoice() {
        return mailQueueViewChoice;
    }

    @Override
    public ConfigurationPath configurationPath() {
        return configurationPath;
    }

    @Override
    public JamesDirectoriesProvider directories() {
        return directories;
    }

    public BlobStoreConfiguration blobStoreConfiguration() {
        return blobStoreConfiguration;
    }

    public SearchConfiguration searchConfiguration() {
        return searchConfiguration;
    }

    public UsersRepositoryModuleChooser.Implementation getUsersRepositoryImplementation() {
        return usersRepositoryImplementation;
    }
}
