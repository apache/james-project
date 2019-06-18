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
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class CassandraMailboxMapperTest {
    
    private static final int UID_VALIDITY = 52;
    private static final String USER = "user";
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final MailboxPath MAILBOX_PATH = MailboxPath.forUser(USER, "name");
    private static final Mailbox MAILBOX = new Mailbox(MAILBOX_PATH, UID_VALIDITY, MAILBOX_ID);

    private static final CassandraId MAILBOX_ID_2 = CassandraId.timeBased();


    private static final Mailbox MAILBOX_BIS = new Mailbox(MAILBOX_PATH, UID_VALIDITY, MAILBOX_ID_2);
    private static final String WILDCARD = "%";

    private static final CassandraModule MODULES = CassandraModule.aggregateModules(
        CassandraMailboxModule.MODULE,
        CassandraSchemaVersionModule.MODULE,
        CassandraAclModule.MODULE);

    @Rule public DockerCassandraRule cassandraServer = new DockerCassandraRule().allowRestart();

    private CassandraCluster cassandra;

    private CassandraMailboxDAO mailboxDAO;
    private CassandraMailboxPathDAOImpl mailboxPathDAO;
    private CassandraMailboxPathV2DAO mailboxPathV2DAO;
    private CassandraMailboxMapper testee;

    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(MODULES, cassandraServer.getHost());
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
        cassandra.clearTables();
        cassandra.closeCluster();
    }

    @Ignore("JAMES-2514 Cassandra 3 supports long mailbox names. Hence we can not rely on this for failing")
    @Test
    public void saveShouldNotRemoveOldMailboxPathWhenCreatingTheNewMailboxPathFails() throws Exception {
        testee.save(new Mailbox(MAILBOX_PATH, UID_VALIDITY));
        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH);

        Mailbox newMailbox = new Mailbox(tooLongMailboxPath(mailbox.generateAssociatedPath()), UID_VALIDITY, mailbox.getMailboxId());
        assertThatThrownBy(() ->
            testee.save(newMailbox))
            .isInstanceOf(TooLongMailboxNameException.class);

        assertThat(mailboxPathV2DAO.retrieveId(MAILBOX_PATH).blockOptional())
            .isPresent();
    }

    private MailboxPath tooLongMailboxPath(MailboxPath fromMailboxPath) {
        return new MailboxPath(fromMailboxPath, StringUtils.repeat("b", 65537));
    }

    @Test
    public void deleteShouldDeleteMailboxAndMailboxPathFromV1Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void deleteShouldDeleteMailboxAndMailboxPathFromV2Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void findMailboxByPathShouldReturnMailboxWhenExistsInV1Table() throws Exception {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH);

        assertThat(mailbox.generateAssociatedPath()).isEqualTo(MAILBOX_PATH);
    }

    @Test
    public void findMailboxByPathShouldReturnMailboxWhenExistsInV2Table() throws Exception {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH);

        assertThat(mailbox.generateAssociatedPath()).isEqualTo(MAILBOX_PATH);
    }

    @Test
    public void findMailboxByPathShouldReturnMailboxWhenExistsInBothTables() throws Exception {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        Mailbox mailbox = testee.findMailboxByPath(MAILBOX_PATH);

        assertThat(mailbox.generateAssociatedPath()).isEqualTo(MAILBOX_PATH);
    }

    @Test
    public void deleteShouldRemoveMailboxWhenInBothTables() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void deleteShouldRemoveMailboxWhenInV1Tables() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void deleteShouldRemoveMailboxWhenInV2Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        testee.delete(MAILBOX);

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void findMailboxByPathShouldThrowWhenDoesntExistInBothTables() {
        mailboxDAO.save(MAILBOX)
            .block();

        assertThatThrownBy(() -> testee.findMailboxByPath(MAILBOX_PATH))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void findMailboxWithPathLikeShouldReturnMailboxesWhenExistsInV1Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
    
        List<Mailbox> mailboxes = testee.findMailboxWithPathLike(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, WILDCARD));

        assertThat(mailboxes).containsOnly(MAILBOX);
    }

    @Test
    public void findMailboxWithPathLikeShouldReturnMailboxesWhenExistsInBothTables() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();

        List<Mailbox> mailboxes = testee.findMailboxWithPathLike(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, WILDCARD));

        assertThat(mailboxes).containsOnly(MAILBOX);
    }

    @Test
    public void findMailboxWithPathLikeShouldReturnMailboxesWhenExistsInV2Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
    
        List<Mailbox> mailboxes = testee.findMailboxWithPathLike(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, WILDCARD));

        assertThat(mailboxes).containsOnly(MAILBOX);
    }

    @Test
    public void hasChildrenShouldReturnChildWhenExistsInV1Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        CassandraId childMailboxId = CassandraId.timeBased();
        MailboxPath childMailboxPath = MailboxPath.forUser(USER, "name.child");
        Mailbox childMailbox = new Mailbox(childMailboxPath, UID_VALIDITY, childMailboxId);
        mailboxDAO.save(childMailbox)
            .block();
        mailboxPathDAO.save(childMailboxPath, childMailboxId)
            .block();
    
        boolean hasChildren = testee.hasChildren(MAILBOX, '.');

        assertThat(hasChildren).isTrue();
    }

    @Test
    public void hasChildrenShouldReturnChildWhenExistsInBothTables() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        CassandraId childMailboxId = CassandraId.timeBased();
        MailboxPath childMailboxPath = MailboxPath.forUser(USER, "name.child");
        Mailbox childMailbox = new Mailbox(childMailboxPath, UID_VALIDITY, childMailboxId);
        mailboxDAO.save(childMailbox)
            .block();
        mailboxPathDAO.save(childMailboxPath, childMailboxId)
            .block();

        boolean hasChildren = testee.hasChildren(MAILBOX, '.');

        assertThat(hasChildren).isTrue();
    }

    @Test
    public void hasChildrenShouldReturnChildWhenExistsInV2Table() {
        mailboxDAO.save(MAILBOX)
            .block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID)
            .block();
        CassandraId childMailboxId = CassandraId.timeBased();
        MailboxPath childMailboxPath = MailboxPath.forUser(USER, "name.child");
        Mailbox childMailbox = new Mailbox(childMailboxPath, UID_VALIDITY, childMailboxId);
        mailboxDAO.save(childMailbox)
            .block();
        mailboxPathV2DAO.save(childMailboxPath, childMailboxId)
            .block();
    
        boolean hasChildren = testee.hasChildren(MAILBOX, '.');
    
        assertThat(hasChildren).isTrue();
    }

    @Test
    public void findMailboxWithPathLikeShouldRemoveDuplicatesAndKeepV2() {
        mailboxDAO.save(MAILBOX).block();
        mailboxPathV2DAO.save(MAILBOX_PATH, MAILBOX_ID).block();

        mailboxDAO.save(MAILBOX_BIS).block();
        mailboxPathDAO.save(MAILBOX_PATH, MAILBOX_ID_2).block();

        assertThat(testee.findMailboxWithPathLike(
            new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, WILDCARD)))
            .containsOnly(MAILBOX);
    }
}
