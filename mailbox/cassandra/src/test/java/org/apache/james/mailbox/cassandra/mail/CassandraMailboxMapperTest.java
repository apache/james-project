/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
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
import static org.assertj.core.api.Assertions.fail;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathDAO.CassandraIdAndPath;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraMailboxMapperTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraMailboxMapperTest.class);

    public static final int UID_VALIDITY = 52;
    public static final MailboxPath MAILBOX_PATH = new MailboxPath("#private", "user", "name");
    private CassandraCluster cassandra;
    private CassandraMailboxPathDAO mailboxPathDAO;
    private CassandraMailboxMapper testee;

    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(new CassandraModuleComposite(new CassandraMailboxModule(), new CassandraAclModule()));
        cassandra.ensureAllTables();

        CassandraMailboxDAO mailboxDAO = new CassandraMailboxDAO(cassandra.getConf(), cassandra.getTypesProvider());
        mailboxPathDAO = new CassandraMailboxPathDAO(cassandra.getConf(), cassandra.getTypesProvider());
        testee = new CassandraMailboxMapper(cassandra.getConf(), mailboxDAO, mailboxPathDAO, CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    @After
    public void tearDown() {
        cassandra.clearAllTables();
        cassandra.close();
    }

    @Test
    public void saveShouldNotRemoveOldMailboxPathWhenCreatingTheNewMailboxPathFails() throws Exception {
        testee.save(new SimpleMailbox(MAILBOX_PATH, UID_VALIDITY));
        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH);

        try {
            SimpleMailbox newMailbox = new SimpleMailbox(tooLongMailboxPath(mailbox.generateAssociatedPath()), UID_VALIDITY, mailbox.getMailboxId());
            testee.save(newMailbox);
            fail("TooLongMailboxNameException expected");
        } catch (TooLongMailboxNameException e) {
            LOGGER.info("Ignored error", e);
        }

        Optional<CassandraIdAndPath> cassandraIdAndPath = mailboxPathDAO.retrieveId(MAILBOX_PATH).get();
        assertThat(cassandraIdAndPath.isPresent()).isTrue();
    }

    private MailboxPath tooLongMailboxPath(MailboxPath fromMailboxPath) {
        return new MailboxPath(fromMailboxPath, StringUtils.repeat("b", 65537));
    }
}
