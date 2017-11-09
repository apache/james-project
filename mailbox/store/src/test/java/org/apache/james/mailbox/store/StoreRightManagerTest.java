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

import javax.mail.Flags;

import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.DifferentDomainException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.fixture.MailboxFixture;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.ACLCommand;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Test;

public class StoreRightManagerTest {

    private static final long UID_VALIDITY = 3421L;
    private StoreRightManager storeRightManager;
    private MockMailboxSession aliceSession;

    private MailboxMapper mockedMailboxMapper;

    @Before
    public void setup() throws MailboxException {
        aliceSession = new MockMailboxSession(MailboxFixture.ALICE);
        MailboxSessionMapperFactory mockedMapperFactory = mock(MailboxSessionMapperFactory.class);
        mockedMailboxMapper = mock(MailboxMapper.class);
        when(mockedMapperFactory.getMailboxMapper(aliceSession))
            .thenReturn(mockedMailboxMapper);

        storeRightManager = new StoreRightManager(mockedMapperFactory,
            new UnionMailboxACLResolver(),
            new SimpleGroupMembershipResolver());
    }

    @Test
    public void hasRightShouldThrowMailboxNotFoundExceptionWhenMailboxDoesNotExist() throws MailboxException {
        MailboxPath mailboxPath = MailboxPath.forUser(MailboxFixture.ALICE, "unexisting mailbox");
        when(mockedMailboxMapper.findMailboxByPath(mailboxPath))
            .thenThrow(new MailboxNotFoundException(""));

        assertThatThrownBy(() ->
            storeRightManager.hasRight(mailboxPath, Right.Read, aliceSession))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    public void hasRightShouldReturnTrueWhenTheUserOwnTheMailbox() throws MailboxException {
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(ALICE, MailboxConstants.INBOX), UID_VALIDITY);

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isTrue();
    }

