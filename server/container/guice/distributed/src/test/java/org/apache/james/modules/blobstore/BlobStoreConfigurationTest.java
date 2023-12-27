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

import static org.apache.james.modules.blobstore.BlobStoreConfiguration.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.FakePropertiesProvider;
import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.server.blob.deduplication.StorageStrategy;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class BlobStoreConfigurationTest {

    private static final String S3 = "s3";
    private static final String CASSANDRA = "cassandra";

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(BlobStoreConfiguration.class)
            .verify();
    }

    @Test
    void provideChoosingConfigurationShouldThrowWhenMissingPropertyField() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> parse(propertyProvider))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void provideChoosingConfigurationShouldThrowWhenEmptyPropertyField() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> parse(propertyProvider))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encryptionShouldRequirePassword() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "s3");
        configuration.addProperty("deduplication.enable", false);
        configuration.addProperty("encryption.aes.enable", true);
        // Hex.encode("salty".getBytes(StandardCharsets.UTF_8))
        configuration.addProperty("encryption.aes.salt", "73616c7479");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> parse(propertyProvider))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encryptionShouldRequireSalt() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "cassandra");
        configuration.addProperty("deduplication.enable", false);
        configuration.addProperty("encryption.aes.enable", true);
        configuration.addProperty("encryption.aes.password", "salty");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> parse(propertyProvider))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encryptionShouldBeDisabledByDefault() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "cassandra");
        configuration.addProperty("deduplication.enable", false);
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.builder()
                .cassandra()
                .disableCache()
                .passthrough()
                .noCryptoConfig());
    }

    @Test
    void encryptionShouldBeDisableable() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "cassandra");
        configuration.addProperty("deduplication.enable", false);
        configuration.addProperty("encryption.aes.enable", false);
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.builder()
                .cassandra()
                .disableCache()
                .passthrough()
                .noCryptoConfig());
    }

    @Test
    void encryptionCanBeActivated() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "cassandra");
        configuration.addProperty("deduplication.enable", false);
        configuration.addProperty("encryption.aes.enable", true);
        configuration.addProperty("encryption.aes.password", "myPass");
        // Hex.encode("salty".getBytes(StandardCharsets.UTF_8))
        configuration.addProperty("encryption.aes.salt", "73616c7479");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.builder()
                .cassandra()
                .disableCache()
                .passthrough()
                .cryptoConfig(CryptoConfig.builder()
                    .password("myPass".toCharArray())
                    .salt("73616c7479")
                    .build()));
    }

    @Test
    void provideChoosingConfigurationShouldThrowWhenPropertyFieldIsNotInSupportedList() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "gabouzomeuh");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThatThrownBy(() -> parse(propertyProvider))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void provideChoosingConfigurationShouldReturnCassandraWhenNoFile() throws Exception {
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register("other_configuration_file", new PropertiesConfiguration())
            .build();

        assertThat(parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.builder()
                    .cassandra()
                    .disableCache()
                    .passthrough()
                    .noCryptoConfig());
    }

    @Test
    void provideChoosingConfigurationShouldReturnObjectStorageFactoryWhenConfigurationImplIsS3() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.S3.getName());
        configuration.addProperty("deduplication.enable", "true");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.builder()
                    .s3()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig());
    }

    @Test
    void provideChoosingConfigurationShouldReturnCassandraFactoryWhenConfigurationImplIsCassandra() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.CASSANDRA.getName());
        configuration.addProperty("deduplication.enable", "false");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.builder()
                    .cassandra()
                    .disableCache()
                    .passthrough()
                    .noCryptoConfig());
    }

    @Test
    void provideChoosingConfigurationShouldReturnFileFactoryWhenConfigurationImplIsFile() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.FILE.getName());
        configuration.addProperty("deduplication.enable", "false");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.builder()
                    .file()
                    .disableCache()
                    .passthrough()
                    .noCryptoConfig());
    }

    @Test
    void provideChoosingConfigurationShouldReturnPostgresFactoryWhenConfigurationImplIsPostgres() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.POSTGRES.getName());
        configuration.addProperty("deduplication.enable", "false");
        FakePropertiesProvider propertyProvider = FakePropertiesProvider.builder()
            .register(ConfigurationComponent.NAME, configuration)
            .build();

        assertThat(parse(propertyProvider))
            .isEqualTo(BlobStoreConfiguration.builder()
                .postgres()
                .disableCache()
                .passthrough()
                .noCryptoConfig());
    }

    @Test
    void fromShouldThrowWhenBlobStoreImplIsMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThatThrownBy(() -> BlobStoreConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("implementation property is missing please use one of supported values in: cassandra, file, s3");
    }

    @Test
    void fromShouldThrowWhenBlobStoreImplIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", null);

        assertThatThrownBy(() -> BlobStoreConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("implementation property is missing please use one of supported values in: cassandra, file, s3");
    }

    @Test
    void fromShouldThrowWhenBlobStoreImplIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "");

        assertThatThrownBy(() -> BlobStoreConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("implementation property is missing please use one of supported values in: cassandra, file, s3");
    }

    @Test
    void fromShouldThrowWhenBlobStoreImplIsNotInSupportedList() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "un_supported");

        assertThatThrownBy(() -> BlobStoreConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("un_supported is not a valid name of BlobStores, please use one of supported values in: cassandra, file, s3");
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsCassandra() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", CASSANDRA);
        configuration.addProperty("deduplication.enable", "false");

        assertThat(
            BlobStoreConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(CASSANDRA);
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsS3() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", S3);
        configuration.addProperty("deduplication.enable", "true");

        assertThat(
            BlobStoreConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(S3);
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsSupportedAndCaseInsensitive() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "CAssAnDrA");
        configuration.addProperty("deduplication.enable", "true");

        assertThat(
            BlobStoreConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(CASSANDRA);
    }

    @Test
    void fromShouldReturnConfigurationWhenBlobStoreImplIsSupportedAndHasExtraSpaces() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", " cassandra ");
        configuration.addProperty("deduplication.enable", "false");

        assertThat(
            BlobStoreConfiguration.from(configuration)
                .getImplementation()
                .getName())
            .isEqualTo(CASSANDRA);
    }

    @Test
    void cacheEnabledShouldBeTrueWhenSpecified() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.S3.getName());
        configuration.addProperty("cache.enable", true);
        configuration.addProperty("deduplication.enable", "true");

        assertThat(BlobStoreConfiguration.from(configuration).cacheEnabled())
            .isTrue();
    }

    @Test
    void cacheEnabledShouldBeFalseWhenSpecified() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.S3.getName());
        configuration.addProperty("cache.enable", false);
        configuration.addProperty("deduplication.enable", "true");

        assertThat(BlobStoreConfiguration.from(configuration).cacheEnabled())
            .isFalse();
    }

    @Test
    void cacheEnabledShouldDefaultToFalse() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.S3.getName());
        configuration.addProperty("deduplication.enable", "true");

        assertThat(BlobStoreConfiguration.from(configuration).cacheEnabled())
            .isFalse();
    }

    @Test
    void storageStrategyShouldBePassthroughWhenDeduplicationDisabled() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.S3.getName());
        configuration.addProperty("deduplication.enable", "false");

        assertThat(BlobStoreConfiguration.from(configuration).storageStrategy())
            .isEqualTo(StorageStrategy.PASSTHROUGH);
    }

    @Test
    void storageStrategyShouldBeDeduplicationWhenDeduplicationEnabled() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.S3.getName());
        configuration.addProperty("deduplication.enable", "true");

        assertThat(BlobStoreConfiguration.from(configuration).storageStrategy())
                .isEqualTo(StorageStrategy.DEDUPLICATION);
    }

    @Test
    void buildingConfigurationShouldThrowWhenDeduplicationPropertieIsOmitted() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", BlobStoreConfiguration.BlobStoreImplName.S3.getName());

        assertThatThrownBy(() -> BlobStoreConfiguration.from(configuration)).isInstanceOf(IllegalStateException.class)
                .hasMessage("deduplication.enable property is missing please use one of the supported values in: true, false\n" +
         "If you choose to enable deduplication, the mails with the same content will be stored only once.\n" +
         "Warning: Once this feature is enabled, there is no turning back as turning it off will lead to the deletion of all\n" +
         "the mails sharing the same content once one is deleted.\n" +
        "Upgrade note: If you are upgrading from James 3.5 or older, the deduplication was enabled.");
    }

}
