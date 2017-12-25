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

package org.apache.james.mailbox.store.mail.model;

import static org.apache.james.mailbox.store.mail.model.ListMailboxAssert.assertMailboxes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Assume;
import org.junit.Test;

/**
 * Generic purpose tests for your implementation MailboxMapper.
 * 
 * You then just need to instantiate your mailbox mapper and an IdGenerator.
 */
public abstract class MailboxMapperTest {
    
    private final static char DELIMITER = '.';
    private final static char WILDCARD = '%';
    private final static long UID_VALIDITY = 42;

    private MailboxPath benwaInboxPath;
    private Mailbox benwaInboxMailbox;
    private MailboxPath benwaWorkPath;
    private Mailbox benwaWorkMailbox;
    private MailboxPath benwaWorkTodoPath;
    private Mailbox benwaWorkTodoMailbox;
    private MailboxPath benwaPersoPath;
    private Mailbox benwaPersoMailbox;
    private MailboxPath benwaWorkDonePath;
    private Mailbox benwaWorkDoneMailbox;
    private MailboxPath bobInboxPath;
    private Mailbox bobyMailbox;
    private MailboxPath bobyMailboxPath;
    private Mailbox bobInboxMailbox;
    private MailboxPath esnDevGroupInboxPath;
    private Mailbox esnDevGroupInboxMailbox;
    private MailboxPath esnDevGroupHublinPath;
    private Mailbox esnDevGroupHublinMailbox;
    private MailboxPath esnDevGroupJamesPath;
    private Mailbox esnDevGroupJamesMailbox;
    private MailboxPath obmTeamGroupInboxPath;
    private Mailbox obmTeamGroupInboxMailbox;
    private MailboxPath obmTeamGroupOPushPath;
    private Mailbox obmTeamGroupOPushMailbox;
    private MailboxPath obmTeamGroupRoundCubePath;
    private Mailbox obmTeamGroupRoundCubeMailbox;
    private MailboxPath bobDifferentNamespacePath;
    private Mailbox bobDifferentNamespaceMailbox;

    private MailboxMapper mailboxMapper;
    private MapperProvider mapperProvider;

    protected abstract MapperProvider createMapperProvider();

