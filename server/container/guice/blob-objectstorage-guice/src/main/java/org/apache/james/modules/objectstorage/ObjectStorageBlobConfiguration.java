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

package org.apache.james.modules.objectstorage;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.blob.objectstorage.ContainerName;
import org.apache.james.blob.objectstorage.PayloadCodec;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone2ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;

public class ObjectStorageBlobConfiguration {

    private static final String OBJECTSTORAGE_CONFIGURATION_NAME = "blobstore";
    private static final String OBJECTSTORAGE_NAMESPACE = "objectstorage.namespace";
    private static final String OBJECTSTORAGE_PROVIDER = "objectstorage.provider";
    private static final String OBJECTSTORAGE_SWIFT_AUTH_API = "objectstorage.swift.authapi";
    private static final String OBJECTSTORAGE_PAYLOAD_CODEC = "objectstorage.payload.codec";
    public static final String OBJECTSTORAGE_AES256_HEXSALT = "objectstorage.aes256.hexsalt";
    public static final String OBJECTSTORAGE_AES256_PASSWORD = "objectstorage.aes256.password";

    public static ObjectStorageBlobConfiguration from(Configuration configuration) throws ConfigurationException {
        String provider = configuration.getString(OBJECTSTORAGE_PROVIDER, null);
        String namespace = configuration.getString(OBJECTSTORAGE_NAMESPACE, null);
        String authApi = configuration.getString(OBJECTSTORAGE_SWIFT_AUTH_API, null);
        String codecName = configuration.getString(OBJECTSTORAGE_PAYLOAD_CODEC, null);
        Optional<String> aesSalt = Optional.ofNullable(configuration.getString(OBJECTSTORAGE_AES256_HEXSALT, null));
        Optional<char[]> aesPassword = Optional.ofNullable(configuration.getString(OBJECTSTORAGE_AES256_PASSWORD, null))
            .map(String::toCharArray);

        if (Strings.isNullOrEmpty(provider)) {
            throw new ConfigurationException("Mandatory configuration value " + OBJECTSTORAGE_PROVIDER + " is missing from " + OBJECTSTORAGE_CONFIGURATION_NAME + " configuration");
        }
        if (Strings.isNullOrEmpty(authApi)) {
            throw new ConfigurationException("Mandatory configuration value " + OBJECTSTORAGE_SWIFT_AUTH_API + " is missing from " + OBJECTSTORAGE_CONFIGURATION_NAME + " configuration");
        }
        if (Strings.isNullOrEmpty(namespace)) {
            throw new ConfigurationException("Mandatory configuration value " + OBJECTSTORAGE_NAMESPACE + " is missing from " + OBJECTSTORAGE_CONFIGURATION_NAME + " configuration");
        }
        if (Strings.isNullOrEmpty(codecName)) {
            throw new ConfigurationException("Mandatory configuration value " + OBJECTSTORAGE_PAYLOAD_CODEC  + " is missing from " + OBJECTSTORAGE_CONFIGURATION_NAME + " configuration");
        }

        PayloadCodecFactory payloadCodecFactory = Arrays.stream(PayloadCodecFactory.values())
            .filter(name -> name.name().equals(codecName))
            .findAny()
            .orElseThrow(() -> new ConfigurationException("unknown payload codec : " + codecName));

        Builder.RequireAuthConfiguration requireAuthConfiguration = builder()
            .codec(payloadCodecFactory)
            .provider(ObjectStorageProvider.from(provider))
            .container(ContainerName.of(namespace));

        return defineAuthApi(configuration, authApi, requireAuthConfiguration)
            .aesSalt(aesSalt)
            .aesPassword(aesPassword)
            .build();
    }

    private static Builder.ReadyToBuild defineAuthApi(Configuration configuration, String authApi, Builder.RequireAuthConfiguration requireAuthConfiguration) {
        switch (authApi) {
            case SwiftTempAuthObjectStorage.AUTH_API_NAME:
                return requireAuthConfiguration.tempAuth(SwiftTmpAuthConfigurationReader.readSwiftConfiguration(configuration));
            case SwiftKeystone2ObjectStorage.AUTH_API_NAME:
                return requireAuthConfiguration.keystone2(SwiftKeystone2ConfigurationReader.readSwiftConfiguration(configuration));
            case SwiftKeystone3ObjectStorage.AUTH_API_NAME:
                return requireAuthConfiguration.keystone3(SwiftKeystone3ConfigurationReader.readSwiftConfiguration(configuration));
        }
        throw new IllegalStateException("unexpected auth api " + authApi);
    }