    @Test
    public void hasRightShouldReturnTrueWhenTheUserDoesnotOwnTheMailboxButHaveTheCorrectRightOnIt() throws MailboxException {
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE, Right.Write)));

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isTrue();
    }

    @Test
    public void hasRightShouldReturnTrueWhenTheUserDoesnotOwnTheMailboxButHasAtLeastTheCorrectRightOnIt() throws MailboxException {
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE, Right.Write, Right.Lookup)));

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isTrue();
    }

    @Test
    public void hasRightShouldReturnFalseWhenTheUserDoesNotOwnTheMailboxAndHasNoRightOnIt() throws MailboxException {
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY);

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isFalse();
    }

    @Test
    public void isReadWriteShouldReturnTrueWhenUserHasInsertRightOnMailbox() throws Exception {
        Flags flags = new Flags();
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE, Right.Insert)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    public void isReadWriteShouldReturnTrueWhenUserHasPerformExpungeRightOnMailbox() throws Exception {
        Flags flags = new Flags();
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE, Right.PerformExpunge)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    public void isReadWriteShouldReturnTrueWhenUserHasDeleteMessagesRightOnMailboxAndFlagsContainDeletedFlag() throws Exception {
        Flags flags = new Flags(Flags.Flag.DELETED);
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE, Right.DeleteMessages)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    public void isReadWriteShouldReturnFalseWhenUserHasDeleteMessagesRightOnMailboxButFlagsDoesNotContainDeletedFlag() throws Exception {
        Flags flags = new Flags();
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE, Right.DeleteMessages)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isFalse();
    }

    @Test
    public void isReadWriteShouldReturnTrueWhenUserHasWriteSeenFlagRightOnMailboxAndFlagsContainSeenFlag() throws Exception {
        Flags flags = new Flags(Flags.Flag.SEEN);
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE, Right.WriteSeenFlag)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    public void isReadWriteShouldReturnFalseWhenUserHasWriteSeenFlagRightOnMailboxAndFlagsDoesNotContainSeenFlag() throws Exception {
        Flags flags = new Flags();
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE, Right.WriteSeenFlag)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isFalse();
    }

    @Test
    public void isReadWriteShouldReturnTrueWhenUserHasWriteRightOnMailboxAndFlagsContainAnsweredFlag() throws Exception {
        Flags flags = new Flags(Flags.Flag.ANSWERED);
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE, Right.Write)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    public void isReadWriteShouldReturnFalseWhenUserDoesNotHaveInsertOrPerformExpungeRightOnMailboxAndNullFlag() throws Exception {
        Mailbox mailbox = new SimpleMailbox(MailboxPath.forUser(BOB, MailboxConstants.INBOX), UID_VALIDITY);
        mailbox.setACL(new MailboxACL(new MailboxACL.Entry(MailboxFixture.ALICE, Right.Administer)));

        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, new Flags()))
            .isFalse();
    }

    @Test
    public void filteredForSessionShouldBeIdentityWhenOwner() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(BOB).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(CEDRIC).asAddition());
        MailboxACL actual = StoreRightManager.filteredForSession(
            new SimpleMailbox(INBOX_ALICE, UID_VALIDITY), acl, aliceSession);
        assertThat(actual).isEqualTo(acl);
    }

    @Test
    public void filteredForSessionShouldBeIdentityWhenAdmin() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(BOB).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(CEDRIC).asAddition());
        MailboxACL actual = StoreRightManager.filteredForSession(
            new SimpleMailbox(INBOX_ALICE, UID_VALIDITY), acl, new MockMailboxSession(CEDRIC));
        assertThat(actual).isEqualTo(acl);
    }

    @Test
    public void filteredForSessionShouldContainOnlyLoggedUserWhenReadWriteAccess() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(BOB).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(CEDRIC).asAddition());
        MailboxACL actual = StoreRightManager.filteredForSession(
            new SimpleMailbox(INBOX_ALICE, UID_VALIDITY), acl, new MockMailboxSession(BOB));
        assertThat(actual.getEntries()).containsKey(MailboxACL.EntryKey.createUserEntryKey(BOB));
    }

    @Test
    public void areDomainsDifferentShouldReturnTrueWhenOneHasDomainNotTheOther() {
        assertThat(storeRightManager.areDomainsDifferent("user@domain.org", "otherUser")).isTrue();
    }

    @Test
    public void areDomainsDifferentShouldReturnTrueWhenOtherHasDomainNotTheOne() {
        assertThat(storeRightManager.areDomainsDifferent("user", "otherUser@domain.org")).isTrue();
    }

    @Test
    public void areDomainsDifferentShouldReturnFalseWhenNoDomain() {
        assertThat(storeRightManager.areDomainsDifferent("user", "otherUser")).isFalse();
    }

    @Test
    public void areDomainsDifferentShouldReturnTrueWhenDomainsAreDifferent() {
        assertThat(storeRightManager.areDomainsDifferent("user@domain.org", "otherUser@otherdomain.org")).isTrue();
    }

    @Test
    public void areDomainsDifferentShouldReturnFalseWhenDomainsAreIdentical() {
        assertThat(storeRightManager.areDomainsDifferent("user@domain.org", "otherUser@domain.org")).isFalse();
    }

    @Test
    public void assertSharesBelongsToUserDomainShouldThrowWhenOneDomainIsDifferent() throws Exception  {
        MailboxACL mailboxACL = new MailboxACL(new MailboxACL.Entry("a@domain.org", Right.Write), 
                new MailboxACL.Entry("b@otherdomain.org", Right.Write), 
                new MailboxACL.Entry("c@domain.org", Right.Write));
        
        assertThatThrownBy(() -> storeRightManager.assertSharesBelongsToUserDomain("user@domain.org", mailboxACL.getEntries()))
            .isInstanceOf(DifferentDomainException.class);
    }

    @Test
    public void assertSharesBelongsToUserDomainShouldNotThrowWhenDomainsAreIdentical() throws Exception  {
        MailboxACL mailboxACL = new MailboxACL(new MailboxACL.Entry("a@domain.org", Right.Write), 
                new MailboxACL.Entry("b@domain.org", Right.Write), 
                new MailboxACL.Entry("c@domain.org", Right.Write));
        
        storeRightManager.assertSharesBelongsToUserDomain("user@domain.org", mailboxACL.getEntries());
    }

    @Test
    public void applyRightsCommandShouldThrowWhenDomainsAreDifferent() {
        MailboxPath mailboxPath = MailboxPath.forUser("user@domain.org", "mailbox");
        ACLCommand aclCommand = MailboxACL.command()
            .forUser("otherUser@otherdomain.org")
            .rights(MailboxACL.FULL_RIGHTS)
            .asAddition();
       
        assertThatThrownBy(() -> storeRightManager.applyRightsCommand(mailboxPath, aclCommand, aliceSession))
            .isInstanceOf(DifferentDomainException.class);
    }
}