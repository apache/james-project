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

package org.apache.james.mailbox.store;

import static org.apache.james.mailbox.fixture.MailboxFixture.ALICE;
import static org.apache.james.mailbox.fixture.MailboxFixture.BOB;
import static org.apache.james.mailbox.fixture.MailboxFixture.CEDRIC;
import static org.apache.james.mailbox.fixture.MailboxFixture.INBOX_ALICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.DifferentDomainException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.fixture.MailboxFixture;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.ACLCommand;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class StoreRightManagerTest {

    static final MailboxId MAILBOX_ID = TestId.of(42);
    static final UidValidity UID_VALIDITY = UidValidity.of(3421L);

    StoreRightManager storeRightManager;
    MailboxSession aliceSession;
    MailboxACLResolver mailboxAclResolver;
    MailboxMapper mockedMailboxMapper;

    @BeforeEach
    void setup() {
        aliceSession = MailboxSessionUtil.create(MailboxFixture.ALICE);
        MailboxSessionMapperFactory mockedMapperFactory = mock(MailboxSessionMapperFactory.class);
        mockedMailboxMapper = mock(MailboxMapper.class);
        mailboxAclResolver = new UnionMailboxACLResolver();
        EventBus eventBus = mock(EventBus.class);
        when(mockedMapperFactory.getMailboxMapper(aliceSession))
            .thenReturn(mockedMailboxMapper);

        storeRightManager = new StoreRightManager(mockedMapperFactory, mailboxAclResolver, eventBus);
    }

    @Test
    void hasRightShouldThrowMailboxNotFoundExceptionWhenMailboxDoesNotExist() {
        MailboxPath mailboxPath = MailboxPath.forUser(MailboxFixture.ALICE, "unexisting mailbox");
        when(mockedMailboxMapper.findMailboxByPath(mailboxPath))
            .thenReturn(Mono.empty());

        assertThatThrownBy(() ->
            storeRightManager.hasRight(mailboxPath, Right.Read, aliceSession))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void hasRightShouldReturnTrueWhenTheUserOwnTheMailbox() {
        Mailbox mailbox = new Mailbox(MailboxPath.forUser(ALICE, MailboxConstants.INBOX), UID_VALIDITY, MAILBOX_ID);

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isTrue();
    }

    @Test
    void hasRightShouldReturnTrueWhenTheUserDoesNotOwnTheMailboxButHaveTheCorrectRightOnIt() throws MailboxException {
        Mailbox mailbox = new Mailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY, MAILBOX_ID);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE.asString(), Right.Write)));

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isTrue();
    }

    @Test
    void hasRightShouldReturnTrueWhenTheUserDoesNotOwnTheMailboxButHasAtLeastTheCorrectRightOnIt() throws MailboxException {
        Mailbox mailbox = new Mailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY, MAILBOX_ID);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE.asString(), Right.Write, Right.Lookup)));

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isTrue();
    }

    @Test
    void hasRightShouldReturnFalseWhenTheUserDoesNotOwnTheMailboxAndHasNoRightOnIt() {
        Mailbox mailbox = new Mailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY, MAILBOX_ID);

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isFalse();
    }

    @Test
    void isReadWriteShouldReturnTrueWhenUserHasInsertRightOnMailbox() throws Exception {
        Flags flags = new Flags();
        Mailbox mailbox = new Mailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY, MAILBOX_ID);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE.asString(), Right.Insert)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    void isReadWriteShouldReturnTrueWhenUserHasPerformExpungeRightOnMailbox() throws Exception {
        Flags flags = new Flags();
        Mailbox mailbox = new Mailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY, MAILBOX_ID);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE.asString(), Right.PerformExpunge)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    void isReadWriteShouldReturnTrueWhenUserHasDeleteMessagesRightOnMailboxAndFlagsContainDeletedFlag() throws Exception {
        Flags flags = new Flags(Flags.Flag.DELETED);
        Mailbox mailbox = new Mailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY, MAILBOX_ID);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE.asString(), Right.DeleteMessages)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    void isReadWriteShouldReturnFalseWhenUserHasDeleteMessagesRightOnMailboxButFlagsDoesNotContainDeletedFlag() throws Exception {
        Flags flags = new Flags();
        Mailbox mailbox = new Mailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY, MAILBOX_ID);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE.asString(), Right.DeleteMessages)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isFalse();
    }

    @Test
    void isReadWriteShouldReturnTrueWhenUserHasWriteSeenFlagRightOnMailboxAndFlagsContainSeenFlag() throws Exception {
        Flags flags = new Flags(Flags.Flag.SEEN);
        Mailbox mailbox = new Mailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY, MAILBOX_ID);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE.asString(), Right.WriteSeenFlag)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    void isReadWriteShouldReturnFalseWhenUserHasWriteSeenFlagRightOnMailboxAndFlagsDoesNotContainSeenFlag() throws Exception {
        Flags flags = new Flags();
        Mailbox mailbox = new Mailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY, MAILBOX_ID);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE.asString(), Right.WriteSeenFlag)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isFalse();
    }

    @Test
    void isReadWriteShouldReturnTrueWhenUserHasWriteRightOnMailboxAndFlagsContainAnsweredFlag() throws Exception {
        Flags flags = new Flags(Flags.Flag.ANSWERED);
        Mailbox mailbox = new Mailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY, MAILBOX_ID);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE.asString(), Right.Write)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    void isReadWriteShouldReturnFalseWhenUserDoesNotHaveInsertOrPerformExpungeRightOnMailboxAndNullFlag() throws Exception {
        Mailbox mailbox = new Mailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY, MAILBOX_ID);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE.asString(), Right.Administer)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, new Flags()))
            .isFalse();
    }

    @Test
    void filteredForSessionShouldBeIdentityWhenOwner() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(BOB).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(CEDRIC).asAddition());
        MailboxACL actual = StoreRightManager.filteredForSession(
            new Mailbox(INBOX_ALICE, UID_VALIDITY, MAILBOX_ID), acl, aliceSession);
        assertThat(actual).isEqualTo(acl);
    }

    @Test
    void filteredForSessionShouldBeIdentityWhenAdmin() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(BOB).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(CEDRIC).asAddition());
        MailboxACL actual = StoreRightManager.filteredForSession(
            new Mailbox(INBOX_ALICE, UID_VALIDITY, MAILBOX_ID), acl, MailboxSessionUtil.create(CEDRIC));
        assertThat(actual).isEqualTo(acl);
    }

    @Test
    void filteredForSessionShouldContainOnlyLoggedUserWhenReadWriteAccess() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(BOB).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(CEDRIC).asAddition());
        MailboxACL actual = StoreRightManager.filteredForSession(
            new Mailbox(INBOX_ALICE, UID_VALIDITY, MAILBOX_ID), acl, MailboxSessionUtil.create(BOB));
        assertThat(actual.getEntries()).containsKey(MailboxACL.EntryKey.createUserEntryKey(BOB));
    }

    @Test
    void areDomainsDifferentShouldReturnTrueWhenOneHasDomainNotTheOther() {
        assertThat(storeRightManager.areDomainsDifferent("user@domain.org", Username.of("otherUser"))).isTrue();
    }

    @Test
    void areDomainsDifferentShouldReturnTrueWhenOtherHasDomainNotTheOne() {
        assertThat(storeRightManager.areDomainsDifferent("user", Username.of("otherUser@domain.org"))).isTrue();
    }

    @Test
    void areDomainsDifferentShouldReturnFalseWhenNoDomain() {
        assertThat(storeRightManager.areDomainsDifferent("user", Username.of("otherUser"))).isFalse();
    }

    @Test
    void areDomainsDifferentShouldReturnTrueWhenDomainsAreDifferent() {
        assertThat(storeRightManager.areDomainsDifferent("user@domain.org", Username.of("otherUser@otherdomain.org"))).isTrue();
    }

    @Test
    void areDomainsDifferentShouldReturnFalseWhenDomainsAreIdentical() {
        assertThat(storeRightManager.areDomainsDifferent("user@domain.org", Username.of("otherUser@domain.org"))).isFalse();
    }

    @Test
    void assertSharesBelongsToUserDomainShouldThrowWhenOneDomainIsDifferent() throws Exception  {
        MailboxACL mailboxACL = new MailboxACL(new MailboxACL.Entry("a@domain.org", Right.Write), 
                new MailboxACL.Entry("b@otherdomain.org", Right.Write), 
                new MailboxACL.Entry("c@domain.org", Right.Write));
        
        assertThatThrownBy(() -> storeRightManager.assertSharesBelongsToUserDomain(Username.of("user@domain.org"), mailboxACL.getEntries()))
            .isInstanceOf(DifferentDomainException.class);
    }

    @Test
    void assertSharesBelongsToUserDomainShouldNotThrowWhenDomainsAreIdentical() throws Exception  {
        MailboxACL mailboxACL = new MailboxACL(new MailboxACL.Entry("a@domain.org", Right.Write), 
                new MailboxACL.Entry("b@domain.org", Right.Write), 
                new MailboxACL.Entry("c@domain.org", Right.Write));
        
        storeRightManager.assertSharesBelongsToUserDomain(Username.of("user@domain.org"), mailboxACL.getEntries());
    }

    @Test
    void applyRightsCommandShouldThrowWhenDomainsAreDifferent() {
        MailboxPath mailboxPath = MailboxPath.forUser(Username.of("user@domain.org"), "mailbox");
        ACLCommand aclCommand = MailboxACL.command()
            .forUser(Username.of("otherUser@otherdomain.org"))
            .rights(MailboxACL.FULL_RIGHTS)
            .asAddition();
       
        assertThatThrownBy(() -> storeRightManager.applyRightsCommand(mailboxPath, aclCommand, aliceSession))
            .isInstanceOf(DifferentDomainException.class);
    }
}