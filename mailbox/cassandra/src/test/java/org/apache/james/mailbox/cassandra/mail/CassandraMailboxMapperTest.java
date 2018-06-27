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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class CassandraMailboxMapperTest {
    
    private static final int UID_VALIDITY = 52;
    private static final String USER = "user";
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final MailboxPath MAILBOX_PATH = MailboxPath.forUser(USER, "name");
    private static final Mailbox MAILBOX = new SimpleMailbox(MAILBOX_PATH, UID_VALIDITY, MAILBOX_ID);

    private static final CassandraId MAILBOX_ID_2 = CassandraId.timeBased();


    private static final Mailbox MAILBOX_BIS = new SimpleMailbox(MAILBOX_PATH, UID_VALIDITY, MAILBOX_ID_2);
    private static final String WILDCARD = "%";

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    
    private CassandraCluster cassandra;
    private CassandraMailboxDAO mailboxDAO;
    private CassandraMailboxPathDAOImpl mailboxPathDAO;
    private CassandraMailboxPathV2DAO mailboxPathV2DAO;
    private CassandraMailboxMapper testee;

    @Before
    public void setUp() {
        CassandraModuleComposite modules = new CassandraModuleComposite(new CassandraMailboxModule(), new CassandraAclModule());
        cassandra = CassandraCluster.create(modules, cassandraServer.getIp(), cassandraServer.getBindingPort());
        mailboxDAO = new CassandraMailboxDAO(cassandra.getConf(), cassandra.getTypesProvider());
        mailboxPathDAO = new CassandraMailboxPathDAOImpl(cassandra.getConf(), cassandra.getTypesProvider());
        mailboxPathV2DAO = new CassandraMailboxPathV2DAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
        CassandraUserMailboxRightsDAO userMailboxRightsDAO = new CassandraUserMailboxRightsDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
        testee = new CassandraMailboxMapper(
            mailboxDAO,
            mailboxPathDAO,
            mailboxPathV2DAO,
            userMailboxRightsDAO,
            new CassandraACLMapper(cassandra.getConf(),
                new CassandraUserMailboxRightsDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION),
                CassandraConfiguration.DEFAULT_CONFIGURATION));
    }

    @After
    public void tearDown() {
        cassandra.close();
    }

    @Test
    public void saveShouldNotRemoveOldMailboxPathWhenCreatingTheNewMailboxPathFails() throws Exception {
        testee.save(new SimpleMailbox(MAILBOX_PATH, UID_VALIDITY));
        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH);

        SimpleMailbox newMailbox = new SimpleMailbox(tooLongMailboxPath(mailbox.generateAssociatedPath()), UID_VALIDITY, mailbox.getMailboxId());
        assertThatThrownBy(() ->
            testee.save(newMailbox))
            .isInstanceOf(TooLongMailboxNameException.class);

        assertThat(mailboxPathV2DAO.retrieveId(MAILBOX_PATH).join())
            .isPresent();
    }

    private MailboxPath tooLongMailboxPath(MailboxPath fromMailboxPath) {
        return new MailboxPath(fromMailboxPath, StringUtils.repeat("b", 65537));
    }

    @Test
    public void deleteShouldDeleteMailboxAndMailboxPathFromV1Table() {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void deleteShouldDeleteMailboxAndMailboxPathFromV2Table() {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void findMailboxByPathShouldReturnMailboxWhenExistsInV1Table() throws Exception {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();

        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH);

        assertThat(mailbox.generateAssociatedPath()).isEqualTo(MAILBOX_PATH);
    }

    @Test
    public void findMailboxByPathShouldReturnMailboxWhenExistsInV2Table() throws Exception {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();

        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH);

        assertThat(mailbox.generateAssociatedPath()).isEqualTo(MAILBOX_PATH);
    }

    @Test
    public void findMailboxByPathShouldReturnMailboxWhenExistsInBothTables() throws Exception {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();

        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH);

        assertThat(mailbox.generateAssociatedPath()).isEqualTo(MAILBOX_PATH);
    }

    @Test
    public void deleteShouldRemoveMailboxWhenInBothTables() {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void deleteShouldRemoveMailboxWhenInV1Tables() {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void deleteShouldRemoveMailboxWhenInV2Table() {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void findMailboxByPathShouldThrowWhenDoesntExistInBothTables() {
        mailboxDAO.save(MAILBOX)
            .join();

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void findMailboxWithPathLikeShouldReturnMailboxesWhenExistsInV1Table() {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();
    
        List<Mailbox> mailboxes = testee.findMailboxWithPathLike(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, WILDCARD));

        assertThat(mailboxes).containsOnly(MAILBOX);
    }

    @Test
    public void findMailboxWithPathLikeShouldReturnMailboxesWhenExistsInBothTables() {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();

        List<Mailbox> mailboxes = testee.findMailboxWithPathLike(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, WILDCARD));

        assertThat(mailboxes).containsOnly(MAILBOX);
    }

    @Test
    public void findMailboxWithPathLikeShouldReturnMailboxesWhenExistsInV2Table() {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();
    
        List<Mailbox> mailboxes = testee.findMailboxWithPathLike(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, WILDCARD));

        assertThat(mailboxes).containsOnly(MAILBOX);
    }

    @Test
    public void hasChildrenShouldReturnChildWhenExistsInV1Table() {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();
        CassandraId childMailboxId = CassandraId.timeBased();
        MailboxPath childMailboxPath = MailboxPath.forUser(USER, "name.child");
        Mailbox childMailbox = new SimpleMailbox(childMailboxPath, UID_VALIDITY, childMailboxId);
        mailboxDAO.save(childMailbox)
            .join();
        mailboxPathDAO.save(childMailboxPath, childMailboxId)
            .join();
    
        boolean hasChildren = testee.hasChildren(MAILBOX, '.');

        assertThat(hasChildren).isTrue();
    }

    @Test
    public void hasChildrenShouldReturnChildWhenExistsInBothTables() {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();
        CassandraId childMailboxId = CassandraId.timeBased();
        MailboxPath childMailboxPath = MailboxPath.forUser(USER, "name.child");
        Mailbox childMailbox = new SimpleMailbox(childMailboxPath, UID_VALIDITY, childMailboxId);
        mailboxDAO.save(childMailbox)
            .join();
        mailboxPathDAO.save(childMailboxPath, childMailboxId)
            .join();

        boolean hasChildren = testee.hasChildren(MAILBOX, '.');

        assertThat(hasChildren).isTrue();
    }

    @Test
    public void hasChildrenShouldReturnChildWhenExistsInV2Table() {
        mailboxDAO.save(MAILBOX)
            .join();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .join();
        CassandraId childMailboxId = CassandraId.timeBased();
        MailboxPath childMailboxPath = MailboxPath.forUser(USER, "name.child");
        Mailbox childMailbox = new SimpleMailbox(childMailboxPath, UID_VALIDITY, childMailboxId);
        mailboxDAO.save(childMailbox)
            .join();
        mailboxPathV2DAO.save(childMailboxPath, childMailboxId)
            .join();
    
        boolean hasChildren = testee.hasChildren(MAILBOX, '.');
    
        assertThat(hasChildren).isTrue();
    }

    @Test
    public void findMailboxWithPathLikeShouldRemoveDuplicatesAndKeepV2() {
        mailboxDAO.save(MAILBOX).join();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID).join();

        mailboxDAO.save(MAILBOX_BIS).join();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID_2).join();

        assertThat(testee.findMailboxWithPathLike(
            new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, WILDCARD)))
            .containsOnly(MAILBOX);
    }
}
