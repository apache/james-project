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

package org.apache.james.jmap.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MailboxUtilsTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxUtilsTest.class);

    private MailboxManager mailboxManager;
    private MailboxMapperFactory mailboxMapperFactory;
    private MailboxSession mailboxSession;
    private String user;
    private MailboxUtils sut;

    @Before
    public void setup() throws Exception {
        InMemoryIntegrationResources inMemoryIntegrationResources = new InMemoryIntegrationResources();
        mailboxManager = inMemoryIntegrationResources.createMailboxManager(inMemoryIntegrationResources.createGroupMembershipResolver());
        mailboxMapperFactory = new InMemoryMailboxSessionMapperFactory();
        user = "user@domain.org";
        mailboxSession = mailboxManager.login(user, "pass", LOGGER);
        sut = new MailboxUtils(mailboxManager, mailboxMapperFactory);
    }

    @Test
    public void mailboxFromMailboxPathShouldReturnNotEmptyWhenMailboxExists() throws Exception {
        MailboxPath mailboxPath = new MailboxPath("#private", user, "mailbox");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        Optional<Mailbox> optionalMailbox = sut.mailboxFromMailboxPath(mailboxPath, mailboxSession);
        assertThat(optionalMailbox).isPresent();
        Mailbox mailbox = optionalMailbox.get();
        assertThat(mailbox.getId()).isNotEmpty();
    }

    @Test
    public void mailboxFromMailboxPathShouldReturnEmptyWhenMailboxDoesntExist() throws Exception {
        MailboxPath mailboxPath = new MailboxPath("#private", user, "mailbox");

        Optional<Mailbox> optionalMailbox = sut.mailboxFromMailboxPath(mailboxPath, mailboxSession);
        assertThat(optionalMailbox).isEmpty();
    }

    @Test
    public void getNameShouldReturnMailboxNameWhenRootMailbox() throws Exception {
        String expected = "mailbox";
        MailboxPath mailboxPath = new MailboxPath("#private", user, expected);

        String name = sut.getName(mailboxPath, mailboxSession);
        assertThat(name).isEqualTo(expected);
    }

    @Test
    public void getNameShouldReturnMailboxNameWhenChildMailbox() throws Exception {
        String expected = "mailbox";
        MailboxPath mailboxPath = new MailboxPath("#private", user, "inbox." + expected);

        String name = sut.getName(mailboxPath, mailboxSession);
        assertThat(name).isEqualTo(expected);
    }

    @Test
    public void getNameShouldReturnMailboxNameWhenChildOfChildMailbox() throws Exception {
        String expected = "mailbox";
        MailboxPath mailboxPath = new MailboxPath("#private", user, "inbox.children." + expected);

        String name = sut.getName(mailboxPath, mailboxSession);
        assertThat(name).isEqualTo(expected);
    }

    @Test
    public void getMailboxNameFromIdShouldReturnNotEmptyWhenMailboxExists() throws Exception {
        String expected = "mailbox";
        MailboxPath mailboxPath = new MailboxPath("#private", user, expected);
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MailboxId mailboxId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
            .findMailboxByPath(mailboxPath)
            .getMailboxId();

        Optional<String> optionalName = sut.getMailboxNameFromId(mailboxId.serialize(), mailboxSession);
        assertThat(optionalName).isPresent();
        String name = optionalName.get();
        assertThat(name).isEqualTo(expected);
    }

    @Test
    public void getMailboxNameFromIdShouldReturnEmptyWhenMailboxDoesntExist() throws Exception {
        Optional<String> optionalName = sut.getMailboxNameFromId("unknown", mailboxSession);
        assertThat(optionalName).isEmpty();
    }

    @Test
    public void getParentIdFromMailboxPathShouldReturNullWhenRootMailbox() throws Exception {
        MailboxPath mailboxPath = new MailboxPath("#private", user, "mailbox");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        Optional<MailboxId> id = sut.getParentIdFromMailboxPath(mailboxPath, mailboxSession);
        assertThat(id).isEmpty();
    }

    @Test
    public void getParentIdFromMailboxPathShouldReturnParentIdWhenChildMailbox() throws Exception {
        MailboxPath parentMailboxPath = new MailboxPath("#private", user, "inbox");
        mailboxManager.createMailbox(parentMailboxPath, mailboxSession);
        MailboxId parentId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(parentMailboxPath)
                .getMailboxId();

        MailboxPath mailboxPath = new MailboxPath("#private", user, "inbox.mailbox");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        Optional<MailboxId> id = sut.getParentIdFromMailboxPath(mailboxPath, mailboxSession);
        assertThat(id).contains(parentId);
    }

    @Test
    public void getParentIdFromMailboxPathShouldReturnParentIdWhenChildOfChildMailbox() throws Exception {
        MailboxPath mailboxPath = new MailboxPath("#private", user, "inbox.children.mailbox");
        mailboxManager.createMailbox(new MailboxPath("#private", user, "inbox"), mailboxSession);

        MailboxPath parentMailboxPath = new MailboxPath("#private", user, "inbox.children");
        mailboxManager.createMailbox(parentMailboxPath, mailboxSession);
        MailboxId parentId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(parentMailboxPath)
                .getMailboxId();

        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        Optional<MailboxId> id = sut.getParentIdFromMailboxPath(mailboxPath, mailboxSession);
        assertThat(id).contains(parentId);
    }

    @Test
    public void mailboxFromMailboxIdShouldReturnPresentWhenExists() throws Exception {
        MailboxPath mailboxPath = new MailboxPath("#private", user, "myBox");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MailboxId mailboxId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(mailboxPath)
                .getMailboxId();

        Optional<Mailbox> mailbox = sut.mailboxFromMailboxId(mailboxId.serialize(), mailboxSession);
        assertThat(mailbox).isPresent();
        assertThat(mailbox.get().getId()).isEqualTo(mailboxId.serialize());
    }

    @Test
    public void mailboxFromMailboxIdShouldReturnAbsentWhenDoesntExist() throws Exception {
        Optional<Mailbox> mailbox = sut.mailboxFromMailboxId("123", mailboxSession);
        assertThat(mailbox).isEmpty();
    }

    @Test
    public void mailboxPathFromMailboxIdShouldReturnPresentWhenExists() throws Exception {
        MailboxPath mailboxPath = new MailboxPath("#private", user, "myBox");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MailboxId mailboxId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(mailboxPath)
                .getMailboxId();

        Optional<MailboxPath> actual = sut.mailboxPathFromMailboxId(mailboxId.serialize(), mailboxSession);
        assertThat(actual).isPresent();
        assertThat(actual.get()).isEqualTo(mailboxPath);
    }

    @Test
    public void mailboxPathFromMailboxIdShouldReturnAbsentWhenDoesntExist() throws Exception {
        Optional<MailboxPath> mailboxPath = sut.mailboxPathFromMailboxId("123", mailboxSession);
        assertThat(mailboxPath).isEmpty();
    }

    @Test(expected = IllegalStateException.class)
    public void mailboxPathFromMailboxIdShouldThrowWhenNullMailboxId() throws Exception {
        sut.mailboxPathFromMailboxId(null, mailboxSession);
    }

    @Test(expected = IllegalStateException.class)
    public void mailboxPathFromMailboxIdShouldThrowWhenNullMailboxSession() throws Exception {
        sut.mailboxPathFromMailboxId("A", null);
    }

    @Test(expected = IllegalStateException.class)
    public void mailboxPathFromMailboxIdShouldThrowWhenNullMailboxSessionAndMailboxId() throws Exception {
        sut.mailboxPathFromMailboxId(null, null);
    }

    @Test(expected = IllegalStateException.class)
    public void mailboxPathFromMailboxIdShouldReturnAbsentWhenEmptyMailboxId() throws Exception {
        sut.mailboxPathFromMailboxId("", mailboxSession);
    }

    @Test
    public void getMailboxPathShouldReturnThePathWhenRootMailbox() throws Exception {
        MailboxPath expected = new MailboxPath("#private", user, "myBox");
        mailboxManager.createMailbox(expected, mailboxSession);
        MailboxId mailboxId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(expected)
                .getMailboxId();

        Mailbox mailbox = Mailbox.builder()
                .id(mailboxId.serialize())
                .name("myBox")
                .build();

        MailboxPath mailboxPath = sut.getMailboxPath(mailbox, mailboxSession);
        assertThat(mailboxPath).isEqualTo(expected);
    }

    @Test
    public void getMailboxPathShouldReturnThePathWhenChildMailbox() throws Exception {
        MailboxPath parentMailboxPath = new MailboxPath("#private", user, "inbox");
        mailboxManager.createMailbox(parentMailboxPath, mailboxSession);
        MailboxId parentId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(parentMailboxPath)
                .getMailboxId();

        MailboxPath expected = new MailboxPath("#private", user, "inbox.myBox");
        mailboxManager.createMailbox(expected, mailboxSession);
        MailboxId mailboxId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(expected)
                .getMailboxId();

        Mailbox mailbox = Mailbox.builder()
                .id(mailboxId.serialize())
                .name("myBox")
                .parentId(parentId.serialize())
                .build();

        MailboxPath mailboxPath = sut.getMailboxPath(mailbox, mailboxSession);
        assertThat(mailboxPath).isEqualTo(expected);
    }

    @Test
    public void getMailboxPathShouldReturnThePathWhenChildOfChildMailbox() throws Exception {
        MailboxPath parentMailboxPath = new MailboxPath("#private", user, "inbox");
        mailboxManager.createMailbox(parentMailboxPath, mailboxSession);

        MailboxPath childMailboxPath = new MailboxPath("#private", user, "inbox.child");
        mailboxManager.createMailbox(childMailboxPath, mailboxSession);
        MailboxId childId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(childMailboxPath)
                .getMailboxId();

        MailboxPath expected = new MailboxPath("#private", user, "inbox.child.myBox");
        mailboxManager.createMailbox(expected, mailboxSession);
        MailboxId mailboxId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(expected)
                .getMailboxId();

        Mailbox mailbox = Mailbox.builder()
                .id(mailboxId.serialize())
                .name("myBox")
                .parentId(childId.serialize())
                .build();

        MailboxPath mailboxPath = sut.getMailboxPath(mailbox, mailboxSession);
        assertThat(mailboxPath).isEqualTo(expected);
    }

    @Test
    public void hasChildrenShouldReturnFalseWhenNoChild() throws Exception {
        MailboxPath mailboxPath = new MailboxPath("#private", user, "myBox");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);
        MailboxId mailboxId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(mailboxPath)
                .getMailboxId();

        assertThat(sut.hasChildren(mailboxId.serialize(), mailboxSession)).isFalse();
    }

    @Test
    public void hasChildrenShouldReturnTrueWhenHasAChild() throws Exception {
        MailboxPath parentMailboxPath = new MailboxPath("#private", user, "inbox");
        mailboxManager.createMailbox(parentMailboxPath, mailboxSession);
        MailboxId parentId = mailboxMapperFactory.getMailboxMapper(mailboxSession)
                .findMailboxByPath(parentMailboxPath)
                .getMailboxId();

        MailboxPath mailboxPath = new MailboxPath("#private", user, "inbox.myBox");
        mailboxManager.createMailbox(mailboxPath, mailboxSession);

        assertThat(sut.hasChildren(parentId.serialize(), mailboxSession)).isTrue();
    }
}
