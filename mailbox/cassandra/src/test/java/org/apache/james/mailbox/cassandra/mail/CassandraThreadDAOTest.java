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

import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraThreadModule;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/*
public class CassandraThreadDAOTest {
    private static final Username ALICE = Username.of("alice");
    private static final Username BOB = Username.of("bob");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraThreadModule.MODULE);

    private CassandraThreadDAO testee;
    private CassandraMessageId messageId1;
    private CassandraMessageId messageId2;
    private CassandraMessageId messageId3;
    private ThreadId threadId1;
    private ThreadId threadId2;
    private MimeMessageId mimeMessageId1;
    private MimeMessageId mimeMessageId2;
    private MimeMessageId mimeMessageId3;
    private MimeMessageId mimeMessageId4;
    private MimeMessageId mimeMessageId5;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraThreadDAO(cassandra.getConf());

        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        messageId1 = messageIdFactory.generate();
        messageId2 = messageIdFactory.generate();
        messageId3 = messageIdFactory.generate();

        threadId1 = ThreadId.fromBaseMessageId(messageId1);
        threadId2 = ThreadId.fromBaseMessageId(messageId3);

        mimeMessageId1 = new MimeMessageId("MimeMessageID1");
        mimeMessageId2 = new MimeMessageId("MimeMessageID2");
        mimeMessageId3 = new MimeMessageId("MimeMessageID3");
        mimeMessageId4 = new MimeMessageId("MimeMessageID4");
        mimeMessageId5 = new MimeMessageId("MimeMessageID5");
    }

    @Test
    void insertShouldSuccess() {
        Optional<Subject> message1BaseSubject = Optional.of(new Subject("subject"));
        testee.insertSome(ALICE, ImmutableSet.of(mimeMessageId1), messageId1, threadId1, message1BaseSubject).collectList().block();

        assertThat(testee.selectSome(ALICE, ImmutableSet.of(mimeMessageId1)).collectList().block())
            .isEqualTo(ImmutableList.of(Pair.of(message1BaseSubject, threadId1)));
    }

    @Test
    void insertNullBaseSubjectShouldBeAllowed() {
        Optional<Subject> message1BaseSubject = Optional.empty();
        testee.insertSome(ALICE, ImmutableSet.of(mimeMessageId1), messageId1, threadId1, message1BaseSubject).collectList().block();

        assertThat(testee.selectSome(ALICE, ImmutableSet.of(mimeMessageId1)).collectList().block())
            .isEqualTo(ImmutableList.of(Pair.of(Optional.empty(), threadId1)));
    }

    @Test
    void insertEmptyBaseSubjectShouldBeAllowed() {
        Optional<Subject> message1BaseSubject = Optional.of(new Subject(""));
        testee.insertSome(ALICE, ImmutableSet.of(mimeMessageId1), messageId1, threadId1, message1BaseSubject).collectList().block();

        assertThat(testee.selectSome(ALICE, ImmutableSet.of(mimeMessageId1)).collectList().block())
            .isEqualTo(ImmutableList.of(Pair.of(message1BaseSubject, threadId1)));
    }

    @Test
    void selectShouldReturnEmptyByDefault() {
        assertThat(testee.selectSome(ALICE, ImmutableSet.of(mimeMessageId1)).collectList().block()
            .isEmpty());
    }

    @Test
    void selectShouldReturnDistinctValues() {
        Optional<Subject> messageBaseSubject = Optional.of(new Subject("subject"));

        // given message1 and message2 belongs to same thread, related to each other by mimeMessageId2, mimeMessageId3
        testee.insertSome(ALICE, ImmutableSet.of(mimeMessageId1, mimeMessageId2, mimeMessageId3), messageId1, threadId1, messageBaseSubject).collectList().block();
        testee.insertSome(ALICE, ImmutableSet.of(mimeMessageId2, mimeMessageId3, mimeMessageId4), messageId2, threadId1, messageBaseSubject).collectList().block();

        // select with new message having mimeMessageId2 and mimeMessageId3
        assertThat(testee.selectSome(ALICE, ImmutableSet.of(mimeMessageId2, mimeMessageId3)).collectList().block())
            .isEqualTo(ImmutableList.of(Pair.of(messageBaseSubject, threadId1)));
    }

    @Test
    void selectShouldReturnOnlyRelatedMessageDataOfAUser() {
        // insert message1 data of ALICE
        Optional<Subject> message1BaseSubject = Optional.of(new Subject("subject"));
        testee.insertSome(ALICE, ImmutableSet.of(mimeMessageId1), messageId1, threadId1, message1BaseSubject).collectList().block();

        // insert message2 data of BOB
        Optional<Subject> message2BaseSubject = Optional.of(new Subject("subject2"));
        testee.insertSome(BOB, ImmutableSet.of(mimeMessageId2), messageId2, threadId2, message2BaseSubject).collectList().block();

        // select some data of BOB
        assertThat(testee.selectSome(BOB, ImmutableSet.of(mimeMessageId2)).collectList().block())
            .isEqualTo(ImmutableList.of(Pair.of(message2BaseSubject, threadId2)));
    }

    @Test
    void selectShouldReturnOnlyRelatedMessageDataOfAThread() {
        // insert message1 data of ALICE which in thread1
        Optional<Subject> message1BaseSubject = Optional.of(new Subject("subject"));
        testee.insertSome(ALICE, ImmutableSet.of(mimeMessageId1), messageId1, threadId1, message1BaseSubject).collectList().block();

        // insert message2 data of ALICE which in thread2
        Optional<Subject> message2BaseSubject = Optional.of(new Subject("subject2"));
        testee.insertSome(ALICE, ImmutableSet.of(mimeMessageId2), messageId2, threadId2, message2BaseSubject).collectList().block();

        // select some data related to thread2
        assertThat(testee.selectSome(ALICE, ImmutableSet.of(mimeMessageId2)).collectList().block())
            .isEqualTo(ImmutableList.of(Pair.of(message2BaseSubject, threadId2)));
    }

    @Test
    void selectWithUnrelatedMimeMessageIDsShouldReturnEmpty() {
        Optional<Subject> message1BaseSubject = Optional.of(new Subject("subject"));
        testee.insertSome(ALICE, ImmutableSet.of(mimeMessageId1, mimeMessageId2), messageId1, threadId1, message1BaseSubject).collectList().block();

        assertThat(testee.selectSome(ALICE, ImmutableSet.of(mimeMessageId3, mimeMessageId4, mimeMessageId5)).collectList().block())
            .isEqualTo(ImmutableList.of());
    }

    @Test
    void deletedEntriesShouldNotBeReturned() {
        Optional<Subject> message1BaseSubject = Optional.of(new Subject("subject"));
        testee.insertSome(ALICE, ImmutableSet.of(mimeMessageId1, mimeMessageId2), messageId1, threadId1, message1BaseSubject).collectList().block();

        testee.deleteSome(ALICE, ImmutableSet.of(mimeMessageId1, mimeMessageId2));

        assertThat(testee.selectSome(ALICE, ImmutableSet.of(mimeMessageId1, mimeMessageId2)).collectList().block()
            .isEmpty());
    }

    @Test
    void deleteWithUnrelatedMimeMessageIDsShouldDeleteNothing() {
        // insert message1 data
        Optional<Subject> message1BaseSubject = Optional.of(new Subject("subject"));
        testee.insertSome(ALICE, ImmutableSet.of(mimeMessageId1, mimeMessageId2), messageId1, threadId1, message1BaseSubject).collectList().block();

        // delete with unrelated mimemessageIds
        testee.deleteSome(ALICE, ImmutableSet.of(mimeMessageId3, mimeMessageId4, mimeMessageId5));

        // alice's data should remain
        assertThat(testee.selectSome(ALICE, ImmutableSet.of(mimeMessageId1, mimeMessageId2)).collectList().block())
            .isEqualTo(ImmutableList.of(Pair.of(message1BaseSubject, threadId1)));
    }

}
 */
