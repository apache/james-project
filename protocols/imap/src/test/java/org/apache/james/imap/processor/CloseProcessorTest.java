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

import static org.apache.james.imap.ImapFixture.TAG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.CloseRequest;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import reactor.core.publisher.Mono;

class CloseProcessorTest {
    private static final MailboxId MAILBOX_ID = TestId.of(42);

    private CloseProcessor testee;
    private MailboxManager mailboxManager;
    private MessageManager messageManager;
    private ImapProcessor.Responder responder;
    private FakeImapSession imapSession;
    private MailboxSession mailboxSession;

    @BeforeEach
    void setUp() throws Exception {
        mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.manageProcessing(any(), any())).thenAnswer((Answer<Mono>) invocation -> (Mono) invocation.getArguments()[0]);
        responder = mock(ImapProcessor.Responder.class);
        mailboxSession = MailboxSessionUtil.create(Username.of("bob"));
        imapSession = new FakeImapSession();
        imapSession.authenticated();
        imapSession.setMailboxSession(mailboxSession);

        messageManager = mock(MessageManager.class);
        when(messageManager.getMailboxEntity()).thenReturn(mock(Mailbox.class));

        testee = new CloseProcessor(mailboxManager, new UnpooledStatusResponseFactory(), new RecordingMetricFactory());
    }

    @Test
    void closeShouldAnswerOkWithoutExpungingWhenTheUserCannotExpunge() throws Exception {
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        when(selectedMailbox.getMailboxId()).thenReturn(MAILBOX_ID);
        when(selectedMailbox.messageManager()).thenReturn(Mono.just(messageManager));
        when(selectedMailbox.isReadOnly()).thenReturn(false);
        when(selectedMailbox.deselect()).thenReturn(Mono.empty());
        imapSession.selected(selectedMailbox).block();
        when(mailboxManager.hasRight(any(Mailbox.class), eq(MailboxACL.Right.PerformExpunge), eq(mailboxSession))).thenReturn(false);

        testee.processRequestReactive(new CloseRequest(TAG), imapSession, responder).block();

        ArgumentCaptor<ImapResponseMessage> captor = ArgumentCaptor.forClass(ImapResponseMessage.class);
        verify(responder).respond(captor.capture());
        assertThat(captor.getValue()).matches(StatusResponseTypeMatcher.OK_RESPONSE_MATCHER::matches);
        assertThat(imapSession.getSelected()).isNull();
        verify(messageManager, never()).expungeReactive(any(), any());
    }
}
