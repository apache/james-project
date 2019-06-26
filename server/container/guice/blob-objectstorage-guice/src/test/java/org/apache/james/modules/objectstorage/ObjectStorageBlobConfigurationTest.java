package org.apache.james.modules.objectstorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.james.blob.api.BucketName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

class ObjectStorageBlobConfigurationTest {

    static final ImmutableMap<String, Object> CONFIGURATION_WITHOUT_CODEC = ImmutableMap.<String, Object>builder()
        .put("objectstorage.provider", "swift")
        .put("objectstorage.swift.authapi", "tmpauth")
        .put("objectstorage.swift.endpoint", "http://swift/endpoint")
        .put("objectstorage.swift.credentials", "testing")
        .put("objectstorage.swift.tempauth.username", "tester")
        .put("objectstorage.swift.tempauth.tenantname", "test")
        .put("objectstorage.swift.tempauth.passheadername", "X-Storage-Pass")
        .put("objectstorage.swift.tempauth.userheadername", "X-Storage-User")
        .build();

    static final ImmutableMap<String, Object> VALID_CONFIGURATION = ImmutableMap.<String, Object>builder()
        .putAll(CONFIGURATION_WITHOUT_CODEC)
        .put("objectstorage.payload.codec", "DEFAULT")
        .build();

    static class RequiredParameters implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of("objectstorage.provider", "objectstorage.swift.authapi", "objectstorage.payload.codec")
                    .map(Arguments::of);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(RequiredParameters.class)
    void shouldThrowWhenRequiredParameterOmitted(String toOmit) {
        Map<String, Object> configurationWithFilteredKey = Maps.filterKeys(VALID_CONFIGURATION, key -> !toOmit.equals(key));

        assertThat(configurationWithFilteredKey).doesNotContainKeys(toOmit);
        assertThatThrownBy(() -> ObjectStorageBlobConfiguration.from(new MapConfiguration(configurationWithFilteredKey)))
            .isInstanceOf(ConfigurationException.class);
    }

