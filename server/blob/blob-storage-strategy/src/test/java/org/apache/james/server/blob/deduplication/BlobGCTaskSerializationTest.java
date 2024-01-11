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

package org.apache.james.server.blob.deduplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.UpdatableTickingClock;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

class BlobGCTaskSerializationTest {
    BlobStoreDAO blobStoreDAO;
    GenerationAwareBlobId.Factory generationAwareBlobIdFactory;
    GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration;
    Set<BlobReferenceSource> blobReferenceSources;
    Clock clock;

    @BeforeEach
    void setUp() {
        blobStoreDAO = mock(BlobStoreDAO.class);
        blobReferenceSources = ImmutableSet.of(mock(BlobReferenceSource.class));
        clock = new UpdatableTickingClock(Instant.parse("2007-12-03T10:15:30.00Z"));
        generationAwareBlobIdConfiguration = GenerationAwareBlobId.Configuration.DEFAULT;
        generationAwareBlobIdFactory = new GenerationAwareBlobId.Factory(clock, new HashBlobId.Factory(), generationAwareBlobIdConfiguration);
    }

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(BlobGCTaskDTO.module(
                blobStoreDAO,
                generationAwareBlobIdFactory,
                generationAwareBlobIdConfiguration,
                blobReferenceSources,
                clock))
            .bean(new BlobGCTask(
                blobStoreDAO,
                generationAwareBlobIdFactory,
                generationAwareBlobIdConfiguration,
                blobReferenceSources,
                BucketName.DEFAULT,
                clock,
                99,
                100,
                0.8
            ))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/blobGC.task.json"))
            .verify();
    }

    @Test
    void shouldDeserializeLegacyData() throws Exception {
        BlobGCTask gcTask = JsonGenericSerializer
            .forModules(BlobGCTaskDTO.module(
                blobStoreDAO,
                generationAwareBlobIdFactory,
                generationAwareBlobIdConfiguration,
                blobReferenceSources,
                clock))
            .withoutNestedType()
            .deserialize(ClassLoaderUtils.getSystemResourceAsString("json/blobGC-legacy.task.json"));

        RecursiveComparisonConfiguration recursiveComparisonConfiguration = new RecursiveComparisonConfiguration();
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingInt(AtomicInteger::get), AtomicInteger.class);
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingLong(AtomicLong::get), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicInteger.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicBoolean.class);
        assertThat(gcTask)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(new BlobGCTask(
                blobStoreDAO,
                generationAwareBlobIdFactory,
                generationAwareBlobIdConfiguration,
                blobReferenceSources,
                BucketName.DEFAULT,
                clock,
                99,
                1000,
                0.8
            ));
    }
}
