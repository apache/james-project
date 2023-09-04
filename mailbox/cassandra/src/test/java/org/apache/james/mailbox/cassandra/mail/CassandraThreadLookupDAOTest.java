/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Set;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraThreadModule;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
/*
public class CassandraThreadLookupDAOTest {
    private static final Username ALICE = Username.of("alice");
    private static final Username BOB = Username.of("bob");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraThreadModule.MODULE);

    private CassandraThreadLookupDAO testee;
    private CassandraMessageId messageId1;
    private CassandraMessageId messageId2;
    private MimeMessageId mimeMessageId1;
    private MimeMessageId mimeMessageId2;
    private MimeMessageId mimeMessageId3;
    private MimeMessageId mimeMessageId4;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraThreadLookupDAO(cassandra.getConf());

        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        messageId1 = messageIdFactory.generate();
        messageId2 = messageIdFactory.generate();

        mimeMessageId1 = new MimeMessageId("MimeMessageID1");
        mimeMessageId2 = new MimeMessageId("MimeMessageID2");
        mimeMessageId3 = new MimeMessageId("MimeMessageID3");
        mimeMessageId4 = new MimeMessageId("MimeMessageID4");
    }

    @Test
    void insertShouldSuccess() {
        testee.insert(messageId1, ALICE, Set.of(mimeMessageId1, mimeMessageId2)).block();

        assertThat(testee.selectOneRow(messageId1).block())
            .isEqualTo(new ThreadTablePartitionKey(ALICE, Set.of(mimeMessageId1, mimeMessageId2)));
    }

    @Test
    void selectShouldReturnNullWhenMessageIdNonExist() {
        assertThat(testee.selectOneRow(messageId1).block())
            .isNull();
    }

    @Test
    void selectShouldReturnOnlyRelatedDataByThatMessageId() {
        testee.insert(messageId1, ALICE, Set.of(mimeMessageId1, mimeMessageId2)).block();
        testee.insert(messageId2, BOB, Set.of(mimeMessageId3, mimeMessageId4)).block();

        assertThat(testee.selectOneRow(messageId1).block())
            .isEqualTo(new ThreadTablePartitionKey(ALICE, Set.of(mimeMessageId1, mimeMessageId2)));
    }

    @Test
    void deletedEntriesShouldNotBeReturned() {
        testee.insert(messageId1, ALICE, Set.of(mimeMessageId1, mimeMessageId2)).block();

        testee.deleteOneRow(messageId1).block();

        assertThat(testee.selectOneRow(messageId1).block())
            .isNull();
    }

    @Test
    void deleteByNonExistMessageIdShouldDeleteNothing() {
        testee.insert(messageId1, ALICE, Set.of(mimeMessageId1, mimeMessageId2)).block();

        testee.deleteOneRow(messageId2).block();

        // message1's data should remain
        assertThat(testee.selectOneRow(messageId1).block())
            .isEqualTo(new ThreadTablePartitionKey(ALICE, Set.of(mimeMessageId1, mimeMessageId2)));
    }

}*/
