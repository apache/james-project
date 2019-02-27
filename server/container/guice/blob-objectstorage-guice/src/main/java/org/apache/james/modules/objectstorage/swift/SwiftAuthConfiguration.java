/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.modules.objectstorage.swift;

import java.util.Optional;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone2ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.apache.james.modules.objectstorage.ObjectStorageBlobConfiguration;
import org.apache.james.modules.objectstorage.SpecificAuthConfiguration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Strings;

public class SwiftAuthConfiguration implements SpecificAuthConfiguration {

    private static final String OBJECTSTORAGE_SWIFT_AUTH_API = "objectstorage.swift.authapi";

    public static SwiftAuthConfiguration from(Configuration configuration) throws ConfigurationException {
        String authApi = configuration.getString(OBJECTSTORAGE_SWIFT_AUTH_API, null);
        if (Strings.isNullOrEmpty(authApi)) {
            throw new ConfigurationException("Mandatory configuration value " + OBJECTSTORAGE_SWIFT_AUTH_API + " is missing from " + ObjectStorageBlobConfiguration.OBJECTSTORAGE_CONFIGURATION_NAME + " configuration");
        }

        switch (authApi) {
            case SwiftTempAuthObjectStorage.AUTH_API_NAME:
                return tempAuth(SwiftTmpAuthConfigurationReader.readSwiftConfiguration(configuration));
            case SwiftKeystone2ObjectStorage.AUTH_API_NAME:
                return keystone2(SwiftKeystone2ConfigurationReader.readSwiftConfiguration(configuration));
            case SwiftKeystone3ObjectStorage.AUTH_API_NAME:
                return keystone3(SwiftKeystone3ConfigurationReader.readSwiftConfiguration(configuration));
        }
        throw new IllegalStateException("unexpected auth api " + authApi);
    }

    private static SwiftAuthConfiguration tempAuth(SwiftTempAuthObjectStorage.Configuration authConfig) {
        return new SwiftAuthConfiguration(SwiftTempAuthObjectStorage.AUTH_API_NAME, Optional.of(authConfig), Optional.empty(), Optional.empty());
    }

    private static SwiftAuthConfiguration keystone2(SwiftKeystone2ObjectStorage.Configuration authConfig) {
        return new SwiftAuthConfiguration(SwiftKeystone2ObjectStorage.AUTH_API_NAME, Optional.empty(), Optional.of(authConfig), Optional.empty());
    }

    private static SwiftAuthConfiguration keystone3(SwiftKeystone3ObjectStorage.Configuration authConfig) {
        return new SwiftAuthConfiguration(SwiftKeystone3ObjectStorage.AUTH_API_NAME, Optional.empty(), Optional.empty(), Optional.of(authConfig));
    }

    private final String authApiName;
    private final Optional<SwiftTempAuthObjectStorage.Configuration> tempAuth;
    private final Optional<SwiftKeystone2ObjectStorage.Configuration> keystone2Configuration;
    private final Optional<SwiftKeystone3ObjectStorage.Configuration> keystone3Configuration;

    @VisibleForTesting
    SwiftAuthConfiguration(String authApiName,
                           Optional<SwiftTempAuthObjectStorage.Configuration> tempAuth,
                           Optional<SwiftKeystone2ObjectStorage.Configuration> keystone2Configuration,
                           Optional<SwiftKeystone3ObjectStorage.Configuration> keystone3Configuration) {
        this.authApiName = authApiName;
        this.tempAuth = tempAuth;
        this.keystone2Configuration = keystone2Configuration;
        this.keystone3Configuration = keystone3Configuration;
    }

    public String getAuthApiName() {
        return authApiName;
    }

    public Optional<SwiftTempAuthObjectStorage.Configuration> getTempAuth() {
        return tempAuth;
    }

    public Optional<SwiftKeystone2ObjectStorage.Configuration> getKeystone2Configuration() {
        return keystone2Configuration;
    }

    public Optional<SwiftKeystone3ObjectStorage.Configuration> getKeystone3Configuration() {
        return keystone3Configuration;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof SwiftAuthConfiguration) {
            SwiftAuthConfiguration that = (SwiftAuthConfiguration) o;
            return Objects.equal(authApiName, that.authApiName) &&
                Objects.equal(tempAuth, that.tempAuth) &&
                Objects.equal(keystone2Configuration, that.keystone2Configuration) &&
                Objects.equal(keystone3Configuration, that.keystone3Configuration);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(authApiName, tempAuth, keystone2Configuration, keystone3Configuration);
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this)
            .add("authApiName", authApiName)
            .add("tempAuth", tempAuth)
            .add("keystone2Configuration", keystone2Configuration)
            .add("keystone3Configuration", keystone3Configuration)
            .toString();
    }
}
