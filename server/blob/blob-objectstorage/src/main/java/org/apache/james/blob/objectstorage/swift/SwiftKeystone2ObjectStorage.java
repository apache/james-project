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

package org.apache.james.blob.objectstorage.swift;

import java.net.URI;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;

import org.apache.james.blob.objectstorage.ObjectStorageBlobStoreBuilder;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.keystone.config.KeystoneProperties;
import org.jclouds.openstack.swift.v1.blobstore.RegionScopedBlobStoreContext;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

public class SwiftKeystone2ObjectStorage {
    public static final String AUTH_API_NAME = "keystone2";

    private static final Iterable<Module> JCLOUDS_MODULES =
        ImmutableSet.of(new SLF4JLoggingModule());

    public static ObjectStorageBlobStoreBuilder.RequireBlobIdFactory blobStoreBuilder(Configuration testConfig) {
        return ObjectStorageBlobStoreBuilder.forBlobStore(new BlobStoreBuilder(testConfig));
    }

    public static Configuration.Builder configBuilder() {
        return new Configuration.Builder();
    }

    public static class BlobStoreBuilder implements Supplier<BlobStore> {
        private final Configuration testConfig;

        private BlobStoreBuilder(Configuration testConfig) {
            this.testConfig = testConfig;
        }

        @Override
        public BlobStore get() {
            RegionScopedBlobStoreContext blobStoreContext = contextBuilder()
                .endpoint(testConfig.getEndpoint().toString())
                .credentials(testConfig.getIdentity().asString(), testConfig.getCredentials().value())
                .overrides(testConfig.getOverrides())
                .modules(JCLOUDS_MODULES)
                .buildView(RegionScopedBlobStoreContext.class);

            return testConfig.getRegion()
                .map(Region::value)
                .map(blobStoreContext::getBlobStore)
                .orElseGet(blobStoreContext::getBlobStore);
        }

        private ContextBuilder contextBuilder() {
            return ContextBuilder.newBuilder("openstack-swift");
        }
    }

    public static final class Configuration {
        public static class Builder {
            private URI endpoint;
            private UserName userName;
            private TenantName tenantName;
            private Credentials credentials;
            private Optional<Region> region;

            private Builder() {
                region = Optional.empty();
            }

            public Builder endpoint(URI endpoint) {
                this.endpoint = endpoint;
                return this;
            }

            public Builder identity(Identity identity) {
                this.tenantName = identity.getTenant();
                this.userName = identity.getUserName();
                return this;
            }

            public Builder tenantName(TenantName tenantName) {
                this.tenantName = tenantName;
                return this;
            }

            public Builder userName(UserName username) {
                this.userName = username;
                return this;
            }

            public Builder credentials(Credentials credentials) {
                this.credentials = credentials;
                return this;
            }

            public Builder region(Optional<Region> region) {
                this.region = region;
                return this;
            }

            public Configuration build() {
                Preconditions.checkState(endpoint != null);
                Preconditions.checkState(tenantName != null);
                Preconditions.checkState(userName != null);
                Preconditions.checkState(credentials != null);
                Identity identity = Identity.of(tenantName, userName);
                return new Configuration(endpoint, identity, credentials, region);
            }
        }

        private final URI endpoint;
        private final Identity identity;
        private final Optional<Region> region;
        private final Credentials credentials;

        private Configuration(URI endpoint,
                              Identity identity,
                              Credentials credentials,
                              Optional<Region> region) {
            this.endpoint = endpoint;
            this.identity = identity;
            this.region = region;
            this.credentials = credentials;
        }

        public URI getEndpoint() {
            return endpoint;
        }

        public Identity getIdentity() {
            return identity;
        }

        public Credentials getCredentials() {
            return credentials;
        }

        public Properties getOverrides() {
            Properties properties = new Properties();
            properties.setProperty(KeystoneProperties.KEYSTONE_VERSION, "2");
            return properties;
        }

        public Optional<Region> getRegion() {
            return region;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Configuration) {
                Configuration that = (Configuration) o;
                return Objects.equal(endpoint, that.endpoint) &&
                    Objects.equal(identity, that.identity) &&
                    Objects.equal(region, that.region) &&
                    Objects.equal(credentials, that.credentials);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(endpoint, identity, region, credentials);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("endpoint", endpoint)
                .add("identity", identity)
                .add("region", region)
                .add("credentials", credentials)
                .toString();
        }
    }
}