    @ParameterizedTest
    @ArgumentsSource(RequiredParameters.class)
    void shouldThrowWhenRequiredParameterEmpty(String toEmpty) {
        Map<String, Object> configurationWithFilteredKey = Maps.transformEntries(VALID_CONFIGURATION, (key, value) -> {
            if (toEmpty.equals(key)) {
                return "";
            } else {
                return value;
            }
        });

        assertThat(configurationWithFilteredKey).containsEntry(toEmpty, "");
        assertThatThrownBy(() -> ObjectStorageBlobConfiguration.from(new MapConfiguration(configurationWithFilteredKey)))
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void shouldBuildAnAESPayloadCodecForAESConfig() throws Exception {
        ObjectStorageBlobConfiguration actual = ObjectStorageBlobConfiguration.from(new MapConfiguration(
            ImmutableMap.<String, Object>builder()
                .putAll(CONFIGURATION_WITHOUT_CODEC)
                .put("objectstorage.payload.codec", PayloadCodecFactory.AES256.name())
                .put("objectstorage.aes256.hexsalt", "12345123451234512345")
                .put("objectstorage.aes256.password", "james is great")
            .build()));
        assertThat(actual.getPayloadCodecFactory()).isEqualTo(PayloadCodecFactory.AES256);
        assertThat(actual.getAesSalt()).contains("12345123451234512345");
        assertThat(actual.getAesPassword()).contains("james is great".toCharArray());
    }

    @Test
    void shouldFailIfCodecKeyIsIncorrect() throws Exception {
        MapConfiguration configuration = new MapConfiguration(
            ImmutableMap.<String, Object>builder()
                .putAll(CONFIGURATION_WITHOUT_CODEC)
                .put("objectstorage.payload.codec", "aes255")
                .build());
        assertThatThrownBy(() -> ObjectStorageBlobConfiguration.from(configuration)).isInstanceOf(ConfigurationException.class);
    }

    @Test
    void shouldFailForAESCodecWhenSaltKeyIsMissing() throws Exception {
        MapConfiguration configuration = new MapConfiguration(
            ImmutableMap.<String, Object>builder()
                .putAll(CONFIGURATION_WITHOUT_CODEC)
                .put("objectstorage.payload.codec", PayloadCodecFactory.AES256.name())
                .put("objectstorage.aes256.password", "james is great")
                .build());
        assertThatThrownBy(() -> ObjectStorageBlobConfiguration.from(configuration)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailForAESCodecWhenSaltKeyIsEmpty() throws Exception {
        MapConfiguration configuration = new MapConfiguration(
            ImmutableMap.<String, Object>builder()
                .putAll(CONFIGURATION_WITHOUT_CODEC)
                .put("objectstorage.payload.codec", PayloadCodecFactory.AES256.name())
                .put("objectstorage.aes256.hexsalt", "")
                .put("objectstorage.aes256.password", "james is great")
                .build());
        assertThatThrownBy(() -> ObjectStorageBlobConfiguration.from(configuration)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailForAESCodecWhenPasswordKeyIsMissing() throws Exception {
        MapConfiguration configuration = new MapConfiguration(
            ImmutableMap.<String, Object>builder()
                .putAll(CONFIGURATION_WITHOUT_CODEC)
                .put("objectstorage.payload.codec", PayloadCodecFactory.AES256.name())
                .put("objectstorage.aes256.hexsalt", "12345123451234512345")
                .build());

        assertThatThrownBy(() -> ObjectStorageBlobConfiguration.from(configuration)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailForAESCodecWhenPasswordKeyIsEmpty() throws Exception {
        MapConfiguration configuration = new MapConfiguration(
            ImmutableMap.<String, Object>builder()
                .putAll(CONFIGURATION_WITHOUT_CODEC)
                .put("objectstorage.payload.codec", PayloadCodecFactory.AES256.name())
                .put("objectstorage.aes256.hexsalt", "12345123451234512345")
                .put("objectstorage.aes256.password", "")
                .build());

        assertThatThrownBy(() -> ObjectStorageBlobConfiguration.from(configuration)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowWhenUnknownProvider() throws Exception {
        MapConfiguration configuration = new MapConfiguration(
            ImmutableMap.<String, Object>builder()
                .put("objectstorage.payload.codec", PayloadCodecFactory.DEFAULT.name())
                .put("objectstorage.provider", "unknown")
                .put("objectstorage.namespace", "foo")
                .build());

        assertThatThrownBy(() -> ObjectStorageBlobConfiguration.from(configuration))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("Unknown object storage provider: unknown");
    }

    @Test
    void fromShouldParseNameSpaceWhenSpecified() throws Exception {
        String bucketNameAsString = "my-bucket";
        MapConfiguration configuration = new MapConfiguration(
            ImmutableMap.<String, Object>builder()
                .putAll(VALID_CONFIGURATION)
                .put("objectstorage.namespace", bucketNameAsString)
                .build());

         assertThat(ObjectStorageBlobConfiguration.from(configuration).getNamespace())
            .isEqualTo(BucketName.of(bucketNameAsString));
    }

     @Test
    void fromShouldUseTheDefaultValueWhenDontSpecifyNameSpace() throws Exception {
        MapConfiguration configuration = new MapConfiguration(
            ImmutableMap.<String, Object>builder()
                .putAll(VALID_CONFIGURATION)
                .build());

         assertThat(ObjectStorageBlobConfiguration.from(configuration).getNamespace())
            .isEqualTo(BucketName.DEFAULT);
    }

    @Test
    void fromShouldParseBucketPrefixWhenSpecified() throws Exception {
        String bucketPrefix = "defaultPrefix";
        MapConfiguration configuration = new MapConfiguration(
            ImmutableMap.<String, Object>builder()
                .putAll(VALID_CONFIGURATION)
                .put("objectstorage.bucketPrefix", bucketPrefix)
                .build());

         assertThat(ObjectStorageBlobConfiguration.from(configuration).getBucketPrefix())
            .isEqualTo(bucketPrefix);
    }

     @Test
    void fromShouldUseTheDefaultValueWhenDontSpecifyBucketPrefix() throws Exception {
        MapConfiguration configuration = new MapConfiguration(
            ImmutableMap.<String, Object>builder()
                .putAll(VALID_CONFIGURATION)
                .build());

         assertThat(ObjectStorageBlobConfiguration.from(configuration).getBucketPrefix())
            .isEqualTo(ObjectStorageBlobConfiguration.DEFAULT_BUCKET_PREFIX);
    }
}