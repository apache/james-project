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

package org.apache.james.modules.blobstore;

import java.io.FileNotFoundException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.server.blob.deduplication.StorageStrategy;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

import io.vavr.control.Try;

public class BlobStoreConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlobStoreConfiguration.class);

    @FunctionalInterface
    public interface RequireImplementation {
        RequireCache implementation(BlobStoreImplName implementation);

        default RequireCache cassandra() {
            return implementation(BlobStoreImplName.CASSANDRA);
        }

        default RequireCache file() {
            return implementation(BlobStoreImplName.FILE);
        }

        default RequireCache s3() {
            return implementation(BlobStoreImplName.S3);
        }

        default RequireCache postgres() {
            return implementation(BlobStoreImplName.POSTGRES);
        }
    }

    @FunctionalInterface
    public interface RequireCache {
        RequireStoringStrategy enableCache(boolean enable);

        default RequireStoringStrategy enableCache() {
            return enableCache(CACHE_ENABLED);
        }

        default RequireStoringStrategy disableCache() {
            return enableCache(!CACHE_ENABLED);
        }
    }

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

    public static RequireImplementation builder() {
        return implementation -> enableCache -> storageStrategy -> cryptoConfig ->
            new BlobStoreConfiguration(implementation, enableCache, storageStrategy, cryptoConfig);
    }

    public enum BlobStoreImplName {
        CASSANDRA("cassandra"),
        FILE("file"),
        S3("s3"),
        POSTGRES("postgres");

        static String supportedImplNames() {
            return Stream.of(BlobStoreImplName.values())
                .map(BlobStoreImplName::getName)
                .collect(Collectors.joining(", "));
        }

        static BlobStoreImplName from(String name) {
            return Stream.of(values())
                .filter(blobName -> blobName.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("%s is not a valid name of BlobStores, " +
                    "please use one of supported values in: %s", name, supportedImplNames())));
        }

        private final String name;

        BlobStoreImplName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    static final String BLOBSTORE_IMPLEMENTATION_PROPERTY = "implementation";
    static final String CACHE_ENABLE_PROPERTY = "cache.enable";
    static final String ENCRYPTION_ENABLE_PROPERTY = "encryption.aes.enable";
    static final String ENCRYPTION_PASSWORD_PROPERTY = "encryption.aes.password";
    static final String ENCRYPTION_SALT_PROPERTY = "encryption.aes.salt";
    static final boolean CACHE_ENABLED = true;
    static final String DEDUPLICATION_ENABLE_PROPERTY = "deduplication.enable";

    public static BlobStoreConfiguration parse(org.apache.james.server.core.configuration.Configuration configuration) throws ConfigurationException {
        PropertiesProvider propertiesProvider = new PropertiesProvider(new FileSystemImpl(configuration.directories()),
            configuration.configurationPath());

        return parse(propertiesProvider);
    }

    public static BlobStoreConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
        return parse(propertiesProvider, BlobStoreImplName.CASSANDRA);
    }

    public static BlobStoreConfiguration parse(PropertiesProvider propertiesProvider, BlobStoreImplName defaultBlobStore) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfigurations(ConfigurationComponent.NAMES);
            return BlobStoreConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ConfigurationComponent.NAME + " configuration file, using " + defaultBlobStore.getName() + " blobstore as the default");
            return BlobStoreConfiguration.builder()
                .implementation(defaultBlobStore)
                .disableCache()
                .passthrough()
                .noCryptoConfig();
        }
    }

    static BlobStoreConfiguration from(Configuration configuration) {
        BlobStoreImplName blobStoreImplName = Optional.ofNullable(configuration.getString(BLOBSTORE_IMPLEMENTATION_PROPERTY))
            .filter(StringUtils::isNotBlank)
            .map(StringUtils::trim)
            .map(BlobStoreImplName::from)
            .orElseThrow(() -> new IllegalStateException(String.format("%s property is missing please use one of " +
                "supported values in: %s", BLOBSTORE_IMPLEMENTATION_PROPERTY, BlobStoreImplName.supportedImplNames())));

        boolean cacheEnabled = configuration.getBoolean(CACHE_ENABLE_PROPERTY, false);
        boolean deduplicationEnabled = Try.ofCallable(() -> configuration.getBoolean(DEDUPLICATION_ENABLE_PROPERTY))
                .getOrElseThrow(() -> new IllegalStateException("deduplication.enable property is missing please use one of the supported values in: true, false\n" +
                        "If you choose to enable deduplication, the mails with the same content will be stored only once.\n" +
                        "Warning: Once this feature is enabled, there is no turning back as turning it off will lead to the deletion of all\n" +
                        "the mails sharing the same content once one is deleted.\n" +
                        "Upgrade note: If you are upgrading from James 3.5 or older, the deduplication was enabled."));
        Optional<CryptoConfig> cryptoConfig = parseCryptoConfig(configuration);

        if (deduplicationEnabled) {
            return builder()
                .implementation(blobStoreImplName)
                .enableCache(cacheEnabled)
                .deduplication()
                .cryptoConfig(cryptoConfig);
        } else {
            return builder()
                .implementation(blobStoreImplName)
                .enableCache(cacheEnabled)
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

    @VisibleForTesting
    public static RequireStoringStrategy cassandra() {
        return builder()
            .cassandra()
            .disableCache();
    }

    public static RequireCache s3() {
        return builder().s3();
    }

    private final BlobStoreImplName implementation;
    private final boolean cacheEnabled;
    private final StorageStrategy storageStrategy;
    private final Optional<CryptoConfig> cryptoConfig;

    BlobStoreConfiguration(BlobStoreImplName implementation, boolean cacheEnabled, StorageStrategy storageStrategy, Optional<CryptoConfig> cryptoConfig) {
        this.implementation = implementation;
        this.cacheEnabled = cacheEnabled;
        this.storageStrategy = storageStrategy;
        this.cryptoConfig = cryptoConfig;
    }

    public boolean cacheEnabled() {
        return cacheEnabled;
    }

    public StorageStrategy storageStrategy() {
        return storageStrategy;
    }

    public BlobStoreImplName getImplementation() {
        return implementation;
    }

    public Optional<CryptoConfig> getCryptoConfig() {
        return cryptoConfig;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof BlobStoreConfiguration) {
            BlobStoreConfiguration that = (BlobStoreConfiguration) o;

            return Objects.equals(this.implementation, that.implementation)
                && Objects.equals(this.cacheEnabled, that.cacheEnabled)
                && Objects.equals(this.storageStrategy, that.storageStrategy)
                && Objects.equals(this.cryptoConfig, that.cryptoConfig);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(implementation, cacheEnabled, storageStrategy, cryptoConfig);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("implementation", implementation)
            .add("cacheEnabled", cacheEnabled)
            .add("storageStrategy", storageStrategy.name())
            .add("cryptoConfig", cryptoConfig)
            .toString();
    }
}
