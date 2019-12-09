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
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraUserMailboxRightsDAOTest {
    private static final Username USER_NAME = Username.of("userName");
    private static final EntryKey ENTRY_KEY = EntryKey.createUserEntryKey(USER_NAME);
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final Rfc4314Rights RIGHTS = MailboxACL.FULL_RIGHTS;
    private static final Rfc4314Rights OTHER_RIGHTS = new Rfc4314Rights(Right.Administer, Right.Read);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraAclModule.MODULE);

    private CassandraUserMailboxRightsDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraUserMailboxRightsDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
    }

    @Test
    void saveShouldInsertNewEntry() {
        testee.update(MAILBOX_ID, ACLDiff.computeDiff(
            MailboxACL.EMPTY,
            new MailboxACL(new Entry(ENTRY_KEY, RIGHTS))))
            .block();

        assertThat(testee.retrieve(USER_NAME, MAILBOX_ID).block())
            .contains(RIGHTS);
    }

    @Test
    void saveOnSecondShouldOverwrite() {
        testee.update(MAILBOX_ID, ACLDiff.computeDiff(
            MailboxACL.EMPTY,
            new MailboxACL(new Entry(ENTRY_KEY, RIGHTS))))
            .block();

        testee.update(MAILBOX_ID, ACLDiff.computeDiff(
            new MailboxACL(new Entry(ENTRY_KEY, RIGHTS)),
            new MailboxACL(new Entry(ENTRY_KEY, OTHER_RIGHTS))))
            .block();

        assertThat(testee.retrieve(USER_NAME, MAILBOX_ID).block())
            .contains(OTHER_RIGHTS);
    }

    @Test
    void listRightsForUserShouldReturnEmptyWhenEmptyData() {
        assertThat(testee.listRightsForUser(USER_NAME).collectList().block())
            .isEmpty();
    }

    @Test
    void deleteShouldDeleteWhenExisting() {
        testee.update(MAILBOX_ID, ACLDiff.computeDiff(
            MailboxACL.EMPTY,
            new MailboxACL(new Entry(ENTRY_KEY, RIGHTS))))
            .block();


        testee.update(MAILBOX_ID, ACLDiff.computeDiff(
            new MailboxACL(new Entry(ENTRY_KEY, RIGHTS)),
            MailboxACL.EMPTY))
            .block();

        assertThat(testee.retrieve(USER_NAME, MAILBOX_ID).block())
            .isEmpty();
    }
}
