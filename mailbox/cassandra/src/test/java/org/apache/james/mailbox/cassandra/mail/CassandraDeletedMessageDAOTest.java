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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageModule;
import org.apache.james.mailbox.model.MessageRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraDeletedMessageDAOTest {
    private static final CassandraId MAILBOX_ID = CassandraId.of(UUID.fromString("110e8400-e29b-11d4-a716-446655440000"));
    private static final MessageUid UID_1 = MessageUid.of(1);
    private static final MessageUid UID_2 = MessageUid.of(2);
    private static final MessageUid UID_3 = MessageUid.of(3);
    private static final MessageUid UID_4 = MessageUid.of(4);
    private static final MessageUid UID_7 = MessageUid.of(7);
    private static final MessageUid UID_8 = MessageUid.of(8);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraDeletedMessageModule.MODULE);

    private CassandraDeletedMessageDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraDeletedMessageDAO(cassandra.getConf());
    }

    @Test
    void retrieveDeletedMessageShouldReturnEmptyByDefault() {
        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
                .collectList()
                .block();

        assertThat(result).isEmpty();
    }

    @Test
    void addDeletedMessageShouldThenBeReportedAsDeletedMessage() {
        testee.addDeleted(MAILBOX_ID, UID_1).block();
        testee.addDeleted(MAILBOX_ID, UID_2).block();

        List<MessageUid> result = testee.retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
                .collectList()
                .block();

        assertThat(result).containsExactly(UID_1, UID_2);
    }

    @Test
    void retrieveDeletedMessageShouldNotReturnDeletedEntries() {
        testee.addDeleted(MAILBOX_ID, UID_1).block();
        testee.addDeleted(MAILBOX_ID, UID_2).block();

        testee.removeAll(MAILBOX_ID).block();

        List<MessageUid> result = testee.retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
                .collectList()
                .block();

        assertThat(result).isEmpty();
    }

    @Test
    void removeAllShouldNotThrowWhenEmpty() {
        assertThatCode(() -> testee.removeAll(MAILBOX_ID).block()).doesNotThrowAnyException();
    }

    @Test
    void addDeletedMessageShouldBeIdempotent() {
        testee.addDeleted(MAILBOX_ID, UID_1).block();
        testee.addDeleted(MAILBOX_ID, UID_1).block();

        List<MessageUid> result = testee.retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
            .collectList()
            .block();

        assertThat(result).containsExactly(UID_1);
    }


    @Test
    void removeUnreadShouldReturnEmptyWhenNoData() {
        testee.removeDeleted(MAILBOX_ID, UID_1).block();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
            .collectList()
            .block();

        assertThat(result).isEmpty();
    }

    @Test
    void removeDeletedMessageShouldNotAffectOtherMessage() {
        testee.addDeleted(MAILBOX_ID, UID_2).block();
        testee.addDeleted(MAILBOX_ID, UID_1).block();

        testee.removeDeleted(MAILBOX_ID, UID_1).block();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
            .collectList()
            .block();

        assertThat(result).containsExactly(UID_2);
    }

    @Test
    void removeDeletedShouldRemoveSpecifiedUID() {
        testee.addDeleted(MAILBOX_ID, UID_2).block();

        testee.removeDeleted(MAILBOX_ID, UID_2).block();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
            .collectList()
            .block();

        assertThat(result).isEmpty();
    }

    private void addMessageForRetrieveTest() {
        testee.addDeleted(MAILBOX_ID, UID_1).block();
        testee.addDeleted(MAILBOX_ID, UID_2).block();
        testee.addDeleted(MAILBOX_ID, UID_3).block();
        testee.addDeleted(MAILBOX_ID, UID_4).block();
        testee.addDeleted(MAILBOX_ID, UID_7).block();
        testee.addDeleted(MAILBOX_ID, UID_8).block();
    }

    @Test
    void retrieveDeletedMessageShouldReturnAllMessageForMessageRangeAll() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
            .collectList()
            .block();

        assertThat(result).containsExactly(UID_1, UID_2, UID_3, UID_4, UID_7, UID_8);
    }

    @Test
    void retrieveDeletedMessageShouldReturnOneMessageForMessageRangeOneIfThisMessageIsPresent() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.one(UID_1))
            .collectList()
            .block();

        assertThat(result).containsExactly(UID_1);
    }

    @Test
    void retrieveDeletedMessageShouldReturnNoMessageForMessageRangeOneIfThisMessageIsNotPresent() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.one(MessageUid.of(42)))
            .collectList()
            .block();

        assertThat(result).isEmpty();
    }

    @Test
    void retrieveDeletedMessageShouldReturnMessageInRangeForMessageRangeRange() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.range(MessageUid.of(3), MessageUid.of(7)))
            .collectList()
            .block();

        assertThat(result).containsExactly(UID_3, UID_4, UID_7);
    }

    @Test
    void retrieveDeletedMessageShouldReturnNoMessageForMessageRangeRangeIfNoDeletedMessageInThatRange() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.range(MessageUid.of(5), MessageUid.of(6)))
            .collectList()
            .block();

        assertThat(result).isEmpty();
    }

    @Test
    void retrieveDeletedMessageShouldReturnNoMessageForMessageRangeFromIfNoDeletedMessageWithIdBiggerOrSameThanFrom() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.from(MessageUid.of(9)))
            .collectList()
            .block();

        assertThat(result).isEmpty();
    }

    @Test
    void retrieveDeletedMessageShouldReturnDeletedMessageWithIdBiggerOrSameThanFrom() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.from(MessageUid.of(4)))
            .collectList()
            .block();

        assertThat(result).containsExactly(UID_4, UID_7, UID_8);
    }
}
