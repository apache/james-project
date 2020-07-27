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
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

public class BlobStoreConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlobStoreConfiguration.class);

    public static class CacheChoice {
        private final  BlobStoreImplName implementation;

        private CacheChoice(BlobStoreImplName implementation) {
            this.implementation = implementation;
        }

        public BlobStoreConfiguration enableCache() {
            return new BlobStoreConfiguration(implementation, CACHE_ENABLED);
        }

        public BlobStoreConfiguration disableCache() {
            return new BlobStoreConfiguration(implementation, !CACHE_ENABLED);
        }
    }

    public enum BlobStoreImplName {
        CASSANDRA("cassandra"),
        OBJECTSTORAGE("objectstorage");

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
    static final boolean CACHE_ENABLED = true;

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
            LOGGER.warn("Could not find " + ConfigurationComponent.NAME + " configuration file, using cassandra blobstore as the default");
            return BlobStoreConfiguration.cassandra();
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

        return new BlobStoreConfiguration(blobStoreImplName, cacheEnabled);
    }

    public static BlobStoreConfiguration cassandra() {
        return new BlobStoreConfiguration(BlobStoreImplName.CASSANDRA, !CACHE_ENABLED);
    }

    public static CacheChoice objectStorage() {
        return new CacheChoice(BlobStoreImplName.OBJECTSTORAGE);
    }

    private final BlobStoreImplName implementation;
    private final boolean cacheEnabled;

    BlobStoreConfiguration(BlobStoreImplName implementation, boolean cacheEnabled) {
        this.implementation = implementation;
        this.cacheEnabled = cacheEnabled;
    }

    public boolean cacheEnabled() {
        return cacheEnabled;
    }

    BlobStoreImplName getImplementation() {
        return implementation;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof BlobStoreConfiguration) {
            BlobStoreConfiguration that = (BlobStoreConfiguration) o;

            return Objects.equals(this.implementation, that.implementation)
                && Objects.equals(this.cacheEnabled, that.cacheEnabled);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(implementation, cacheEnabled);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("implementation", implementation)
            .add("cacheEnabled", cacheEnabled)
            .toString();
    }
}
