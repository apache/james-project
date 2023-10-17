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
import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.jmap.draft.JMAPModule;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.MissingArgumentException;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.vault.VaultConfiguration;

import com.github.fge.lambdas.Throwing;

/**
 * See https://issues.apache.org/jira/browse/JAMES-3767
 *
 * Cassandra APP will be removed after 3.8.0 release.
 *
 * Please migrate to the distributed APP.
 */
@Deprecated(forRemoval = true)
public class CassandraJamesServerConfiguration implements Configuration {
    public static class Builder {
        private Optional<SearchConfiguration> searchConfiguration;
        private Optional<String> rootDirectory;
        private Optional<ConfigurationPath> configurationPath;
        private Optional<BlobStoreConfiguration> blobStoreConfiguration;
        private Optional<UsersRepositoryModuleChooser.Implementation> usersRepositoryImplementation;
        private Optional<VaultConfiguration> vaultConfiguration;
        private Optional<Boolean> jmapEnabled;
        private Optional<Boolean> quotaCompatibilityMode;

        private Builder() {
            rootDirectory = Optional.empty();
            configurationPath = Optional.empty();
            searchConfiguration = Optional.empty();
            usersRepositoryImplementation = Optional.empty();
            blobStoreConfiguration = Optional.empty();
            vaultConfiguration = Optional.empty();
            jmapEnabled = Optional.empty();
            quotaCompatibilityMode = Optional.empty();
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

        public Builder vaultConfiguration(VaultConfiguration vaultConfiguration) {
            this.vaultConfiguration = Optional.of(vaultConfiguration);
            return this;
        }

        public Builder quotaCompatibilityModeEnabled(boolean value) {
            this.quotaCompatibilityMode = Optional.of(value);
            return this;
        }

        public CassandraJamesServerConfiguration build() {
            ConfigurationPath configurationPath = this.configurationPath.orElse(new ConfigurationPath(FileSystem.FILE_PROTOCOL_AND_CONF));
            JamesServerResourceLoader directories = new JamesServerResourceLoader(rootDirectory
                .orElseThrow(() -> new MissingArgumentException("Server needs a working.directory env entry")));

            FileSystemImpl fileSystem = new FileSystemImpl(directories);
            PropertiesProvider propertiesProvider = new PropertiesProvider(fileSystem, configurationPath);
            SearchConfiguration searchConfiguration = this.searchConfiguration.orElseGet(Throwing.supplier(
                () -> SearchConfiguration.parse(propertiesProvider)));

            BlobStoreConfiguration blobStoreConfiguration = this.blobStoreConfiguration.orElseGet(Throwing.supplier(
                () -> BlobStoreConfiguration.parse(propertiesProvider)));

            FileConfigurationProvider configurationProvider = new FileConfigurationProvider(fileSystem, Basic.builder()
                .configurationPath(configurationPath)
                .workingDirectory(directories.getRootDirectory())
                .build());
            UsersRepositoryModuleChooser.Implementation usersRepositoryChoice = usersRepositoryImplementation.orElseGet(
                () -> UsersRepositoryModuleChooser.Implementation.parse(configurationProvider));

            VaultConfiguration vaultConfiguration = this.vaultConfiguration.orElseGet(() -> {
                try {
                    return VaultConfiguration.from(propertiesProvider.getConfiguration("deletedMessageVault"));
                } catch (FileNotFoundException e) {
                    return VaultConfiguration.DEFAULT;
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });

            boolean quotaCompatibilityMode = this.quotaCompatibilityMode.orElseGet(() -> {
                try {
                    return propertiesProvider.getConfiguration("cassandra").getBoolean("quota.compatibility.mode", false);
                } catch (FileNotFoundException e) {
                    return false;
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });

            boolean jmapEnabled = this.jmapEnabled.orElseGet(() -> {
                try {
                    return JMAPModule.parseConfiguration(propertiesProvider).isEnabled();
                } catch (FileNotFoundException e) {
                    return false;
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });

            return new CassandraJamesServerConfiguration(configurationPath, directories, searchConfiguration, blobStoreConfiguration, usersRepositoryChoice, vaultConfiguration, jmapEnabled, quotaCompatibilityMode);
        }
    }

    public static CassandraJamesServerConfiguration.Builder builder() {
        return new Builder();
    }

    private final ConfigurationPath configurationPath;
    private final JamesDirectoriesProvider directories;
    private final SearchConfiguration searchConfiguration;
    private final BlobStoreConfiguration blobStoreConfiguration;
    private final UsersRepositoryModuleChooser.Implementation usersRepositoryImplementation;
    private final VaultConfiguration vaultConfiguration;
    private final boolean jmapEnabled;
    private final boolean quotaCompatibilityMode;

    private CassandraJamesServerConfiguration(ConfigurationPath configurationPath, JamesDirectoriesProvider directories,
                                              SearchConfiguration searchConfiguration, BlobStoreConfiguration blobStoreConfiguration,
                                              UsersRepositoryModuleChooser.Implementation usersRepositoryImplementation,
                                              VaultConfiguration vaultConfiguration, boolean jmapEnabled, boolean quotaCompatibilityMode) {
        this.configurationPath = configurationPath;
        this.directories = directories;
        this.searchConfiguration = searchConfiguration;
        this.blobStoreConfiguration = blobStoreConfiguration;
        this.usersRepositoryImplementation = usersRepositoryImplementation;
        this.vaultConfiguration = vaultConfiguration;
        this.jmapEnabled = jmapEnabled;
        this.quotaCompatibilityMode = quotaCompatibilityMode;
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

    public VaultConfiguration getVaultConfiguration() {
        return vaultConfiguration;
    }

    public UsersRepositoryModuleChooser.Implementation getUsersRepositoryImplementation() {
        return usersRepositoryImplementation;
    }

    public BlobStoreConfiguration getBlobStoreConfiguration() {
        return blobStoreConfiguration;
    }

    public boolean isJmapEnabled() {
        return jmapEnabled;
    }

    public boolean isQuotaCompatibilityMode() {
        return quotaCompatibilityMode;
    }
}
