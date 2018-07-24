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

import java.util.List;
import java.util.UUID;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageModule;
import org.apache.james.mailbox.model.MessageRange;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.github.steveash.guavate.Guavate;

public class CassandraDeletedMessageDAOTest {
    
    public static final CassandraId MAILBOX_ID = CassandraId.of(UUID.fromString("110e8400-e29b-11d4-a716-446655440000"));
    public static final MessageUid UID_1 = MessageUid.of(1);
    public static final MessageUid UID_2 = MessageUid.of(2);
    public static final MessageUid UID_3 = MessageUid.of(3);
    public static final MessageUid UID_4 = MessageUid.of(4);
    public static final MessageUid UID_7 = MessageUid.of(7);
    public static final MessageUid UID_8 = MessageUid.of(8);

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    private static CassandraCluster cassandra;

    private CassandraDeletedMessageDAO testee;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(CassandraDeletedMessageModule.MODULE, cassandraServer.getHost());
    }

    @Before
    public void setUp() {
        testee = new CassandraDeletedMessageDAO(cassandra.getConf());
    }

    @After
    public void tearDown() {
        cassandra.clearTables();
    }

    @AfterClass
    public static void tearDownClass() {
        cassandra.closeCluster();
    }

    @Test
    public void retrieveDeletedMessageShouldReturnEmptyByDefault() {
        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(result).isEmpty();
    }

    @Test
    public void addDeletedMessageShouldThenBeReportedAsDeletedMessage() {
        testee.addDeleted(MAILBOX_ID, UID_1).join();
        testee.addDeleted(MAILBOX_ID, UID_2).join();

        List<MessageUid> result = testee.retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(result).containsExactly(UID_1, UID_2);
    }

    @Test
    public void addDeletedMessageShouldBeIdempotent() {
        testee.addDeleted(MAILBOX_ID, UID_1).join();
        testee.addDeleted(MAILBOX_ID, UID_1).join();

        List<MessageUid> result = testee.retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(result).containsExactly(UID_1);
    }


    @Test
    public void removeUnreadShouldReturnEmptyWhenNoData() {
        testee.removeDeleted(MAILBOX_ID, UID_1).join();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(result).isEmpty();
    }

    @Test
    public void removeDeletedMessageShouldNotAffectOtherMessage() {
        testee.addDeleted(MAILBOX_ID, UID_2).join();
        testee.addDeleted(MAILBOX_ID, UID_1).join();

        testee.removeDeleted(MAILBOX_ID, UID_1).join();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(result).containsExactly(UID_2);
    }

    @Test
    public void removeDeletedShouldRemoveSpecifiedUID() {
        testee.addDeleted(MAILBOX_ID, UID_2).join();

        testee.removeDeleted(MAILBOX_ID, UID_2).join();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(result).isEmpty();
    }

    private void addMessageForRetrieveTest() {
        testee.addDeleted(MAILBOX_ID, UID_1).join();
        testee.addDeleted(MAILBOX_ID, UID_2).join();
        testee.addDeleted(MAILBOX_ID, UID_3).join();
        testee.addDeleted(MAILBOX_ID, UID_4).join();
        testee.addDeleted(MAILBOX_ID, UID_7).join();
        testee.addDeleted(MAILBOX_ID, UID_8).join();
    }

    @Test
    public void retrieveDeletedMessageShouldReturnAllMessageForMessageRangeAll() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.all())
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(result).containsExactly(UID_1, UID_2, UID_3, UID_4, UID_7, UID_8);
    }

    @Test
    public void retrieveDeletedMessageShouldReturnOneMessageForMessageRangeOneIfThisMessageIsPresent() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.one(UID_1))
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(result).containsExactly(UID_1);
    }

    @Test
    public void retrieveDeletedMessageShouldReturnNoMessageForMessageRangeOneIfThisMessageIsNotPresent() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.one(MessageUid.of(42)))
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(result).isEmpty();
    }

    @Test
    public void retrieveDeletedMessageShouldReturnMessageInRangeForMessageRangeRange() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.range(MessageUid.of(3), MessageUid.of(7)))
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(result).containsExactly(UID_3, UID_4, UID_7);
    }

    @Test
    public void retrieveDeletedMessageShouldReturnNoMessageForMessageRangeRangeIfNoDeletedMessageInThatRange() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.range(MessageUid.of(5), MessageUid.of(6)))
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(result).isEmpty();
    }

    @Test
    public void retrieveDeletedMessageShouldReturnNoMessageForMessageRangeFromIfNoDeletedMessageWithIdBiggerOrSameThanFrom() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.from(MessageUid.of(9)))
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(result).isEmpty();
    }

    @Test
    public void retrieveDeletedMessageShouldReturnDeletedMessageWithIdBiggerOrSameThanFrom() {
        addMessageForRetrieveTest();

        List<MessageUid> result = testee
            .retrieveDeletedMessage(MAILBOX_ID, MessageRange.from(MessageUid.of(4)))
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(result).containsExactly(UID_4, UID_7, UID_8);
    }
}
