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
import java.util.Iterator;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;

public class MailboxManagementTest {

    public static final String USER = "user";
    public static final int UID_VALIDITY = 10;
    public static final int LIMIT = 1;

    private MailboxManagerManagement mailboxManagerManagement;
    private InMemoryMailboxSessionMapperFactory inMemoryMapperFactory;
    private MailboxSession session;

    @Before
    public void setUp() throws Exception {
        inMemoryMapperFactory = new InMemoryMailboxSessionMapperFactory();
        StoreRightManager storeRightManager = new StoreRightManager(inMemoryMapperFactory,
                                                                    new UnionMailboxACLResolver(),
                                                                    new SimpleGroupMembershipResolver());


        DefaultDelegatingMailboxListener delegatingListener = new DefaultDelegatingMailboxListener();
        MailboxEventDispatcher mailboxEventDispatcher = new MailboxEventDispatcher(delegatingListener);
        StoreMailboxManager mailboxManager = new StoreMailboxManager(
            inMemoryMapperFactory,
            new FakeAuthenticator(),
            FakeAuthorizator.defaultReject(),
            new JVMMailboxPathLocker(),
            new MessageParser(),
            new DefaultMessageId.Factory(),
            mailboxEventDispatcher,
            delegatingListener,
            storeRightManager);

        mailboxManager.init();
        mailboxManagerManagement = new MailboxManagerManagement();
        mailboxManagerManagement.setMailboxManager(mailboxManager);
        session = mailboxManager.createSystemSession("TEST");
    }

