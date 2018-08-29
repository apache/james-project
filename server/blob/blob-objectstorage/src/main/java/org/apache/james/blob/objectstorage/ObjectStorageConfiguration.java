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

package org.apache.james.blob.objectstorage;

import java.net.URI;
import java.util.Optional;
import java.util.Properties;

import org.jclouds.openstack.keystone.config.KeystoneProperties;
import org.jclouds.openstack.swift.v1.reference.TempAuthHeaders;

import com.google.common.base.Preconditions;

public class ObjectStorageConfiguration {
    public static class Builder {
        private URI endpoint;
        private Identity identity;
        private Credentials credentials;
        private Optional<Region> region;
        private Optional<UserHeaderName> userHeaderName;
        private Optional<PassHeaderName> passHeaderName;

        public Builder() {
            region = Optional.empty();
            userHeaderName = Optional.empty();
            passHeaderName = Optional.empty();
        }

        public ObjectStorageConfiguration.Builder endpoint(URI endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public ObjectStorageConfiguration.Builder identity(Identity identity) {
            this.identity = identity;
            return this;
        }

        public ObjectStorageConfiguration.Builder credentials(Credentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public ObjectStorageConfiguration.Builder region(Region region) {
            this.region = Optional.of(region);
            return this;
        }

        public ObjectStorageConfiguration.Builder tempAuthHeaderUserName(UserHeaderName tmpAuthHeaderUser) {
            userHeaderName = Optional.of(tmpAuthHeaderUser);
            return this;
        }

        public ObjectStorageConfiguration.Builder tempAuthHeaderPassName(PassHeaderName tmpAuthHeaderPass) {
            passHeaderName = Optional.of(tmpAuthHeaderPass);
            return this;
        }

        public ObjectStorageConfiguration build() {
            Preconditions.checkState(endpoint != null);
            Preconditions.checkState(identity != null);
            Preconditions.checkState(credentials != null);
            return new ObjectStorageConfiguration(endpoint, identity, credentials, region, userHeaderName, passHeaderName);
        }
    }

    private final URI endpoint;
    private final Optional<Region> region;
    private final Identity identity;
    private final Credentials credentials;
    private final Optional<UserHeaderName> userHeaderName;
    private final Optional<PassHeaderName> passHeaderName;

    private ObjectStorageConfiguration(URI endpoint,
                                       Identity identity,
                                       Credentials credentials,
                                       Optional<Region> region,
                                       Optional<UserHeaderName> userHeaderName,
                                       Optional<PassHeaderName> passHeaderName) {
        this.endpoint = endpoint;
        this.region = region;
        this.userHeaderName = userHeaderName;
        this.passHeaderName = passHeaderName;
        this.identity = identity;
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
        properties.setProperty(KeystoneProperties.CREDENTIAL_TYPE, "tempAuthCredentials");
        userHeaderName.ifPresent(tmpAuthHeaderUser ->
            properties.setProperty(TempAuthHeaders.TEMP_AUTH_HEADER_USER, tmpAuthHeaderUser.value())
        );
        passHeaderName.ifPresent(tmpAuthHeaderPass ->
            properties.setProperty(TempAuthHeaders.TEMP_AUTH_HEADER_PASS, tmpAuthHeaderPass.value())
        );
        return properties;
    }

    public Optional<Region> getRegion() {
        return region;
    }
}
