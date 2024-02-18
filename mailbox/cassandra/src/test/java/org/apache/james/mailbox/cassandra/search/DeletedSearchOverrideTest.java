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
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageModule;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UidValidity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DeletedSearchOverrideTest {
    private static final MailboxSession MAILBOX_SESSION = MailboxSessionUtil.create(Username.of("benwa"));
    private static final Mailbox MAILBOX = new Mailbox(MailboxPath.inbox(MAILBOX_SESSION), UidValidity.of(12), CassandraId.timeBased());
    private static final CassandraModule MODULE = CassandraModule.aggregateModules(
        CassandraDeletedMessageModule.MODULE,
        CassandraSchemaVersionModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    private CassandraDeletedMessageDAO dao;
    private DeletedSearchOverride testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        dao = new CassandraDeletedMessageDAO(cassandra.getConf());
        testee = new DeletedSearchOverride(dao);
    }

    @Test
    void deletedQueryShouldBeApplicable() {
        assertThat(testee.applicable(
            SearchQuery.builder()
                .andCriteria(SearchQuery.flagIsSet(DELETED))
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
                .andCriteria(SearchQuery.flagIsUnSet(SEEN))
                .build()).collectList().block())
            .isEmpty();
    }

    @Test
    void searchShouldReturnMailboxEntries() {
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        MessageUid messageUid3 = MessageUid.of(3);

        dao.addDeleted((CassandraId) MAILBOX.getMailboxId(), messageUid).block();
        dao.addDeleted((CassandraId) MAILBOX.getMailboxId(), messageUid2).block();
        dao.addDeleted((CassandraId) MAILBOX.getMailboxId(), messageUid3).block();

        assertThat(testee.search(MAILBOX_SESSION, MAILBOX,
            SearchQuery.builder()
                .andCriteria(SearchQuery.flagIsUnSet(DELETED))
                .build()).collectList().block())
            .containsOnly(messageUid, messageUid2, messageUid3);
    }
}