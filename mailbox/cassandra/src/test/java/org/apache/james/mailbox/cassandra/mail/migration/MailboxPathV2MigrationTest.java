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
import org.apache.james.backends.cassandra.CassandraRestartExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.CassandraACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraIdAndPath;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathDAOImpl;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV2DAO;
import org.apache.james.mailbox.cassandra.mail.CassandraUserMailboxRightsDAO;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

@ExtendWith(CassandraRestartExtension.class)
class MailboxPathV2MigrationTest {

    private static final MailboxPath MAILBOX_PATH_1 = MailboxPath.forUser("bob", "Important");
    private static final int UID_VALIDITY_1 = 452;
    private static final Mailbox MAILBOX_1 = new Mailbox(MAILBOX_PATH_1, UID_VALIDITY_1);
    private static final CassandraId MAILBOX_ID_1 = CassandraId.timeBased();

    public static final CassandraModule MODULES = CassandraModule.aggregateModules(
            CassandraMailboxModule.MODULE,
            CassandraAclModule.MODULE,
            CassandraSchemaVersionModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULES);

    private CassandraMailboxPathDAOImpl daoV1;
    private CassandraMailboxPathV2DAO daoV2;
    private CassandraMailboxMapper mailboxMapper;
    private CassandraMailboxDAO mailboxDAO;

    @BeforeAll
    static void setUpClass() {
        MAILBOX_1.setMailboxId(MAILBOX_ID_1);
    }

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        daoV1 = new CassandraMailboxPathDAOImpl(
            cassandra.getConf(),
            cassandra.getTypesProvider(),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION);
        daoV2 = new CassandraMailboxPathV2DAO(
            cassandra.getConf(),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION);

        CassandraUserMailboxRightsDAO userMailboxRightsDAO = new CassandraUserMailboxRightsDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
        mailboxDAO = new CassandraMailboxDAO(cassandra.getConf(), cassandra.getTypesProvider());
        mailboxMapper = new CassandraMailboxMapper(
            mailboxDAO,
            daoV1,
            daoV2,
            userMailboxRightsDAO,
            new CassandraACLMapper(cassandra.getConf(), userMailboxRightsDAO, CassandraConfiguration.DEFAULT_CONFIGURATION));
    }

    @Test
    void newValuesShouldBeSavedInMostRecentDAO() throws Exception {
        mailboxMapper.save(MAILBOX_1);

        assertThat(daoV2.retrieveId(MAILBOX_PATH_1).blockOptional())
            .contains(new CassandraIdAndPath(MAILBOX_ID_1, MAILBOX_PATH_1));
    }

    @Test
    void newValuesShouldNotBeSavedInOldDAO() throws Exception {
        mailboxMapper.save(MAILBOX_1);

        assertThat(daoV1.retrieveId(MAILBOX_PATH_1).blockOptional())
            .isEmpty();
    }

    @Test
    void readingOldValuesShouldMigrateThem() throws Exception {
        daoV1.save(MAILBOX_PATH_1, MAILBOX_ID_1).block();
        mailboxDAO.save(MAILBOX_1).block();

        mailboxMapper.findMailboxByPath(MAILBOX_PATH_1);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(daoV1.retrieveId(MAILBOX_PATH_1).blockOptional()).isEmpty();
        softly.assertThat(daoV2.retrieveId(MAILBOX_PATH_1).blockOptional())
            .contains(new CassandraIdAndPath(MAILBOX_ID_1, MAILBOX_PATH_1));
        softly.assertAll();
    }

    @Test
    void migrationTaskShouldMoveDataToMostRecentDao() {
        daoV1.save(MAILBOX_PATH_1, MAILBOX_ID_1).block();

        new MailboxPathV2Migration(daoV1, daoV2).run();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(daoV1.retrieveId(MAILBOX_PATH_1).blockOptional()).isEmpty();
        softly.assertThat(daoV2.retrieveId(MAILBOX_PATH_1).blockOptional())
            .contains(new CassandraIdAndPath(MAILBOX_ID_1, MAILBOX_PATH_1));
        softly.assertAll();
    }
}