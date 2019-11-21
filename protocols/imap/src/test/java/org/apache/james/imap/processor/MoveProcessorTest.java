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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.request.CopyRequest;
import org.apache.james.imap.message.request.MoveRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManager.MailboxCapabilities;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

public class MoveProcessorTest {
    private static final Username USERNAME = Username.of("username");
    private static final MailboxPath INBOX = MailboxPath.inbox(USERNAME);

    private MoveProcessor testee;
    private ImapProcessor mockNextProcessor;
    private MailboxManager mockMailboxManager;
    private StatusResponseFactory mockStatusResponseFactory;
    private ImapProcessor.Responder mockResponder;
    private ImapSession mockImapSession;
    private MailboxSession mailboxSession;

    @Before
    public void setUp() {
        mockNextProcessor = mock(ImapProcessor.class);
        mockMailboxManager = mock(MailboxManager.class);
        mockStatusResponseFactory = mock(StatusResponseFactory.class);
        mockResponder = mock(ImapProcessor.Responder.class);
        mockImapSession = mock(ImapSession.class);
        mailboxSession = MailboxSessionUtil.create(USERNAME);

        when(mockMailboxManager.hasCapability(eq(MailboxCapabilities.Move))).thenReturn(true);
        testee = new MoveProcessor(mockNextProcessor, mockMailboxManager, mockStatusResponseFactory, new NoopMetricFactory());
        verify(mockMailboxManager).hasCapability(MailboxCapabilities.Move);
    }

    @Test
    public void getImplementedCapabilitiesShouldContainMoveWhenSupportedByMailboxManager() {
        assertThat(testee.getImplementedCapabilities(null)).containsExactly(ImapConstants.MOVE_COMMAND_NAME);
    }

    @Test
    public void getImplementedCapabilitiesShouldNotContainMoveWhenUnSupportedByMailboxManager() {
        when(mockMailboxManager.hasCapability(eq(MailboxCapabilities.Move))).thenReturn(false);
        assertThat(new MoveProcessor(mockNextProcessor, mockMailboxManager, mockStatusResponseFactory, new NoopMetricFactory())
                .getImplementedCapabilities(null)).isEmpty();
    }

    @Test
    public void processShouldWork() throws Exception {
        MoveRequest moveRequest = new MoveRequest(ImapCommand.anyStateCommand("Name"), new IdRange[] {new IdRange(4, 6)}, ImapConstants.INBOX_NAME, true, TAG);

        when(mockImapSession.getState()).thenReturn(ImapSessionState.SELECTED);
        when(mockImapSession.getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY)).thenReturn(mailboxSession);
        MailboxPath selected = new MailboxPath(INBOX, "selected");
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        when(selectedMailbox.getLastUid()).thenReturn(Optional.of(MessageUid.of(8)));
        when(selectedMailbox.existsCount()).thenReturn(8L);
        when(selectedMailbox.getPath()).thenReturn(selected);
        when(mockImapSession.getSelected()).thenReturn(selectedMailbox);
        when(mockMailboxManager.mailboxExists(INBOX, mailboxSession)).thenReturn(true);
        MessageManager targetMessageManager = mock(MessageManager.class);
        when(mockMailboxManager.getMailbox(INBOX, mailboxSession)).thenReturn(targetMessageManager);
        Mailbox mailbox = mock(Mailbox.class);
        when(mailbox.getUidValidity()).thenReturn(58L);
        when(targetMessageManager.getMailboxEntity()).thenReturn(mailbox);
        StatusResponse okResponse = mock(StatusResponse.class);
        when(mockStatusResponseFactory.taggedOk(any(Tag.class), any(ImapCommand.class), any(HumanReadableText.class), any(StatusResponse.ResponseCode.class))).thenReturn(okResponse);
        when(mockMailboxManager.moveMessages(MessageRange.range(MessageUid.of(4), MessageUid.of(6)), selected, INBOX, mailboxSession))
            .thenReturn(Lists.<MessageRange>newArrayList(MessageRange.range(MessageUid.of(4), MessageUid.of(6))));

        testee.process(moveRequest, mockResponder, mockImapSession);

