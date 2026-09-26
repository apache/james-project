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

package org.apache.james.blob.compaction;

import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.blob.api.BucketName;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GCBlobCompactionTaskSerializationTest {
    private BlobCompactionAlgorithm algorithm;
    private Clock clock;

    @BeforeEach
    void setUp() {
        algorithm = mock(BlobCompactionAlgorithm.class);
        clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        CompactionConfiguration config = CompactionConfiguration.builder()
            .chunkTargetSize(100 * 1024 * 1024L)
            .purgeDeadRatio(0.1)
            .mergeDeadRatio(0.5)
            .gainThreshold(0.1)
            .build();

        CompactionRequest request = CompactionRequest.builder()
            .bucketName(BucketName.DEFAULT)
            .generation(2L)
            .family(1)
            .configuration(config)
            .build();

        GCBlobCompactionTask task = new GCBlobCompactionTask(algorithm, request, clock);

        JsonSerializationVerifier.dtoModule(BlobCompactionDTOModules.gcCompactionTaskModule(algorithm, clock))
            .bean(task)
            .json(ClassLoaderUtils.getSystemResourceAsString("json/gcBlobCompaction.task.json"))
            .verify();
    }
}
