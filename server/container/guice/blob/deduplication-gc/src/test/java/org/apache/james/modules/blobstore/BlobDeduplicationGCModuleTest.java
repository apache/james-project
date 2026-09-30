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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.server.blob.deduplication.MinIOGenerationAwareBlobId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlobDeduplicationGCModuleTest {

    private static final String FOLDER_HIERARCHY_PROPERTY = "james.blobstore.folder.hierarchy";
    private static final String S3_MINIO_COMPATIBILITY_PROPERTY = "james.s3.minio.compatibility.mode";

    private BlobDeduplicationGCModule module;

    @BeforeEach
    @AfterEach
    void clearProperties() {
        System.clearProperty(FOLDER_HIERARCHY_PROPERTY);
        System.clearProperty(S3_MINIO_COMPATIBILITY_PROPERTY);
    }

    @BeforeEach
    void setUp() {
        module = new BlobDeduplicationGCModule();
    }

    @Test
    void shouldReturnDefaultFactoryWhenNoPropertiesSet() {
        BlobId.Factory factory = module.generationAwareBlobIdFactory(
            Clock.systemUTC(),
            new PlainBlobId.Factory(),
            GenerationAwareBlobId.Configuration.DEFAULT);

        assertThat(factory).isExactlyInstanceOf(GenerationAwareBlobId.Factory.class);
    }

    @Test
    void shouldReturnMinIOFactoryWhenFolderHierarchyPropertyIsTrue() {
        System.setProperty(FOLDER_HIERARCHY_PROPERTY, "true");

        BlobId.Factory factory = module.generationAwareBlobIdFactory(
            Clock.systemUTC(),
            new PlainBlobId.Factory(),
            GenerationAwareBlobId.Configuration.DEFAULT);

        assertThat(factory).isExactlyInstanceOf(MinIOGenerationAwareBlobId.Factory.class);
    }

    @Test
    void shouldReturnMinIOFactoryWhenS3MinioCompatibilityModeIsTrue() {
        System.setProperty(S3_MINIO_COMPATIBILITY_PROPERTY, "true");

        BlobId.Factory factory = module.generationAwareBlobIdFactory(
            Clock.systemUTC(),
            new PlainBlobId.Factory(),
            GenerationAwareBlobId.Configuration.DEFAULT);

        assertThat(factory).isExactlyInstanceOf(MinIOGenerationAwareBlobId.Factory.class);
    }

    @Test
    void shouldPrioritizeFolderHierarchyPropertyOverCompatibilityMode() {
        System.setProperty(FOLDER_HIERARCHY_PROPERTY, "false");
        System.setProperty(S3_MINIO_COMPATIBILITY_PROPERTY, "true");

        BlobId.Factory factory = module.generationAwareBlobIdFactory(
            Clock.systemUTC(),
            new PlainBlobId.Factory(),
            GenerationAwareBlobId.Configuration.DEFAULT);

        assertThat(factory).isExactlyInstanceOf(GenerationAwareBlobId.Factory.class);
    }
}
