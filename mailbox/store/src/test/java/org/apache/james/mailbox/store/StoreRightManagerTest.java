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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.mail.Flags;

import com.google.common.collect.ImmutableMap;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class StoreRightManagerTest {

    public static final long UID_VALIDITY = 3421l;
    private StoreRightManager storeRightManager;
    private MailboxACLResolver mailboxAclResolver;
    private GroupMembershipResolver groupMembershipResolver;
    private String alice;
    private MockMailboxSession aliceSession;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private MailboxMapper mockedMailboxMapper;

    @Before
    public void setup() throws MailboxException {
        alice = "Alice";
        aliceSession = new MockMailboxSession(alice);
        MailboxSessionMapperFactory mockedMapperFactory = mock(MailboxSessionMapperFactory.class);
        mockedMailboxMapper = mock(MailboxMapper.class);
        mailboxAclResolver = new UnionMailboxACLResolver();
        groupMembershipResolver = new SimpleGroupMembershipResolver();
        when(mockedMapperFactory.getMailboxMapper(aliceSession))
            .thenReturn(mockedMailboxMapper);
        storeRightManager = new StoreRightManager(mockedMapperFactory,
                                                  mailboxAclResolver,
                                                  groupMembershipResolver);
    }

    @Test
    public void hasRightShouldThrowMailboxNotFoundExceptionWhenMailboxDoesNotExist() throws MailboxException {
        expectedException.expect(MailboxNotFoundException.class);

        MailboxPath mailboxPath = MailboxPath.forUser(alice, "unexisting mailbox");
        when(mockedMailboxMapper.findMailboxByPath(mailboxPath)).thenThrow(MailboxNotFoundException.class);
        storeRightManager.hasRight(mailboxPath, Right.Read, aliceSession);
    }

    @Test
    public void hasRightShouldReturnTrueWhenTheUserOwnTheMailbox() throws MailboxException {
        Mailbox mailbox = mock(Mailbox.class);
        when(mailbox.getUser()).thenReturn(alice);

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isFalse();
    }

    @Test
    public void hasRightShouldReturnTrueWhenTheUserDoesnotOwnTheMailboxButHaveTheCorrectRightOnIt() throws MailboxException {
        Mailbox mailbox = mock(Mailbox.class);
        when(mailbox.getUser()).thenReturn("bob");
        when(mailbox.getACL()).thenReturn(new MailboxACL(new MailboxACL.Entry(alice, Right.Write)));

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isTrue();
    }

    @Test
    public void hasRightShouldReturnTrueWhenTheUserDoesnotOwnTheMailboxButHasAtLeastTheCorrectRightOnIt() throws MailboxException {
        Mailbox mailbox = mock(Mailbox.class);
        when(mailbox.getUser()).thenReturn("bob");
        when(mailbox.getACL()).thenReturn(new MailboxACL(new MailboxACL.Entry(alice, Right.Write, Right.Lookup)));

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isTrue();
    }

    @Test
    public void hasRightShouldReturnFalseWhenTheUserDoesNotOwnTheMailboxAndHasNoRightOnIt() throws MailboxException {
        Mailbox mailbox = mock(Mailbox.class);
        when(mailbox.getUser()).thenReturn("bob");

        assertThat(storeRightManager.hasRight(mailbox, Right.Write, aliceSession))
            .isFalse();
    }

    @Test
    public void isReadWriteShouldReturnTrueWhenUserHasInsertRightOnMailbox() throws Exception {
        Mailbox mailbox = mock(Mailbox.class);
        Flags flags = new Flags();
        when(mailbox.getACL()).thenReturn(new MailboxACL(new MailboxACL.Entry(alice, Right.Insert)));
        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    public void isReadWriteShouldReturnTrueWhenUserHasPerformExpungeRightOnMailbox() throws Exception {
        Mailbox mailbox = mock(Mailbox.class);
        Flags flags = new Flags();
        when(mailbox.getACL()).thenReturn(new MailboxACL(new MailboxACL.Entry(alice, Right.PerformExpunge)));
        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    public void isReadWriteShouldReturnTrueWhenUserHasDeleteMessagesRightOnMailboxAndFlagsContainDeletedFlag() throws Exception {
        Mailbox mailbox = mock(Mailbox.class);
        Flags flags = new Flags(Flags.Flag.DELETED);
        when(mailbox.getACL()).thenReturn(new MailboxACL(new MailboxACL.Entry(alice, Right.DeleteMessages)));
        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    public void isReadWriteShouldReturnFalseWhenUserHasDeleteMessagesRightOnMailboxButFlagsDoesNotContainDeletedFlag() throws Exception {
        Mailbox mailbox = mock(Mailbox.class);
        Flags flags = new Flags();
        when(mailbox.getACL()).thenReturn(new MailboxACL(new MailboxACL.Entry(alice, Right.DeleteMessages)));
        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isFalse();
    }

    @Test
    public void isReadWriteShouldReturnTrueWhenUserHasWriteSeenFlagRightOnMailboxAndFlagsContainSeenFlag() throws Exception {
        Mailbox mailbox = mock(Mailbox.class);
        Flags flags = new Flags(Flags.Flag.SEEN);
        when(mailbox.getACL()).thenReturn(new MailboxACL(new MailboxACL.Entry(alice, Right.WriteSeenFlag)));
        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    public void isReadWriteShouldReturnFalseWhenUserHasWriteSeenFlagRightOnMailboxAndFlagsDoesNotContainSeenFlag() throws Exception {
        Mailbox mailbox = mock(Mailbox.class);
        Flags flags = new Flags();
        when(mailbox.getACL()).thenReturn(new MailboxACL(new MailboxACL.Entry(alice, Right.WriteSeenFlag)));
        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isFalse();
    }

    @Test
    public void isReadWriteShouldReturnTrueWhenUserHasWriteRightOnMailboxAndFlagsContainAnsweredFlag() throws Exception {
        Mailbox mailbox = mock(Mailbox.class);
        Flags flags = new Flags(Flags.Flag.ANSWERED);
        when(mailbox.getACL()).thenReturn(new MailboxACL(new MailboxACL.Entry(alice, Right.Write)));
        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, flags))
            .isTrue();
    }

    @Test
    public void isReadWriteShouldReturnFalseWhenUserDoesNotHaveInsertOrPerformExpungeRightOnMailboxAndNullFlag() throws Exception {
        Mailbox mailbox = mock(Mailbox.class);
        when(mailbox.getACL()).thenReturn(new MailboxACL(new MailboxACL.Entry(alice, Right.Administer)));
        assertThat(storeRightManager.isReadWrite(aliceSession, mailbox, null))
            .isFalse();
    }

    @Test
    public void filteredForSessionShouldBeIdentityWhenOwner() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(BOB).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(CEDRIC).asAddition());
        MailboxACL actual = StoreRightManager.filteredForSession(
            new SimpleMailbox(INBOX_ALICE, UID_VALIDITY), acl, new MockMailboxSession(ALICE));
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
}