    @Test
    public void deleteMailboxesShouldDeleteMailboxes() throws Exception {
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY));
        mailboxManagerManagement.deleteMailboxes(USER);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    public void deleteMailboxesShouldDeleteInbox() throws Exception {
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox(MailboxPath.forUser(USER, "INBOX"), UID_VALIDITY));
        mailboxManagerManagement.deleteMailboxes(USER);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    public void deleteMailboxesShouldDeleteMailboxesChildren() throws Exception {
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox(MailboxPath.forUser(USER, "INBOX.test"), UID_VALIDITY));
        mailboxManagerManagement.deleteMailboxes(USER);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    public void deleteMailboxesShouldNotDeleteMailboxesBelongingToNotPrivateNamespace() throws Exception {
        Mailbox mailbox = new SimpleMailbox(new MailboxPath("#top", USER, "name"), UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);
        mailboxManagerManagement.deleteMailboxes(USER);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).containsExactly(mailbox);
    }

    @Test
    public void deleteMailboxesShouldNotDeleteMailboxesBelongingToOtherUsers() throws Exception {
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser("userbis", "name"), UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);
        mailboxManagerManagement.deleteMailboxes(USER);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).containsExactly(mailbox);
    }

    @Test
    public void deleteMailboxesShouldDeleteMailboxesWithEmptyNames() throws Exception {
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox(MailboxPath.forUser(USER, ""), UID_VALIDITY));
        mailboxManagerManagement.deleteMailboxes(USER);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test(expected = NullPointerException.class)
    public void deleteMailboxesShouldThrowOnNullUserName() throws Exception {
        mailboxManagerManagement.deleteMailboxes(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteMailboxesShouldThrowOnEmptyUserName() throws Exception {
        mailboxManagerManagement.deleteMailboxes("");
    }

    @Test
    public void deleteMailboxesShouldDeleteMultipleMailboxes() throws Exception {
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY));
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox(MailboxPath.forUser(USER, "INBOX"), UID_VALIDITY));
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox(MailboxPath.forUser(USER, "INBOX.test"), UID_VALIDITY));
        mailboxManagerManagement.deleteMailboxes(USER);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    public void createMailboxShouldCreateAMailbox() throws Exception {
        mailboxManagerManagement.createMailbox(MailboxConstants.USER_NAMESPACE, USER, "name");
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).hasSize(1);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).findMailboxByPath(MailboxPath.forUser(USER, "name"))).isNotNull();
    }

    @Test
    public void createMailboxShouldThrowIfMailboxAlreadyExists() throws Exception {
        MailboxPath path = MailboxPath.forUser(USER, "name");
        Mailbox mailbox = new SimpleMailbox(path, UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);

        assertThatThrownBy(() -> mailboxManagerManagement.createMailbox(MailboxConstants.USER_NAMESPACE, USER, "name"))
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(MailboxExistsException.class);
    }

    @Test
    public void createMailboxShouldNotCreateAdditionalMailboxesIfMailboxAlreadyExists() throws Exception {
        MailboxPath path = MailboxPath.forUser(USER, "name");
        Mailbox mailbox = new SimpleMailbox(path, UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);

        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).containsExactly(mailbox);
    }

    @Test(expected = NullPointerException.class)
    public void createMailboxShouldThrowOnNullNamespace() {
        mailboxManagerManagement.createMailbox(null, "a", "a");
    }

    @Test(expected = NullPointerException.class)
    public void createMailboxShouldThrowOnNullUser() {
        mailboxManagerManagement.createMailbox("a", null, "a");
    }

    @Test(expected = NullPointerException.class)
    public void createMailboxShouldThrowOnNullName() {
        mailboxManagerManagement.createMailbox("a", "a", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createMailboxShouldThrowOnEmptyNamespace() {
        mailboxManagerManagement.createMailbox("", "a", "a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void createMailboxShouldThrowOnEmptyUser() {
        mailboxManagerManagement.createMailbox("a", "", "a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void createMailboxShouldThrowOnEmptyName() {
        mailboxManagerManagement.createMailbox("a", "a", "");
    }

    @Test
    public void listMailboxesShouldReturnUserMailboxes() throws Exception {
        Mailbox mailbox1 = new SimpleMailbox(new MailboxPath("#top", USER, "name1"), UID_VALIDITY);
        Mailbox mailbox2 = new SimpleMailbox(MailboxPath.forUser(USER, "name2"), UID_VALIDITY);
        Mailbox mailbox3 = new SimpleMailbox(MailboxPath.forUser("other_user", "name3"), UID_VALIDITY);
        Mailbox mailbox4 = new SimpleMailbox(MailboxPath.forUser(USER, "name4"), UID_VALIDITY);
        Mailbox mailbox5 = new SimpleMailbox(MailboxPath.forUser(USER, "INBOX"), UID_VALIDITY);
        Mailbox mailbox6 = new SimpleMailbox(MailboxPath.forUser(USER, "INBOX.toto"), UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox1);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox2);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox3);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox4);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox5);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox6);
        assertThat(mailboxManagerManagement.listMailboxes(USER)).containsOnly("name2", "name4", "INBOX", "INBOX.toto");
    }

    @Test(expected = NullPointerException.class)
    public void listMailboxesShouldThrowOnNullUserName() {
        mailboxManagerManagement.listMailboxes(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void listMailboxesShouldThrowOnEmptyUserName() {
        mailboxManagerManagement.listMailboxes("");
    }

    @Test
    public void deleteMailboxShouldDeleteGivenMailbox() throws Exception {
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox(MailboxPath.forUser(USER, "name"), UID_VALIDITY));
        mailboxManagerManagement.deleteMailbox(MailboxConstants.USER_NAMESPACE, USER, "name");
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    public void deleteMailboxShouldNotDeleteGivenMailboxIfWrongNamespace() throws Exception {
        Mailbox mailbox = new SimpleMailbox(new MailboxPath("#top", USER, "name"), UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);
        mailboxManagerManagement.deleteMailbox(MailboxConstants.USER_NAMESPACE, USER, "name");
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).containsOnly(mailbox);
    }

    @Test
    public void deleteMailboxShouldNotDeleteGivenMailboxIfWrongUser() throws Exception {
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser("userbis", "name"), UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);
        mailboxManagerManagement.deleteMailbox(MailboxConstants.USER_NAMESPACE, USER, "name");
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).containsOnly(mailbox);
    }

    @Test
    public void deleteMailboxShouldNotDeleteGivenMailboxIfWrongName() throws Exception {
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(USER, "wrong_name"), UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);
        mailboxManagerManagement.deleteMailbox(MailboxConstants.USER_NAMESPACE, USER, "name");
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).containsOnly(mailbox);
    }

    @Test
    public void importEmlFileToMailboxShouldImportEmlFileToGivenMailbox() throws Exception {
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(USER, "name"),
                UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);
        String emlpath = ClassLoader.getSystemResource("eml/frnog.eml").getFile();
        mailboxManagerManagement.importEmlFileToMailbox(MailboxConstants.USER_NAMESPACE, USER, "name", emlpath);

        assertThat(inMemoryMapperFactory.getMessageMapper(session).countMessagesInMailbox(mailbox)).isEqualTo(1);
        Iterator<MailboxMessage> iterator = inMemoryMapperFactory.getMessageMapper(session).findInMailbox(mailbox,
                MessageRange.all(), MessageMapper.FetchType.Full, LIMIT);
        MailboxMessage mailboxMessage = iterator.next();

        assertThat(IOUtils.toString(new FileInputStream(new File(emlpath)), Charsets.UTF_8))
                .isEqualTo(IOUtils.toString(mailboxMessage.getFullContent(), Charsets.UTF_8));
    }

    @Test
    public void importEmlFileToMailboxShouldNotImportEmlFileWithWrongPathToGivenMailbox() throws Exception {
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(USER, "name"),
                UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);
        String emlpath = ClassLoader.getSystemResource("eml/frnog.eml").getFile();
        mailboxManagerManagement.importEmlFileToMailbox(MailboxConstants.USER_NAMESPACE, USER, "name", "wrong_path" + emlpath);

        assertThat(inMemoryMapperFactory.getMessageMapper(session).countMessagesInMailbox(mailbox)).isEqualTo(0);
        Iterator<MailboxMessage> iterator = inMemoryMapperFactory.getMessageMapper(session).findInMailbox(mailbox,
                MessageRange.all(), MessageMapper.FetchType.Full, LIMIT);
        assertThat(iterator.hasNext()).isFalse();
    }

	
    @Test(expected = NullPointerException.class)
    public void deleteMailboxShouldThrowOnNullNamespace() {
        mailboxManagerManagement.deleteMailbox(null, "a", "a");
    }

    @Test(expected = NullPointerException.class)
    public void deleteMailboxShouldThrowOnNullUser() {
        mailboxManagerManagement.deleteMailbox("a", null, "a");
    }

    @Test(expected = NullPointerException.class)
    public void deleteMailboxShouldThrowOnNullName() {
        mailboxManagerManagement.deleteMailbox("a", "a", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteMailboxShouldThrowOnEmptyNamespace() {
        mailboxManagerManagement.deleteMailbox("", "a", "a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteMailboxShouldThrowOnEmptyUser() {
        mailboxManagerManagement.deleteMailbox("a", "", "a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteMailboxShouldThrowOnEmptyName() {
        mailboxManagerManagement.deleteMailbox("a", "a", "");
    }

}
