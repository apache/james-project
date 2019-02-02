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

import java.util.stream.IntStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.store.mail.model.Username;
import org.apache.james.util.streams.JamesCollectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;

class CassandraAttachmentOwnerDAOTest {
    private static final AttachmentId ATTACHMENT_ID = AttachmentId.from("id1");
    private static final Username OWNER_1 = Username.fromRawValue("owner1");
    private static final Username OWNER_2 = Username.fromRawValue("owner2");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraAttachmentModule.MODULE);

    private CassandraAttachmentOwnerDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraAttachmentOwnerDAO(cassandra.getConf(),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION);
    }

    @Test
    void retrieveOwnersShouldReturnEmptyByDefault() {
        assertThat(testee.retrieveOwners(ATTACHMENT_ID).join())
            .isEmpty();
    }

    @Test
    void retrieveOwnersShouldReturnAddedOwner() {
        testee.addOwner(ATTACHMENT_ID, OWNER_1).block();

        assertThat(testee.retrieveOwners(ATTACHMENT_ID).join())
            .containsOnly(OWNER_1);
    }

    @Test
    void retrieveOwnersShouldReturnAddedOwners() {
        testee.addOwner(ATTACHMENT_ID, OWNER_1).block();
        testee.addOwner(ATTACHMENT_ID, OWNER_2).block();

        assertThat(testee.retrieveOwners(ATTACHMENT_ID).join())
            .containsOnly(OWNER_1, OWNER_2);
    }

    @Test
    void retrieveOwnersShouldNotThrowWhenMoreReferencesThanPaging() {
        int referenceCountExceedingPaging = 5050;

        IntStream.range(0, referenceCountExceedingPaging)
            .boxed()
            .collect(JamesCollectors.chunker(128))
            .forEach(chunk -> Flux.fromIterable(chunk)
                    .flatMap(i -> testee.addOwner(ATTACHMENT_ID, Username.fromRawValue("owner" + i)))
                    .then()
                    .block());

        assertThat(testee.retrieveOwners(ATTACHMENT_ID).join())
            .hasSize(referenceCountExceedingPaging);
    }
}