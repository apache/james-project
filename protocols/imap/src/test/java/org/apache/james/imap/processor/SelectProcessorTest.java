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

package org.apache.james.imap.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.decode.main.OutputStreamImapResponseWriter;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.encode.main.DefaultLocalizer;
import org.apache.james.imap.main.ResponseEncoder;
import org.apache.james.imap.message.request.AbstractMailboxSelectionRequest;
import org.apache.james.imap.message.request.SelectRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class SelectProcessorTest {
    private static final Username BOB = Username.of("bob");

    private SelectProcessor testee;
    private InMemoryMailboxManager mailboxManager;
    private MailboxSession mailboxSession;
    private UidValidity uidValidity;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources integrationResources = InMemoryIntegrationResources.defaultResources();

        mailboxManager = integrationResources.getMailboxManager();
        testee = new SelectProcessor(mailboxManager,
            integrationResources.getEventBus(),
            new UnpooledStatusResponseFactory(),
            new RecordingMetricFactory());

        mailboxSession = mailboxManager.createSystemSession(Username.of("bob"));
        mailboxManager.createMailbox(MailboxPath.inbox(BOB), mailboxSession);
        uidValidity = mailboxManager.getMailbox(MailboxPath.inbox(BOB), mailboxSession).getMailboxEntity().getUidValidity();
    }

    @Test
    void vanishedResponsesShouldNotBeSentWhenNoDeletes() throws Exception {
        FakeImapSession session = new FakeImapSession();
        session.authenticated();
        session.setMailboxSession(mailboxSession);
        EnableProcessor.getEnabledCapabilities(session)
            .add(ImapConstants.SUPPORTS_QRESYNC);

        MessageManager mailbox = mailboxManager.getMailbox(MailboxPath.inbox(BOB), mailboxSession);
        MessageManager.AppendCommand appendCommand = MessageManager.AppendCommand
            .builder()
            .withFlags(new Flags(Flags.Flag.SEEN))
            .notRecent()
            .build(new SharedByteArrayInputStream("header: value\r\n\r\nbody".getBytes()));
        mailbox.appendMessage(appendCommand, mailboxSession);
        mailbox.appendMessage(appendCommand, mailboxSession);
        mailbox.appendMessage(appendCommand, mailboxSession);
        mailbox.appendMessage(appendCommand, mailboxSession);
        mailbox.appendMessage(appendCommand, mailboxSession);

        UidRange[] uidRanges = null;
        IdRange[] sequences = null;
        SelectRequest message = new SelectRequest("INBOX", false,
            AbstractMailboxSelectionRequest.ClientSpecifiedUidValidity.valid(uidValidity),
            4L, uidRanges, uidRanges, sequences, new Tag("AA"));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        testee.process(message,
            new ResponseEncoder(
                new DefaultImapEncoderFactory(new DefaultLocalizer(), true).buildImapEncoder(),
                new ImapResponseComposerImpl(new OutputStreamImapResponseWriter(outputStream))),
            session);

        assertThat(new String(outputStream.toByteArray()))
            .doesNotContain("* VANISHED (EARLIER) 1:4");
    }

    @Test
    void vanishedResponsesShouldBeSentWhenDeletes() throws Exception {
        FakeImapSession session = new FakeImapSession();
        session.authenticated();
        session.setMailboxSession(mailboxSession);
        EnableProcessor.getEnabledCapabilities(session)
            .add(ImapConstants.SUPPORTS_QRESYNC);

        MessageManager mailbox = mailboxManager.getMailbox(MailboxPath.inbox(BOB), mailboxSession);
        MessageManager.AppendCommand appendCommand = MessageManager.AppendCommand
            .builder()
            .withFlags(new Flags(Flags.Flag.SEEN))
            .notRecent()
            .build(new SharedByteArrayInputStream("header: value\r\n\r\nbody".getBytes()));
        mailbox.appendMessage(appendCommand, mailboxSession);

        MessageManager.AppendResult msg2 = mailbox.appendMessage(appendCommand, mailboxSession);

        mailbox.appendMessage(appendCommand, mailboxSession);

        MessageManager.AppendResult msg4 = mailbox.appendMessage(appendCommand, mailboxSession);

        mailbox.appendMessage(appendCommand, mailboxSession);

        mailbox.delete(ImmutableList.of(msg2.getId().getUid(), msg4.getId().getUid()), mailboxSession);

        UidRange[] uidRanges = null;
        IdRange[] sequences = null;
        SelectRequest message = new SelectRequest("INBOX", false,
            AbstractMailboxSelectionRequest.ClientSpecifiedUidValidity.valid(uidValidity),
            4L, uidRanges, uidRanges, sequences, new Tag("AA"));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        ImapResponseComposerImpl composer = new ImapResponseComposerImpl(new OutputStreamImapResponseWriter(outputStream));
        testee.process(message,
            new ResponseEncoder(
                new DefaultImapEncoderFactory(new DefaultLocalizer(), true).buildImapEncoder(),
                composer),
            session);
        composer.flush();

        assertThat(new String(outputStream.toByteArray()))
            .contains("* VANISHED (EARLIER) 2,4");
    }
}