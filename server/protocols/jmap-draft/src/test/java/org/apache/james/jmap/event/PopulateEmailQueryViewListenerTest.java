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

import static javax.mail.Flags.Flag.DELETED;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.events.Group;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.jmap.memory.projections.MemoryEmailQueryView;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.util.streams.Limit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public class PopulateEmailQueryViewListenerTest {
    private static final Username BOB = Username.of("bob");
    private static final MailboxPath BOB_INBOX_PATH = MailboxPath.inbox(BOB);
    private static final MailboxPath BOB_OTHER_BOX_PATH = MailboxPath.forUser(BOB, "otherBox");

    MailboxSession mailboxSession;
    StoreMailboxManager mailboxManager;

    MessageManager inboxMessageManager;
    MessageManager otherBoxMessageManager;
    PopulateEmailQueryViewListener listener;
    MessageIdManager messageIdManager;
    private MemoryEmailQueryView view;
    private MailboxId inboxId;

    @BeforeEach
    void setup() throws Exception {
        // Default RetryBackoffConfiguration leads each events to be re-executed for 30s which is too long
        // Reducing the wait time for the event bus allow a faster test suite execution without harming test correctness
        RetryBackoffConfiguration backoffConfiguration = RetryBackoffConfiguration.builder()
            .maxRetries(2)
            .firstBackoff(Duration.ofMillis(1))
            .jitterFactor(0.5)
            .build();
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .eventBus(new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), backoffConfiguration, new MemoryEventDeadLetters()))
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        mailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();

        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(BOB, "12345");
        SessionProviderImpl sessionProvider = new SessionProviderImpl(authenticator, FakeAuthorizator.defaultReject());

        view = new MemoryEmailQueryView();
        listener = new PopulateEmailQueryViewListener(messageIdManager, view, sessionProvider);

        resources.getEventBus().register(listener);

        mailboxSession = MailboxSessionUtil.create(BOB);

        inboxId = mailboxManager.createMailbox(BOB_INBOX_PATH, mailboxSession).get();
        inboxMessageManager = mailboxManager.getMailbox(inboxId, mailboxSession);

        MailboxId otherBoxId = mailboxManager.createMailbox(BOB_OTHER_BOX_PATH, mailboxSession).get();
        otherBoxMessageManager = mailboxManager.getMailbox(otherBoxId, mailboxSession);
    }


    @Test
    void deserializeMailboxAnnotationListenerGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.jmap.event.PopulateEmailQueryViewListener$PopulateEmailQueryViewListenerGroup"))
            .isEqualTo(new PopulateEmailQueryViewListener.PopulateEmailQueryViewListenerGroup());
    }

    @Test
    void appendingAMessageShouldAddItToTheView() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .withInternalDate(Date.from(ZonedDateTime.parse("2014-10-30T15:12:00Z").toInstant()))
                .build(emptyMessage(Date.from(ZonedDateTime.parse("2014-10-30T14:12:00Z").toInstant()))),
            mailboxSession).getId();

        assertThat(view.listMailboxContentSortedBySentAt(inboxId, Limit.limit(12)).collectList().block())
            .containsOnly(composedId.getMessageId());
    }

    @Test
    void appendingADeletedMessageSHouldNotAddItToTheView() throws Exception {
        inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .withInternalDate(Date.from(ZonedDateTime.parse("2014-10-30T15:12:00Z").toInstant()))
                .withFlags(new Flags(DELETED))
                .build(emptyMessage(Date.from(ZonedDateTime.parse("2014-10-30T14:12:00Z").toInstant()))),
            mailboxSession).getId();

        assertThat(view.listMailboxContentSortedBySentAt(inboxId, Limit.limit(12)).collectList().block())
            .isEmpty();
    }

    @Test
    void removingDeletedFlagsShouldAddItToTheView() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .withInternalDate(Date.from(ZonedDateTime.parse("2014-10-30T15:12:00Z").toInstant()))
                .withFlags(new Flags(DELETED))
                .build(emptyMessage(Date.from(ZonedDateTime.parse("2014-10-30T14:12:00Z").toInstant()))),
            mailboxSession).getId();

        inboxMessageManager.setFlags(new Flags(), MessageManager.FlagsUpdateMode.REPLACE, MessageRange.all(), mailboxSession);

        assertThat(view.listMailboxContentSortedBySentAt(inboxId, Limit.limit(12)).collectList().block())
            .containsOnly(composedId.getMessageId());
    }

    @Test
    void addingDeletedFlagsShouldRemoveItToTheView() throws Exception {
        inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .withInternalDate(Date.from(ZonedDateTime.parse("2014-10-30T15:12:00Z").toInstant()))
                .build(emptyMessage(Date.from(ZonedDateTime.parse("2014-10-30T14:12:00Z").toInstant()))),
            mailboxSession).getId();

        inboxMessageManager.setFlags(new Flags(DELETED), MessageManager.FlagsUpdateMode.REPLACE, MessageRange.all(), mailboxSession);

        assertThat(view.listMailboxContentSortedBySentAt(inboxId, Limit.limit(12)).collectList().block())
            .isEmpty();
    }

    @Test
    void deletingMailboxShouldClearTheView() throws Exception {
        inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .withInternalDate(Date.from(ZonedDateTime.parse("2014-10-30T15:12:00Z").toInstant()))
                .build(emptyMessage(Date.from(ZonedDateTime.parse("2014-10-30T14:12:00Z").toInstant()))),
            mailboxSession).getId();

        mailboxManager.deleteMailbox(inboxId, mailboxSession);

        assertThat(view.listMailboxContentSortedBySentAt(inboxId, Limit.limit(12)).collectList().block())
            .isEmpty();
    }

    @Test
    void deletingEmailShouldClearTheView() throws Exception {
        ComposedMessageId composedMessageId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .withInternalDate(Date.from(ZonedDateTime.parse("2014-10-30T15:12:00Z").toInstant()))
                .build(emptyMessage(Date.from(ZonedDateTime.parse("2014-10-30T14:12:00Z").toInstant()))),
            mailboxSession).getId();

        inboxMessageManager.delete(ImmutableList.of(composedMessageId.getUid()), mailboxSession);

        assertThat(view.listMailboxContentSortedBySentAt(inboxId, Limit.limit(12)).collectList().block())
            .isEmpty();
    }

    private Message emptyMessage(Date sentAt) throws Exception {
        return Message.Builder.of()
            .setSubject("Empty message")
            .setDate(sentAt)
            .setBody("", StandardCharsets.UTF_8)
            .build();
    }
}