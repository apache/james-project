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
import java.io.FileNotFoundException;
import java.util.Optional;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.postgres.PostgresConfiguration;
import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.jmap.JMAPModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.MissingArgumentException;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.vault.VaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;

public class PostgresJamesConfiguration implements Configuration {

    private static final Logger LOGGER = LoggerFactory.getLogger("org.apache.james.CONFIGURATION");

    private static final BlobStoreConfiguration.BlobStoreImplName DEFAULT_BLOB_STORE = BlobStoreConfiguration.BlobStoreImplName.POSTGRES;

    public enum EventBusImpl {
        IN_MEMORY, RABBITMQ;

        public static EventBusImpl from(PropertiesProvider configurationProvider) {
            try {
                configurationProvider.getConfiguration("rabbitmq");
                return EventBusImpl.RABBITMQ;
            } catch (FileNotFoundException e) {
                LOGGER.info("RabbitMQ configuration was not found, defaulting to in memory event bus");
                return EventBusImpl.IN_MEMORY;
            } catch (ConfigurationException e) {
                LOGGER.warn("Error reading rabbitmq.xml, defaulting to in memory event bus", e);
                return EventBusImpl.IN_MEMORY;
            }
        }
    }

    public static class Builder {
        private Optional<String> rootDirectory;
        private Optional<ConfigurationPath> configurationPath;
        private Optional<UsersRepositoryModuleChooser.Implementation> usersRepositoryImplementation;
        private Optional<SearchConfiguration> searchConfiguration;
        private Optional<BlobStoreConfiguration> blobStoreConfiguration;
        private Optional<EventBusImpl> eventBusImpl;
        private Optional<VaultConfiguration> deletedMessageVaultConfiguration;
        private Optional<Boolean> jmapEnabled;
        private Optional<Boolean> dropListsEnabled;
        private Optional<Boolean> rlsEnabled;