    public static Builder.RequirePayloadCodec builder() {
        return payloadCodec -> provider -> container -> new Builder.RequireAuthConfiguration(payloadCodec, provider, container);
    }

    public interface Builder {
        @FunctionalInterface
        public interface RequirePayloadCodec {
            RequireProvider codec(PayloadCodecFactory codec);
        }

        @FunctionalInterface
        public interface RequireProvider {
            RequireContainerName provider(ObjectStorageProvider provider);
        }

        @FunctionalInterface
        public interface RequireContainerName {
            RequireAuthConfiguration container(ContainerName container);
        }

        public static class RequireAuthConfiguration {

            private final PayloadCodecFactory payloadCodec;
            private final ObjectStorageProvider provider;
            private final ContainerName container;

            private RequireAuthConfiguration(PayloadCodecFactory payloadCodec, ObjectStorageProvider provider, ContainerName container) {
                this.payloadCodec = payloadCodec;
                this.provider = provider;
                this.container = container;
            }

            public ReadyToBuild tempAuth(SwiftTempAuthObjectStorage.Configuration authConfig) {
                return new ReadyToBuild(payloadCodec, provider, container, SwiftTempAuthObjectStorage.AUTH_API_NAME, Optional.of(authConfig), Optional.empty(), Optional.empty());
            }

            public ReadyToBuild keystone2(SwiftKeystone2ObjectStorage.Configuration authConfig) {
                return new ReadyToBuild(payloadCodec, provider, container, SwiftKeystone2ObjectStorage.AUTH_API_NAME, Optional.empty(), Optional.of(authConfig), Optional.empty());
            }

            public ReadyToBuild keystone3(SwiftKeystone3ObjectStorage.Configuration authConfig) {
                return new ReadyToBuild(payloadCodec, provider, container, SwiftKeystone3ObjectStorage.AUTH_API_NAME, Optional.empty(), Optional.empty(), Optional.of(authConfig));
            }
        }

        public static class ReadyToBuild {

            private final PayloadCodecFactory payloadCodecFactory;
            private final ObjectStorageProvider provider;
            private final ContainerName container;
            private final String authApiName;
            private final Optional<SwiftTempAuthObjectStorage.Configuration> tempAuth;
            private final Optional<SwiftKeystone2ObjectStorage.Configuration> keystone2Configuration;
            private final Optional<SwiftKeystone3ObjectStorage.Configuration> keystone3Configuration;
            private Optional<String> aesSalt;
            private Optional<char[]> aesPassword;

            public ReadyToBuild(PayloadCodecFactory payloadCodecFactory, ObjectStorageProvider provider,
                                ContainerName container, String authApiName,
                                Optional<SwiftTempAuthObjectStorage.Configuration> tempAuth,
                                Optional<SwiftKeystone2ObjectStorage.Configuration> keystone2Configuration,
                                Optional<SwiftKeystone3ObjectStorage.Configuration> keystone3Configuration) {
                this.aesSalt = Optional.empty();
                this.aesPassword = Optional.empty();
                this.payloadCodecFactory = payloadCodecFactory;
                this.provider = provider;
                this.container = container;
                this.authApiName = authApiName;
                this.tempAuth = tempAuth;
                this.keystone2Configuration = keystone2Configuration;
                this.keystone3Configuration = keystone3Configuration;
            }

            public ReadyToBuild aesSalt(String aesSalt) {
                return aesSalt(Optional.of(aesSalt));
            }

            public ReadyToBuild aesSalt(Optional<String> aesSalt) {
                this.aesSalt = aesSalt;
                return this;
            }

            public ReadyToBuild aesPassword(char[] aesPassword) {
                return aesPassword(Optional.of(aesPassword));
            }

            public ReadyToBuild aesPassword(Optional<char[]> aesPassword) {
                this.aesPassword = aesPassword;
                return this;
            }

