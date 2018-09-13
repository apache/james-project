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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.blob.objectstorage.AESPayloadCodec;
import org.apache.james.blob.objectstorage.DefaultPayloadCodec;
import org.apache.james.blob.objectstorage.PayloadCodec;
import org.apache.james.utils.PropertiesProvider;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

class PayloadCodecProviderTest {

    private static final FakePropertiesProvider DEFAULT_PROPERTIES_PROVIDER =
        FakePropertiesProvider.builder()
            .register("objectstorage",
                newConfigBuilder()
                    .put("objectstorage.payload.codec", PayloadCodecs.DEFAULT.name())
                    .build())
            .build();

    private static final FakePropertiesProvider EMPTY_PROPERTIES_PROVIDER =
        FakePropertiesProvider.builder()
            .register("objectstorage",
                newConfigBuilder().build())
            .build();

    private static final FakePropertiesProvider AES_PROPERTIES_PROVIDER =
        FakePropertiesProvider.builder()
            .register("objectstorage",
                newConfigBuilder()
                    .put("objectstorage.payload.codec", PayloadCodecs.AES256.name())
                    .put("objectstorage.aes256.hexsalt", "12345123451234512345")
                    .put("objectstorage.aes256.password", "james is great")
                    .build())
            .build();

    private static final FakePropertiesProvider MISSING_SALT_PROPERTIES_PROVIDER =
        FakePropertiesProvider.builder()
            .register("objectstorage",
                newConfigBuilder()
                    .put("objectstorage.payload.codec", PayloadCodecs.AES256.name())
                    .put("objectstorage.aes256.password", "james is great")
                    .build())
            .build();

    private static final FakePropertiesProvider EMPTY_SALT_PROPERTIES_PROVIDER =
        FakePropertiesProvider.builder()
            .register("objectstorage",
                newConfigBuilder()
                    .put("objectstorage.payload.codec", PayloadCodecs.AES256.name())
                    .put("objectstorage.aes256.hexsalt", "")
                    .put("objectstorage.aes256.password", "james is great")
                    .build())
            .build();

    private static final FakePropertiesProvider MISSING_PASSWORD_PROPERTIES_PROVIDER =
        FakePropertiesProvider.builder()
            .register("objectstorage",
                newConfigBuilder()
                    .put("objectstorage.payload.codec", PayloadCodecs.AES256.name())
                    .put("objectstorage.aes256.hexsalt", "12345123451234512345")
                    .build())
            .build();

    private static final FakePropertiesProvider EMPTY_PASSWORD_PROPERTIES_PROVIDER =
        FakePropertiesProvider.builder()
            .register("objectstorage",
                newConfigBuilder()
                    .put("objectstorage.payload.codec", PayloadCodecs.AES256.name())
                    .put("objectstorage.aes256.hexsalt", "12345123451234512345")
                    .put("objectstorage.aes256.password", "")
                    .build())
            .build();

    @Test
    void shouldBuildADefaultPayloadCodecForDefaultConfig() throws Exception {
        PayloadCodec payloadCodec = new PayloadCodecProvider(DEFAULT_PROPERTIES_PROVIDER).get();
        assertThat(payloadCodec).isInstanceOf(DefaultPayloadCodec.class);
    }

    @Test
    void shouldBuildAnAESPayloadCodecForAESConfig() throws Exception {
        PayloadCodec payloadCodec = new PayloadCodecProvider(AES_PROPERTIES_PROVIDER).get();
        assertThat(payloadCodec).isInstanceOf(AESPayloadCodec.class);
    }

    @Test
    void shouldFailIfCodecKeyIsMissing() throws Exception {
        assertThatThrownBy(() -> new PayloadCodecProvider(EMPTY_PROPERTIES_PROVIDER).get()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailIfCodecKeyIsIncorrect() throws Exception {
        FakePropertiesProvider propertiesWithTypo = FakePropertiesProvider.builder()
            .register("objectstorage",
                newConfigBuilder()
                    .put("objectstorage.payload.codec", "aes255")
                    .put("objectstorage.aes256.password", "james is great")
                    .build())
            .build();
        assertThatThrownBy(() -> new PayloadCodecProvider(propertiesWithTypo).get()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailForAESCodecWhenSaltKeyIsMissing() throws Exception {
        assertThatThrownBy(() -> new PayloadCodecProvider(MISSING_SALT_PROPERTIES_PROVIDER).get()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailForAESCodecWhenSaltKeyIsEmpty() throws Exception {
        assertThatThrownBy(() -> new PayloadCodecProvider(EMPTY_SALT_PROPERTIES_PROVIDER).get()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailForAESCodecWhenPasswordKeyIsMissing() throws Exception {

        assertThatThrownBy(() -> new PayloadCodecProvider(MISSING_PASSWORD_PROPERTIES_PROVIDER).get()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailForAESCodecWhenPasswordKeyIsEmpty() throws Exception {

        assertThatThrownBy(() -> new PayloadCodecProvider(EMPTY_PASSWORD_PROPERTIES_PROVIDER).get()).isInstanceOf(IllegalArgumentException.class);
    }

    private static MapConfigurationBuilder newConfigBuilder() {
        return new MapConfigurationBuilder();
    }

    private static class FakePropertiesProvider extends PropertiesProvider {
        private Map<String, Configuration> configurations;

        public FakePropertiesProvider(Map<String, Configuration> configurations) {
            super(null);
            this.configurations = configurations;
        }


        @Override
        public Configuration getConfiguration(String fileName) throws FileNotFoundException, ConfigurationException {
            if (configurations.containsKey(fileName)) {
                return configurations.get(fileName);
            } else {
                throw new FileNotFoundException(
                    "no configuration defined for " +
                        fileName +
                        " know configurations are (" +
                        StringUtils.join(configurations.keySet(), ",") +
                        ")");
            }
        }

        public static FakePropertiesProviderBuilder builder() {
            return new FakePropertiesProviderBuilder();
        }

        static class FakePropertiesProviderBuilder {
            private final Map<String, Configuration> configurations;

            public FakePropertiesProviderBuilder() {
                configurations = new HashMap<>();
            }

            public FakePropertiesProviderBuilder register(String objectstorage, Configuration configuration) {
                configurations.put(objectstorage, configuration);
                return this;
            }

            public FakePropertiesProvider build() {
                return new FakePropertiesProvider(configurations);
            }
        }
    }

    private static class MapConfigurationBuilder {
        private ImmutableMap.Builder<String, Object> config;

        public MapConfigurationBuilder() {
            this.config = new ImmutableMap.Builder<>();
        }

        public MapConfigurationBuilder put(String key, Object value) {
            config.put(key, value);
            return this;
        }

        public MapConfiguration build() {
            return new MapConfiguration(config.build());
        }
    }
}

