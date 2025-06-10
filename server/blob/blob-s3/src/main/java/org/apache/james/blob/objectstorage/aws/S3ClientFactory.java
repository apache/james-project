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

package org.apache.james.blob.objectstorage.aws;

import static org.apache.james.blob.objectstorage.aws.JamesS3MetricPublisher.DEFAULT_S3_METRICS_PREFIX;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.james.lifecycle.api.Startable;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.collect.ImmutableList;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.TlsTrustManagersProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.s3.LegacyMd5Plugin;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Singleton
public class S3ClientFactory implements Startable, Closeable {
    private static final TrustManager DUMMY_TRUST_MANAGER = new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Always trust
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Always trust
        }
    };

    public static final String S3_METRICS_ENABLED_PROPERTY_KEY = "james.s3.metrics.enabled";
    public static final String S3_METRICS_ENABLED_DEFAULT_VALUE = "true";
    public static final String S3_METRICS_PREFIX = System.getProperty("james.s3.metrics.prefix", DEFAULT_S3_METRICS_PREFIX);
    public static final boolean S3_CHECKSUM_BACKWARD_COMPATIBILITY_ENABLED = Boolean.parseBoolean(System.getProperty("james.s3.sdk.checksum.backward.compatibility", "true"));

    private final S3AsyncClient s3Client;

    @Inject
    public S3ClientFactory(S3BlobStoreConfiguration configuration, MetricFactory metricFactory, GaugeRegistry gaugeRegistry) {
        this(configuration, () -> new JamesS3MetricPublisher(metricFactory, gaugeRegistry, S3_METRICS_PREFIX));
    }

    public S3ClientFactory(S3BlobStoreConfiguration configuration, Provider<JamesS3MetricPublisher> jamesS3MetricPublisherProvider) {
        AwsS3AuthConfiguration authConfiguration = configuration.getSpecificAuthConfiguration();
        S3Configuration pathStyleAccess = S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .build();

        s3Client = createS3AsyncClient(configuration, jamesS3MetricPublisherProvider, authConfiguration, pathStyleAccess);
    }

    private S3AsyncClient createS3AsyncClient(S3BlobStoreConfiguration configuration, Provider<JamesS3MetricPublisher> jamesS3MetricPublisherProvider, AwsS3AuthConfiguration authConfiguration, S3Configuration pathStyleAccess) {
        S3AsyncClientBuilder s3AsyncClientBuilder = S3AsyncClient.builder()
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(authConfiguration.getAccessKeyId(), authConfiguration.getSecretKey())))
            .httpClientBuilder(httpClientBuilder(configuration))
            .endpointOverride(authConfiguration.getEndpoint())
            .region(configuration.getRegion().asAws())
            .serviceConfiguration(pathStyleAccess)
            .overrideConfiguration(builder -> {
                boolean s3MetricsEnabled = Boolean.parseBoolean(System.getProperty(S3_METRICS_ENABLED_PROPERTY_KEY, S3_METRICS_ENABLED_DEFAULT_VALUE));
                if (s3MetricsEnabled) {
                    builder.addMetricPublisher(jamesS3MetricPublisherProvider.get());
                }
            });

        if (S3_CHECKSUM_BACKWARD_COMPATIBILITY_ENABLED) {
            s3AsyncClientBuilder
                .addPlugin(LegacyMd5Plugin.create())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        }

        return s3AsyncClientBuilder.build();
    }

    private NettyNioAsyncHttpClient.Builder httpClientBuilder(S3BlobStoreConfiguration configuration) {
        NettyNioAsyncHttpClient.Builder result = NettyNioAsyncHttpClient.builder()
            .tlsTrustManagersProvider(getTrustManagerProvider(configuration.getSpecificAuthConfiguration()))
            .maxConcurrency(configuration.getHttpConcurrency())
            .maxPendingConnectionAcquires(10_000);
        configuration.getWriteTimeout().ifPresent(result::writeTimeout);
        configuration.getReadTimeout().ifPresent(result::readTimeout);
        configuration.getConnectionTimeout().ifPresent(result::connectionTimeout);
        result.useNonBlockingDnsResolver(true);
        return result;
    }

    private TlsTrustManagersProvider getTrustManagerProvider(AwsS3AuthConfiguration configuration) {
        if (configuration.isTrustAll()) {
            return () -> ImmutableList.of(DUMMY_TRUST_MANAGER).toArray(new TrustManager[0]);
        }
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                configuration.getTrustStoreAlgorithm().orElse(TrustManagerFactory.getDefaultAlgorithm()));
            KeyStore trustStore = loadTrustStore(configuration);
            trustManagerFactory.init(trustStore);
            return trustManagerFactory::getTrustManagers;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private KeyStore loadTrustStore(AwsS3AuthConfiguration configuration) {
        if (configuration.getTrustStorePath().isEmpty()) {
            return null; // use java default truststore
        }
        try (FileInputStream trustStoreStream = new FileInputStream(configuration.getTrustStorePath().get())) {
            char[] secret = configuration.getTrustStoreSecret().map(String::toCharArray).orElse(null);
            KeyStore trustStore = KeyStore.getInstance(
                configuration.getTrustStoreType().orElse(KeyStore.getDefaultType()));
            trustStore.load(trustStoreStream, secret);
            return trustStore;
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public S3AsyncClient get() {
        return s3Client;
    }

    @Override
    @PreDestroy
    public void close() {
        s3Client.close();
    }
}
