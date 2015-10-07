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

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MockAuthenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MailboxManagementTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxManagementTest.class);
    public static final String USER = "user";
    public static final int UID_VALIDITY = 10;

    private MailboxManagerManagement mailboxManagerManagement;
    private InMemoryMailboxSessionMapperFactory inMemoryMapperFactory;
    private MailboxSession session;

    @Before
    public void setUp() throws Exception {
        inMemoryMapperFactory = new InMemoryMailboxSessionMapperFactory();
        StoreMailboxManager<InMemoryId> mailboxManager = new StoreMailboxManager<InMemoryId>(
            inMemoryMapperFactory,
            new MockAuthenticator(),
            new JVMMailboxPathLocker(),
            new UnionMailboxACLResolver(),
            new SimpleGroupMembershipResolver());
        mailboxManager.init();
        mailboxManagerManagement = new MailboxManagerManagement();
        mailboxManagerManagement.setMailboxManager(mailboxManager);
        mailboxManagerManagement.setLog(LOGGER);
        session = mailboxManager.createSystemSession("TEST", LOGGER);
    }

    @Test
    public void deleteMailboxesShouldDeleteMailboxes() throws Exception {
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "name"), UID_VALIDITY));
        mailboxManagerManagement.deleteMailboxes(USER);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    public void deleteMailboxesShouldDeleteInbox() throws Exception {
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "INBOX"), UID_VALIDITY));
        mailboxManagerManagement.deleteMailboxes(USER);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    public void deleteMailboxesShouldDeleteMailboxesChildren() throws Exception {
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "INBOX.test"), UID_VALIDITY));
        mailboxManagerManagement.deleteMailboxes(USER);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    public void deleteMailboxesShouldNotDeleteMailboxesBelongingToNotPrivateNamespace() throws Exception {
        Mailbox<InMemoryId> mailbox = new SimpleMailbox<InMemoryId>(new MailboxPath("#top", USER, "name"), UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);
        mailboxManagerManagement.deleteMailboxes(USER);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).containsExactly(mailbox);
    }

    @Test
    public void deleteMailboxesShouldNotDeleteMailboxesBelongingToOtherUsers() throws Exception {
        Mailbox<InMemoryId> mailbox = new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, "userbis", "name"), UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);
        mailboxManagerManagement.deleteMailboxes(USER);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).containsExactly(mailbox);
    }

    @Test
    public void deleteMailboxesShouldDeleteMailboxesWithEmptyNames() throws Exception {
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, ""), UID_VALIDITY));
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
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "name"), UID_VALIDITY));
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "INBOX"), UID_VALIDITY));
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "INBOX.test"), UID_VALIDITY));
        mailboxManagerManagement.deleteMailboxes(USER);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    public void createMailboxShouldCreateAMailbox() throws Exception {
        mailboxManagerManagement.createMailbox(MailboxConstants.USER_NAMESPACE, USER, "name");
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).hasSize(1);
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).findMailboxByPath(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "name"))).isNotNull();
    }

    @Test
    public void createMailboxShouldNotThrowIfMailboxAlreadyExist() throws Exception {
        MailboxPath path = new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "name");
        Mailbox<InMemoryId> mailbox = new SimpleMailbox<InMemoryId>(path, UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);
        mailboxManagerManagement.createMailbox(MailboxConstants.USER_NAMESPACE, USER, "name");
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
        Mailbox<InMemoryId> mailbox1 = new SimpleMailbox<InMemoryId>(new MailboxPath("#top", USER, "name1"), UID_VALIDITY);
        Mailbox<InMemoryId> mailbox2 = new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "name2"), UID_VALIDITY);
        Mailbox<InMemoryId> mailbox3 = new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, "other_user", "name3"), UID_VALIDITY);
        Mailbox<InMemoryId> mailbox4 = new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "name4"), UID_VALIDITY);
        Mailbox<InMemoryId> mailbox5 = new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "INBOX"), UID_VALIDITY);
        Mailbox<InMemoryId> mailbox6 = new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "INBOX.toto"), UID_VALIDITY);
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
        inMemoryMapperFactory.createMailboxMapper(session).save(new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "name"), UID_VALIDITY));
        mailboxManagerManagement.deleteMailbox(MailboxConstants.USER_NAMESPACE, USER, "name");
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).isEmpty();
    }

    @Test
    public void deleteMailboxShouldNotDeleteGivenMailboxIfWrongNamespace() throws Exception {
        Mailbox<InMemoryId> mailbox = new SimpleMailbox<InMemoryId>(new MailboxPath("#top", USER, "name"), UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);
        mailboxManagerManagement.deleteMailbox(MailboxConstants.USER_NAMESPACE, USER, "name");
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).containsOnly(mailbox);
    }

    @Test
    public void deleteMailboxShouldNotDeleteGivenMailboxIfWrongUser() throws Exception {
        Mailbox<InMemoryId> mailbox = new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, "userbis", "name"), UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);
        mailboxManagerManagement.deleteMailbox(MailboxConstants.USER_NAMESPACE, USER, "name");
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).containsOnly(mailbox);
    }

    @Test
    public void deleteMailboxShouldNotDeleteGivenMailboxIfWrongName() throws Exception {
        Mailbox<InMemoryId> mailbox = new SimpleMailbox<InMemoryId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, "wrong_name"), UID_VALIDITY);
        inMemoryMapperFactory.createMailboxMapper(session).save(mailbox);
        mailboxManagerManagement.deleteMailbox(MailboxConstants.USER_NAMESPACE, USER, "name");
        assertThat(inMemoryMapperFactory.createMailboxMapper(session).list()).containsOnly(mailbox);
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
