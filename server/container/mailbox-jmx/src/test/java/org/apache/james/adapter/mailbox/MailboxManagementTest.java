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

package org.apache.james.adapter.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MailboxManagementTest {

    public static final Username USER = Username.of("user");
    public static final UidValidity UID_VALIDITY = UidValidity.of(10);
    public static final int LIMIT = 1;

    private MailboxManagerManagement mailboxManagerManagement;
    private MailboxSessionMapperFactory mapperFactory;
    private MailboxSession session;

    @BeforeEach
    void setUp() throws Exception {
        StoreMailboxManager mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        mapperFactory = mailboxManager.getMapperFactory();

        mailboxManagerManagement = new MailboxManagerManagement();
        mailboxManagerManagement.setMailboxManager(mailboxManager);
        session = mailboxManager.createSystemSession(Username.of("TEST"));
    }

    @Test
    void deleteMailboxesShouldDeleteMailboxes() throws Exception {
        mapperFactory.createMailboxMapper(session).create(MailboxPath.forUser(USER, "name"), UID_VALIDITY);
        mailboxManagerManagement.deleteMailboxes(USER.asString());
        assertThat(mapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    void deleteMailboxesShouldDeleteInbox() throws Exception {
        mapperFactory.createMailboxMapper(session).create(MailboxPath.inbox(USER), UID_VALIDITY);
        mailboxManagerManagement.deleteMailboxes(USER.asString());
        assertThat(mapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    void deleteMailboxesShouldDeleteMailboxesChildren() throws Exception {
        mapperFactory.createMailboxMapper(session).create(MailboxPath.forUser(USER, "INBOX.test"), UID_VALIDITY);
        mailboxManagerManagement.deleteMailboxes(USER.asString());
        assertThat(mapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    void deleteMailboxesShouldNotDeleteMailboxesBelongingToNotPrivateNamespace() throws Exception {
        Mailbox mailbox = mapperFactory.createMailboxMapper(session).create(new MailboxPath("#top", USER, "name"), UID_VALIDITY);
        mailboxManagerManagement.deleteMailboxes(USER.asString());
        assertThat(mapperFactory.createMailboxMapper(session).list()).containsExactly(mailbox);
    }

    @Test
    void deleteMailboxesShouldNotDeleteMailboxesBelongingToOtherUsers() throws Exception {
        Mailbox mailbox = mapperFactory.createMailboxMapper(session).create(MailboxPath.forUser(Username.of("userbis"), "name"), UID_VALIDITY);
        mailboxManagerManagement.deleteMailboxes(USER.asString());
        assertThat(mapperFactory.createMailboxMapper(session).list()).containsExactly(mailbox);
    }

    @Test
    void deleteMailboxesShouldDeleteMailboxesWithEmptyNames() throws Exception {
        mapperFactory.createMailboxMapper(session).create(MailboxPath.forUser(USER, ""), UID_VALIDITY);
        mailboxManagerManagement.deleteMailboxes(USER.asString());
        assertThat(mapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    void deleteMailboxesShouldThrowOnNullUserName() throws Exception {
        assertThatThrownBy(() -> mailboxManagerManagement.deleteMailboxes(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deleteMailboxesShouldThrowOnEmptyUserName() throws Exception {
        assertThatThrownBy(() -> mailboxManagerManagement.deleteMailboxes(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteMailboxesShouldDeleteMultipleMailboxes() throws Exception {
        mapperFactory.createMailboxMapper(session).create(MailboxPath.forUser(USER, "name"), UID_VALIDITY);
        mapperFactory.createMailboxMapper(session).create(MailboxPath.forUser(USER, "INBOX"), UID_VALIDITY);
        mapperFactory.createMailboxMapper(session).create(MailboxPath.forUser(USER, "INBOX.test"), UID_VALIDITY);
        mailboxManagerManagement.deleteMailboxes(USER.asString());
        assertThat(mapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    void createMailboxShouldCreateAMailbox() throws Exception {
        mailboxManagerManagement.createMailbox(MailboxConstants.USER_NAMESPACE, USER.asString(), "name");
        assertThat(mapperFactory.createMailboxMapper(session).list()).hasSize(1);
        assertThat(mapperFactory.createMailboxMapper(session).findMailboxByPath(MailboxPath.forUser(USER, "name"))).isNotNull();
    }

    @Test
    void createMailboxShouldThrowIfMailboxAlreadyExists() throws Exception {
        MailboxPath path = MailboxPath.forUser(USER, "name");
        mapperFactory.createMailboxMapper(session).create(path, UID_VALIDITY);

        assertThatThrownBy(() -> mailboxManagerManagement.createMailbox(MailboxConstants.USER_NAMESPACE, USER.asString(), "name"))
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(MailboxExistsException.class);
    }

    @Test
    void createMailboxShouldNotCreateAdditionalMailboxesIfMailboxAlreadyExists() throws Exception {
        MailboxPath path = MailboxPath.forUser(USER, "name");
        Mailbox mailbox = mapperFactory.createMailboxMapper(session).create(path, UID_VALIDITY);

        assertThat(mapperFactory.createMailboxMapper(session).list()).containsExactly(mailbox);
    }

    @Test
    void createMailboxShouldThrowOnNullNamespace() {
        assertThatThrownBy(() -> mailboxManagerManagement.createMailbox(null, "a", "a"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createMailboxShouldThrowOnNullUser() {
        assertThatThrownBy(() -> mailboxManagerManagement.createMailbox("a", null, "a"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createMailboxShouldThrowOnNullName() {
        assertThatThrownBy(() -> mailboxManagerManagement.createMailbox("a", "a", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createMailboxShouldThrowOnEmptyNamespace() {
        assertThatThrownBy(() -> mailboxManagerManagement.createMailbox("", "a", "a"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createMailboxShouldThrowOnEmptyUser() {
        assertThatThrownBy(() -> mailboxManagerManagement.createMailbox("a", "", "a"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createMailboxShouldThrowOnEmptyName() {
        assertThatThrownBy(() -> mailboxManagerManagement.createMailbox("a", "a", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listMailboxesShouldReturnUserMailboxes() throws Exception {
        MailboxMapper mapper = mapperFactory.createMailboxMapper(session);
        mapper.create(new MailboxPath("#top", USER, "name1"), UID_VALIDITY);
        mapper.create(MailboxPath.forUser(USER, "name2"), UID_VALIDITY);
        mapper.create(MailboxPath.forUser(Username.of("other_user"), "name3"), UID_VALIDITY);
        mapper.create(MailboxPath.forUser(USER, "name4"), UID_VALIDITY);
        mapper.create(MailboxPath.forUser(USER, "INBOX"), UID_VALIDITY);
        mapper.create(MailboxPath.forUser(USER, "INBOX.toto"), UID_VALIDITY);
        assertThat(mailboxManagerManagement.listMailboxes(USER.asString())).containsOnly("name2", "name4", "INBOX", "INBOX.toto");
    }

    @Test
    void listMailboxesShouldThrowOnNullUserName() {
        assertThatThrownBy(() -> mailboxManagerManagement.listMailboxes(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void listMailboxesShouldThrowOnEmptyUserName() {
        assertThatThrownBy(() -> mailboxManagerManagement.listMailboxes(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteMailboxShouldDeleteGivenMailbox() throws Exception {
        mapperFactory.createMailboxMapper(session).create(MailboxPath.forUser(USER, "name"), UID_VALIDITY);
        mailboxManagerManagement.deleteMailbox(MailboxConstants.USER_NAMESPACE, USER.asString(), "name");
        assertThat(mapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    void deleteMailboxShouldNotDeleteGivenMailboxIfWrongNamespace() throws Exception {
        Mailbox mailbox = mapperFactory.createMailboxMapper(session).create(new MailboxPath("#top", USER, "name"), UID_VALIDITY);
        mailboxManagerManagement.deleteMailbox(MailboxConstants.USER_NAMESPACE, USER.asString(), "name");
        assertThat(mapperFactory.createMailboxMapper(session).list()).containsOnly(mailbox);
    }

    @Test
    void deleteMailboxShouldNotDeleteGivenMailboxIfWrongUser() throws Exception {
        Mailbox mailbox = mapperFactory.createMailboxMapper(session).create(MailboxPath.forUser(Username.of("userbis"), "name"), UID_VALIDITY);
        mailboxManagerManagement.deleteMailbox(MailboxConstants.USER_NAMESPACE, USER.asString(), "name");
        assertThat(mapperFactory.createMailboxMapper(session).list()).containsOnly(mailbox);
    }

    @Test
    void deleteMailboxShouldNotDeleteGivenMailboxIfWrongName() throws Exception {
        Mailbox mailbox = mapperFactory.createMailboxMapper(session).create(MailboxPath.forUser(USER, "wrong_name"), UID_VALIDITY);
        mailboxManagerManagement.deleteMailbox(MailboxConstants.USER_NAMESPACE, USER.asString(), "name");
        assertThat(mapperFactory.createMailboxMapper(session).list()).containsOnly(mailbox);
    }

    @Test
    void importEmlFileToMailboxShouldImportEmlFileToGivenMailbox() throws Exception {
        Mailbox mailbox = mapperFactory.createMailboxMapper(session).create(MailboxPath.forUser(USER, "name"), UID_VALIDITY);
        String emlpath = ClassLoader.getSystemResource("eml/frnog.eml").getFile();
        mailboxManagerManagement.importEmlFileToMailbox(MailboxConstants.USER_NAMESPACE, USER.asString(), "name", emlpath);

        assertThat(mapperFactory.getMessageMapper(session).countMessagesInMailbox(mailbox)).isEqualTo(1);
        Iterator<MailboxMessage> iterator = mapperFactory.getMessageMapper(session).findInMailbox(mailbox,
                MessageRange.all(), MessageMapper.FetchType.Full, LIMIT);
        MailboxMessage mailboxMessage = iterator.next();

        assertThat(IOUtils.toString(new FileInputStream(new File(emlpath)), StandardCharsets.UTF_8))
                .isEqualTo(IOUtils.toString(mailboxMessage.getFullContent(), StandardCharsets.UTF_8));
    }

    @Test
    void importEmlFileToMailboxShouldNotImportEmlFileWithWrongPathToGivenMailbox() throws Exception {
        Mailbox mailbox = mapperFactory.createMailboxMapper(session).create(MailboxPath.forUser(USER, "name"), UID_VALIDITY);
        String emlpath = ClassLoader.getSystemResource("eml/frnog.eml").getFile();
        mailboxManagerManagement.importEmlFileToMailbox(MailboxConstants.USER_NAMESPACE, USER.asString(), "name", "wrong_path" + emlpath);

        assertThat(mapperFactory.getMessageMapper(session).countMessagesInMailbox(mailbox)).isEqualTo(0);
        Iterator<MailboxMessage> iterator = mapperFactory.getMessageMapper(session).findInMailbox(mailbox,
                MessageRange.all(), MessageMapper.FetchType.Full, LIMIT);
        assertThat(iterator.hasNext()).isFalse();
    }


    @Test
    void deleteMailboxShouldThrowOnNullNamespace() {
        assertThatThrownBy(() -> mailboxManagerManagement.deleteMailbox(null, "a", "a"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deleteMailboxShouldThrowOnNullUser() {
        assertThatThrownBy(() -> mailboxManagerManagement.deleteMailbox("a", null, "a"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deleteMailboxShouldThrowOnNullName() {
        assertThatThrownBy(() -> mailboxManagerManagement.deleteMailbox("a", "a", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deleteMailboxShouldThrowOnEmptyNamespace() {
        assertThatThrownBy(() -> mailboxManagerManagement.deleteMailbox("", "a", "a"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteMailboxShouldThrowOnEmptyUser() {
        assertThatThrownBy(() -> mailboxManagerManagement.deleteMailbox("a", "", "a"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteMailboxShouldThrowOnEmptyName() {
        assertThatThrownBy(() -> mailboxManagerManagement.deleteMailbox("a", "a", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
