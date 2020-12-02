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

package org.apache.james.mailbox.cassandra.mail.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraACLDAOV1;
import org.apache.james.mailbox.cassandra.mail.CassandraACLDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableMap;

class AclV2MigrationTest {
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final Mailbox MAILBOX = new Mailbox(new MailboxPath(MailboxConstants.USER_NAMESPACE, Username.of("bob"), MailboxConstants.INBOX),
        UidValidity.generate(), MAILBOX_ID);

    public static final CassandraModule MODULES = CassandraModule.aggregateModules(
        CassandraMailboxModule.MODULE,
        CassandraAclModule.MODULE,
        CassandraSchemaVersionModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULES);

    private CassandraACLDAOV1 daoV1;
    private CassandraACLDAOV2 daoV2;
    private CassandraMailboxDAO mailboxDAO;
    private AclV2Migration migration;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        mailboxDAO = new CassandraMailboxDAO(cassandra.getConf(), cassandra.getTypesProvider(), CassandraConsistenciesConfiguration.DEFAULT);
        daoV1 = new CassandraACLDAOV1(cassandra.getConf(), CassandraConfiguration.DEFAULT_CONFIGURATION, CassandraConsistenciesConfiguration.DEFAULT);
        daoV2 = new CassandraACLDAOV2(cassandra.getConf());
        migration = new AclV2Migration(mailboxDAO, daoV1, daoV2);
    }

    @Test
    void shouldCompleteWhenNoMailboxes() throws Exception {
        Task.Result result = migration.runTask();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void shouldCompleteWhenMailboxWithNoAcl() throws Exception {
        mailboxDAO.save(MAILBOX).block();

        Task.Result result = migration.runTask();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void shouldCompleteWhenMailboxWithAcl() throws Exception {
        mailboxDAO.save(MAILBOX).block();
        MailboxACL acl = new MailboxACL(ImmutableMap.of(MailboxACL.EntryKey.createUserEntryKey(Username.of("alice")), MailboxACL.FULL_RIGHTS));
        daoV1.setACL(MAILBOX_ID, acl).block();

        Task.Result result = migration.runTask();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void shouldCopyAclToNewestVersion() throws Exception {
        mailboxDAO.save(MAILBOX).block();
        MailboxACL acl = new MailboxACL(ImmutableMap.of(MailboxACL.EntryKey.createUserEntryKey(Username.of("alice")), MailboxACL.FULL_RIGHTS));
        daoV1.setACL(MAILBOX_ID, acl).block();

        Task.Result result = migration.runTask();

        assertThat(daoV2.getACL(MAILBOX_ID).block()).isEqualTo(acl);
    }
}