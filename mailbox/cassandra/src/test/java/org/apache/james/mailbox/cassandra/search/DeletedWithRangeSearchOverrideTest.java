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

import static jakarta.mail.Flags.Flag.DELETED;
import static jakarta.mail.Flags.Flag.SEEN;
import static org.apache.james.mailbox.model.SearchQuery.DEFAULT_SORTS;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageDataDefinition;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UidValidity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DeletedWithRangeSearchOverrideTest {
    private static final MailboxSession MAILBOX_SESSION = MailboxSessionUtil.create(Username.of("benwa"));
    private static final Mailbox MAILBOX = new Mailbox(MailboxPath.inbox(MAILBOX_SESSION), UidValidity.of(12), CassandraId.timeBased());
    private static final CassandraDataDefinition MODULE = CassandraDataDefinition.aggregateModules(
        CassandraDeletedMessageDataDefinition.MODULE,
        CassandraSchemaVersionDataDefinition.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    private CassandraDeletedMessageDAO dao;
    private DeletedWithRangeSearchOverride testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        dao = new CassandraDeletedMessageDAO(cassandra.getConf());
        testee = new DeletedWithRangeSearchOverride(dao);
    }

    @Test
    void deletedWithRangeQueryShouldBeApplicable() {
        assertThat(testee.applicable(
            SearchQuery.builder()
                .andCriteria(SearchQuery.flagIsSet(DELETED))
                .andCriteria(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.of(4), MessageUid.of(45))))
                .sorts(DEFAULT_SORTS)
                .build(),
            MAILBOX_SESSION))
            .isTrue();
    }

    @Test
    void deletedQueryShouldNotBeApplicable() {
        assertThat(testee.applicable(
            SearchQuery.builder()
                .andCriteria(SearchQuery.flagIsSet(DELETED))
                .sorts(DEFAULT_SORTS)
                .build(),
            MAILBOX_SESSION))
            .isFalse();
    }

    @Test
    void sizeQueryShouldNotBeApplicable() {
        assertThat(testee.applicable(
            SearchQuery.builder()
                .andCriteria(SearchQuery.sizeEquals(12))
                .sorts(DEFAULT_SORTS)
                .build(),
            MAILBOX_SESSION))
            .isFalse();
    }

    @Test
    void searchShouldReturnEmptyByDefault() {
        assertThat(testee.search(MAILBOX_SESSION, MAILBOX,
            SearchQuery.builder()
                .andCriteria(SearchQuery.flagIsUnSet(SEEN))
                .andCriteria(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.MIN_VALUE, MessageUid.of(45))))
                .sorts(DEFAULT_SORTS)
                .build()).collectList().block())
            .isEmpty();
    }

    @Test
    void searchShouldReturnMailboxEntries() {
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        MessageUid messageUid3 = MessageUid.of(3);
        MessageUid messageUid4 = MessageUid.of(4);
        MessageUid messageUid5 = MessageUid.of(5);

        dao.addDeleted((CassandraId) MAILBOX.getMailboxId(), messageUid).block();
        dao.addDeleted((CassandraId) MAILBOX.getMailboxId(), messageUid2).block();
        dao.addDeleted((CassandraId) MAILBOX.getMailboxId(), messageUid3).block();
        dao.addDeleted((CassandraId) MAILBOX.getMailboxId(), messageUid4).block();
        dao.addDeleted((CassandraId) MAILBOX.getMailboxId(), messageUid5).block();

        assertThat(testee.search(MAILBOX_SESSION, MAILBOX,
            SearchQuery.builder()
                .andCriteria(SearchQuery.flagIsUnSet(DELETED))
                .andCriteria(SearchQuery.uid(new SearchQuery.UidRange(messageUid2, messageUid4)))
                .sorts(DEFAULT_SORTS)
                .build()).collectList().block())
            .containsOnly(messageUid2, messageUid3, messageUid4);
    }
}