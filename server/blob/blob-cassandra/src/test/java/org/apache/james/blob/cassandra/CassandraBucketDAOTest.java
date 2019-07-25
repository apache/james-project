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

import static org.apache.james.blob.cassandra.CassandraBlobStoreFixture.BLOB_ID;
import static org.apache.james.blob.cassandra.CassandraBlobStoreFixture.BLOB_ID_2;
import static org.apache.james.blob.cassandra.CassandraBlobStoreFixture.BUCKET_NAME;
import static org.apache.james.blob.cassandra.CassandraBlobStoreFixture.BUCKET_NAME_2;
import static org.apache.james.blob.cassandra.CassandraBlobStoreFixture.DATA;
import static org.apache.james.blob.cassandra.CassandraBlobStoreFixture.DATA_2;
import static org.apache.james.blob.cassandra.CassandraBlobStoreFixture.NUMBER_OF_CHUNK;
import static org.apache.james.blob.cassandra.CassandraBlobStoreFixture.POSITION;
import static org.apache.james.blob.cassandra.CassandraBlobStoreFixture.POSITION_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.HashBlobId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraBucketDAOTest {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraBlobModule.MODULE);

    private CassandraBucketDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraBucketDAO(new HashBlobId.Factory(), cassandraCluster.getCassandraCluster().getConf());
    }

    @Test
    void readPartShouldReturnEmptyWhenNone() {
        Optional<byte[]> maybeBytes = testee.readPart(BUCKET_NAME, BLOB_ID, POSITION).blockOptional();

        assertThat(maybeBytes).isEmpty();
    }

    @Test
    void deletePositionShouldNotThrowWhenMissing() {
        assertThatCode(() -> testee.deletePosition(BUCKET_NAME, BLOB_ID).block())
            .doesNotThrowAnyException();
    }

    @Test
    void deletePartShouldNotThrowWhenMissing() {
        assertThatCode(() -> testee.deleteParts(BUCKET_NAME, BLOB_ID).block())
            .doesNotThrowAnyException();
    }

    @Test
    void selectRowCountShouldNotReturnDeletedData() {
        testee.saveBlobPartsReferences(BUCKET_NAME, BLOB_ID, NUMBER_OF_CHUNK).block();

        testee.deletePosition(BUCKET_NAME, BLOB_ID).block();

        Optional<Integer> maybeRowCount = testee.selectRowCount(BUCKET_NAME, BLOB_ID).blockOptional();
        assertThat(maybeRowCount).isEmpty();
    }

    @Test
    void readPartShouldNotReturnDeletedItem() {
        testee.writePart(ByteBuffer.wrap(DATA), BUCKET_NAME, BLOB_ID, POSITION).block();

        testee.deleteParts(BUCKET_NAME, BLOB_ID).block();

        Optional<byte[]> maybeBytes = testee.readPart(BUCKET_NAME, BLOB_ID, POSITION).blockOptional();
        assertThat(maybeBytes).isEmpty();
    }

    @Test
    void readPartShouldNotReturnDeletedItems() {
        testee.writePart(ByteBuffer.wrap(DATA), BUCKET_NAME, BLOB_ID, POSITION).block();
        testee.writePart(ByteBuffer.wrap(DATA), BUCKET_NAME, BLOB_ID, POSITION_2).block();

        testee.deleteParts(BUCKET_NAME, BLOB_ID).block();

        Optional<byte[]> maybeBytes = testee.readPart(BUCKET_NAME, BLOB_ID, POSITION).blockOptional();
        Optional<byte[]> maybeBytes2 = testee.readPart(BUCKET_NAME, BLOB_ID, POSITION_2).blockOptional();
        assertThat(maybeBytes).isEmpty();
        assertThat(maybeBytes2).isEmpty();
    }

    @Test
    void readPartShouldReturnPreviouslySavedData() {
        testee.writePart(ByteBuffer.wrap(DATA), BUCKET_NAME, BLOB_ID, POSITION).block();

        Optional<byte[]> maybeBytes = testee.readPart(BUCKET_NAME, BLOB_ID, POSITION).blockOptional();

        assertThat(maybeBytes).contains(DATA);
    }

    @Test
    void readPartShouldNotReturnContentOfOtherParts() {
        testee.writePart(ByteBuffer.wrap(DATA), BUCKET_NAME, BLOB_ID, POSITION).block();

        Optional<byte[]> maybeBytes = testee.readPart(BUCKET_NAME, BLOB_ID, POSITION_2).blockOptional();

        assertThat(maybeBytes).isEmpty();
    }

    @Test
    void readPartShouldNotReturnContentOfOtherBuckets() {
        testee.writePart(ByteBuffer.wrap(DATA), BUCKET_NAME, BLOB_ID, POSITION).block();

        Optional<byte[]> maybeBytes = testee.readPart(BUCKET_NAME_2, BLOB_ID, POSITION).blockOptional();

        assertThat(maybeBytes).isEmpty();
    }

    @Test
    void readPartShouldReturnLatestValue() {
        testee.writePart(ByteBuffer.wrap(DATA), BUCKET_NAME, BLOB_ID, POSITION).block();
        testee.writePart(ByteBuffer.wrap(DATA_2), BUCKET_NAME, BLOB_ID, POSITION).block();

        Optional<byte[]> maybeBytes = testee.readPart(BUCKET_NAME, BLOB_ID, POSITION).blockOptional();

        assertThat(maybeBytes).contains(DATA_2);
    }

    @Test
    void selectRowCountShouldReturnEmptyByDefault() {
        Optional<Integer> maybeRowCount = testee.selectRowCount(BUCKET_NAME, BLOB_ID).blockOptional();

        assertThat(maybeRowCount).isEmpty();
    }

    @Test
    void selectRowCountShouldReturnPreviouslySavedValue() {
        testee.saveBlobPartsReferences(BUCKET_NAME, BLOB_ID, NUMBER_OF_CHUNK).block();

        Optional<Integer> maybeRowCount = testee.selectRowCount(BUCKET_NAME, BLOB_ID).blockOptional();

        assertThat(maybeRowCount).contains(NUMBER_OF_CHUNK);
    }

    @Test
    void selectRowCountShouldNotReturnOtherBlobIdValue() {
        testee.saveBlobPartsReferences(BUCKET_NAME, BLOB_ID, NUMBER_OF_CHUNK).block();

        Optional<Integer> maybeRowCount = testee.selectRowCount(BUCKET_NAME, BLOB_ID_2).blockOptional();

        assertThat(maybeRowCount).isEmpty();
    }

    @Test
    void listAllShouldReturnEmptyWhenNone() {
        assertThat(testee.listAll().toStream()).isEmpty();
    }

    @Test
    void listAllShouldReturnPreviouslyInsertedData() {
        testee.saveBlobPartsReferences(BUCKET_NAME, BLOB_ID, NUMBER_OF_CHUNK).block();
        testee.saveBlobPartsReferences(BUCKET_NAME_2, BLOB_ID, NUMBER_OF_CHUNK).block();
        testee.saveBlobPartsReferences(BUCKET_NAME, BLOB_ID_2, NUMBER_OF_CHUNK).block();

        assertThat(testee.listAll().toStream()).containsOnly(
            Pair.of(BUCKET_NAME, BLOB_ID),
            Pair.of(BUCKET_NAME_2, BLOB_ID),
            Pair.of(BUCKET_NAME, BLOB_ID_2));
    }

    @Test
    void listAllShouldNotReturnDeletedData() {
        testee.saveBlobPartsReferences(BUCKET_NAME, BLOB_ID, NUMBER_OF_CHUNK).block();

        testee.deletePosition(BUCKET_NAME, BLOB_ID).block();

        assertThat(testee.listAll().toStream()).isEmpty();
    }
}