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

package org.apache.james.blob.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraDefaultBucketDAOTest {
    static final byte[] DATA = "anydata".getBytes(StandardCharsets.UTF_8);
    static final byte[] DATA_2 = "anydata2".getBytes(StandardCharsets.UTF_8);
    static final int POSITION = 42;
    static final int POSITION_2 = 43;
    static final int NUMBER_OF_CHUNK = 17;
    static final int NUMBER_OF_CHUNK_2 = 18;
    static BlobId BLOB_ID = new HashBlobId.Factory().from("05dcb33b-8382-4744-923a-bc593ad84d23");
    static BlobId BLOB_ID_2 = new HashBlobId.Factory().from("05dcb33b-8382-4744-923a-bc593ad84d24");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraBlobModule.MODULE);

    private CassandraDefaultBucketDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraDefaultBucketDAO(cassandraCluster.getCassandraCluster().getConf());
    }

    @Test
    void readPartShouldReturnEmptyWhenNone() {
        Optional<byte[]> maybeBytes = testee.readPart(BLOB_ID, POSITION).blockOptional();

        assertThat(maybeBytes).isEmpty();
    }

    @Test
    void readPartShouldReturnPreviouslySavedData() {
        testee.writePart(ByteBuffer.wrap(DATA), BLOB_ID, POSITION).block();

        Optional<byte[]> maybeBytes = testee.readPart(BLOB_ID, POSITION).blockOptional();

        assertThat(maybeBytes).contains(DATA);
    }

    @Test
    void readPartShouldNotReturnContentOfOtherParts() {
        testee.writePart(ByteBuffer.wrap(DATA), BLOB_ID, POSITION).block();

        Optional<byte[]> maybeBytes = testee.readPart(BLOB_ID, POSITION_2).blockOptional();

        assertThat(maybeBytes).isEmpty();
    }

    @Test
    void readPartShouldReturnLatestValue() {
        testee.writePart(ByteBuffer.wrap(DATA), BLOB_ID, POSITION).block();
        testee.writePart(ByteBuffer.wrap(DATA_2), BLOB_ID, POSITION).block();

        Optional<byte[]> maybeBytes = testee.readPart(BLOB_ID, POSITION).blockOptional();

        assertThat(maybeBytes).contains(DATA_2);
    }

    @Test
    void selectRowCountShouldReturnEmptyByDefault() {
        Optional<Integer> maybeRowCount = testee.selectRowCount(BLOB_ID).blockOptional();

        assertThat(maybeRowCount).isEmpty();
    }

    @Test
    void selectRowCountShouldReturnPreviouslySavedValue() {
        testee.saveBlobPartsReferences(BLOB_ID, NUMBER_OF_CHUNK).block();

        Optional<Integer> maybeRowCount = testee.selectRowCount(BLOB_ID).blockOptional();

        assertThat(maybeRowCount).contains(NUMBER_OF_CHUNK);
    }

    @Test
    void selectRowCountShouldNotReturnOtherBlobIdValue() {
        testee.saveBlobPartsReferences(BLOB_ID, NUMBER_OF_CHUNK).block();

        Optional<Integer> maybeRowCount = testee.selectRowCount(BLOB_ID_2).blockOptional();

        assertThat(maybeRowCount).isEmpty();
    }
}