    public void setUp() throws Exception {
        this.mapperProvider = createMapperProvider();
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.MAILBOX));

        this.mailboxMapper = mapperProvider.createMailboxMapper();
        
        initData();
    }

    @Test
    public void findMailboxByPathWhenAbsentShouldFail() throws MailboxException {
        assertThatThrownBy(() -> mailboxMapper.findMailboxByPath(MailboxPath.forUser("benwa", "INBOX")))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void saveShouldPersistTheMailbox() throws MailboxException{
        mailboxMapper.save(benwaInboxMailbox);
        MailboxAssert.assertThat(mailboxMapper.findMailboxByPath(benwaInboxPath)).isEqualTo(benwaInboxMailbox);
    }

    @Test
    public void saveShouldThrowWhenMailboxAlreadyExist() throws MailboxException{
        mailboxMapper.save(benwaInboxMailbox);

        SimpleMailbox mailbox = new SimpleMailbox(benwaInboxMailbox);
        mailbox.setMailboxId(null);

        assertThatThrownBy(() ->mailboxMapper.save(mailbox))
            .isInstanceOf(MailboxExistsException.class);
    }

    @Test
    public void saveWithNullUserShouldPersistTheMailbox() throws MailboxException{
        mailboxMapper.save(esnDevGroupInboxMailbox);
        MailboxAssert.assertThat(mailboxMapper.findMailboxByPath(esnDevGroupInboxPath)).isEqualTo(esnDevGroupInboxMailbox);
    }

    @Test
    public void listShouldRetrieveAllMailbox() throws MailboxException {
        saveAll();
        List<Mailbox> mailboxes = mailboxMapper.list();

        assertMailboxes(mailboxes)
            .containOnly(benwaInboxMailbox, benwaWorkMailbox, benwaWorkTodoMailbox, benwaPersoMailbox, benwaWorkDoneMailbox, 
                esnDevGroupInboxMailbox, esnDevGroupHublinMailbox, esnDevGroupJamesMailbox, 
                obmTeamGroupInboxMailbox, obmTeamGroupOPushMailbox, obmTeamGroupRoundCubeMailbox, 
                bobyMailbox, bobDifferentNamespaceMailbox, bobInboxMailbox);
    }
    
    @Test
    public void hasChildrenShouldReturnFalseWhenNoChildrenExists() throws MailboxException {
        saveAll();
        assertThat(mailboxMapper.hasChildren(benwaWorkTodoMailbox, DELIMITER)).isFalse();
    }

    @Test
    public void hasChildrenShouldReturnTrueWhenChildrenExists() throws MailboxException {
        saveAll();
        assertThat(mailboxMapper.hasChildren(benwaInboxMailbox, DELIMITER)).isTrue();
    }

    @Test
    public void hasChildrenWithNullUserShouldReturnFalseWhenNoChildrenExists() throws MailboxException {
        saveAll();
        assertThat(mailboxMapper.hasChildren(esnDevGroupHublinMailbox, DELIMITER)).isFalse();
    }

    @Test
    public void hasChildrenWithNullUserShouldReturnTrueWhenChildrenExists() throws MailboxException {
        saveAll();
        assertThat(mailboxMapper.hasChildren(esnDevGroupInboxMailbox, DELIMITER)).isTrue();
    }

    @Test
    public void hasChildrenShouldNotBeAcrossUsersAndNamespace() throws MailboxException {
        saveAll();
        assertThat(mailboxMapper.hasChildren(bobInboxMailbox, '.')).isFalse();
    }

    @Test
    public void findMailboxWithPathLikeShouldBeLimitedToUserAndNamespace() throws MailboxException {
        saveAll();
        MailboxPath mailboxPathQuery = new MailboxPath(bobInboxMailbox.getNamespace(), bobInboxMailbox.getUser(), "IN" + WILDCARD);
        List<Mailbox> mailboxes = mailboxMapper.findMailboxWithPathLike(mailboxPathQuery);

        assertMailboxes(mailboxes).containOnly(bobInboxMailbox);
    }
    
    @Test
    public void deleteShouldEraseTheGivenMailbox() throws MailboxException {
        saveAll();
        mailboxMapper.delete(benwaInboxMailbox);

        assertThatThrownBy(() -> mailboxMapper.findMailboxByPath(benwaInboxPath))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void deleteWithNullUserShouldEraseTheGivenMailbox() throws MailboxException {
        saveAll();
        mailboxMapper.delete(esnDevGroupJamesMailbox);

        assertThatThrownBy(() -> mailboxMapper.findMailboxByPath(esnDevGroupJamesPath))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void findMailboxWithPathLikeWithChildRegexShouldRetrieveChildren() throws MailboxException {
        saveAll();
        MailboxPath regexPath = new MailboxPath(benwaWorkPath.getNamespace(), benwaWorkPath.getUser(), benwaWorkPath.getName() + WILDCARD);
        List<Mailbox> mailboxes = mailboxMapper.findMailboxWithPathLike(regexPath);

        assertMailboxes(mailboxes).containOnly(benwaWorkMailbox, benwaWorkDoneMailbox, benwaWorkTodoMailbox);
    }

    @Test
    public void findMailboxWithPathLikeWithNullUserWithChildRegexShouldRetrieveChildren() throws MailboxException {
        saveAll();
        MailboxPath regexPath = new MailboxPath(obmTeamGroupInboxPath.getNamespace(), obmTeamGroupInboxPath.getUser(), obmTeamGroupInboxPath.getName() + WILDCARD);

        List<Mailbox> mailboxes = mailboxMapper.findMailboxWithPathLike(regexPath);

        assertMailboxes(mailboxes).containOnly(obmTeamGroupInboxMailbox, obmTeamGroupOPushMailbox, obmTeamGroupRoundCubeMailbox);
    }
    
    @Test
    public void findMailboxWithPathLikeWithRegexShouldRetrieveCorrespondingMailbox() throws MailboxException {
        saveAll();
        MailboxPath regexPath = new MailboxPath(benwaInboxPath.getNamespace(), benwaInboxPath.getUser(), WILDCARD + "X");

        List<Mailbox> mailboxes = mailboxMapper.findMailboxWithPathLike(regexPath);

        assertMailboxes(mailboxes).containOnly(benwaInboxMailbox);
    }

    @Test
    public void findMailboxWithPathLikeWithNullUserWithRegexShouldRetrieveCorrespondingMailbox() throws MailboxException {
        saveAll();
        MailboxPath regexPath = new MailboxPath(esnDevGroupInboxPath.getNamespace(), esnDevGroupInboxPath.getUser(), WILDCARD + "X");

        List<Mailbox> mailboxes = mailboxMapper.findMailboxWithPathLike(regexPath);

        assertMailboxes(mailboxes).containOnly(esnDevGroupInboxMailbox);
    }

    @Test
    public void findMailboxWithPathLikeShouldEscapeMailboxName() throws MailboxException {
        saveAll();
        MailboxPath regexPath = new MailboxPath(benwaInboxPath.getNamespace(), benwaInboxPath.getUser(), "INB?X");
        assertThat(mailboxMapper.findMailboxWithPathLike(regexPath)).isEmpty();
    }

    @Test
    public void findMailboxByIdShouldReturnExistingMailbox() throws MailboxException {
        saveAll();
        Mailbox actual = mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId());
        MailboxAssert.assertThat(actual).isEqualTo(benwaInboxMailbox);
    }
    
    @Test
    public void findMailboxByIdShouldFailWhenAbsent() throws MailboxException {
        saveAll();
        MailboxId removed = benwaInboxMailbox.getMailboxId();
        mailboxMapper.delete(benwaInboxMailbox);
        assertThatThrownBy(() -> mailboxMapper.findMailboxById(removed))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    private void initData() {
        benwaInboxPath = MailboxPath.forUser("benwa", "INBOX");
        benwaWorkPath = MailboxPath.forUser("benwa", "INBOX"+DELIMITER+"work");
        benwaWorkTodoPath = MailboxPath.forUser("benwa", "INBOX"+DELIMITER+"work"+DELIMITER+"todo");
        benwaPersoPath = MailboxPath.forUser("benwa", "INBOX"+DELIMITER+"perso");
        benwaWorkDonePath = MailboxPath.forUser("benwa", "INBOX"+DELIMITER+"work"+DELIMITER+"done");
        bobInboxPath = MailboxPath.forUser("bob", "INBOX");
        bobyMailboxPath = MailboxPath.forUser("boby", "INBOX.that.is.a.trick");
        bobDifferentNamespacePath = new MailboxPath("#private_bob", "bob", "INBOX.bob");
        esnDevGroupInboxPath = new MailboxPath("#community_ESN_DEV", null, "INBOX");
        esnDevGroupHublinPath = new MailboxPath("#community_ESN_DEV", null, "INBOX"+DELIMITER+"hublin");
        esnDevGroupJamesPath = new MailboxPath("#community_ESN_DEV", null, "INBOX"+DELIMITER+"james");
        obmTeamGroupInboxPath = new MailboxPath("#community_OBM_Core_Team", null, "INBOX");
        obmTeamGroupOPushPath = new MailboxPath("#community_OBM_Core_Team", null, "INBOX"+DELIMITER+"OPush");
        obmTeamGroupRoundCubePath = new MailboxPath("#community_OBM_Core_Team", null, "INBOX"+DELIMITER+"roundCube");

        benwaInboxMailbox = createMailbox(benwaInboxPath);
        benwaWorkMailbox = createMailbox(benwaWorkPath);
        benwaWorkTodoMailbox = createMailbox(benwaWorkTodoPath);
        benwaPersoMailbox = createMailbox(benwaPersoPath);
        benwaWorkDoneMailbox = createMailbox(benwaWorkDonePath);
        bobInboxMailbox = createMailbox(bobInboxPath);
        esnDevGroupInboxMailbox = createMailbox(esnDevGroupInboxPath);
        esnDevGroupHublinMailbox = createMailbox(esnDevGroupHublinPath);
        esnDevGroupJamesMailbox = createMailbox(esnDevGroupJamesPath);
        obmTeamGroupInboxMailbox = createMailbox(obmTeamGroupInboxPath);
        obmTeamGroupOPushMailbox = createMailbox(obmTeamGroupOPushPath);
        obmTeamGroupRoundCubeMailbox = createMailbox(obmTeamGroupRoundCubePath);
        bobyMailbox = createMailbox(bobyMailboxPath);
        bobDifferentNamespaceMailbox = createMailbox(bobDifferentNamespacePath);
    }

    private void saveAll() throws MailboxException{
        mailboxMapper.save(benwaInboxMailbox);
        mailboxMapper.save(benwaWorkMailbox);
        mailboxMapper.save(benwaWorkTodoMailbox);
        mailboxMapper.save(benwaPersoMailbox);
        mailboxMapper.save(benwaWorkDoneMailbox);
        mailboxMapper.save(esnDevGroupInboxMailbox);
        mailboxMapper.save(esnDevGroupHublinMailbox);
        mailboxMapper.save(esnDevGroupJamesMailbox);
        mailboxMapper.save(obmTeamGroupInboxMailbox);
        mailboxMapper.save(obmTeamGroupOPushMailbox);
        mailboxMapper.save(obmTeamGroupRoundCubeMailbox);
        mailboxMapper.save(bobyMailbox);
        mailboxMapper.save(bobDifferentNamespaceMailbox);
        mailboxMapper.save(bobInboxMailbox);
    }

    private SimpleMailbox createMailbox(MailboxPath mailboxPath) {
        SimpleMailbox mailbox = new SimpleMailbox(mailboxPath, UID_VALIDITY);
        mailbox.setMailboxId(mapperProvider.generateId());
        return mailbox;
    }

}
