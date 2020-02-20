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

import static org.apache.james.mailbox.model.MailboxAssertingTool.assertThat;
import static org.apache.james.mailbox.store.mail.model.ListMailboxAssert.assertMailboxes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.ExactName;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedWildcard;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Generic purpose tests for your implementation MailboxMapper.
 * 
 * You then just need to instantiate your mailbox mapper and an IdGenerator.
 */
public abstract class MailboxMapperTest {

    private static final char DELIMITER = '.';
    private static final long UID_VALIDITY = 42;
    private static final Username BENWA = Username.of("benwa");
    private static final MailboxPath benwaInboxPath = MailboxPath.forUser(BENWA, "INBOX");
    private static final MailboxPath benwaWorkPath = MailboxPath.forUser(BENWA, "INBOX" + DELIMITER + "work");
    private static final MailboxPath benwaWorkTodoPath = MailboxPath.forUser(BENWA, "INBOX" + DELIMITER + "work" + DELIMITER + "todo");
    private static final MailboxPath benwaPersoPath = MailboxPath.forUser(BENWA, "INBOX" + DELIMITER + "perso");
    private static final MailboxPath benwaWorkDonePath = MailboxPath.forUser(BENWA, "INBOX" + DELIMITER + "work" + DELIMITER + "done");
    private static final MailboxPath bobInboxPath = MailboxPath.forUser(Username.of("bob"), "INBOX");
    private static final MailboxPath bobyMailboxPath = MailboxPath.forUser(Username.of("boby"), "INBOX.that.is.a.trick");
    private static final MailboxPath bobDifferentNamespacePath = new MailboxPath("#private_bob", Username.of("bob"), "INBOX.bob");

    private Mailbox benwaInboxMailbox;
    private Mailbox benwaWorkMailbox;
    private Mailbox benwaWorkTodoMailbox;
    private Mailbox benwaPersoMailbox;
    private Mailbox benwaWorkDoneMailbox;
    private Mailbox bobyMailbox;
    private Mailbox bobInboxMailbox;
    private Mailbox bobDifferentNamespaceMailbox;

    private MailboxMapper mailboxMapper;

    protected abstract MailboxMapper createMailboxMapper();

    protected abstract MailboxId generateId();

    @BeforeEach
    void setUp() {
        this.mailboxMapper = createMailboxMapper();
    }

    @Test
    void findMailboxByPathWhenAbsentShouldFail() {
        assertThatThrownBy(() -> mailboxMapper.findMailboxByPath(MailboxPath.forUser(BENWA, "INBOX")))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void createShouldPersistTheMailbox() throws MailboxException {
        benwaInboxMailbox = createMailbox(benwaInboxPath);

        assertThat(mailboxMapper.findMailboxByPath(benwaInboxPath)).isEqualTo(benwaInboxMailbox);
        assertThat(mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId())).isEqualTo(benwaInboxMailbox);
    }

    @Test
    void createShouldThrowWhenMailboxAlreadyExists() throws MailboxException {
        benwaInboxMailbox = createMailbox(benwaInboxPath);

        assertThatThrownBy(() -> createMailbox(benwaInboxPath))
            .isInstanceOf(MailboxExistsException.class);
    }

    @Test
    void createShouldSetAMailboxIdForMailbox() throws MailboxException {
        benwaInboxMailbox = createMailbox(benwaInboxPath);

        assertThat(benwaInboxMailbox.getMailboxId()).isNotNull();
    }