        verify(mockMailboxManager).startProcessingRequest(mailboxSession);
        verify(mockMailboxManager).endProcessingRequest(mailboxSession);
        verify(mockMailboxManager).mailboxExists(INBOX, mailboxSession);
        verify(mockMailboxManager).getMailbox(INBOX, mailboxSession);
        verify(mockMailboxManager).moveMessages(MessageRange.range(MessageUid.of(4), MessageUid.of(6)), selected, INBOX, mailboxSession);
        verify(targetMessageManager).getMailboxEntity();
        verify(mockResponder).respond(okResponse);
        verifyNoMoreInteractions(mockMailboxManager, targetMessageManager, mockResponder, mockNextProcessor);
    }


    @Test
    public void processShouldWorkWithMultipleRanges() throws Exception {
        MoveRequest moveRequest = new MoveRequest(ImapCommand.anyStateCommand("Name"), new IdRange[] {new IdRange(5, 6), new IdRange(1,3)}, ImapConstants.INBOX_NAME, true, TAG);

        when(mockImapSession.getState()).thenReturn(ImapSessionState.SELECTED);
        when(mockImapSession.getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY)).thenReturn(mailboxSession);
        MailboxPath selected = new MailboxPath(INBOX, "selected");
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        when(selectedMailbox.getLastUid()).thenReturn(Optional.of(MessageUid.of(8)));
        when(selectedMailbox.existsCount()).thenReturn(8L);
        when(selectedMailbox.getPath()).thenReturn(selected);
        when(mockImapSession.getSelected()).thenReturn(selectedMailbox);
        when(mockMailboxManager.mailboxExists(INBOX, mailboxSession)).thenReturn(true);
        MessageManager targetMessageManager = mock(MessageManager.class);
        when(mockMailboxManager.getMailbox(INBOX, mailboxSession)).thenReturn(targetMessageManager);
        Mailbox mailbox = mock(Mailbox.class);
        when(mailbox.getUidValidity()).thenReturn(58L);
        when(targetMessageManager.getMailboxEntity()).thenReturn(mailbox);
        StatusResponse okResponse = mock(StatusResponse.class);
        when(mockStatusResponseFactory.taggedOk(any(Tag.class), any(ImapCommand.class), any(HumanReadableText.class), any(StatusResponse.ResponseCode.class))).thenReturn(okResponse);

        testee.process(moveRequest, mockResponder, mockImapSession);

        verify(mockMailboxManager).startProcessingRequest(mailboxSession);
        verify(mockMailboxManager).endProcessingRequest(mailboxSession);
        verify(mockMailboxManager).mailboxExists(INBOX, mailboxSession);
        verify(mockMailboxManager).getMailbox(INBOX, mailboxSession);
        verify(mockMailboxManager).moveMessages(MessageRange.range(MessageUid.of(5), MessageUid.of(6)), selected, INBOX, mailboxSession);
        verify(mockMailboxManager).moveMessages(MessageRange.range(MessageUid.of(1), MessageUid.of(3)), selected, INBOX, mailboxSession);
        verify(targetMessageManager).getMailboxEntity();
        verify(mockResponder).respond(okResponse);
        verifyNoMoreInteractions(mockMailboxManager, targetMessageManager, mockResponder, mockNextProcessor);
    }

    @Test
    public void processShouldRespondNoOnUnExistingTargetMailbox() throws Exception {
        MoveRequest moveRequest = new MoveRequest(ImapCommand.anyStateCommand("Name"), new IdRange[] {new IdRange(5, 6), new IdRange(1,3)}, ImapConstants.INBOX_NAME, true, TAG);

        when(mockImapSession.getState()).thenReturn(ImapSessionState.SELECTED);
        when(mockImapSession.getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY)).thenReturn(mailboxSession);
        MailboxPath selected = new MailboxPath(INBOX, "selected");
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        when(selectedMailbox.getLastUid()).thenReturn(Optional.of(MessageUid.of(8)));
        when(selectedMailbox.existsCount()).thenReturn(8L);
        when(selectedMailbox.getPath()).thenReturn(selected);
        when(mockImapSession.getSelected()).thenReturn(selectedMailbox);
        when(mockMailboxManager.mailboxExists(INBOX, mailboxSession)).thenReturn(false);

        StatusResponse noResponse = mock(StatusResponse.class);
        when(mockStatusResponseFactory.taggedNo(any(Tag.class), any(ImapCommand.class), any(HumanReadableText.class), any(StatusResponse.ResponseCode.class))).thenReturn(noResponse);

        testee.process(moveRequest, mockResponder, mockImapSession);

        verify(mockMailboxManager).startProcessingRequest(mailboxSession);
        verify(mockMailboxManager).endProcessingRequest(mailboxSession);
        verify(mockMailboxManager).mailboxExists(INBOX, mailboxSession);
        verify(mockResponder).respond(noResponse);
        verifyNoMoreInteractions(mockMailboxManager, mockResponder, mockNextProcessor);
    }

    @Test
    public void processShouldRespondNoOnMailboxException() throws Exception {
        MoveRequest moveRequest = new MoveRequest(ImapCommand.anyStateCommand("Name"), new IdRange[] {new IdRange(5, 6), new IdRange(1,3)}, ImapConstants.INBOX_NAME, true, TAG);

        when(mockImapSession.getState()).thenReturn(ImapSessionState.SELECTED);
        when(mockImapSession.getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY)).thenReturn(mailboxSession);
        MailboxPath selected = new MailboxPath(INBOX, "selected");
        SelectedMailbox selectedMailbox = mock(SelectedMailbox.class);
        when(selectedMailbox.getLastUid()).thenReturn(Optional.of(MessageUid.of(8)));
        when(selectedMailbox.existsCount()).thenReturn(8L);
        when(selectedMailbox.getPath()).thenReturn(selected);
        when(mockImapSession.getSelected()).thenReturn(selectedMailbox);
        when(mockMailboxManager.mailboxExists(INBOX, mailboxSession)).thenThrow(new MailboxException());

        StatusResponse noResponse = mock(StatusResponse.class);
        when(mockStatusResponseFactory.taggedNo(any(Tag.class), any(ImapCommand.class), any(HumanReadableText.class))).thenReturn(noResponse);

        testee.process(moveRequest, mockResponder, mockImapSession);

        verify(mockMailboxManager).startProcessingRequest(mailboxSession);
        verify(mockMailboxManager).endProcessingRequest(mailboxSession);
        verify(mockMailboxManager).mailboxExists(INBOX, mailboxSession);
        verify(mockResponder).respond(noResponse);
        verifyNoMoreInteractions(mockMailboxManager, mockResponder, mockNextProcessor);
    }

    @Test
    public void processShouldNotHandleCopyRequests() {
        CopyRequest copyRequest = new CopyRequest(ImapCommand.anyStateCommand("Name"), new IdRange[] {new IdRange(4, 6)}, ImapConstants.INBOX_NAME, true, TAG);

        testee.process(copyRequest, mockResponder, mockImapSession);

        verify(mockNextProcessor).process(copyRequest, mockResponder, mockImapSession);
        verifyNoMoreInteractions(mockMailboxManager, mockResponder, mockNextProcessor);
    }
}
