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

import java.io.FileNotFoundException;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.server.blob.deduplication.StorageStrategy;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

import io.vavr.control.Try;

/**
 * See https://issues.apache.org/jira/browse/JAMES-3767
 *
 * Cassandra APP will be removed after 3.8.0 release.
 *
 * Please migrate to the distributed APP.
 */
@Deprecated(forRemoval = true)
public class BlobStoreConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlobStoreConfiguration.class);

    @FunctionalInterface
    public interface RequireStoringStrategy {
        RequireCryptoConfig strategy(StorageStrategy storageStrategy);

        default RequireCryptoConfig passthrough() {
            return strategy(StorageStrategy.PASSTHROUGH);
        }

        default RequireCryptoConfig deduplication() {
            return strategy(StorageStrategy.DEDUPLICATION);
        }
    }

    @FunctionalInterface
    public interface RequireCryptoConfig {
        BlobStoreConfiguration cryptoConfig(Optional<CryptoConfig> cryptoConfig);

        default BlobStoreConfiguration noCryptoConfig() {
            return cryptoConfig(Optional.empty());
        }

        default BlobStoreConfiguration cryptoConfig(CryptoConfig cryptoConfig) {
            return cryptoConfig(Optional.of(cryptoConfig));
        }
    }

    public static RequireStoringStrategy builder() {
        return storageStrategy -> cryptoConfig ->
            new BlobStoreConfiguration(storageStrategy, cryptoConfig);
    }

    static final String ENCRYPTION_ENABLE_PROPERTY = "encryption.aes.enable";
    static final String ENCRYPTION_PASSWORD_PROPERTY = "encryption.aes.password";
    static final String ENCRYPTION_SALT_PROPERTY = "encryption.aes.salt";
    static final String DEDUPLICATION_ENABLE_PROPERTY = "deduplication.enable";

    public static BlobStoreConfiguration parse(org.apache.james.server.core.configuration.Configuration configuration) throws ConfigurationException {
        PropertiesProvider propertiesProvider = new PropertiesProvider(new FileSystemImpl(configuration.directories()),
            configuration.configurationPath());

        return parse(propertiesProvider);
    }

    public static BlobStoreConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfigurations(ConfigurationComponent.NAMES);
            return BlobStoreConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ConfigurationComponent.NAME + " configuration file, using deduplicating blobstore as the default");
            return BlobStoreConfiguration.builder()
                .deduplication()
                .noCryptoConfig();
        }
    }

    static BlobStoreConfiguration from(Configuration configuration) {
        boolean deduplicationEnabled = Try.ofCallable(() -> configuration.getBoolean(DEDUPLICATION_ENABLE_PROPERTY))
                .getOrElseThrow(() -> new IllegalStateException("deduplication.enable property is missing please use one of the supported values in: true, false\n" +
                        "If you choose to enable deduplication, the mails with the same content will be stored only once.\n" +
                        "Warning: Once this feature is enabled, there is no turning back as turning it off will lead to the deletion of all\n" +
                        "the mails sharing the same content once one is deleted.\n" +
                        "Upgrade note: If you are upgrading from James 3.5 or older, the deduplication was enabled."));
        Optional<CryptoConfig> cryptoConfig = parseCryptoConfig(configuration);

        if (deduplicationEnabled) {
            return builder()
                .deduplication()
                .cryptoConfig(cryptoConfig);
        } else {
            return builder()
                .passthrough()
                .cryptoConfig(cryptoConfig);
        }
    }

    private static Optional<CryptoConfig> parseCryptoConfig(Configuration configuration) {
        final boolean enabled = configuration.getBoolean(ENCRYPTION_ENABLE_PROPERTY, false);
        if (enabled) {
            return Optional.of(CryptoConfig.builder()
                .password(Optional.ofNullable(configuration.getString(ENCRYPTION_PASSWORD_PROPERTY, null)).map(String::toCharArray).orElse(null))
                .salt(configuration.getString(ENCRYPTION_SALT_PROPERTY, null))
                .build());
        }
        return Optional.empty();
    }


    private final StorageStrategy storageStrategy;
    private final Optional<CryptoConfig> cryptoConfig;

    BlobStoreConfiguration(StorageStrategy storageStrategy, Optional<CryptoConfig> cryptoConfig) {

        this.storageStrategy = storageStrategy;
        this.cryptoConfig = cryptoConfig;
    }


    public StorageStrategy storageStrategy() {
        return storageStrategy;
    }

    public Optional<CryptoConfig> getCryptoConfig() {
        return cryptoConfig;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof BlobStoreConfiguration) {
            BlobStoreConfiguration that = (BlobStoreConfiguration) o;

            return Objects.equals(this.storageStrategy, that.storageStrategy)
                && Objects.equals(this.cryptoConfig, that.cryptoConfig);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(storageStrategy, cryptoConfig);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("storageStrategy", storageStrategy.name())
            .add("cryptoConfig", cryptoConfig)
            .toString();
    }
}
