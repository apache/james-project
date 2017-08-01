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

package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Optional;

import org.apache.commons.io.Charsets;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraConfiguration;
import org.apache.james.mailbox.cassandra.ids.BlobId;
import org.apache.james.mailbox.cassandra.modules.CassandraBlobModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Strings;

public class CassandraBlobsDAOTest {
    private static final int CHUNK_SIZE = 1024;
    private static final int MULTIPLE_CHUNK_SIZE = 3;
    private CassandraCluster cassandra;
    private CassandraBlobsDAO testee;

    @Before
    public void setUp() throws Exception {
        cassandra = CassandraCluster.create(new CassandraBlobModule());
        cassandra.ensureAllTables();

        testee = new CassandraBlobsDAO(cassandra.getConf(),
            CassandraConfiguration.builder()
                .blobPartSize(CHUNK_SIZE)
                .build());
    }

    @After
    public void tearDown() throws Exception {
        cassandra.clearAllTables();
        cassandra.close();
    }

    @Test
    public void saveShouldReturnEmptyWhenNullData() throws Exception {
        Optional<BlobId> blobId = testee.save(null).join();


        assertThat(blobId).isEmpty();
    }

    @Test
    public void saveShouldSaveEmptyData() throws Exception {
        Optional<BlobId> blobId = testee.save(new byte[]{}).join();

        byte[] bytes = testee.read(blobId.get()).join();

        assertThat(blobId).isPresent();
        assertThat(new String(bytes, Charsets.UTF_8)).isEmpty();
    }

    @Test
    public void saveShouldSaveBlankData() throws Exception {
        Optional<BlobId> blobId = testee.save("".getBytes(Charsets.UTF_8)).join();

        byte[] bytes = testee.read(blobId.get()).join();

        assertThat(blobId).isPresent();
        assertThat(new String(bytes, Charsets.UTF_8)).isEmpty();
    }

    @Test
    public void saveShouldReturnBlobId() throws Exception {
        Optional<BlobId> blobId = testee.save("toto".getBytes(Charsets.UTF_8)).join();

        assertThat(blobId).isPresent();
    }

    @Test
    public void readShouldBeEmptyWhenNoExisting() throws IOException {
        byte[] bytes = testee.read(BlobId.from("unknown")).join();

        assertThat(bytes).isEmpty();
    }

    @Test
    public void readShouldReturnSavedData() throws IOException {
        Optional<BlobId> blobId = testee.save("toto".getBytes(Charsets.UTF_8)).join();

        byte[] bytes = testee.read(blobId.get()).join();

        assertThat(new String(bytes, Charsets.UTF_8)).isEqualTo("toto");
    }

    @Test
    public void readShouldReturnLongSavedData() throws IOException {
        String longString = Strings.repeat("0123456789\n", 1000);
        Optional<BlobId> blobId = testee.save(longString.getBytes(Charsets.UTF_8)).join();

        byte[] bytes = testee.read(blobId.get()).join();

        assertThat(new String(bytes, Charsets.UTF_8)).isEqualTo(longString);
    }

    @Test
    public void readShouldReturnSplitSavedDataByChunk() throws IOException {
        String longString = Strings.repeat("0123456789\n", MULTIPLE_CHUNK_SIZE);
        Optional<BlobId> blobId = testee.save(longString.getBytes(Charsets.UTF_8)).join();

        byte[] bytes = testee.read(blobId.get()).join();

        assertThat(new String(bytes, Charsets.UTF_8)).isEqualTo(longString);
    }

}