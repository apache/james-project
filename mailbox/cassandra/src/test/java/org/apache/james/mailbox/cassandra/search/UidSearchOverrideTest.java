/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMetadata;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class UidSearchOverrideTest {
    private static final MailboxSession MAILBOX_SESSION = MailboxSessionUtil.create(Username.of("benwa"));
    private static final Mailbox MAILBOX = new Mailbox(MailboxPath.inbox(MAILBOX_SESSION), UidValidity.of(12), CassandraId.timeBased());
    private static final HashBlobId HEADER_BLOB_ID_1 = new HashBlobId.Factory().forPayload("abc".getBytes());
    private static final CassandraModule MODULE = CassandraModule.aggregateModules(
        CassandraMessageModule.MODULE,
        CassandraSchemaVersionModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    private CassandraMessageIdDAO dao;
    private UidSearchOverride testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        dao = new CassandraMessageIdDAO(cassandra.getConf(), new HashBlobId.Factory());
        testee = new UidSearchOverride(dao);
    }

    @Test
    void rangeQueryShouldBeApplicable() {
        assertThat(testee.applicable(
            SearchQuery.builder()
                .andCriteria(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.of(4), MessageUid.of(45))))
                .build(),
            MAILBOX_SESSION))
            .isTrue();
    }

    @Test
    void sizeQueryShouldNotBeApplicable() {
        assertThat(testee.applicable(
            SearchQuery.builder()
                .andCriteria(SearchQuery.sizeEquals(12))
                .build(),
            MAILBOX_SESSION))
            .isFalse();
    }

    @Test
    void searchShouldReturnEmptyByDefault() {
        assertThat(testee.search(MAILBOX_SESSION, MAILBOX,
            SearchQuery.builder()
                .andCriteria(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.of(34), MessageUid.of(345))))
                .build()).collectList().block())
            .isEmpty();
    }

    @Test
    void searchShouldReturnMailboxEntries() {
        MessageUid messageUid = MessageUid.of(1);
        insert(messageUid, MAILBOX.getMailboxId());
        MessageUid messageUid2 = MessageUid.of(2);
        insert(messageUid2, MAILBOX.getMailboxId());
        MessageUid messageUid3 = MessageUid.of(3);
        insert(messageUid3, MAILBOX.getMailboxId());
        MessageUid messageUid4 = MessageUid.of(5);
        insert(messageUid4, MAILBOX.getMailboxId());
        MessageUid messageUid5 = MessageUid.of(5);
        insert(messageUid5, MAILBOX.getMailboxId());
        MessageUid messageUid6 = MessageUid.of(6);
        insert(messageUid6, CassandraId.timeBased());

        assertThat(testee.search(MAILBOX_SESSION, MAILBOX,
            SearchQuery.builder()
                .andCriteria(SearchQuery.uid(new SearchQuery.UidRange(messageUid2, messageUid4)))
                .build()).collectList().block())
            .containsOnly(messageUid2, messageUid3, messageUid4);
    }

    private void insert(MessageUid messageUid5, MailboxId cassandraId) {
        CassandraMessageId messageId5 = new CassandraMessageId.Factory().generate();
        dao.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(cassandraId, messageId5, messageUid5))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId5))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();
    }
}