            public ObjectStorageBlobConfiguration build() {
                if (payloadCodecFactory == PayloadCodecFactory.AES256) {
                    aesSalt.filter(s -> !s.isEmpty())
                        .orElseThrow(() -> new IllegalStateException("AES code requires an non-empty salt parameter"));
                    aesPassword.filter(s -> s.length > 0)
                        .orElseThrow(() -> new IllegalStateException("AES code requires an non-empty password parameter"));
                }

                return new ObjectStorageBlobConfiguration(payloadCodecFactory, provider, container, aesSalt, aesPassword,
                    authApiName, tempAuth, keystone2Configuration, keystone3Configuration);
            }

        }

    }

    private final PayloadCodecFactory payloadCodec;
    private final String authApi;
    private final ContainerName namespace;
    private final ObjectStorageProvider provider;
    private final Optional<SwiftTempAuthObjectStorage.Configuration> tempAuth;
    private final Optional<SwiftKeystone2ObjectStorage.Configuration> keystone2Configuration;
    private final Optional<SwiftKeystone3ObjectStorage.Configuration> keystone3Configuration;
    private Optional<String> aesSalt;
    private Optional<char[]> aesPassword;

    @VisibleForTesting
    ObjectStorageBlobConfiguration(PayloadCodecFactory payloadCodec, ObjectStorageProvider provider,
                                   ContainerName namespace,
                                   Optional<String> aesSalt,
                                   Optional<char[]> aesPassword, String authApi,
                                   Optional<SwiftTempAuthObjectStorage.Configuration> tempAuth,
                                   Optional<SwiftKeystone2ObjectStorage.Configuration> keystone2Configuration,
                                   Optional<SwiftKeystone3ObjectStorage.Configuration> keystone3Configuration) {
        this.payloadCodec = payloadCodec;
        this.aesSalt = aesSalt;
        this.aesPassword = aesPassword;
        this.authApi = authApi;
        this.namespace = namespace;
        this.provider = provider;
        this.tempAuth = tempAuth;
        this.keystone2Configuration = keystone2Configuration;
        this.keystone3Configuration = keystone3Configuration;
    }

    public String getAuthApi() {
        return authApi;
    }

    public ContainerName getNamespace() {
        return namespace;
    }

    public ObjectStorageProvider getProvider() {
        return provider;
    }

    public Optional<SwiftTempAuthObjectStorage.Configuration> getTempAuthConfiguration() {
        return tempAuth;
    }

    public Optional<SwiftKeystone2ObjectStorage.Configuration> getKeystone2Configuration() {
        return keystone2Configuration;
    }

    public Optional<SwiftKeystone3ObjectStorage.Configuration> getKeystone3Configuration() {
        return keystone3Configuration;
    }

    public PayloadCodecFactory getPayloadCodecFactory() {
        return payloadCodec;
    }

    public PayloadCodec getPayloadCodec() {
        return payloadCodec.create(this);
    }

    public Optional<String> getAesSalt() {
        return aesSalt;
    }

    public Optional<char[]> getAesPassword() {
        return aesPassword;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ObjectStorageBlobConfiguration that = (ObjectStorageBlobConfiguration) o;
        return Objects.equals(payloadCodec, that.payloadCodec) &&
            Objects.equals(authApi, that.authApi) &&
            Objects.equals(namespace, that.namespace) &&
            Objects.equals(provider, that.provider) &&
            Objects.equals(tempAuth, that.tempAuth) &&
            Objects.equals(keystone2Configuration, that.keystone2Configuration) &&
            Objects.equals(keystone3Configuration, that.keystone3Configuration) &&
            Objects.equals(aesSalt, that.aesSalt) &&
            Objects.equals(aesPassword, that.aesPassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(payloadCodec, authApi, namespace, provider, tempAuth, keystone2Configuration, keystone3Configuration, aesSalt, aesPassword);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("payloadCodec", payloadCodec)
            .add("authApi", authApi)
            .add("namespace", namespace)
            .add("provider", provider)
            .add("tempAuth", tempAuth)
            .add("keystone2Configuration", keystone2Configuration)
            .add("keystone3Configuration", keystone3Configuration)
            .add("aesSalt", aesSalt)
            .add("aesPassword", aesPassword)
            .toString();
    }
}
