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

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.james.blob.objectstorage.ContainerName;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAOBuilder;
import org.apache.james.util.Size;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

public class AwsS3ObjectStorage {

    private static final Iterable<Module> JCLOUDS_MODULES =
        ImmutableSet.of(new SLF4JLoggingModule());
    public static final int MAX_UPLOAD_THREADS = 5;
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

    public static Optional<Function<Blob, String>> putBlob(ContainerName containerName, AwsS3AuthConfiguration configuration) {
        return Optional.of((blob) -> {
            try {
                PutObjectRequest request = new PutObjectRequest(containerName.value(),
                    blob.getMetadata().getName(),
                    blob.getPayload().openStream(),
                    new ObjectMetadata());

                return getTransferManager(configuration)
                    .upload(request)
                    .waitForUploadResult()
                    .getETag();
            } catch (AmazonClientException | InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static TransferManager getTransferManager(AwsS3AuthConfiguration configuration) {
        AmazonS3 amazonS3 = AmazonS3ClientBuilder
            .standard()
            .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(configuration.getAccessKeyId(), configuration.getSecretKey())))
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(configuration.getEndpoint(), null))
            .build();

        return TransferManagerBuilder
            .standard()
            .withS3Client(amazonS3)
            .withMultipartUploadThreshold(MULTIPART_UPLOAD_THRESHOLD.getValue())
            .withExecutorFactory(() -> Executors.newFixedThreadPool(MAX_UPLOAD_THREADS))
            .build();
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
