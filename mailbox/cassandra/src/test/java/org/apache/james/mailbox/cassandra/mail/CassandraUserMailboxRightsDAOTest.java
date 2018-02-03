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
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class CassandraUserMailboxRightsDAOTest {

    private static final String USER_NAME = "userName";
    private static final EntryKey ENTRY_KEY = EntryKey.createUserEntryKey(USER_NAME);
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final Rfc4314Rights RIGHTS = MailboxACL.FULL_RIGHTS;
    private static final Rfc4314Rights OTHER_RIGHTS = new Rfc4314Rights(Right.Administer, Right.Read);

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    
    private CassandraCluster cassandra;

    private CassandraUserMailboxRightsDAO testee;

    @Before
    public void setUp() throws Exception {
        cassandra = CassandraCluster.create(new CassandraAclModule(), cassandraServer.getIp(), cassandraServer.getBindingPort());
        testee = new CassandraUserMailboxRightsDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
    }

    @After
    public void tearDown() throws Exception {
        cassandra.close();
    }

    @Test
    public void saveShouldInsertNewEntry() throws Exception {
        testee.update(MAILBOX_ID, ACLDiff.computeDiff(
            MailboxACL.EMPTY,
            new MailboxACL(new Entry(ENTRY_KEY, RIGHTS))))
            .join();

        assertThat(testee.retrieve(USER_NAME, MAILBOX_ID).join())
            .contains(RIGHTS);
    }

    @Test
    public void saveOnSecondShouldOverwrite() throws Exception {
        testee.update(MAILBOX_ID, ACLDiff.computeDiff(
            MailboxACL.EMPTY,
            new MailboxACL(new Entry(ENTRY_KEY, RIGHTS))))
            .join();

        testee.update(MAILBOX_ID, ACLDiff.computeDiff(
            new MailboxACL(new Entry(ENTRY_KEY, RIGHTS)),
            new MailboxACL(new Entry(ENTRY_KEY, OTHER_RIGHTS))))
            .join();

        assertThat(testee.retrieve(USER_NAME, MAILBOX_ID).join())
            .contains(OTHER_RIGHTS);
    }

    @Test
    public void listRightsForUserShouldReturnEmptyWhenEmptyData() throws Exception {
        assertThat(testee.listRightsForUser(USER_NAME).join())
            .isEmpty();
    }

    @Test
    public void deleteShouldDeleteWhenExisting() throws Exception {
        testee.update(MAILBOX_ID, ACLDiff.computeDiff(
            MailboxACL.EMPTY,
            new MailboxACL(new Entry(ENTRY_KEY, RIGHTS))))
            .join();


        testee.update(MAILBOX_ID, ACLDiff.computeDiff(
            new MailboxACL(new Entry(ENTRY_KEY, RIGHTS)),
            MailboxACL.EMPTY))
            .join();

        assertThat(testee.retrieve(USER_NAME, MAILBOX_ID).join())
            .isEmpty();
    }
}
