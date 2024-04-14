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

package org.apache.james.jmap.event;

import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.IGNORE;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.junit.Before;
import org.junit.Test;

public class PropagateLookupRightListenerTest {
    private static final Username OWNER_USER = Username.of("user");
    private static final Username SHARED_USER = Username.of("sharee");
    private static final EntryKey SHARED_USER_KEY = EntryKey.createUserEntryKey(SHARED_USER);

    private static final MailboxPath PARENT_MAILBOX = MailboxPath.forUser(OWNER_USER, "shared");
    private static final MailboxPath CHILD_MAILBOX = MailboxPath.forUser(OWNER_USER, "shared.sub1");

    private static final MailboxPath PARENT_MAILBOX1 = MailboxPath.forUser(OWNER_USER, "shared1");
    private static final MailboxPath CHILD_MAILBOX1 = MailboxPath.forUser(OWNER_USER, "shared1.sub1");

    private static final MailboxPath GRAND_CHILD_MAILBOX = MailboxPath.forUser(OWNER_USER, "shared.sub1.sub2");

    private StoreRightManager storeRightManager;
    private StoreMailboxManager storeMailboxManager;
    private PropagateLookupRightListener testee;

    private MailboxSession mailboxSession = MailboxSessionUtil.create(OWNER_USER);

    private MailboxId parentMailboxId;
    private MailboxId parentMailboxId1;
    private MailboxId childMailboxId;
    private MailboxId childMailboxId1;
    private MailboxId grandChildMailboxId;
    private Entry lookupEntry;

    private MailboxSessionMapperFactory mailboxMapper;

    @Before
    public void setup() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        storeMailboxManager = resources.getMailboxManager();
        storeRightManager = resources.getStoreRightManager();
        mailboxMapper = storeMailboxManager.getMapperFactory();

        testee = new PropagateLookupRightListener(storeRightManager, storeMailboxManager);
        storeMailboxManager.getEventBus().register(testee);

        parentMailboxId = storeMailboxManager.createMailbox(PARENT_MAILBOX, mailboxSession).get();
        parentMailboxId1 = storeMailboxManager.createMailbox(PARENT_MAILBOX1, mailboxSession).get();
        childMailboxId = storeMailboxManager.createMailbox(CHILD_MAILBOX, mailboxSession).get();
        childMailboxId1 = storeMailboxManager.createMailbox(CHILD_MAILBOX1, mailboxSession).get();
        grandChildMailboxId = storeMailboxManager.createMailbox(GRAND_CHILD_MAILBOX, mailboxSession).get();