    @Test
    void renameShouldThrowWhenMailboxIdIsNull() {
        assertThatThrownBy(() -> mailboxMapper.rename(benwaInboxMailbox))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void renameShouldRenameTheMailbox() throws MailboxException {
        benwaInboxMailbox = createMailbox(benwaInboxPath);
        MailboxId mailboxId = benwaInboxMailbox.getMailboxId();

        benwaWorkMailbox = new Mailbox(benwaWorkPath, UID_VALIDITY, mailboxId);
        mailboxMapper.rename(benwaWorkMailbox);

        assertThat(mailboxMapper.findMailboxById(mailboxId)).isEqualTo(benwaWorkMailbox);
    }

    @Test
    void renameShouldThrowWhenMailboxAlreadyExist() throws MailboxException {
        benwaInboxMailbox = createMailbox(benwaInboxPath);

        assertThatThrownBy(() -> mailboxMapper.rename(benwaInboxMailbox))
            .isInstanceOf(MailboxExistsException.class);
    }

    @Test
    void renameShouldThrowWhenMailboxDoesNotExist() {
        benwaInboxMailbox = new Mailbox(benwaInboxPath, UID_VALIDITY, generateId());

        assertThatThrownBy(() -> mailboxMapper.rename(benwaInboxMailbox))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void renameShouldRemoveOldMailboxPath() throws MailboxException {
        MailboxId mailboxId = createMailbox(benwaInboxPath).getMailboxId();

        benwaWorkMailbox = new Mailbox(benwaWorkPath, UID_VALIDITY, mailboxId);
        mailboxMapper.rename(benwaWorkMailbox);

        assertThatThrownBy(() -> mailboxMapper.findMailboxByPath(benwaInboxPath))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void listShouldRetrieveAllMailbox() throws MailboxException {
        createAll();
        List<Mailbox> mailboxes = mailboxMapper.list();

        assertMailboxes(mailboxes)
            .containOnly(benwaInboxMailbox, benwaWorkMailbox, benwaWorkTodoMailbox, benwaPersoMailbox, benwaWorkDoneMailbox, 
                bobyMailbox, bobDifferentNamespaceMailbox, bobInboxMailbox);
    }
    
    @Test
    void hasChildrenShouldReturnFalseWhenNoChildrenExists() throws MailboxException {
        createAll();
        assertThat(mailboxMapper.hasChildren(benwaWorkTodoMailbox, DELIMITER)).isFalse();
    }

    @Test
    void hasChildrenShouldReturnTrueWhenChildrenExists() throws MailboxException {
        createAll();
        assertThat(mailboxMapper.hasChildren(benwaInboxMailbox, DELIMITER)).isTrue();
    }

    @Test
    void hasChildrenShouldNotBeAcrossUsersAndNamespace() throws MailboxException {
        createAll();
        assertThat(mailboxMapper.hasChildren(bobInboxMailbox, '.')).isFalse();
    }

    @Test
    void findMailboxWithPathLikeShouldBeLimitedToUserAndNamespace() throws MailboxException {
        createAll();
        MailboxQuery.UserBound mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(bobInboxPath)
            .expression(new PrefixedWildcard("IN"))
            .build()
            .asUserBound();

        List<Mailbox> mailboxes = mailboxMapper.findMailboxWithPathLike(mailboxQuery);

        assertMailboxes(mailboxes).containOnly(bobInboxMailbox);
    }
    
    @Test
    void deleteShouldEraseTheGivenMailbox() throws MailboxException {
        createAll();
        mailboxMapper.delete(benwaInboxMailbox);

        assertThatThrownBy(() -> mailboxMapper.findMailboxByPath(benwaInboxPath))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void findMailboxWithPathLikeWithChildRegexShouldRetrieveChildren() throws MailboxException {
        createAll();
        MailboxQuery.UserBound mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(benwaWorkPath)
            .expression(new PrefixedWildcard(benwaWorkPath.getName()))
            .build()
            .asUserBound();

        List<Mailbox> mailboxes = mailboxMapper.findMailboxWithPathLike(mailboxQuery);

        assertMailboxes(mailboxes).containOnly(benwaWorkMailbox, benwaWorkDoneMailbox, benwaWorkTodoMailbox);
    }

    @Test
    void findMailboxWithPathLikeWithRegexShouldRetrieveCorrespondingMailbox() throws MailboxException {
        createAll();
        MailboxQuery.UserBound mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(benwaWorkPath)
            .expression(new ExactName("INBOX"))
            .build()
            .asUserBound();

        List<Mailbox> mailboxes = mailboxMapper.findMailboxWithPathLike(mailboxQuery);

        assertMailboxes(mailboxes).containOnly(benwaInboxMailbox);
    }

    @Test
    void findMailboxWithPathLikeShouldEscapeMailboxName() throws MailboxException {
        createAll();
        MailboxQuery.UserBound mailboxQuery = MailboxQuery.builder()
            .userAndNamespaceFrom(benwaInboxPath)
            .expression(new ExactName("INB?X"))
            .build()
            .asUserBound();

        assertThat(mailboxMapper.findMailboxWithPathLike(mailboxQuery)).isEmpty();
    }

    @Test
    void findMailboxByIdShouldReturnExistingMailbox() throws MailboxException {
        createAll();
        Mailbox actual = mailboxMapper.findMailboxById(benwaInboxMailbox.getMailboxId());
        assertThat(actual).isEqualTo(benwaInboxMailbox);
    }
    
    @Test
    void findMailboxByIdShouldFailWhenAbsent() throws MailboxException {
        createAll();
        MailboxId removed = benwaInboxMailbox.getMailboxId();
        mailboxMapper.delete(benwaInboxMailbox);
        assertThatThrownBy(() -> mailboxMapper.findMailboxById(removed))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    private void createAll() throws MailboxException {
        benwaInboxMailbox = createMailbox(benwaInboxPath);
        benwaWorkMailbox = createMailbox(benwaWorkPath);
        benwaWorkTodoMailbox = createMailbox(benwaWorkTodoPath);
        benwaPersoMailbox = createMailbox(benwaPersoPath);
        benwaWorkDoneMailbox = createMailbox(benwaWorkDonePath);
        bobInboxMailbox = createMailbox(bobInboxPath);
        bobyMailbox = createMailbox(bobyMailboxPath);
        bobDifferentNamespaceMailbox = createMailbox(bobDifferentNamespacePath);
    }

    private Mailbox createMailbox(MailboxPath mailboxPath) throws MailboxException {
        return mailboxMapper.create(mailboxPath, UID_VALIDITY);
    }

}
