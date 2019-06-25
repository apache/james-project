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
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.objectstorage.PayloadCodec;
import org.apache.james.blob.objectstorage.SpecificAuthConfiguration;
import org.apache.james.modules.objectstorage.aws.s3.AwsS3ConfigurationReader;
import org.apache.james.modules.objectstorage.swift.SwiftAuthConfiguration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;

public class ObjectStorageBlobConfiguration {

    public static final String OBJECTSTORAGE_CONFIGURATION_NAME = "blobstore";
    private static final String OBJECTSTORAGE_NAMESPACE = "objectstorage.namespace";
    private static final String OBJECTSTORAGE_PROVIDER = "objectstorage.provider";
    private static final String OBJECTSTORAGE_PAYLOAD_CODEC = "objectstorage.payload.codec";
    public static final String OBJECTSTORAGE_AES256_HEXSALT = "objectstorage.aes256.hexsalt";
    public static final String OBJECTSTORAGE_AES256_PASSWORD = "objectstorage.aes256.password";

    public static ObjectStorageBlobConfiguration from(Configuration configuration) throws ConfigurationException {
        String provider = configuration.getString(OBJECTSTORAGE_PROVIDER, null);
        String namespace = configuration.getString(OBJECTSTORAGE_NAMESPACE, null);
        String codecName = configuration.getString(OBJECTSTORAGE_PAYLOAD_CODEC, null);
        Optional<String> aesSalt = Optional.ofNullable(configuration.getString(OBJECTSTORAGE_AES256_HEXSALT, null));
        Optional<char[]> aesPassword = Optional.ofNullable(configuration.getString(OBJECTSTORAGE_AES256_PASSWORD, null))
            .map(String::toCharArray);

        if (Strings.isNullOrEmpty(provider)) {
            throw new ConfigurationException("Mandatory configuration value " + OBJECTSTORAGE_PROVIDER + " is missing from " + OBJECTSTORAGE_CONFIGURATION_NAME + " configuration");
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

        return builder()
            .codec(payloadCodecFactory)
            .provider(ObjectStorageProvider.from(provider))
            .bucketName(BucketName.of(namespace))
            .authConfiguration(authConfiguration(provider, configuration))
            .aesSalt(aesSalt)
            .aesPassword(aesPassword)
            .build();
    }

    private static SpecificAuthConfiguration authConfiguration(String provider, Configuration configuration) throws ConfigurationException {
        switch (ObjectStorageProvider.from(provider)) {
            case SWIFT:
                return SwiftAuthConfiguration.from(configuration);
            case AWSS3:
                return AwsS3ConfigurationReader.from(configuration);
        }
        throw new ConfigurationException("Unknown object storage provider: " + provider);
    }

    public static Builder.RequirePayloadCodec builder() {
        return payloadCodec -> provider -> container -> authConfiguration -> new Builder.ReadyToBuild(payloadCodec, provider, container, authConfiguration);
    }

    public interface Builder {
        @FunctionalInterface
        interface RequirePayloadCodec {
            RequireProvider codec(PayloadCodecFactory codec);
        }

        @FunctionalInterface
        interface RequireProvider {
            RequireContainerName provider(ObjectStorageProvider provider);
        }

        @FunctionalInterface
        interface RequireContainerName {
            RequireAuthConfiguration bucketName(BucketName bucketName);
        }

        @FunctionalInterface
        interface RequireAuthConfiguration {
            ReadyToBuild authConfiguration(SpecificAuthConfiguration authConfiguration);
        }

        class ReadyToBuild {

            private final PayloadCodecFactory payloadCodecFactory;
            private final ObjectStorageProvider provider;
            private final BucketName bucketName;
            private final SpecificAuthConfiguration specificAuthConfiguration;

            private Optional<String> aesSalt;
            private Optional<char[]> aesPassword;

            public ReadyToBuild(PayloadCodecFactory payloadCodecFactory,
                                ObjectStorageProvider provider,
                                BucketName bucketName,
                                SpecificAuthConfiguration specificAuthConfiguration) {
                this.aesSalt = Optional.empty();
                this.aesPassword = Optional.empty();
                this.payloadCodecFactory = payloadCodecFactory;
                this.provider = provider;
                this.bucketName = bucketName;
                this.specificAuthConfiguration = specificAuthConfiguration;
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

                return new ObjectStorageBlobConfiguration(payloadCodecFactory, provider, bucketName, specificAuthConfiguration, aesSalt, aesPassword);
            }

        }

    }

    private final PayloadCodecFactory payloadCodec;
    private final BucketName namespace;
    private final ObjectStorageProvider provider;
    private final SpecificAuthConfiguration specificAuthConfiguration;
    private Optional<String> aesSalt;
    private Optional<char[]> aesPassword;

    @VisibleForTesting
    ObjectStorageBlobConfiguration(PayloadCodecFactory payloadCodec, ObjectStorageProvider provider,
                                   BucketName namespace,
                                   SpecificAuthConfiguration specificAuthConfiguration,
                                   Optional<String> aesSalt,
                                   Optional<char[]> aesPassword) {
        this.payloadCodec = payloadCodec;
        this.provider = provider;
        this.namespace = namespace;
        this.specificAuthConfiguration = specificAuthConfiguration;
        this.aesSalt = aesSalt;
        this.aesPassword = aesPassword;
    }

    public BucketName getNamespace() {
        return namespace;
    }

    public ObjectStorageProvider getProvider() {
        return provider;
    }

    public PayloadCodecFactory getPayloadCodecFactory() {
        return payloadCodec;
    }

    public PayloadCodec getPayloadCodec() {
        return payloadCodec.create(this);
    }

    public SpecificAuthConfiguration getSpecificAuthConfiguration() {
        return specificAuthConfiguration;
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
            Objects.equals(namespace, that.namespace) &&
            Objects.equals(provider, that.provider) &&
            Objects.equals(specificAuthConfiguration, that.specificAuthConfiguration) &&
            Objects.equals(aesSalt, that.aesSalt) &&
            Objects.equals(aesPassword, that.aesPassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(payloadCodec, namespace, provider, specificAuthConfiguration, aesSalt, aesPassword);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("payloadCodec", payloadCodec)
            .add("namespace", namespace)
            .add("provider", provider)
            .add("specificAuthConfiguration", specificAuthConfiguration)
            .add("aesSalt", aesSalt)
            .add("aesPassword", aesPassword)
            .toString();
    }
}