        private Builder() {
            searchConfiguration = Optional.empty();
            rootDirectory = Optional.empty();
            configurationPath = Optional.empty();
            usersRepositoryImplementation = Optional.empty();
            blobStoreConfiguration = Optional.empty();
            eventBusImpl = Optional.empty();
            deletedMessageVaultConfiguration = Optional.empty();
            jmapEnabled = Optional.empty();
            dropListsEnabled = Optional.empty();
            rlsEnabled = Optional.empty();
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

        public Builder usersRepository(UsersRepositoryModuleChooser.Implementation implementation) {
            this.usersRepositoryImplementation = Optional.of(implementation);
            return this;
        }

        public Builder searchConfiguration(SearchConfiguration searchConfiguration) {
            this.searchConfiguration = Optional.of(searchConfiguration);
            return this;
        }

        public Builder blobStore(BlobStoreConfiguration blobStoreConfiguration) {
            this.blobStoreConfiguration = Optional.of(blobStoreConfiguration);
            return this;
        }

        public Builder eventBusImpl(EventBusImpl eventBusImpl) {
            this.eventBusImpl = Optional.of(eventBusImpl);
            return this;
        }

        public Builder deletedMessageVaultConfiguration(VaultConfiguration vaultConfiguration) {
            this.deletedMessageVaultConfiguration = Optional.of(vaultConfiguration);
            return this;
        }

        public Builder jmapEnabled(Optional<Boolean> jmapEnabled) {
            this.jmapEnabled = jmapEnabled;
            return this;
        }

        public Builder enableDropLists() {
            this.dropListsEnabled = Optional.of(true);
            return this;
        }

        public Builder rlsEnabled(Optional<Boolean> rlsEnabled) {
            this.rlsEnabled = rlsEnabled;
            return this;
        }

        public PostgresJamesConfiguration build() {
            ConfigurationPath configurationPath = this.configurationPath.orElse(new ConfigurationPath(FileSystem.FILE_PROTOCOL_AND_CONF));
            JamesServerResourceLoader directories = new JamesServerResourceLoader(rootDirectory
                .orElseThrow(() -> new MissingArgumentException("Server needs a working.directory env entry")));

            FileSystemImpl fileSystem = new FileSystemImpl(directories);
            PropertiesProvider propertiesProvider = new PropertiesProvider(fileSystem, configurationPath);

            SearchConfiguration searchConfiguration = this.searchConfiguration.orElseGet(Throwing.supplier(
                () -> SearchConfiguration.parse(propertiesProvider)));

            BlobStoreConfiguration blobStoreConfiguration = this.blobStoreConfiguration.orElseGet(Throwing.supplier(
                () -> BlobStoreConfiguration.parse(propertiesProvider, DEFAULT_BLOB_STORE)));
            Preconditions.checkState(!blobStoreConfiguration.getImplementation().equals(BlobStoreConfiguration.BlobStoreImplName.CASSANDRA), "Cassandra BlobStore is not supported by postgres-app.");
            Preconditions.checkState(!blobStoreConfiguration.cacheEnabled(), "BlobStore caching is not supported by postgres-app.");

            FileConfigurationProvider configurationProvider = new FileConfigurationProvider(fileSystem, Basic.builder()
                .configurationPath(configurationPath)
                .workingDirectory(directories.getRootDirectory())
                .build());
            UsersRepositoryModuleChooser.Implementation usersRepositoryChoice = usersRepositoryImplementation.orElseGet(
                () -> UsersRepositoryModuleChooser.Implementation.parse(configurationProvider));

            EventBusImpl eventBusImpl = this.eventBusImpl.orElseGet(() -> EventBusImpl.from(propertiesProvider));

            VaultConfiguration deletedMessageVaultConfiguration = this.deletedMessageVaultConfiguration.orElseGet(() -> {
                try {
                    return VaultConfiguration.from(propertiesProvider.getConfiguration("deletedMessageVault"));
                } catch (FileNotFoundException e) {
                    return VaultConfiguration.DEFAULT;
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });

            boolean rlsEnabled = this.rlsEnabled.orElse(readRLSEnabledFromFile(propertiesProvider));

            boolean jmapEnabled = this.jmapEnabled.orElseGet(() -> {
                try {
                    return JMAPModule.parseConfiguration(propertiesProvider).isEnabled();
                } catch (FileNotFoundException e) {
                    return false;
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });

            boolean dropListsEnabled = this.dropListsEnabled.orElseGet(() -> {
                try {
                    return configurationProvider.getConfiguration("droplists").getBoolean("enabled", false);
                } catch (ConfigurationException e) {
                    return false;
                }
            });

            LOGGER.info("BlobStore configuration {}", blobStoreConfiguration);
            return new PostgresJamesConfiguration(
                configurationPath,
                directories,
                searchConfiguration,
                usersRepositoryChoice,
                blobStoreConfiguration,
                eventBusImpl,
                deletedMessageVaultConfiguration,
                jmapEnabled,
                dropListsEnabled,
                rlsEnabled);
        }

        private boolean readRLSEnabledFromFile(PropertiesProvider propertiesProvider) {
            try {
                return PostgresConfiguration.from(propertiesProvider.getConfiguration(PostgresConfiguration.POSTGRES_CONFIGURATION_NAME))
                    .getRowLevelSecurity()
                    .isRowLevelSecurityEnabled();
            } catch (FileNotFoundException | ConfigurationException e) {
                return false;
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final ConfigurationPath configurationPath;
    private final JamesDirectoriesProvider directories;
    private final SearchConfiguration searchConfiguration;
    private final UsersRepositoryModuleChooser.Implementation usersRepositoryImplementation;
    private final BlobStoreConfiguration blobStoreConfiguration;
    private final EventBusImpl eventBusImpl;
    private final VaultConfiguration deletedMessageVaultConfiguration;
    private final boolean jmapEnabled;
    private final boolean dropListsEnabled;
    private final boolean rlsEnabled;

    private PostgresJamesConfiguration(ConfigurationPath configurationPath,
                                       JamesDirectoriesProvider directories,
                                       SearchConfiguration searchConfiguration,
                                       UsersRepositoryModuleChooser.Implementation usersRepositoryImplementation,
                                       BlobStoreConfiguration blobStoreConfiguration,
                                       EventBusImpl eventBusImpl,
                                       VaultConfiguration deletedMessageVaultConfiguration,
                                       boolean jmapEnabled,
                                       boolean dropListsEnabled,
                                       boolean rlsEnabled) {
        this.configurationPath = configurationPath;
        this.directories = directories;
        this.searchConfiguration = searchConfiguration;
        this.usersRepositoryImplementation = usersRepositoryImplementation;
        this.blobStoreConfiguration = blobStoreConfiguration;
        this.eventBusImpl = eventBusImpl;
        this.deletedMessageVaultConfiguration = deletedMessageVaultConfiguration;
        this.jmapEnabled = jmapEnabled;
        this.dropListsEnabled = dropListsEnabled;
        this.rlsEnabled = rlsEnabled;
    }

    @Override
    public ConfigurationPath configurationPath() {
        return configurationPath;
    }

    @Override
    public JamesDirectoriesProvider directories() {
        return directories;
    }

    public SearchConfiguration searchConfiguration() {
        return searchConfiguration;
    }

    public UsersRepositoryModuleChooser.Implementation getUsersRepositoryImplementation() {
        return usersRepositoryImplementation;
    }

    public BlobStoreConfiguration blobStoreConfiguration() {
        return blobStoreConfiguration;
    }

    public EventBusImpl eventBusImpl() {
        return eventBusImpl;
    }

    public VaultConfiguration getDeletedMessageVaultConfiguration() {
        return deletedMessageVaultConfiguration;
    }

    public boolean isJmapEnabled() {
        return jmapEnabled;
    }

    public boolean isDropListsEnabled() {
        return dropListsEnabled;
    }

    public boolean isRlsEnabled() {
        return rlsEnabled;
    }
}
