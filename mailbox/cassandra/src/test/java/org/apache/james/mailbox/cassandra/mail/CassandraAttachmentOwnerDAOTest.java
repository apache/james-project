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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.model.AttachmentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;

class CassandraAttachmentOwnerDAOTest {
    private static final AttachmentId ATTACHMENT_ID = AttachmentId.from("id1");
    private static final Username OWNER_1 = Username.of("owner1");
    private static final Username OWNER_2 = Username.of("owner2");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraAttachmentModule.MODULE);

    private CassandraAttachmentOwnerDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraAttachmentOwnerDAO(cassandra.getConf()
        );
    }

    @Test
    void retrieveOwnersShouldReturnEmptyByDefault() {
        assertThat(testee.retrieveOwners(ATTACHMENT_ID).toIterable())
            .isEmpty();
    }

    @Test
    void retrieveOwnersShouldReturnAddedOwner() {
        testee.addOwner(ATTACHMENT_ID, OWNER_1).block();

        assertThat(testee.retrieveOwners(ATTACHMENT_ID).toIterable())
            .containsOnly(OWNER_1);
    }

    @Test
    void retrieveOwnersShouldReturnAddedOwners() {
        testee.addOwner(ATTACHMENT_ID, OWNER_1).block();
        testee.addOwner(ATTACHMENT_ID, OWNER_2).block();

        assertThat(testee.retrieveOwners(ATTACHMENT_ID).toIterable())
            .containsOnly(OWNER_1, OWNER_2);
    }

    @Test
    void retrieveOwnersShouldNotThrowWhenMoreReferencesThanPaging() {
        int concurrency = 128;
        int referenceCountExceedingPaging = 5050;

        Flux.range(0, referenceCountExceedingPaging)
            .flatMap(i -> testee.addOwner(ATTACHMENT_ID, Username.of("owner" + i)), concurrency)
            .blockLast();

        assertThat(testee.retrieveOwners(ATTACHMENT_ID).toIterable())
            .hasSize(referenceCountExceedingPaging);
    }
}