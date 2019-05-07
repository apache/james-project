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

package org.apache.james.blob.objectstorage.aws;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.objectstorage.ContainerName;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAOBuilder;
import org.apache.james.blob.objectstorage.PutBlobFunction;
import org.apache.james.util.Size;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

public class AwsS3ObjectStorage {

    private static final Iterable<Module> JCLOUDS_MODULES = ImmutableSet.of(new SLF4JLoggingModule());
    public static final int MAX_THREADS = 5;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(MAX_THREADS, NamedThreadFactory.withClassName(AwsS3ObjectStorage.class));
    private static final boolean DO_NOT_SHUTDOWN_THREAD_POOL = false;
    private static final int MAX_ERROR_RETRY = 5;
    private static final int FIRST_TRY = 0;
    private static final int MAX_RETRY_ON_EXCEPTION = 3;
    public static Size MULTIPART_UPLOAD_THRESHOLD;

    static {
        try {
            MULTIPART_UPLOAD_THRESHOLD = Size.parse("5M");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectStorageBlobsDAOBuilder.RequireContainerName daoBuilder(AwsS3AuthConfiguration configuration) {
        return ObjectStorageBlobsDAOBuilder.forBlobStore(new BlobStoreBuilder(configuration));
    }

    public static Optional<PutBlobFunction> putBlob(BlobId.Factory blobIdFactory, ContainerName containerName, AwsS3AuthConfiguration configuration) {
        return Optional.of((blob) -> {
            File file = null;
            try {
                file = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
                FileUtils.copyToFile(blob.getPayload().openStream(), file);
                putWithRetry(blobIdFactory, containerName, configuration, blob, file, FIRST_TRY);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (file != null) {
                    FileUtils.deleteQuietly(file);
                }
            }
        });
    }

    private static void putWithRetry(BlobId.Factory blobIdFactory, ContainerName containerName, AwsS3AuthConfiguration configuration, Blob blob, File file, int tried) {
        try {
            put(blobIdFactory, containerName, configuration, blob, file);
        } catch (RuntimeException e) {
            if (tried < MAX_RETRY_ON_EXCEPTION) {
                putWithRetry(blobIdFactory, containerName, configuration, blob, file, tried + 1);
            } else {
                throw e;
            }
        }
    }

    private static void put(BlobId.Factory blobIdFactory, ContainerName containerName, AwsS3AuthConfiguration configuration, Blob blob, File file) {
        try {
            PutObjectRequest request = new PutObjectRequest(containerName.value(),
                blob.getMetadata().getName(),
                file);

            getTransferManager(configuration)
                .upload(request)
                .waitForUploadResult();
        } catch (AmazonClientException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static TransferManager getTransferManager(AwsS3AuthConfiguration configuration) {
        ClientConfiguration clientConfiguration = getClientConfiguration();
        AmazonS3 amazonS3 = getS3Client(configuration, clientConfiguration);

        return TransferManagerBuilder
            .standard()
            .withS3Client(amazonS3)
            .withMultipartUploadThreshold(MULTIPART_UPLOAD_THRESHOLD.getValue())
            .withExecutorFactory(() -> EXECUTOR_SERVICE)
            .withShutDownThreadPools(DO_NOT_SHUTDOWN_THREAD_POOL)
            .build();
    }

    private static AmazonS3 getS3Client(AwsS3AuthConfiguration configuration, ClientConfiguration clientConfiguration) {
        return AmazonS3ClientBuilder
                .standard()
                .withClientConfiguration(clientConfiguration)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(configuration.getAccessKeyId(), configuration.getSecretKey())))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(configuration.getEndpoint(), null))
                .build();
    }

    private static ClientConfiguration getClientConfiguration() {
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setRetryPolicy(PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(MAX_ERROR_RETRY));
        return clientConfiguration;
    }

    private static class BlobStoreBuilder implements Supplier<BlobStore> {
        private final AwsS3AuthConfiguration configuration;

        private BlobStoreBuilder(AwsS3AuthConfiguration configuration) {
            this.configuration = configuration;
        }

        public BlobStore get() {
            Properties overrides = new Properties();
            overrides.setProperty("PROPERTY_S3_VIRTUAL_HOST_BUCKETS", "false");

            return contextBuilder()
                .endpoint(configuration.getEndpoint())
                .credentials(configuration.getAccessKeyId(), configuration.getSecretKey())
                .overrides(overrides)
                .modules(JCLOUDS_MODULES)
                .buildView(BlobStoreContext.class)
                .getBlobStore();
        }

        private ContextBuilder contextBuilder() {
            return ContextBuilder.newBuilder("s3");
        }
    }
}
