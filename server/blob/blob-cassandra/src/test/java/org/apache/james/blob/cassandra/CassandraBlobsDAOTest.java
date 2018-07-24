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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraExtension;
import org.apache.james.backends.cassandra.DockerCassandraExtension.DockerCassandra;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.ObjectStore;
import org.apache.james.blob.api.ObjectStoreContract;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.base.Strings;

@ExtendWith(DockerCassandraExtension.class)
public class CassandraBlobsDAOTest implements ObjectStoreContract {

    private static final int CHUNK_SIZE = 10240;
    private static final int MULTIPLE_CHUNK_SIZE = 3;

    private static CassandraCluster cassandra;
    private CassandraBlobsDAO testee;

    @BeforeAll
    static void setUpClass(DockerCassandra dockerCassandra) {
        cassandra = CassandraCluster.create(new CassandraBlobModule(), dockerCassandra.getHost());
    }

    @BeforeEach
    void setUp() {
        testee = new CassandraBlobsDAO(cassandra.getConf(),
            CassandraConfiguration.builder()
                .blobPartSize(CHUNK_SIZE)
                .build(),
            new CassandraBlobId.Factory());
    }

    @AfterEach
    void tearDown() {
        cassandra.clearTables();
    }

    @AfterAll
    public static void tearDownClass() {
        cassandra.closeCluster();
    }

    @Override
    public ObjectStore testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return new CassandraBlobId.Factory();
    }

    @Test
    public void readShouldReturnSplitSavedDataByChunk() throws IOException {
        String longString = Strings.repeat("0123456789\n", MULTIPLE_CHUNK_SIZE);
        BlobId blobId = testee.save(longString.getBytes(StandardCharsets.UTF_8)).join();

        byte[] bytes = testee.read(blobId).join();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(longString);
    }

}