        lookupEntry = new Entry(SHARED_USER.asString(), "l");
    }

    @Test
    public void deserializePropagateLookupRightListenerGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.jmap.event.PropagateLookupRightListener$PropagateLookupRightListenerGroup"))
            .isEqualTo(new PropagateLookupRightListener.PropagateLookupRightListenerGroup());
    }

    @Test
    public void getExecutionModeShouldReturnAsynchronous() throws Exception {
        assertThat(testee.getExecutionMode()).isEqualTo(EventListener.ExecutionMode.SYNCHRONOUS);
    }

    @Test
    public void eventShouldDoNothingWhenEmptyNewRights() throws Exception {
        storeRightManager.applyRightsCommand(
            GRAND_CHILD_MAILBOX,
            MailboxACL.command()
                .key(SHARED_USER_KEY)
                .rights()
                .asAddition(),
            mailboxSession);

        MailboxACL actualACL = storeMailboxManager.getMailbox(parentMailboxId, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();

        assertThat(actualACL.getEntries())
            .doesNotContainKeys(SHARED_USER_KEY);
    }

    @Test
    public void eventShouldDoNothingWhenNewACLIsTheSameAsTheOldOne() throws Exception {
        Mailbox grandChildMailbox = mailboxMapper.getMailboxMapper(mailboxSession).findMailboxById(grandChildMailboxId).block();
        mailboxMapper.getMailboxMapper(mailboxSession).setACL(grandChildMailbox, new MailboxACL(
            new Entry(SHARED_USER_KEY, new Rfc4314Rights(Right.Lookup)))).block();

        storeRightManager.applyRightsCommand(
            GRAND_CHILD_MAILBOX,
            MailboxACL.command()
                .key(SHARED_USER_KEY)
                .rights(Right.Lookup)
                .asAddition(),
            mailboxSession);

        MailboxACL actualACL = storeMailboxManager.getMailbox(parentMailboxId, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();

        assertThat(actualACL.getEntries())
            .doesNotContainKeys(SHARED_USER_KEY);
    }

    @Test
    public void eventShouldUpdateParentWhenMailboxACLAddLookupRight() throws Exception {
        storeRightManager.applyRightsCommand(
            GRAND_CHILD_MAILBOX,
            MailboxACL.command()
                .key(SHARED_USER_KEY)
                .rights(Right.Lookup)
                .asAddition(),
            mailboxSession);

        MailboxACL actualACL = storeMailboxManager.getMailbox(parentMailboxId, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();

        assertThat(actualACL.getEntries())
            .hasSize(2)
            .contains(lookupEntry);
    }

    @Test
    public void eventShouldUpdateParentWhenMailboxACLUpdateLookupRight() throws Exception {
        Mailbox grandChildMailbox = mailboxMapper.getMailboxMapper(mailboxSession).findMailboxById(grandChildMailboxId).block();
        mailboxMapper.getMailboxMapper(mailboxSession).setACL(grandChildMailbox, new MailboxACL(
            new Entry(SHARED_USER_KEY, new Rfc4314Rights(Right.Write)))).block();

        storeRightManager.setRights(
            GRAND_CHILD_MAILBOX,
            new MailboxACL(
                new Entry(SHARED_USER_KEY, new Rfc4314Rights(Right.Lookup))),
            mailboxSession);

        MailboxACL actualACL = storeMailboxManager.getMailbox(parentMailboxId, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();

        assertThat(actualACL.getEntries())
            .hasSize(2)
            .contains(lookupEntry);
    }

    @Test
    public void eventShouldUpdateAllParentWhenMailboxACLUpdateLookupRight() throws Exception {
        Mailbox grandChildMailbox = mailboxMapper.getMailboxMapper(mailboxSession).findMailboxById(grandChildMailboxId).block();
        mailboxMapper.getMailboxMapper(mailboxSession).setACL(grandChildMailbox, new MailboxACL(
            new Entry(SHARED_USER_KEY, new Rfc4314Rights(Right.Write)))).block();

        storeRightManager.setRights(
            GRAND_CHILD_MAILBOX,
            new MailboxACL(
                new Entry(SHARED_USER_KEY, new Rfc4314Rights(Right.Lookup))),
            mailboxSession);

        MailboxACL actualParentACL = storeMailboxManager.getMailbox(parentMailboxId, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();

        MailboxACL actualChildACL = storeMailboxManager.getMailbox(parentMailboxId, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();

        assertThat(actualParentACL.getEntries())
            .contains(lookupEntry);
        assertThat(actualChildACL.getEntries())
            .contains(lookupEntry);
    }

    @Test
    public void eventShouldDoNothingWhenMailboxACLRemoveLookupRight() throws Exception {
        Mailbox grandChildMailbox = mailboxMapper.getMailboxMapper(mailboxSession).findMailboxById(grandChildMailboxId).block();
        mailboxMapper.getMailboxMapper(mailboxSession).setACL(grandChildMailbox, new MailboxACL(
            new Entry(SHARED_USER_KEY, new Rfc4314Rights(Right.Write, Right.Lookup)))).block();

        storeRightManager.applyRightsCommand(
            GRAND_CHILD_MAILBOX,
            MailboxACL.command()
                .key(SHARED_USER_KEY)
                .rights(Right.Lookup)
                .asRemoval(),
            mailboxSession);

        MailboxACL actualACL = storeMailboxManager.getMailbox(parentMailboxId, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();

        assertThat(actualACL.getEntries())
            .doesNotContainKeys(SHARED_USER_KEY);
    }

    @Test
    public void eventShouldDoNothingWhenMailboxACLButNoLookupRight() throws Exception {
        storeRightManager.applyRightsCommand(
            GRAND_CHILD_MAILBOX,
            MailboxACL.command()
                .key(SHARED_USER_KEY)
                .rights(Right.Administer)
                .asAddition(),
            mailboxSession);

        MailboxACL actualACL = storeMailboxManager.getMailbox(parentMailboxId, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();

        assertThat(actualACL.getEntries())
            .doesNotContainKeys(SHARED_USER_KEY);
    }

    @Test
    public void eventShouldDoNothingWhenMailboxACLUpdatedButNoLookupRight() throws Exception {
        storeRightManager.applyRightsCommand(
            GRAND_CHILD_MAILBOX,
            MailboxACL.command()
                .key(SHARED_USER_KEY)
                .rights(Right.Administer)
                .asReplacement(),
            mailboxSession);

        MailboxACL actualACL = storeMailboxManager.getMailbox(parentMailboxId, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();

        assertThat(actualACL.getEntries())
            .doesNotContainKeys(SHARED_USER_KEY);
    }

    @Test
    public void eventShouldUpdateNewParentWhenRenameMailboxWhichContainLookupRight() throws Exception {
        Mailbox childMailbox = mailboxMapper.getMailboxMapper(mailboxSession).findMailboxById(childMailboxId).block();
        mailboxMapper.getMailboxMapper(mailboxSession).setACL(childMailbox, new MailboxACL(
            new Entry(SHARED_USER_KEY, new Rfc4314Rights(Right.Write, Right.Lookup)))).block();

        storeMailboxManager.renameMailbox(CHILD_MAILBOX, MailboxPath.forUser(OWNER_USER, "shared1.sub1New"), mailboxSession);

        MailboxACL actualACL = storeMailboxManager.getMailbox(parentMailboxId1, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();

        assertThat(actualACL.getEntries())
            .contains(lookupEntry);
    }

    @Test
    public void eventShouldNotUpdateNewParentWhenRenameMailboxWhichDoesContainLookupRight() throws Exception {
        Mailbox childMailbox = mailboxMapper.getMailboxMapper(mailboxSession).findMailboxById(childMailboxId).block();
        mailboxMapper.getMailboxMapper(mailboxSession).setACL(childMailbox, new MailboxACL(
            new Entry(SHARED_USER_KEY, new Rfc4314Rights(Right.Write)))).block();

        storeMailboxManager.renameMailbox(CHILD_MAILBOX, MailboxPath.forUser(OWNER_USER, "shared1.sub1New"), mailboxSession);

        MailboxACL actualACL = storeMailboxManager.getMailbox(parentMailboxId1, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();

        assertThat(actualACL.getEntries())
            .doesNotContainKeys(SHARED_USER_KEY);
    }

    @Test
    public void eventShouldUpdateAllNewParentWhenRenameMailboxWhichContainLookupRight() throws Exception {
        Mailbox grandChildMailbox = mailboxMapper.getMailboxMapper(mailboxSession).findMailboxById(grandChildMailboxId).block();
        mailboxMapper.getMailboxMapper(mailboxSession).setACL(grandChildMailbox, new MailboxACL(
            new Entry(SHARED_USER_KEY, new Rfc4314Rights(Right.Write, Right.Lookup)))).block();

        storeMailboxManager.renameMailbox(GRAND_CHILD_MAILBOX, MailboxPath.forUser(OWNER_USER, "shared1.sub1.sub2"), mailboxSession);

        MailboxACL parentActualACL = storeMailboxManager.getMailbox(parentMailboxId1, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();
        MailboxACL childActualACL = storeMailboxManager.getMailbox(childMailboxId1, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();

        assertThat(parentActualACL.getEntries())
            .contains(lookupEntry);
        assertThat(childActualACL.getEntries())
            .contains(lookupEntry);
    }

    @Test
    public void eventShouldDoNothingWhenNegativeACLEntry() throws Exception {
        EntryKey negativeUserKey = EntryKey.createUserEntryKey(SHARED_USER, true);
        storeRightManager.applyRightsCommand(
            GRAND_CHILD_MAILBOX,
            MailboxACL.command()
                .key(negativeUserKey)
                .rights(Right.Lookup)
                .asAddition(),
            mailboxSession);

        MailboxACL actualACL = storeMailboxManager.getMailbox(parentMailboxId, mailboxSession)
            .getMetaData(IGNORE, mailboxSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT)
            .getACL();

        assertThat(actualACL.getEntries())
            .hasSize(1)
            .doesNotContainKeys(negativeUserKey);
    }
}